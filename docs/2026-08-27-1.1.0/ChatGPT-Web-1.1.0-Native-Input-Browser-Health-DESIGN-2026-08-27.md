# ChatGPT Web 1.1.0 — Native-first Input + Browser Health Supervisor 设计

## 目标

将 ChatGPT Web 从“GeckoView 外壳 + DOM 输入补丁 + 固定轮询恢复”升级为可长期维护的浏览器级架构：实体/IME 输入先在 Android 原生层归一化为语义命令，ChatGPT DOM 只由版本化 Site Adapter 执行；加载、缓存、刷新由多信号 BrowserHealthSupervisor 管理。版本身份：`versionName=1.1.0`、`versionCode=110`、built-in extension `1.1.0`，GeckoView 继续冻结 `153.0.20260810162159`。

## 1. 已确认根因

1. Android/Gecko 输入存在两条路径：View `KeyEvent` 与 `InputConnection`。软件键盘不保证生成标准 KeyEvent，因此不能把 Shift/Enter 的最终语义押在 DOM `keydown.shiftKey` 上。
2. 1.0.3 的 Web 层 hard-break 在 Host/Node 中通过，但目标设备仍失败，说明真实设备上的 modifier/event 可能在到达 DOM 前已经改变或丢失。
3. 当前 page-health 使用固定 5 秒轮询，主要依据 WebExtension heartbeat；bridge 故障、页面故障、长任务和真正卡死没有被充分区分。

## 2. Native-first Composer Input Router

新增 `ComposerInputRouter` 纯策略类，把输入归一化为 `NONE / SUBMIT / INSERT_LINE_BREAK`。输入来源统一标记为 `VIEW_KEY / INPUT_CONNECTION_KEY / INPUT_CONNECTION_COMMIT / IME_ACTION / DOM_FALLBACK / NATIVE_UI`。

- 确认当前为 ChatGPT 主 composer、非 composition 时：真实 `Shift+Enter` => `INSERT_LINE_BREAK`；真实普通 Enter 且 sendReady => `SUBMIT`。
- 真实硬件/IME `sendKeyEvent()` 由 `ChatGPTGeckoView` 在 Gecko 之前处理；被消费的 Enter DOWN/UP 成对消费，避免孤立 key-up 进入页面。
- `performEditorAction(SEND/DONE)` 只映射为 `SUBMIT`，不推断 Shift。
- `commitText()` 只在文本**恰好是单独换行符**（`\n` / `\r` / `\r\n`）时归一化为 Enter 意图；普通文本、多行粘贴、`setComposingText/delete/selection` 全部继续委托 Gecko。Native 插入 Shift+Enter 时直接调用捕获到的 raw InputConnection，绕过 wrapper，避免递归。
- Native-first 成功后原 KeyEvent 不再到达 Gecko/DOM；DOM `keydown` 仅作为最低级兼容 fallback。DOM `beforeinput(insertParagraph/insertLineBreak)` **不再具有 submit 权限**，只负责 DOM fallback Shift+Enter 的尾随事件抑制/诊断，防止 Native newline 被 Web 层二次翻译成发送。

## 3. Input Trace Buffer

新增固定容量 `InputTraceBuffer`，仅保存元数据，禁止 prompt/text/clipboard/cookie/token：
`timestamp, source, action, shift, composing, composerGeneration, result`。
用于定位信号究竟在 View、InputConnection、Native bridge 或 DOM 哪一层丢失。正式 UI 不主动展示文本数据；诊断日志可输出最近元数据。

## 4. Versioned ChatGPT Site Adapter

新增 `assets/chatgpt_bridge/site_adapter.js`，唯一负责：
- 主 composer 定位；
- send control 变体定位；
- `submit()`；
- `insertLineBreak()`；
- composer snapshot。

`content.js` 只负责 WebExtension transport、native command、DOM fallback 和 liveness。网页结构变化只修改 Site Adapter，不再牵连 Android 输入/健康策略。

Native port 命令：`SUBMIT_COMPOSER`、`INSERT_LINE_BREAK`、`PING`。执行后通过 metadata-only `INPUT_ROUTE/PONG/COMPOSER_STATE` 回报，不传 prompt 文本。

## 5. BrowserHealthSupervisor

用事件驱动 deadline 状态机替代 `PAGE_HEALTH_POLL_MS=5000` 永久轮询。健康信号分三组：

- Gecko content：page start/progress/page stop/first-contentful-paint；
- Web bridge：READY/HEARTBEAT/PONG/port connected；
- 用户/系统：touch/key interaction、foreground/background、loading、recording、external flow、当前 top-level trusted origin。

Supervisor 每次收到信号重新计算唯一 `nextDeadlineMs`，后台取消 deadline，不固定唤醒主线程。

## 6. 四级恢复梯度

1. `REBIND_BRIDGE`：bridge stale 但页面仍有 Gecko/user 活性时，仅重新绑定 MessageDelegate/Port，并尝试 PING；不 reload。
2. `SOFT_RELOAD`：bridge 与 content activity 同时 stale，且无草稿/composition/media/external/loading，执行默认 cache `session.reload()`。
3. `HARD_SESSION_REPLACE`：soft 后仍无新 FCP/bridge 活性，销毁当前 page context，以 shared GeckoRuntime/profile 打开 last trusted ChatGPT URI。
4. `SHOW_RECOVERY_UI`：hard 后仍失败，停止自动操作。

每 incident：rebind 最多 1 次、soft 最多 1 次、hard 最多 1 次。

## 7. 缓存与生命周期原则

- 正常加载与自动恢复统一使用 Gecko 默认缓存规则；禁止 `LOAD_FLAGS_BYPASS_CACHE`、Cookie/storage purge。
- Launcher 显式重入仍打开 fresh ChatGPT home；Recents/普通 foreground resume 保留 live Session。
- 不添加 WorkManager/后台刷新 Service；不可见 Session 使用 `setActive(false)`。
- 不通过删除 ChatGPT DOM/React 节点优化长对话。


## 8. Release 安全硬化

- 正式/候选发布 Manifest 必须显式 `android:debuggable="false"`；1.0.3 继承的 `debuggable=true` 不再允许进入 1.1.0。
- 新增 `tools/patch_release_manifest.py`，对实际 GeckoView 合并后的 binary Manifest 做可重复 patch：`versionCode/versionName/debuggable=false`，并要求 89 service / 1 provider / 1 receiver 组件图保持可解析。
- `allowBackup=false`、`usesCleartextTraffic=false`、现有权限集合继续保持；不公开 signing secret。

## 9. 版本和兼容边界

- `1.1.0 / vc110 / extension 1.1.0`。
- GeckoView 保持 153，本轮不并行升级引擎。
- 包名、min/target/compile SDK、Java17、release certificate、上传/下载/麦克风/外部流程全部保持。

## 10. 验证要求

TDD 必须覆盖：View KeyEvent Enter/Shift+Enter、InputConnection sendKeyEvent、IME SEND/DONE、composition、被消费 DOWN/UP 成对、stale generation、Site Adapter command、DOM fallback、trace 无文本泄露；Supervisor 覆盖 bridge-only failure、content-only activity、双 stale、dirty/composition/media/external/background/trusted-origin guard、deadline reschedule、每 incident 有界恢复。

最终 Gate：完整历史 Python/Java、API37 compile、Node 真实 content-script simulation、minSdk26 lint、真实 GeckoView 153 ABI、app-only DEX、`debuggable=false`、v2/v3 signer、16KiB、payload byte audit、标准 7z 多卷 round-trip。没有目标 ADB 设备时只发布 RC，不得把实体设备 Shift+Enter 宣称为已验证，也不得替换 1.0.3 canonical。
