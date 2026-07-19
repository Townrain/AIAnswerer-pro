# Changelog

All notable changes to AIAnswerer will be documented in this file.

## [1.6.1] - 2026-07-19

### Added
- 多语言输出支持：主页输出语言下拉扩展至 11 项，覆盖中文/English/日本語/Francais/Deutsch/Espanol/Portugues/한국어/Русский(VLM)/العربية(VLM)/其他语言(VLM)
- 首次启动弹窗新增「跟随系统」选项，选择后不再覆写 Android Locale
- 新增 11 个语言常量与完整 Locale/Prompt 映射
- `Constants.PROMPT_VERSION = 2`：提示词版本追踪常量 + 构建时 AppLog 日志
- `Constants.promptStr()` 私有辅助函数，统一提示词字符串加载入口
- 输出语言下拉列表 日本語/한국어 标注 `(VLM)`，提示需切视觉模式

### Changed
- LLM 核心系统提示词重构：新增角色定义、行为准则、4 个 Few-shot 示例
- 应用图标重设计：新 logo 以书本+AI电路节点+珊瑚暖调问号为视觉核心，品牌奶油底 (#FAF9F5)
- 自适应图标背景色从蓝色 (#4A6CF7) 更新为品牌奶油色，与 Claude Warm 设计体系统一
- Play Store 展示图标同步更新为 512px 新 logo
- 联网搜索上下文指令明确化：增加搜索结果使用规则
- VLM 视觉提示词（单图/多图）从硬编码中文改为中英文双语资源
- `countQuestions()` 提示词从硬编码中文改为中英文双语资源
- 输出语言指令 `"请用X回答"` 从硬编码改为字符串资源
- SettingsCard 题型标签不再硬编码中文，改为 `MyApplication.getString()` locale-aware
- LanguageUtil 注释更新：从"支持中英两种"改为"支持中英日韩法德西葡俄阿等"
- LanguageUtil：跟随系统模式下跳过 attachBaseContext 覆写
- Constants：promptLocale 支持全部语言 + 系统默认
- PillButtonCard：悬浮按钮主色改为主题响应式（`t.p→t.pe`），Idle 态不再硬编码深紫 `#2D2B55`，完全匹配 DESIGN.md Claude Warm 设计规范；阴影/光晕/进度弧同步改用主题色板
- **测试性能优化**：消除单元测试中的人工延迟（delay/sleep），全量测试延迟从 ~28s 降至 ~1.2s（24x 加速）
  - `CaptureHandlerTest` 改用 `TestCoroutineScheduler` 虚拟时间，22 处 `delay()` 替换为 `advanceUntilIdle()`
  - `RecordingCoordinatorTest` 25 处 `delay(500/300/100)` 降至 `delay(1)`，`coAnswers` 延迟保留但大幅减少
  - `AnswerFetcherTest` 4 处 `Thread.sleep()` 降至 1ms
  - `RecordingCoordinatorProgressTest` 独立 `delay()` 降至 1ms，mock 内部延迟降至 50/30ms
  - `OpenAIClientTest` MockWebServer `setBodyDelay` 5s→2s，协程 delay 缩减
- 所有 96 个测试通过，生产代码零改动

### Removed
- 移除开屏显示：取消 SplashActivity，启动直达 MainActivity，零延迟
- 移除旧机器人图标前景 (icon_foreground.png) 及旧 mipmap webp 回落文件

### Fixed
- 输出语言选择不再覆盖 UI 语言：MergedCard 移除 `saveLanguage(code)` 调用，输出语言与 UI 语言完全解耦
- `promptResources` 从 lazy val 改为 `getPromptResources()` 函数，语言切换后提示词实时生效不再需要重启
- LanguageUtil 未知语言代码回退统一为 ENGLISH（与 Constants 提示词路由一致）
- VLM 后缀 `(VLM)` 剥离逻辑从 3 处重复提取为 `cleanOutputLang()` 辅助函数
- `normalizeQuestionTypes` 新增 `"不定项"` 匹配，同时支持中英文题型标签的 locale-aware 归一化
- FloatingWindowRegressionTest：pillVisual 签名适配 `Th` 主题参数
- LanguageUtilTest：未知代码回退断言从 `SIMPLIFIED_CHINESE` 同步为 `ENGLISH`
- OpenAIClientTest：mock 适配 `getPromptResources()` 新 API
- 消除全部 28 个测试编译警告：Robolectric/Android 废弃 API、Kotlin createTempDir、缺少 @OptIn 及冗余 is 检查
- ConfigStorageTest 永真断言修复：改为无崩溃验证

## [1.6.0] - 2026-07-18

### Added
- 自定义提示词：LLM 系统提示词与 VLM 视觉提示词均可在设置页自定义，留空使用默认
- 放大镜图标缩放：设置页悬浮窗外观分区新增图标缩放滑块 (0.5x~2.0x)，独立调节图标大小
- 多图模式：录制多张截图自动去重、合并发送 LLM 答题
- 主题可选项系统：多主题切换 + JSON 导入/导出接口

### Fixed
- RecordingCoordinator 线程安全加固（AtomicInteger + CopyOnWriteArrayList + synchronized）
- ImageCollector 线程安全加固
- 悬浮窗收起时显示答案摘要而非完整题目
- 主页设置图标用 VectorDrawable 替代有误差的内联 ImageVector
- 主页下拉框第二下点击无法关闭
- UIConfig 硬编码主题 ID 改为常量引用

### Changed
- 厂商数据源切换至 OpenCode，同步管理器适配
- 新增 provider_data.json 生成脚本
- 测试覆盖提升：新增 RecordingCoordinator 测试

## [1.5.1] - 2025-11

### Fixed
- 并行模式答案顺序修复：答案不再乱序，按题目原始顺序排列

## [1.5.0] - 2025-10

### Changed
- 大文件拆分：CommonComponents(1256行)→5文件、OpenAIClient(996行)→2文件、AppConfig(1005行)→8文件(门面模式)
- 新增 38 个单元测试覆盖 JSON 解析、弹窗排队、提示词生成
- 厂商列表从 13 个扩充至 52 个
- System Prompt 精简约 80%
- maxTokens 1024→512，超时时间 180s→60s
- 并行模式默认开启，并发数默认 10

### Fixed
- 录制模式 captureCount 不递增导致结果无法显示
- 隐身模式/悬浮窗外观设置不立即生效
- 悬浮窗大小滑块不生效
- 厂商链接/关于页 GitHub 链接点击无反应
- 搜索开关重启后状态丢失
- 题型选择翻译不一致导致 AI 输出串题型
- 答案卡片过长被裁

### UI
- 自定义应用图标（神经网络节点 + 对勾设计）
- Android 12+ 启动屏联动新图标
- 悬浮窗默认大小 56→40
- VLM 快捷开关默认跟随视觉模型配置自动开启

## [1.4.1] - 2025-09

### Added
- 快捷按钮排列方式设置：弧形排列 / 横向排列

### Fixed
- 深色模式下状态卡片图标颜色适配
- 设置页合并并发数设置与连接测试为同一卡片

## [1.4.0] - 2025-08

### Added
- 录制模式：连续拍摄多道题目，自动去重，统一输出答案
- 中止搜索：HTTP 请求改用 OkHttp 异步 API，协程取消时断开连接
- 快捷面板弹簧动画

## [1.3.1] - 2025-07

### Fixed
- 12 个空安全/线程安全/资源泄漏/逻辑错误修复
- 安全增强：EncryptedSharedPreferences 降级保护、日志脱敏、证书固定
- 构建优化：签名 fallback、abiFilters 兼容、AGP 内部 API 替换
- 悬浮窗快捷开关（VLM、联网搜索、深度思考）
- 悬浮窗交互与窗口位置修复

## [1.3.0] - 2025-06

### Changed
- 悬浮窗 UI 重构：按钮独立显示，统一深灰色调
- 状态消息集成到标题右侧
- 并发测试反馈修复

## [1.2.1] - 2025-05

### Changed
- 全局点击反馈：深色模式紫色发光轮廓 + 浅色模式柔和涟漪
- CTA 按钮重做为明亮紫色发光渐变
- 背景色调优化（象牙暖色调）

## [1.2.0] - 2025-04

### Added
- 屏幕读取模式：通过 AccessibilityService 获取屏幕文字
- VLM 视觉模型集成替代 OCR

### Fixed
- 正则崩溃修复、复制内容优化
- 深色模式 UI 修复（双层边框、阴影系统重写）
- 组件渲染修复（graphicsLayer 顺序、图标颜色等）

## [1.1.0] - 2025-03

### Added
- 正则过滤开关（多题搜索控制）
- 思考模式开关（DeepSeek-R1 等推理模型）

## [1.0.0] - 2025-02

### Fixed
- 10 个 Bug 修复（VLM 降级、内存泄漏、竞态条件等）
- 性能优化：全局 Gson 实例、HTTP 连接自动关闭
- JSON 解析 5 级降级策略

## [0.9.0] - 2025-01

### Added
- 并行答题模式（可配置并发数 1-10）
- API 延迟测试按钮
- GitHub Actions 自动打包

## [0.8.0] - 2024-12

### Changed
- 毛玻璃材质卡片
- 渐变发光按钮
- 实时主题切换（跟随系统/浅色/深色）
- 色彩体系重构

## [0.7.0] - 2024-11

### Added
- 视觉模型 (VLM) 集成
- 多题模式逐题搜索优化
- 图片压缩优化（2048x2048 限制）
- `api/vision/` 模块独立

## [0.6.0] - 2024-10

### Added
- Tavily 联网搜索引擎集成
- 悬浮窗拖拽移动 + 吸附边缘
- 悬浮窗外观自定义（按钮大小、透明度）

## [0.5.0] - 2024-09

### Changed
- API Key 加密存储（EncryptedSharedPreferences）
- BaseActivity 语言配置统一
- 国际化完善（中英文切换）
- 网络预检 + 自动重试
- 移除冗余 ML Kit 依赖（-10MB）

## [0.4.0] - 2024-08

### Fixed
- Prompt 优化
- GPT-5 markdown 格式兼容

## [0.3.0] - 2024-07

### Added
- OCR 前裁剪功能

## [0.2.0] - 2024-06

### Fixed
- Release 包无法请求 AI API

## [0.1.0] - 2024-05

### Added
- 初次发版
