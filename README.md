# AI答题助手 (AIAnswerer)

> 📌 **项目起源**：本项目基于 [wb-hwang/AIAnswerer-Android](https://github.com/wb-hwang/AIAnswerer-Android)（MIT License）Fork 并独立发展。上游已停止维护（最后更新：2025年10月），本仓库持续迭代中。感谢原作者 [@wb-hwang](https://github.com/wb-hwang) 的优秀基础工作。

[中文](#中文使用指南) | [English](README_EN.md)

## 中文使用指南

### 应用简介
AI答题助手是一款基于 OCR/视觉模型与大语言模型的安卓答题工具。通过悬浮窗截图识别题目，并调用 DeepSeek AI 等兼容 OpenAI 接口的模型为你快速给出答案，适用于练习、查缺补漏或自测场景。


### 功能亮点
- 🖼️ 屏幕快速截取：一键截取当前屏幕，自动聚焦题目区域
- 📖 屏幕读取模式：通过无障碍服务直接读取屏幕文字，无需截图，速度更快
- 📝 智能文字识别：支持中英文识别，可在提交前编辑校正
- 👁️ 视觉模型支持：可使用视觉模型替代 OCR，适合噪音较多的页面
- 🔍 联网搜索增强：自动搜索相关资料作为答题参考
- 🤖 AI 实时答题：根据题型生成解析，并自动复制答案
- 📋 批量答题：截图包含多题时逐题搜索并返回所有答案
- ⚡ 并行答题：多题模式下支持并发处理，显著提升答题速度
- 💬 悬浮窗操作：无需切换应用即可完成截屏、预览、提交
- 🔒 本地可控：自定义 API Key，随时启停网络请求
- 🌐 中英双语：支持中文和英文界面切换

### 技术栈
| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material3 |
| OCR | Google ML Kit (中文+拉丁文) |
| 视觉模型 | OpenAI 兼容接口 (DeepSeek/GPT-4o 等) |
| 联网搜索 | Tavily API |
| 网络 | OkHttp 4.12.0 |
| 存储 | MMKV + EncryptedSharedPreferences |
| 构建 | Gradle (AGP 8.13.0) |

### 安装与准备
1. 使用 Android 11 及以上系统的设备，并保持网络通畅。
2. 安装提供的 APK 文件；首次安装需按照系统提示允许来自未知来源的应用。
3. 设置 LLM 模型信息。
4. 首次启动时，按照屏幕提示授予悬浮窗、截屏和通知等必要权限。

### 快速上手
1. 参考应用内说明

### 支持的题型
- 选择题：识别题干与选项，标记推荐答案并给出理由
- 填空题：生成精炼答案，适用于多空位题目
- 问答题：提供结构化解答或要点式分析

### 使用小贴士
- 保持截图清晰、居中，避免复杂背景，以提升 OCR 准确率。
- 如需暂停网络请求，可暂时断网或在设置页关闭 AI 回答。
- 答案生成后可再次点击悬浮按钮刷新题目，便于连续练习。

### 常见问题
- **提示缺少权限？** 前往系统设置搜索"悬浮窗""屏幕录制"等选项，手动开启相关权限。
- **识别不准确？** 在确认页手动修正文本，或重新截图后再提交。
- **AI 没有回应？** 检查网络、确认 API Key 有效，并确保 DeepSeek 账户余额充足。



### 隐私与免责声明
- 应用会将识别出的文字发送至所选 AI 服务，请避免上传敏感或受限内容。
- DeepSeek API 请求可能产生费用，请留意使用频率。
- 本应用仅用于学习与研究，请遵守考试纪律和法律法规，任何违规使用后果自负。

### 项目结构

```
com.hwb.aianswerer/
├── BaseActivity.kt           # 统一语言配置基类
├── MyApplication.kt          # Application 初始化
├── MainActivity.kt           # 主界面（权限管理、答题设置）
├── FloatingWindowService.kt  # 悬浮窗核心服务
├── ScreenReaderService.kt    # 无障碍屏幕读取服务
├── ScreenCaptureManager.kt   # 截图管理（MediaProjection）
├── TextRecognitionManager.kt # OCR 文字识别
├── ConfirmTextActivity.kt    # 识别文本确认/编辑
├── ImageCropActivity.kt      # 图片裁剪（四角拖拽）
├── SettingsActivity.kt       # 通用设置
├── ModelSettingsActivity.kt  # API 模型配置
├── AboutActivity.kt          # 关于页面
├── Constants.kt              # 常量与系统提示词
├── api/
│   ├── OpenAIClient.kt       # OpenAI 兼容 API 客户端
│   ├── TavilyClient.kt       # Tavily 联网搜索客户端
│   └── vision/               # 视觉模型模块
│       ├── VisionProvider.kt
│       ├── VisionProviderFactory.kt
│       ├── VisionFilterResult.kt
│       └── OpenAIVisionProvider.kt
├── config/
│   └── AppConfig.kt          # 配置管理（MMKV + 加密存储）
├── models/                   # 数据模型
├── ui/
│   ├── components/           # 共享 Compose 组件
│   ├── dialogs/              # 对话框
│   ├── icons/                # 本地图标定义
│   └── theme/                # Material3 主题
└── utils/
    ├── AppLog.kt             # 统一日志工具
    ├── ClipboardUtil.kt      # 剪贴板工具
    ├── ImageCropUtil.kt      # 图片裁剪工具
    └── LanguageUtil.kt       # 语言切换工具
```

### 更新说明

#### v1.2.1 (UI 体验优化)
* **全局点击反馈**
  - 深色模式点击任意可交互元素时显示紫色发光轮廓光效
  - 浅色模式统一柔和紫色涟漪，去除生硬阴影
* **CTA 按钮重做**
  - 进入答题模式按钮从深色渐变改为明亮紫色发光渐变
  - 移除多余图标，纯文字按钮更简洁
* **背景色调优化**
  - 浅色模式整体背景偏暖（象牙色调）
  - 深色模式背景提亮，减少视觉疲劳
* **使用说明卡片**
  - 展开/收起箭头更换为圆润三角图标
  - 动画更 Q 弹（低阻尼弹簧）

#### v1.2 (屏幕读取模式 & UI 修复)
* **屏幕读取模式（新功能）**
  - 新增采集模式切换：截图识别 / 屏幕读取，设置页芯片选择
  - 屏幕读取通过 AccessibilityService 直接获取屏幕文字节点，无需截图权限，速度更快
  - 选择屏幕读取时若无障碍服务未开启，自动跳转系统无障碍设置页
  - 无障碍状态指示点（绿色/红色）实时显示服务开启状态
  - 从系统设置返回后自动刷新状态（LifecycleEventObserver 监听 ON_RESUME）
  - 无障碍提示文字可点击，直接跳转系统设置
  - 新增 ScreenReaderService、accessibility_service_config.xml，AndroidManifest 注册服务
* **Bug 修复**
  - 修复正则 `\\{[^{}]*}` 在部分 Android 设备崩溃的问题（改为 `\\{[^\\{\\}]*\\}` 显式转义）
  - 修复自动复制到剪贴板会复制题目+答案的问题，改为只复制答案文本
* **深色模式 UI 修复**
  - 修复深色模式下按钮出现直角边框与大圆角边框重叠的问题
  - 重写 Glass.kt 和 Shadow.kt：统一使用 drawBehind 绘制背景+边框+阴影，消除双层边框
  - 阴影系统从 Modifier.shadow() 改为 drawIntoCanvas + setShadowLayer 原生阴影
  - 阴影参数从 Shape 改为 Dp（cornerRadius），统一所有调用点
* **组件修复**
  - 修复 AnimatedButton graphicsLayer 顺序导致的渲染问题
  - 修复 Tonal 按钮直角边框，简化为 Modifier.background + shape
  - 修复图标因 Color.Unspecified 不显示的问题，恢复为 Color.Black
  - 统一所有 shadow 调用参数适配新的 Dp 类型

#### v1.1 (正则过滤与思考模式)
* **正则过滤开关**
  - 联网搜索设置页新增"多题正则过滤"开关（默认开启）
  - 关闭后，OCR 检测到多题时仍会进行联网搜索
* **思考模式开关**
  - LLM 模型设置页新增"启用思考模式"开关（默认关闭）
  - 开启后向 API 发送 `reasoning_effort: "medium"` 参数
  - 适用于 o1、DeepSeek-R1 等推理模型

#### v1.0 (稳定性与质量提升)
* **Bug修复 (10个)**
  - 修复VLM失败后OCR降级失效的问题
  - 修复VLM配置修改后不生效的问题
  - 修复截图时Bitmap内存泄漏
  - 修复按返回键意外停止悬浮窗服务
  - 修复并发请求竞态条件
  - 修复裁剪Activity临时文件残留
  - 修复语言切换后通知栏残留幽灵通知
  - 修复HTTP错误响应未正确关闭连接
  - 修复VLM连接测试格式错误
  - 修复JSON解析正则贪婪匹配问题
* **性能优化**
  - 全局共享Gson实例，减少内存分配
  - HTTP响应使用use{}自动关闭，防止连接泄漏
  - LLM Temperature参数可配置化
  - Compose重组延迟提取为常量
* **代码质量**
  - 添加Mutex互斥锁防止并发请求
  - 统一错误处理和资源释放
  - 完善ProGuard规则
* **版本号** v1.0.0

#### v0.9 (并行答题 & 性能优化)
* **并行答题模式**
  - 新增并发答题设置卡片，支持启用/禁用并行模式
  - 可配置最大并发数（1-10），默认3
  - 高并发数（>5）显示警告提示，避免API限流
  - 分别为LLM、VLM、联网搜索提供测试按钮，可测试各API延迟
  - 多题模式下自动并行处理，显著提升答题速度
* **进度显示优化**
  - 并行模式显示 `答题中 (3/8)` 格式，实时更新进度
  - 部分题目失败时显示警告，只返回成功的结果
* **测试功能增强**
  - 测试按钮显示实际延迟时间（毫秒）
  - 支持测试LLM、VLM、Tavily三个API的并发性能
  - 测试结果独立显示，互不干扰
* **GitHub Actions 自动打包**
  - 新增 CI/CD 工作流，支持自动构建 Debug APK
  - 推送 `v*` 标签时自动发布 Release
* **版本号** v0.0.9

#### v0.8 (UI 高级感优化)
* **毛玻璃材质**
  - 所有卡片采用微渐变 + 半透明 + 渐变边框，告别纯色平面
  - 悬浮窗答案卡片支持毛玻璃效果，透明度可由设置页调节
  - 暗色模式下卡片有"浮起来"的玻璃质感
* **渐变发光按钮**
  - 底部答题按钮从纯色升级为横向渐变（primary → secondary）
  - 带发光阴影效果，视觉焦点更突出
* **实时主题切换**
  - 设置页新增外观模式：跟随系统 / 浅色模式 / 深色模式
  - 切换即时生效，无需重启应用
  - 状态栏样式自动同步
* **色彩体系重构**
  - 浅色模式：明蓝主色 `#4A6CF7`，暖米背景 `#F6F5F3`
  - 暗色模式：深蓝主色 `#2563EB`，深灰蓝背景 `#0F1118`
  - 通知栏颜色与主题统一
* **圆角与间距**
  - 统一圆角 16-24dp，更大呼吸感
  - 按钮圆角 18dp，胶囊形 FilterChip
* **状态栏适配**
  - 使用 `enableEdgeToEdge` 适配状态栏
  - 浅色模式深色图标，暗色模式浅色图标
* **清理冗余功能**
  - 移除"题目内容范围"输入框及相关代码
* **版本号** v0.0.8

#### v0.7 (视觉模型集成 & 多题优化)
* **视觉模型 (VLM) 集成**
  - 支持使用视觉模型直接分析截图，替代 OCR 识别
  - 抽象 VisionProvider 接口，支持 OpenAI 兼容格式
  - 通过工厂模式创建 Provider，便于扩展
  - 设置页可配置视觉模型 API 地址、Key、模型名称
  - 支持测试视觉模型连接
  - VLM 失败时自动降级为 OCR 模式
* **多题模式优化**
  - VLM 自动分离多题截图中的每道题目
  - 每道题单独进行联网搜索，提升搜索精准度
  - 逐题调用 LLM 答题，确保每道题都能获得答案
  - 状态显示优化：`搜索中 (1/8)`、`获取答案中 (2/8)` 等
* **图片压缩优化**
  - 修复图片尺寸超过 API 限制的问题（2048x2048）
  - 同时限制宽度和高度，等比缩放
* **超时优化**
  - 视觉模型 API 超时时间从 60 秒增加到 120 秒
* **架构优化**
  - 新增 `api/vision/` 模块，独立管理视觉模型相关代码
  - VisionFilterResult 支持分离题目列表
  - AppConfig 扩展视觉模型配置项
* **版本号** v0.0.8

#### v0.6 (联网搜索增强 & 悬浮窗优化)
* **Tavily 联网搜索**
  - 集成 Tavily 搜索引擎 API，单题模式下自动搜索相关资料并注入 LLM 上下文
  - 搜索结果作为答题参考，提升冷门题目的准确率
  - 设置页可配置 Tavily API Key（加密存储）和启用开关
  - 支持测试 Tavily 连接
  - 智能提取搜索关键词：从 OCR 文本中提取题干和选项，过滤 UI 噪音
  - 多题模式自动跳过搜索，避免无效 API 调用
* **悬浮窗交互重构**
  - 悬浮按钮支持自由拖拽移动，可吸附到屏幕左/右边缘
  - 点击截图，拖拽移动，一个按钮两个功能
  - 卡片从按钮正下方出现，不遮挡按钮位置
  - 按钮位置不受卡片显隐影响，窗口宽度变化时按钮稳定
  - 修复悬浮窗遮挡下层应用触摸的问题（WRAP_CONTENT 窗口）
* **悬浮窗外观自定义**
  - 设置页新增悬浮窗外观配置：按钮大小（32~80dp）、按钮透明度、卡片透明度
  - 实时生效，无需重启 Service
* **Bug 修复**
  - 修复第二次截图输出第一次答案的问题（等待 Compose 重组完成 + 清除 ImageReader 旧帧）
* **UI 优化**
  - 设置页支持上下滚动
  - 关于页面更新 GitHub 地址，移除邮箱卡片
* **版本号** v0.0.6

#### v0.5 (代码质量优化)
* **安全增强**
  - API Key 使用 EncryptedSharedPreferences 加密存储，不再明文保存
  - Release 构建移除 HTTP 日志，防止 API Key 泄露到 logcat
  - 添加 OkHttp CertificatePinner 证书固定，防止中间人攻击
  - 即使 Debug 模式也对 Authorization 头脱敏
* **架构优化**
  - 抽取 BaseActivity 统一语言配置，消除 6 处重复代码
  - 悬浮窗 Composable 组件独立文件，Service 职责更清晰
  - 统一协程作用域，修复 CancellationException 被吞噬的问题
* **国际化完善**
  - 所有悬浮窗状态消息支持中英文切换
  - 通知渠道名、剪贴板标签等均使用字符串资源
* **网络增强**
  - 添加网络连接预检，无网络时快速提示
  - API 请求支持自动重试（指数退避）
  - Service 销毁时自动取消进行中的网络请求
* **构建优化**
  - 移除冗余 ML Kit 依赖（-10MB 包体积）
  - 所有依赖版本统一到 Version Catalog
  - 收紧 ProGuard 规则，提升 R8 混淆效果
* **代码质量**
  - 统一日志工具 AppLog，Release 构建静默
  - 消除所有 `e.printStackTrace()` 调用
  - 修复 `savedCropRect!!` 空安全风险
  - 补充核心单元测试（extractJsonPayload、isApiConfigValid）
* **JSON 解析优化**
  - 支持批量答题：截图包含多题时返回所有答案
  - 5 级降级解析策略：直接解析 → 提取修复 → 正则数组 → 正则对象 → 文本提取
  - 修复中文引号导致 JSON 截断的问题
  - 系统提示词优化：强制 AI 填写 answer 字段，不得留空

#### v0.4
* 优化了prompt
* 兼容了GPT-5传回的markdown 格式

#### v0.3
* 加入COR 前裁剪功能，提高题目识别能力

#### v0.2
* 修复release 包无法请求ai api 的问题

#### v0.1
* 初次发版

### License
This project is released under the [MIT License](/LICENSE)