# AI答题助手 (FloatyAnswer)

<p align="center">
<a href="https://github.com/Townrain/FloatyAnswer/releases"><img alt="GitHub Release" src="https://img.shields.io/github/v/release/Townrain/FloatyAnswer"></a>
<a href="https://github.com/Townrain/FloatyAnswer/stargazers"><img alt="GitHub Stars" src="https://img.shields.io/github/stars/Townrain/FloatyAnswer"></a>
<a href="https://github.com/Townrain/FloatyAnswer/actions"><img alt="Build Status" src="https://img.shields.io/github/actions/workflow/status/Townrain/FloatyAnswer/android_build.yml"></a>
<a href="/LICENSE"><img alt="License" src="https://img.shields.io/badge/License-AGPLv3-blue"></a>
</p>


> 📌 **项目起源**：本项目基于 [wb-hwang/AIAnswerer-Android](https://github.com/wb-hwang/AIAnswerer-Android)（MIT License）Fork 并独立发展。上游已停止维护（最后更新：2025年10月），本仓库持续迭代中。感谢原作者 [@wb-hwang](https://github.com/wb-hwang) 的优秀基础工作。

[中文](#中文使用指南) | [English](README_EN.md)

## 中文使用指南

### 应用简介

AI答题助手是一款基于 OCR/视觉模型与大语言模型的安卓答题工具。通过悬浮窗截图识别题目，并调用 DeepSeek AI 等兼容 OpenAI 接口的模型为你快速给出答案，适用于练习、查缺补漏或自测场景。

### 功能亮点

- 🖼️ 屏幕快速截取：一键截取当前屏幕，自动聚焦题目区域
- 📖 屏幕读取模式：通过无障碍服务直接读取屏幕文字，无需截图，速度更快
- 📝 智能文字识别：支持中日韩法德西葡等本地 OCR 识别 + 俄阿等 VLM 云端识别
- 👁️ 视觉模型支持：可使用视觉模型替代 OCR，适合噪音较多的页面
- 🔍 联网搜索增强：支持 LLM 工具调用模式（模型自主决定搜索时机并可多轮追问）
- 🤖 AI 实时答题：根据题型生成解析，并自动复制答案
- 📋 批量答题：截图包含多题时逐题搜索并返回所有答案
- ⚡ 并行答题：多题模式下支持并发处理，显著提升答题速度
- 💬 悬浮窗操作：无需切换应用即可完成截屏、预览、提交
- 🔒 本地可控：自定义 API Key，随时启停网络请求
- 🎨 多款精致主题：8 套内置主题（含 Claude Warm 珊瑚暖调）+ JSON 导入自定义，悬浮按钮随主题自动变色
- 🌐 多语言输出：UI 支持中英双语，AI 输出支持 11 种语言（中英日法德西葡韩俄阿 + 自适应）

### 工作原理

```
┌──────────┐   ┌──────────────────┐   ┌──────────────┐   ┌───────────────┐   ┌──────────┐
│ 悬浮球    │──▶│ 采集：截图 / 读屏  │──▶│ OCR / VLM    │──▶│ LLM 答题+联网  │──▶│ 答案卡片  │
│ (常驻)    │   │ 录制 / 多图收集   │   │ (识别文字)    │   │ (生成解析)     │   │ (折叠/复制)│
└──────────┘   └──────────────────┘   └──────────────┘   └───────────────┘   └──────────┘
```

### 安装与准备

1. 使用 Android 10 及以上系统的设备，并保持网络通畅。
2. 安装 APK 文件；首次安装需按照系统提示允许来自未知来源的应用。
3. 首次启动时，按照屏幕提示授予悬浮窗、屏幕录制（或无障碍）和通知等必要权限。
4. 在设置页配置 LLM 模型信息（见下方「快速上手」）。

### 快速上手

**第一步：配置模型（只需一次）**

1. 打开应用，点击主页右上角**设置**图标。
2. 进入**模型设置**（LLM 答题模型）：
   - 从厂商列表中选择一家（如 DeepSeek、OpenAI 兼容服务等，内置 50+ 厂商预设）；
   - 展开厂商卡片，填入你的 **API Key**（API 地址一般已自动填好）；
   - 点击**测试连接**确认可用；
   - 在模型列表中选择要使用的模型。
3. （可选）进入**视觉模型设置**，开启视觉模型开关并同样配置 API Key 与模型——开启后遇到截图噪音较多或含图的题目时，会优先用视觉模型理解题目。
4. 返回主页，若底部「进入答题模式」按钮可点击（非灰色），说明模型已配置完成。

**第二步：开始答题**

1. 回到主页，点击**进入答题模式**。
2. 首次使用按提示授权：
   - **悬浮窗权限**：用于显示悬浮球和答案卡片（必开）；
   - **屏幕录制权限**：截图模式需要（一次性授权，之后可长期使用）；
   - 或开启**无障碍服务**：使用「屏幕读取」模式需要。
3. 屏幕边缘会出现悬浮球。

**第三步：答题**

1. 停留在题目页面（任意 App 均可）。
2. 点击悬浮球 → 自动截图并识别题目 → 弹出答案卡片。
3. 答案已按设置自动复制到剪贴板，可直接粘贴使用；点击卡片可展开查看完整解析。

> 💡 截图后如需手动修正识别文本，可在确认页编辑后再提交。

### 四种答题模式

#### 1. 截图答题（默认模式）

点击悬浮球 → 截取当前屏幕 → OCR 识别 → LLM 答题。适合大多数场景。

配合**裁剪模式**使用（设置 → 答题设置）：

| 裁剪模式 | 行为 |
|---------|------|
| 全屏识别 | 整屏识别，适合题目占据大部分屏幕 |
| 逐题裁剪 | 每道题单独框选，保存选区供后续使用 |
| 单次裁剪 | 框选一次，固定区域每次只识别该区域 |

#### 2. 屏幕读取模式（无障碍，更快）

不截图，通过**无障碍服务直接读取屏幕上的文字**，速度更快、无需截图权限。

启用方法：主页的采集模式卡片切换为「屏幕读取」→ 跳转系统设置开启「AI答题助手」的无障碍服务 → 返回。

> 注意：该模式读取的是页面文字节点，无法处理图片型题目；此时建议开启视觉模型（VLM）作为补充。

#### 3. 录制模式（批量答题）

适合一页多题 / 连续刷题：连续截取多张屏幕，自动去重、逐题识别，停止后统一返回全部答案。

1. 悬浮球长按弹出快捷面板 → 点击**录制**开启；
2. 停留在题目上，每次点击悬浮球采集一屏，可连续采集多题；
3. 采集完成后点击**录制**结束（或直接关闭悬浮窗），等待统一分析；
4. 所有题目答案按顺序显示在答案卡片中，并自动复制全部答案。

多题模式下自动启用**并行答题**（可在设置中调整并发数 1-10），速度显著提升。

#### 4. 多图收集模式

将多张截图合并为一次提问：适合跨页题目、或需要把几道相关题一起发给 AI 的场景。

1. 快捷面板点击**多图**开启；
2. 每次点击悬浮球添加一屏，重复直到收集完毕；
3. 再次点击**多图**结束并提交，AI 合并分析后统一返回答案。

### 悬浮窗操作指南

| 操作 | 行为 |
|------|------|
| 单击悬浮球 | 截图并答题（或录制模式下采集一题） |
| 长按悬浮球 | 展开/收起快捷面板（可调长按时长，默认 300ms-3000ms 可设置） |
| 拖拽悬浮球 | 移动位置，松手自动吸附屏幕边缘 |
| 快捷面板 | VLM 视觉、联网搜索、深度思考、录制、多图 开关 |

答案卡片支持**折叠**：折叠后仅显示答案摘要（如选择题字母），点击展开查看完整解析。

### 支持的题型

- 选择题：识别题干与选项，标记推荐答案并给出理由
- 填空题：生成精炼答案，适用于多空位题目
- 问答题：提供结构化解答或要点式分析

### 联网搜索

支持 LLM 工具调用模式的联网搜索：开启后，模型会根据题目自主判断是否需要搜索（时政、最新资讯类题目），并可多轮追问。内置 Tavily / Bocha / Zhipu / Exa 等 10 家搜索供应商，在「设置 → 联网搜索」中配置。

> 若当前模型不支持函数调用（function calling），联网搜索会自动禁用并在设置页提示。

### 使用小贴士

- 保持截图清晰、居中，避免复杂背景，以提升 OCR 准确率。
- 如需暂停网络请求，可暂时断网或在设置页关闭 AI 回答。
- 答案生成后可再次点击悬浮按钮刷新题目，便于连续练习。
- 遇到图片型/含图题目时，开启视觉模型（VLM）效果更好。
- 在「设置 → 外观」中可调整悬浮球大小、透明度、图标缩放，并选择 8 套内置主题或导入自定义 JSON 主题。

### 常见问题

- **提示缺少权限？** 前往系统设置搜索「悬浮窗」「屏幕录制」「无障碍」等选项，手动开启相关权限。
- **「进入答题模式」按钮是灰色的？** 说明模型尚未配置完成：需要至少一个厂商已填写 API Key 并选中模型。前往设置 → 模型设置完成配置。
- **识别不准确？** 在确认页手动修正文本，或重新截图后再提交；图片噪音多时可开启视觉模型（VLM）。
- **AI 没有回应？** 检查网络、确认 API Key 有效，并确保模型账户余额充足；可在设置中用「测试连接」快速排查。
- **屏幕读取模式没有反应？** 确认无障碍服务已开启且未被系统省电策略限制。
- **联网搜索不生效？** 检查设置中搜索开关和供应商 API Key；若模型不支持函数调用，该功能会被自动禁用。
- **答案卡片挡住了屏幕？** 点击卡片可折叠为仅摘要，或直接关闭卡片；悬浮球可拖到任意位置。
- **担心隐私？** 所有配置仅保存在本地（API Key 使用加密存储），不注册账号、不上传使用数据；识别内容仅在答题时发送到你指定的 AI 服务。

### 隐私与免责声明

- 应用会将识别出的文字发送至所选 AI 服务，请避免上传敏感或受限内容。
- API 请求可能产生费用（如 DeepSeek），请留意使用频率。
- 本应用仅用于学习与研究，请遵守考试纪律和法律法规，任何违规使用后果自负。

### 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material3 |
| OCR | Google ML Kit (中文/拉丁/日/韩) + VLM 降级 |
| 视觉模型 | OpenAI 兼容接口 (DeepSeek/GPT-4o 等) |
| 联网搜索 | 多供应商 (Tavily/Bocha/Zhipu/ExaMCP 等 10 家) |
| 网络 | OkHttp 4.12.0 |
| 存储 | MMKV + EncryptedSharedPreferences |
| 构建 | Gradle (AGP 8.13.0) |

### 项目结构

```
com.hwb.aianswerer/
├── BaseActivity.kt            # 统一语言配置基类
├── MyApplication.kt           # Application 初始化
├── MainActivity.kt            # 主界面（权限管理、答题设置）
├── FloatingWindowService.kt   # 悬浮窗核心服务（多窗口协调）
├── FloatingWindowManager.kt   # 窗口管理（A/B/C/D 独立窗口）
├── FloatingWindowViewModel.kt # 悬浮窗状态管理
├── SettingsService.kt         # 集中式设置读取
├── CaptureHandler.kt          # 截图→裁剪→识别管线
├── CapturePipeline.kt         # 核心识别管线（OCR→VLM→LLM）
├── RecordingCoordinator.kt    # 录制模式协调器
├── ImageCollector.kt          # 图片收集器
├── AnswerFetcher.kt           # 答案获取器
├── FloatingAnswerCard.kt      # 答案卡片组件
├── ConfirmTextActivity.kt     # 识别文本确认/编辑
├── ImageCropActivity.kt       # 图片裁剪（四角拖拽）
├── SettingsActivity.kt        # 通用设置
├── ModelSettingsActivity.kt   # API 模型配置
├── AboutActivity.kt           # 关于页面
├── Constants.kt               # 常量 · 提示词组装 · 多语言路由中枢
├── api/
│   ├── OpenAIClient.kt        # OpenAI 兼容 API 客户端
│   ├── search/                 # 联网搜索模块
│   │   ├── WebSearchProviders.kt # 多供应商联网搜索
│   │   ├── WebSearchClientFactory.kt # 供应商工厂
│   │   ├── WebSearchProvider.kt # 搜索 Provider 基类
│   │   └── WebSearchToolExecutor.kt # LLM 工具调用搜索执行器
│   └── vision/                # 视觉模型模块
│       ├── VisionProvider.kt
│       ├── VisionProviderFactory.kt
│       ├── VisionFilterResult.kt
│       └── OpenAIVisionProvider.kt
├── config/
│   ├── AppConfig.kt           # 配置管理门面（MMKV + 加密存储）
│   ├── ApiConfig.kt           # API 配置
│   ├── UIConfig.kt            # UI 配置
│   ├── VisionConfig.kt        # 视觉模型配置
│   └── ConfigStorage.kt       # MMKV 存储键定义
├── models/                    # 数据模型（ChatMessage、ToolSpec、ToolCall、ModelCapabilityChecker 等）
├── providers/                 # 模型厂商管理
│   ├── ProviderStorage.kt
│   ├── ProviderConfigResolver.kt
│   └── ProviderSyncManager.kt
├── ui/
│   ├── components/            # 共享 Compose 组件
│   │   ├── PillButtonCard.kt  # 悬浮按钮 + 长按手势
│   │   ├── QuickToggles.kt    # 快捷开关面板
│   │   ├── FloatingWindowContent.kt # Window A/B/C/D Composable
│   │   ├── FloatingAnswerCard.kt    # 答案卡片
│   │   ├── RecordingResultCard.kt   # 录制结果卡片
│   │   ├── AnswerCard.kt      # 单题答案卡片
│   │   ├── CtaBar.kt          # CTA 按钮（模型检测）
│   │   └── ...                # 其他组件
│   ├── pages/                 # 页面
│   │   ├── HomePage.kt        # 主页
│   │   ├── SettingsPage.kt    # 设置页
│   │   └── WebSearchPage.kt   # 联网搜索设置
│   ├── dialogs/               # 对话框
│   ├── icons/                 # 本地图标定义
│   └── theme/                 # Material3 主题
└── utils/
    ├── AppLog.kt              # 统一日志工具
    ├── ClipboardUtil.kt       # 剪贴板工具
    ├── ImageCropUtil.kt       # 图片裁剪工具
    └── LanguageUtil.kt        # 语言切换工具
```

### 更新说明

详见 [变更日志](变更日志.md) 或 [CHANGELOG](CHANGELOG.md)。近期重要更新：
- **最新（未发布）**: 联网搜索统一为 LLM 工具调用模式（预搜索注入已移除）+ 录制模式可靠性修复（连续录制/快速重启/进度分母/超时兜底）+ 答案解析强化（题号前缀剥离/JSON 强制输出）+ 悬浮窗折叠交互与 C/D 窗 WRAP_CONTENT 自适应
- **v1.7.0**: 四窗口悬浮窗架构（A/B/C/D 独立窗口）+ C/D 生命周期分离 + 长按时长自定义 + 模型配置检测 + 悬浮窗徽标移除 + 多窗口缺陷修复
- **v1.6.2**: 多厂商测试连接修复 + 悬浮按钮拖拽卡死修复 + 暗色主题响应 + API URL 多版本兼容 + LLM maxTokens 512→4096 + 图标去重 & 主题合规 & 动画可测试性重构 + 答案卡片独立组件

### License

This project is released under the [GNU Affero General Public License v3.0](/LICENSE)
