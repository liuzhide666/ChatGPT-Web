# ChatGPT-Web 1.1.0 后续工程实施总方案（2026-10-17）

> 状态：**PROPOSED / 待逐项执行**。此文件是工程计划，不代表已发布新版 APK 或 HarmonyOS HAP。此文件可公开，但不得附加个人账号、Cookie、设备序列号、口令、签名私钥或未脱敏日志。
>
> 主仓库：[liuzhide666/ChatGPT-Web](https://github.com/liuzhide666/ChatGPT-Web)；当前代码基线 `b4bb098a1b62f2e87450def63a918c6616a5aaeb`；项目资产主档：ChatGPT Library `/任务项目/01-软件开发/ChatGPT-Web/`。

## 0. 决策摘要与范围

**决策 D1：Android 维护与鸿蒙原生验证采用两条独立产品线、共用用户体验验收矩阵。**

- **Android-A**：保持现有 Android/Java/GeckoView 架构，目标是在可明确识别的 Android 或 Android 兼容环境中运行；开发 `1.1.1/vc114` 以构建可复现与最小修复为主，再把浏览器升级、窗口与 targetSdk 迁移分别纳入后续候选版本。不要假设“能安装 APK”就意味着鸿蒙原生兼容。
- **Harmony-H**：新建独立 ArkTS/ArkUI/ArkWeb 原型，优先完成真实设备的登录、编辑、持久化、文件与音频可行性证明。只有核心功能通过后，才创建可发布的 Harmony HAP alpha；禁止把 APK 改名为 HAP 或假定能够嵌入 GeckoView。
- **正式基线不变**：保持 `1.1.0/vc113` 和 Library 中已发布原始 APK 原字节与哈希，保留 `1.0.3 Formal` 回滚资产；后续任何修改都产生新身份、新构建证据和新真机门禁。
- **非官方性质**：这是用户维护的网页外壳，不代表 OpenAI 或华为官方产品；未经许可不复制网站服务内容、不绕过登录或安全策略。

### 排除项

不开发替代 ChatGPT 的聊天后端；不代理/收集 ChatGPT 账号密码；不通过 DOM 注入实现发送/换行；不在后台擅自刷新页面、清空 Cookie、localStorage、Service Worker 或浏览器 Profile；不在公开 GitHub 存储签名材料、会话、APK 发行分卷、未脱敏日志。

## 1. 已核验现状（2026-10-17）

| 对象 | 现状和来源 | 尚不证明 |
|---|---|---|
| GitHub `main` | `b4bb098a…`，1.1.0 代码、文档与测试，另含一个根目录扁平 ZIP | 未发生新 APK/HAP 的构建或安装 |
| 仓库 | 当前 **PUBLIC**，源码 CI `source-checks` 最近运行成功 | 尚未证实 `main` 有强制分支保护 |
| CI | `pytest` 174 项、Node 键盘中立检查、脚本语法、路径卫生检查 | 未执行 Android SDK 编译、Java Host 30 组、签名/真机 |
| Android | `com.liuchenlin.chatgptweb`；Java 17；`AGP 9.1.1`；`min 26 / target 28 / compile 37`；GeckoView `153.0.20260810162159`；扩展 `1.1.2`、Bridge v3 | 未证实新的安卓设备或 HarmonyOS 7 兼容环境通过 |
| 可复现构建 | `build.gradle` 存在；Git 树中未见 `gradlew`、`gradle/wrapper`；部分 `tools/*.sh` 默认依赖 `/mnt/data/android-build-toolkit-linux-x86_64` | 不具备全新主机开箱即用的已验收构建流程 |
| 项目标识 | `settings.gradle` 的 `rootProject.name` 仍为 `ChatGPT-Web-0.4.0` | 非应用包名；是可修复的命名技术债 |
| Android 权限 | Manifest 当前包含网络、录音、相机、位置、传感器等权限 | 每项必要性、运行时授权和最小权限尚待独立审核 |
| 历史正式包 | 1.1.0 APK SHA-256 `9136ef03010302df2e527fca5b664d4d479f216cbbad5670ca0307ea5ac328ac` | 历史用户确认的 A/B 不是本轮鸿蒙 7 客观遥测 |

官方边界：华为 2026-10-14 的 [ArkWeb FAQ](https://developer.huawei.com/consumer/cn/doc/doccenter-dev-faq/arkweb-kit-faq-0003) 确认当前不支持第三方 WebView 内核；华为官方开发者网站注明 HarmonyOS 7 为 API 26；Google Play 自 2026-08-31 起要求新应用及更新目标 API 36+（该政策仅在计划上架 Google Play 时适用），参见 [Play 要求](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)；AGP 9.1.1 官方配套条件为 Gradle 9.3.1、JDK 17、Build Tools 36.0.0，参见 [Android AGP 文档](https://developer.android.com/build/releases/agp-9-1-0-release-notes)。

## 2. 产品成功定义：明确哪些行为必须保持

**首要标准不是“界面像原生 App”，而是实际 ChatGPT 网站可用且不丢用户输入与会话。**

1. **输入原生一致性**：对同设备同输入法、同账号和同一网站状态，验证 Enter、Shift+Enter、中文候选词确认、删除、选区替换、粘贴、换行、撤销/重做。网页/浏览器独占编辑语义；Android/Harmony 原生层不得主动伪造 Submit/Newline。
2. **登录与可信边界**：官方域名及必要登录重定向单独建允许/外部处理规则；第三方网页不得获得高权限 Native Bridge；不能通过注入或自建表单收集凭证。
3. **状态持久性**：前台、后台、Recents、旋屏、分屏、断网恢复、长对话滚动都不得因闲置时长或 Bridge 健康误判而触发破坏性刷新。真实浏览器崩溃可以进入分级恢复，但必须保留已输入内容的风险阻止条件。
4. **附件与媒体**：系统文件选择、图片上传、下载、麦克风授权和使用、外部 OAuth/App Link 回跳在真机验证；能力不可用时呈现明确错误，不伪称成功。
5. **诊断最小化**：版本、浏览器状态、错误码、导航代数、是否安全恢复等元数据可用于诊断；绝不采集问题文本、候选词、会话凭证、Cookie、剪贴板或 DOM 内容。

## 3. 开发分期及执行任务（所有阶段先开 Issue → RED/基线 → 最小变更 → 回归 → 审查）

### 阶段 G0｜治理和可复核起点（必须先做）

**对应 Issue [#2](https://github.com/liuzhide666/ChatGPT-Web/issues/2)、[#3](https://github.com/liuzhide666/ChatGPT-Web/issues/3)、[#10](https://github.com/liuzhide666/ChatGPT-Web/issues/10)。**

- 记录 `main` SHA、当前公开可见性、Actions 成功运行链接、1.1.0 APK 源代码与发行物摘要到 `docs/release-baseline.md` 的后续审计日志；检查仓库历史中有无凭证/私钥/令牌，**不能把路径黑名单扫描当作完整泄密扫描**。
- 用户决定是否保留 Public；如需要私有，先变更 GitHub Settings 并重新核实（修改权限须用户实际操作），不要假称已改。
- 配置 GitHub Rulesets/branch protection：主分支拒绝强推与删除、要求 PR、要求 `source-checks/contracts` 检查通过；涉及发行物的 PR 必须另设 `android-build` 及发布门禁。个人仓库若无法要求独立第二审批人，应明确自审+CI+真机结果的替代政策，而不伪造双人审批。
- 建立简洁的 Issue 标签 `platform:android/harmony/infra`、`priority:P0/P1/P2`、`type:bug/feature/research/security`、`gate:blocked/device/ready`；每个 Issue 具有依赖、验收标准、证据链接。
- 保留已有 GitHub `main` 工程源代码目录；**ZIP 无子目录**仅约束下载交付格式。新平铺 ZIP 必须包含路径映射、哈希和恢复脚本，而不是把 GitHub 源码拆成一堆根目录文件。

**G0 通过条件**：仓库权限和可见性有新鲜查询证据；源代码状态可复现；敏感信息风险评估完成；签名材料仍不进入 Git。

### 阶段 G1｜构建可复现化 / Android 1.1.1 的先决条件

**对应 Issue [#4](https://github.com/liuzhide666/ChatGPT-Web/issues/4)。** 每个子任务单独 PR：

1. `build/gradle-wrapper`: 用官方 Gradle 9.3.1 Wrapper 标准生成 `gradlew`, `gradlew.bat`, `gradle/wrapper/*`，校验 jar 来源和 distribution SHA-256，修订 `.gitignore`。验证 Gradle Wrapper 不携带密钥与机器绝对路径。
2. `build/toolchain-pinning`: 固定 JDK 17、Android SDK Platform 37、Build Tools 36.0.0、AGP 9.1.1、GeckoView 153；对依赖仓库 URL、版本、离线缓存与第三方许可证做溯源；避免每次动态解析不同版本。
3. `build/portable-tests`: 将 `tools/compile_api37_contract.sh`、`tools/run_all_host_tests.sh` 等脚本中 `/mnt/data/android-build-toolkit-linux-x86_64` 的默认耦合改成环境变量加标准 SDK/JDK 探测；失败时有清楚缺项说明，不悄悄绕过。
4. `build/diagnostic`: 增加 `tools/doctor.sh` 或 Python 预检：运行系统、Java、Gradle、SDK、Gecko 解析、签名资源不可得时都输出状态；生成非敏感 JSON build manifest。
5. `ci/android-build`: 增加只读 GitHub Android 构建 workflow，分为 `source-checks`、真实 Android compile、Java Host 测试、基础 APK manifest/ABI/16KiB 预检；CI 不接入发布签名、不上传账号状态。构建失败不能降级为 warning。
6. `build/project-name`: 审核 `settings.gradle` 的 `ChatGPT-Web-0.4.0` 是否被脚本引用，修订为 `ChatGPT-Web` 并验证构建输出和文档引用未退化。

**G1 通过条件**：全新干净环境 `./gradlew :app:assembleProbeDebug`（准确 Gradle Task 名称以生成后的 task 列表核定）、174 Python、30 Java Host、Node 输入中立检查、Android API 编译与 lint 全部通过；完成可审计依赖/构建哈希记录。Probe APK 不得冒充正式可升级包。

### 阶段 G2｜Android 真实设备基准和回归防护

**对应 Issue [#7](https://github.com/liuzhide666/ChatGPT-Web/issues/7)，与 G1 部分并行，但发布门禁必须依赖 G1。**

- 同一台设备比较“原始 chatgpt.com 网页”和安装的 1.1.0 APK，分别在真实 Android 设备或有 Android 兼容环境的华为设备执行；区分网页所用系统浏览器与 GeckoView 的内核差异。每个案例记录系统构建号（脱敏）、兼容运行时版本、输入法、APP SHA-256 和观察结果。
- 必测：9 类键盘动作，3 种会话长度（新/常规/超长），恢复/前后台/离线，7 个文件媒体流程，2 个异常恢复及安全阻止条件；异常难以安全复现时记 NOT_RUN，不用推测 PASS。
- 最少做一次冷启动与热返回；以“草稿内容不丢失、同会话不误刷新、授权可回跳”为零容忍回归项目。记录启动、内存、卡顿、网络和电池基准，仅在相同配置下做对比，不设毫无依据的固定性能承诺。

**G2 通过条件**：所有 Critical 用例均为通过或有获准的公开阻断记录；禁止把历史用户确认记录当作本轮设备实测。

### 阶段 G3｜Android 1.1.1 / vc114（保守维护候选）

- 第一候选 **不同时升级 GeckoView、targetSdk 和 IME 行为**。只纳入 G1 构建链、已确认的极小错误修复、必要的可靠性补丁和可逆的日志诊断改善。
- 对安装升级验证同包名、同签名证书、`versionCode > 113`、Cookie/会话连续性、下载权限、手势返回、前后台、崩溃恢复；对安装失败保留 1.1.0 回滚路径。
- 签名身份由受控、非仓库的发布流程使用。Release gate 记录签名校验、zip/native 对齐、组件与权限、依赖、Android 安装升级和真机 A/B。形式上通过审计后才打 `android-v1.1.1` 标签；标签不是“已经安装”。

**G3 通过条件**：与 1.1.0 对照的 Critical 回归全过、目标设备真实安装升级成功、APK 数字签名与哈希复查、发行资料和精确版本一致；若无法提供真机或签名，状态只能为 RC，不得宣称 Formal。

### 阶段 G4｜Android 1.2.x 平台现代化（单变量迁移）

**对应 Issue [#5](https://github.com/liuzhide666/ChatGPT-Web/issues/5)、[#6](https://github.com/liuzhide666/ChatGPT-Web/issues/6)。** 分两条独立开发分支，分别做基线/回归/数据证据，再选择发布顺序。

- **GeckoView 升级分支**：先列出现用 API 与拟升级版本的差异，检查 153 → 目标候选的发布说明、WebExtension API、二进制架构、大小变化、隐私与许可证；先完成可编译，然后检查登录/编辑/录音/文件/下载/长会话，再在真实兼容运行环境 A/B。不得仅凭目标版本号认定更安全或更快。失败则维持 153。
- **targetSdk / UI 分支**：先按行为变化分批目标版本升级，再验证 `SystemBarPolicy`、`MainActivity` 的系统栏废弃 API、Insets、Edge-to-edge、`windowSoftInputMode`、沉浸/手势导航、深色主题、分屏和旋转；独立审核 Manifest 位置/传感器/麦克风/相机权限的最小必要性及用户提示。目标上架 Google Play 时必须计划满足 API 36+ 规则；私人侧载与兼容环境不应伪称受到完全相同的商店上架约束。
- 两分支要进行单变量回归，合并时再执行组合回归。如果更改了同一组件就追加交互测试。

**G4 通过条件**：构建、静态检查、权限审计、设备矩阵和安装升级通过；保留上一版二进制可回滚，性能/耗电仅依据同条件对照测量后主张改善。

### 阶段 H0｜鸿蒙 7 ArkWeb 可行性研究（应与 G1/G2 尽早并行）

**对应 Issue [#8](https://github.com/liuzhide666/ChatGPT-Web/issues/8)。** 尽快解决唯一关键前提：`chatgpt.com` 能否在目标设备 ArkWeb 中稳定登录并完成输入与媒体工作流。

1. 获取受控实验设备信息：精确 HarmonyOS 7 build/API、DevEco Studio/SDK 版本、ArkWeb 组件版本、键盘及网络环境；公开日志仅留脱敏信息。
2. 在独立 `harmony/` 原型工程中新建最小 ArkTS Stage/ArkUI Web Ability：先只打开 `https://chatgpt.com`，处理生命周期与外链；先不要添加 JS 注入、消息代理、键盘模拟或复杂 UI。
3. P0 阻断能力：TLS/导航到可信起始站点、登录与重定向/二次验证、Cookie 保持、IME 中文组合与 Enter/Shift+Enter、流式回答与长会话。认证服务可能拒绝内嵌 Web 容器；出现限制应记录官方渠道的原因，不使用绕过安全验证的手段。
4. P1 功能：文件选择与授权、图片上传、音频录制、下载和打开、系统返回、后台/前台、网络恢复、系统密码填充、深浅色与安全区域。每个能力查当前 API 26 官方接口及系统权限模型，不照搬 Android 类名。
5. 出 `docs/adr/0002-harmony-arkweb-feasibility.md`：每条测试给 PASS/FAIL/NOT_RUN、原因、复现步骤、环境、可用替代方案和剩余风险。

**H0 决策**：
- **GO**：账户登录/会话、输入核心能力、页面渲染可在真机持续通过，进入 H1。
- **CONDITIONAL**：仅非关键媒体/文件能力缺失，有明确可验证的用户同意降级方案，提交范围收缩的 HAP alpha 方案，不宣传完整功能等价。
- **NO-GO**：登录、安全校验或核心输入不可用且无被服务提供方允许的修复路径，停止原生外壳研发；继续维护 Android 兼容路线或使用官方浏览器网页，不投入大量包装性 UI 代码。

### 阶段 H1｜原生鸿蒙 alpha（仅 H0 GO 后启动）

**对应 Issue [#9](https://github.com/liuzhide666/ChatGPT-Web/issues/9)。**

- 项目结构建议：`harmony/entry/src/main/ets/pages/`（ArkUI/Web）、`.../browser/`（WebController/导航可信源）、`.../permissions/`（用户授权和文件选择）、`.../diagnostics/`（隐私安全诊断）；按实际 DevEco 生成结构调整，不预制与 SDK 不匹配的接口。
- 使用独立 bundleName、版本与签名身份（如 `0.1.0-alpha.1`，最终身份需确认签名证书及发布约束），独立生成 HAP；不可复用 Android keystore 或假装可直接覆盖 APK。
- 明确 `ArkWeb`、网页、ArkTS 原生 UI 的职责；回调强制可信源校验、最小数据接口、错误路径可恢复；不在 Native 边桥读取或写入编辑器内容。
- 逐项完成真正的授权/文件/音频/下载适配和性能基准，先发布可复现内部 RC，再通过 HAP 包体检查、签名及真机验证进入 alpha。

**H1 通过条件**：HAP 的编译、安装、签名身份、真机登录和关键流程有明确 PASS；发布对象和旧 Android APK 的用户数据隔离关系被说明清楚；不把模拟器通过当成真机通过。

## 4. 跨平台验收矩阵与验收严度

| 验收域 | 最小案例 | Critical 门槛 | 证据 |
|---|---:|---|---|
| 输入/IME | 9 | 不可多发、漏发、破坏组合输入或丢草稿 | 原网站与 App 同设备视频/脱敏文字记录 |
| 登录/OAuth | 4 | 无登录阻塞、凭证泄漏、恶意跳转 | 会话可持续记录、允许源决策 |
| 持久化/恢复 | 8 | 不能无故清空用户状态 | 页面状态/Recovery 过程记录 |
| 文件/媒体 | 7 | 不可静默误授权、上传错误文件、下载损坏 | 文件大小/哈希、权限弹窗记录 |
| UI/设备 | 6 | 键盘与导航不能挡住编辑区域 | 不同窗口状态截图和操作记录 |
| 安全/发行 | 8 | 无秘密进入 Git；签名/版本/来源匹配 | 扫描报告、SHA-256、包体与证书证据 |

上表案例数是**计划覆盖清单，不是已经运行的测试数**。Critical 失败一项即阻断对应版本发布。确实不可执行的设备或服务流程必须标记 `NOT_RUN`，不可换成 `PASS`。给每个案例固定 `case_id`、`baseline_revision`、`test_binary_sha256`、`device_runtime`、`steps`、`expected`、`observed`、`evidence`、`result`、`reviewer`，用于追溯。

## 5. GitHub 工作流、分支与证据

- `main`：最新通过适用质量门禁的可维护源码，**不等于已发布 APK**。
- `feat/android-build-*`、`fix/android-*`、`deps/gecko-*`、`spike/harmony-arkweb-*`：按变更意图创建短生命周期分支，一项核心行为改动一个 PR。
- `docs/adr/` 保存不可逆或架构性决策，记录背景、候选、理由、反方、取舍、回滚；`docs/release-baseline.md` 保留不可变基线，新版本进入独立发布/QA 记录。
- 每个 PR 模板字段：`Issue`、`Scope/Non-goals`、`Changed contracts`、`RED proof`、`GREEN evidence`、`Build & dependency hash`、`Privacy check`、`Device gate`、`Rollback`。
- CI 三层：① Source（每 PR）；② Android Debug/Host/Package（相关 PR）；③ 实机及签名发行（需要受控设备与发布权限，不在无凭证公开 CI 自动执行）。Harmony 使用 DevEco 官方可用环境单独建立流水线，不能把未配置的 HAP 编译列为已验证。
- GitHub Release 和 Library 的角色：Git 保存源代码与已批准公开的元数据；Library `source/` 保存源归档，`releases/` 保存经过授权的发行二进制，`docs/` 保存审计，`历史版本/` 保留回滚；任何新提交/新包要建立源提交 SHA→输入/依赖→输出 SHA→签名→测试环境→结论的证据链。
- 扁平 ZIP 仅用于“一键下载、不含内部目录”的用户交付，每次发布由已冻结源树构建，纳入 `FILE-MAP.csv`、`SHA256SUMS.txt`、`restore_flat.py`，并验证可还原同一 Git 工作树。源代码仓库仍保留正常目录，不把所有源码平铺提交。

## 6. 风险、依赖及优先决策

| 风险 | 后果 | 处理 / 停止条件 |
|---|---|---|
| 鸿蒙 ArkWeb 的登录流程遭服务/认证平台限制 | 原生 HAP 失去核心用途 | H0 先测，失败则 NO-GO，不绕过认证 |
| 浏览器升级引入 IME 回归 | 重现 vc112 类问题 | 独立候选、先测试、网站 A/B、快速回滚 |
| `targetSdk 28` 与未来安卓系统/商店要求冲突 | 可安装但权限/策略失效或无法上架 | 分步升级，单独评估 Google Play API 36+ |
| Release 签名证书/密钥失管 | 无法原地升级或不安全 | 用独立安全密钥托管，先核对证书指纹，防止泄露 |
| 公开 GitHub 与本地资料混用 | 用户数据或内部签名泄漏 | 公共仓库只保留审定源码和脱敏证据；扫描历史、最小权限 |
| 真实设备/DevEco 工具缺失 | 只能证明编译无法证明可用 | 明确 GATE_BLOCKED，冻结发布表述，不以模拟器代替 |
| 网页功能随站点更新变化 | 兼容性再次退化 | 避免 DOM 依赖；定期与原网站交叉复测，保留回归样例 |
| 社区误认官方客户端 | 信誉、商标与分发风险 | README/应用标识声明非官方，核实品牌及分发要求 |

## 7. 推荐实施序列（工作量仅为计划估算，不是可保证完成日期）

| 顺序 | 建议并发 | 工作包 | 依赖 | 交付文件 | 目标状态 |
|---:|---|---|---|---|---|
| 1 | 串行 | G0 仓库治理、秘密与 release 审计 | 当前 `main` | `docs/governance-*` + Issues | governance PASS |
| 2 | 并行 | G1 Android Wrapper、SDK 锁定、移除硬编码绝对路径 | G0 | `gradle/wrapper/*` + `ci/android-build.yml` + doctor 报告 | clean BUILD PASS |
| 3 | 并行 | H0 ArkWeb 最小登录/IME 原型研究 | G0 + 真机 + DevEco | H0 决策 ADR + 设备矩阵 | GO/CONDITIONAL/NO-GO |
| 4 | 串行 | G2 基线 Android 真实设备复测 | G1 + 实机 | 设备 A/B 结果 | G2 PASS / BLOCKED |
| 5 | 串行 | G3 Android 1.1.1 RC→Formal | G1 + G2 + 签名授权 | APK+哈希+审计+回滚 | 仅真实通过后 Formal |
| 6 | 并行 | G4 Gecko / targetSdk / UI 各自现代化分支 | G3 稳定后 | 依赖评估 + 独立 PR | 选择下一 Android 版本 |
| 7 | 条件并行 | H1 Harmony HAP alpha | H0 GO | 源码、HAP、签名及真机报告 | 仅通过后 alpha |

可用工程资源不足时，依次优先：**G0 → G1 → H0（及早判明可行性）→ G2 → G3 → G4/H1**。有真机和 DevEco 可同时开展 H0，但不能跳过 G0 的公开仓库/密钥边界。任何工期估算都应在取得实际设备、SDK 和构建运行结果后调整；项目是否进入下一阶段取决于门禁结果，而非日历。

## 8. 第一轮可立即执行的五个 PR/调研任务

1. `docs(governance): lock evidence + ruleset policy`：Issue #2；决策 PUBLIC/PRIVATE 后落实规则；无应用字节变化。
2. `build: add verified Gradle wrapper and pinned doctor`：Issue #4；在新环境先失败，再加入经过校验的 Wrapper 与医生脚本；产出独立测试结果。
3. `ci: add Android debug build + Java Host lane`：Issue #3/#4；先小步编译，不触碰 release signing；与 source CI 并行但彼此独立验收。
4. `test(android): establish HarmonyOS 7 compatible-runtime A/B baseline`：Issue #7；需要用户授权的真实设备执行，保存脱敏结果；先不发版。
5. `spike(harmony): ArkWeb auth + IME proof`：Issue #8；优先解决 GO/NO-GO，再逐渐加入文件与媒体。

**需用户外部介入的现实依赖**：GitHub 仓库可见性/保护设置、真实设备和 DevEco 连接、任何签名密钥使用或公开发布行为。没有这些回执时，将任务标为 `BLOCKED` 而非已完成。