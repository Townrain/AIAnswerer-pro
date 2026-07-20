# AI答题助手 (AIAnswerer)

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
- 🔍 联网搜索增强：自动搜索相关资料作为答题参考
- 🤖 AI 实时答题：根据题型生成解析，并自动复制答案
- 📋 批量答题：截图包含多题时逐题搜索并返回所有答案
- ⚡ 并行答题：多题模式下支持并发处理，显著提升答题速度
- 💬 悬浮窗操作：无需切换应用即可完成截屏、预览、提交
- 🔒 本地可控：自定义 API Key，随时启停网络请求
- 🎨 多款精致主题：8 套内置主题（含 Claude Warm 珊瑚暖调）+ JSON 导入自定义，悬浮按钮随主题自动变色
- 🌐 多语言输出：UI 支持中英双语，AI 输出支持 11 种语言（中英日法德西葡韩俄阿 + 自适应）

### 技术栈
| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material3 |
| OCR | Google ML Kit (中文/拉丁/日/韩) + VLM 降级 |
| 视觉模型 | OpenAI 兼容接口 (DeepSeek/GPT-4o 等) |
| 联网搜索 | 多供应商 (Tavily/Bocha/Zhipu等) |
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
├── FloatingWindowManager.kt  # 窗口管理（位置/动画）
├── FloatingWindowViewModel.kt # 悬浮窗状态管理
├── InteractiveTouchLayout.kt # 触摸穿透容器
├── CaptureHandler.kt         # 截图→裁剪→识别管线
├── CapturePipeline.kt        # 核心识别管线（OCR→VLM→LLM）
├── FloatingAnswerCard.kt     # 答案卡片组件
├── ConfirmTextActivity.kt    # 识别文本确认/编辑
├── ImageCropActivity.kt      # 图片裁剪（四角拖拽）
├── SettingsActivity.kt       # 通用设置
├── ModelSettingsActivity.kt  # API 模型配置
├── AboutActivity.kt          # 关于页面
├── Constants.kt              # 常量 · 提示词组装 · 多语言路由中枢
├── api/
│   ├── OpenAIClient.kt       # OpenAI 兼容 API 客户端
│   ├── WebSearchProviders.kt  # 多供应商联网搜索
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

详见 [CHANGELOG.md](CHANGELOG.md)。近期重要修复：
- **v1.6.2**: 暗色模式按钮主题响应 + 多版本 API URL 拼接修复（智谱/豆包/千帆 v2-v4 兼容）
- **v1.6.1**: 多语言输出 11 种 + 新 Logo + 测试 24x 加速

### License
This project is released under the [GNU Affero General Public License v3.0](/LICENSE)