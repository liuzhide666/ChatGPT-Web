# ChatGPT Web 1.0.3 — Shift+Enter 与加载/缓存/刷新最终设计

## 目标

将 RC4 的 `Enter=发送` 保持不变，同时把 `Shift+Enter` 从“放行给 ChatGPT 自己处理”改成“由 bridge 在 capture 阶段明确拥有 hard line break 语义”，从根源避免 ChatGPT 当前页面 handler 把 Shift+Enter 继续当发送。最终发布身份为 `versionName 1.0.3 / versionCode 105 / bridge 1.0.5`。

## 根因结论

RC4 对普通 Enter 在 capture 阶段完成语义发送后会 `preventDefault()+stopImmediatePropagation()`；但对 Shift+Enter 只设置短期 line-break token 后直接 return，原始 keydown 仍继续传播。若当前 ChatGPT/ProseMirror keymap 在该页面变体把 Shift+Enter 解释成 submit，发送会在 `beforeinput` 之前发生，因此 RC4 的 token 无法阻止它。公开 ProseMirror资料也表明 Shift+Enter 并不是 base keymap 的固定语义，必须由具体应用显式绑定；移动 Firefox/Android 下 Shift+Enter 与普通 Enter 的事件语义尤其不可靠。

## 方案

1. 主 composer 的 Shift+Enter 在 capture `keydown` 中立即 `preventDefault()` 与 `stopImmediatePropagation()`，不再让 ChatGPT 自身 handler 收到原事件。
2. 新增 `insertComposerLineBreak(editor)`：
   - `textarea`：`setRangeText("\n", selectionStart, selectionEnd, "end")`，随后发一个 `input` 事件；
   - ProseMirror/contenteditable：优先 `document.execCommand("insertLineBreak")`。Firefox/Gecko 已支持该编辑命令，并能保留浏览器编辑/撤销语义；
   - 若 `insertLineBreak` 返回 false，再尝试 `execCommand("insertText", false, "\n")`；
   - 两种方法都失败时仍消费 Shift+Enter，记录 `LINE_BREAK_FAILED`，绝不退化为发送。
3. 用 `lineBreakInsertionActive` 防止 `execCommand()` 触发的嵌套 `beforeinput/input` 递归进入发送路由；同时使用一次性 `suppressNextShiftLineBreakInput` token 压制 Shift+Enter 后可能迟到的浏览器 `beforeinput`。该 token 不依赖 150ms/600ms 等设备时序猜测；新的真实 Enter keydown 会主动清除旧 token，另有 1s 兜底超时避免状态永久残留。
4. “主编辑器识别”与“是否可发送”分离：Shift+Enter 只要求确认 `#prompt-textarea` 主编辑器，即使发送按钮在回答生成/A-B UI 中暂时未挂载，也必须能够换行；普通 Enter 发送才要求同 composer 的 send control 存在且 enabled。
5. 普通 Enter、中文 composition、Native IME fallback 保持 RC4 的窄边界：只在主 composer、非 composing、sendReady 时发送；不生成 Android `KeyEvent`。
6. 加载/缓存/刷新继续使用共享 Gecko profile 的默认 cache：无 bypass-cache、无 Cookie/storage purge、无普通生命周期 reload。为减少超长对话误恢复，将 heartbeat/stale/grace 调整为：visible heartbeat 10s、foreground grace 15s、stale 45s、soft→hard 30s、hard→UI 45s。仍只允许每 incident 一次 soft reload 与一次 hard session replacement。
6. 自动恢复继续要求当前顶层页面为 `https://chatgpt.com`，并继续在 dirty/composition/sendReady、录音、文件/外部流程、loading、后台状态下完全禁用。

## 正式版 Gate

- `versionName=1.0.3`, `versionCode=105`, extension `1.0.5`。
- Python / Java host / API37 compile / Node content.js event simulation / minSdk26 NewApi lint 全绿。
- Node QA 必须真实验证：Enter 发送；Shift+Enter 不发送并执行 line-break command；Shift+Enter command 失败时也不发送；composition 不发送；非主输入框不拦截。
- 最终 DEX 只允许应用命名空间；实际 GeckoView 153 ABI 复核。
- APK v2/v3 同 canonical RSA-4096 signer；16 KiB alignment；39 native `.so`、resources、四个非应用 DEX 与 1.0.1 byte-identical。
- 正式 7z 多卷：只解压 `.7z.001` 直接得到 APK，做完整 round-trip hash 验证。
- 用户已提供 RC4 目标设备反馈作为运行时依据，但 1.0.3 新 Shift+Enter 路径在本构建会话中仍没有在线 ADB 设备，因此发布文档必须区分“用户验收反馈”与“本会话自动化证据”。
## 最终实现补充（冻结前审查）

- `keydown` / `beforeinput` 在 `window` capture 层处理，使语义路由早于 document/root/editor 层的页面处理器。
- Shift+Enter 只依赖“确认是主编辑器”，不依赖发送按钮已挂载；普通 Enter 仍必须确认同一 composer 内存在有效发送控件。
- Shift+Enter 后仅保留 **150 ms** 的 trailing `beforeinput` 抑制窗口，用于吞掉同一 Gecko 事件序列的尾随 line-break 事件；不使用 RC4/早期草案中过长的保护窗口，避免吞掉下一次真实 Enter。
- Native `performEditorAction()` fallback 额外读取最近硬件 `KeyEvent.isShiftPressed()`；硬件 Shift 激活时即使 IME 报 SEND/DONE 也不接受语义发送。Native 层仍不合成 Enter KeyEvent。
- line-break 插入具备重入保护；`execCommand("insertLineBreak")` 失败时退到 `insertText("\n")`，两者均失败时仍消费 Shift+Enter，绝不退化为发送。

