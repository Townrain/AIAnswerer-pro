# AI Answer Assistant (AIAnswerer)

> 📌 **Project Origin**: This project is forked and independently developed from [wb-hwang/AIAnswerer-Android](https://github.com/wb-hwang/AIAnswerer-Android) (MIT License). The upstream has been inactive since October 2025. This repository continues active development. Thanks to the original author [@wb-hwang](https://github.com/wb-hwang) for the excellent foundational work.

[中文](README.md) | [English](#english-user-guide)

## English User Guide

### App Introduction
AI Answer Assistant is an Android answering tool based on OCR/vision models and large language models. It captures screenshots through a floating window to recognize questions and calls DeepSeek AI and other OpenAI-compatible models to quickly provide answers. It's suitable for practice, gap-filling, or self-assessment scenarios.

### Feature Highlights
- 🖼️ Quick Screen Capture: One-click screenshot of the current screen, automatically focusing on the question area
- 📖 Screen Reading Mode: Directly read screen text through accessibility services without screenshots, faster speed
- 📝 Smart Text Recognition: Supports CJK/FR/DE/ES/PT local OCR + VLM cloud recognition for RU/AR
- 👁️ Vision Model Support: Can use vision models instead of OCR, suitable for noisy pages
- 🔍 Web Search Enhancement: Two modes available — LLM Tool Calling (the model decides when to search and may ask follow-up rounds) and Pre-Search Injection (legacy mode), switchable in the web search settings
- 🤖 AI Real-time Answering: Generate analysis based on question types and automatically copy answers
- 📋 Batch Answering: When multiple questions are in a screenshot, search each question and return all answers
- ⚡ Parallel Answering: Support concurrent processing in multi-question mode, significantly improving answering speed
- 💬 Floating Window Operation: Complete screenshots, preview, and submission without switching apps
- 🔒 Local Control: Custom API Key, start/stop network requests anytime
- 🎨 8 Built-in Themes: Including Claude Warm coral tones + JSON import for custom themes, floating button auto-colors with theme
- 🌐 Multilingual UI: Chinese/English interface + 11 AI output languages (CN/EN/JP/FR/DE/ES/PT/KO/RU/AR + adaptive)

### Tech Stack
| Category | Technology |
|----------|------------|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material3 |
| OCR | Google ML Kit (Chinese/Latin/Japanese/Korean) + VLM fallback |
| Vision Model | OpenAI Compatible API (DeepSeek/GPT-4o, etc.) |
| Web Search | Multi-provider (Tavily/Bocha/Zhipu etc.) |
| Network | OkHttp 4.12.0 |
| Storage | MMKV + EncryptedSharedPreferences |
| Build | Gradle (AGP 8.13.0) |

### Installation and Preparation
1. Use a device with Android 11 or above and keep the network connected.
2. Install the provided APK file; first installation requires allowing apps from unknown sources according to system prompts.
3. Set up LLM model information.
4. On first launch, follow the on-screen prompts to grant necessary permissions such as floating window, screen capture, and notifications.

### Quick Start
1. Refer to in-app instructions

### Supported Question Types
- Multiple Choice: Identify question stem and options, mark recommended answers and provide reasons
- Fill-in-the-blank: Generate concise answers, suitable for multi-blank questions
- Essay Questions: Provide structured solutions or key-point analysis

### Usage Tips
- Keep screenshots clear and centered, avoid complex backgrounds to improve OCR accuracy.
- If you need to pause network requests, temporarily disconnect from the network or disable AI answers in the settings page.
- After answer generation, you can click the floating button again to refresh the question for continuous practice.

### FAQ
- **Missing permissions prompt?** Go to system settings and search for "floating window", "screen recording", etc., to manually enable relevant permissions.
- **Inaccurate recognition?** Manually correct the text on the confirmation page, or take a new screenshot before submitting.
- **AI not responding?** Check the network, confirm the API Key is valid, and ensure the DeepSeek account has sufficient balance.

### Privacy and Disclaimer
- The application will send recognized text to the selected AI service. Please avoid uploading sensitive or restricted content.
- DeepSeek API requests may incur charges, please pay attention to usage frequency.
- This application is only for learning and research purposes. Please comply with exam discipline and laws and regulations. Any consequences of illegal use are at your own risk.

### Project Structure

```
com.hwb.aianswerer/
├── BaseActivity.kt            # Unified language configuration base class
├── MyApplication.kt           # Application initialization
├── MainActivity.kt            # Main interface (permission management, answer settings)
├── FloatingWindowService.kt   # Floating window core service (multi-window coordination)
├── FloatingWindowManager.kt   # Window management (A/B/C/D independent windows)
├── FloatingWindowViewModel.kt # Floating window state management
├── SettingsService.kt         # Centralized settings reader
├── CaptureHandler.kt          # Screenshot → crop → recognition pipeline
├── CaptureHandler.kt          # Screenshot → crop → recognition pipeline
├── CapturePipeline.kt         # Core recognition pipeline (OCR→VLM→LLM)
├── RecordingCoordinator.kt    # Recording mode coordinator
├── ImageCollector.kt          # Image collector
├── AnswerFetcher.kt           # Answer fetcher
├── FloatingAnswerCard.kt      # Answer card component
├── ConfirmTextActivity.kt     # Recognition text confirmation/editing
├── ImageCropActivity.kt       # Image cropping (four-corner drag)
├── SettingsActivity.kt        # General settings
├── ModelSettingsActivity.kt   # API model configuration
├── AboutActivity.kt           # About page
├── Constants.kt               # Constants · prompt assembly · i18n routing hub
├── api/
│   ├── OpenAIClient.kt        # OpenAI compatible API client
│   ├── search/                 # Web search module
│   │   ├── WebSearchProviders.kt # Multi-provider web search
│   │   ├── WebSearchClientFactory.kt # Provider factory
│   │   ├── WebSearchProvider.kt # Search provider base class
│   │   └── WebSearchToolExecutor.kt # LLM tool-calling search executor
│   └── vision/                # Vision model module
│       ├── VisionProvider.kt
│       ├── VisionProviderFactory.kt
│       ├── VisionFilterResult.kt
│       └── OpenAIVisionProvider.kt
├── config/
│   ├── AppConfig.kt           # Configuration facade (MMKV + encrypted storage)
│   ├── ApiConfig.kt           # API configuration
│   ├── UIConfig.kt            # UI configuration
│   ├── VisionConfig.kt        # Vision model configuration
│   └── ConfigStorage.kt       # MMKV storage key definitions
├── models/                    # Data models (ChatMessage, ToolSpec, ToolCall, ModelCapabilityChecker etc.)
├── providers/                 # Model provider management
│   ├── ProviderStorage.kt
│   ├── ProviderConfigResolver.kt
│   └── ProviderSyncManager.kt
├── ui/
│   ├── components/            # Shared Compose components
│   │   ├── PillButtonCard.kt  # Floating pill + long-press gesture
│   │   ├── QuickToggles.kt    # Quick-toggle panel
│   │   ├── FloatingWindowContent.kt # Window A/B/C/D Composables
│   │   ├── FloatingAnswerCard.kt    # Answer card
│   │   ├── RecordingResultCard.kt   # Recording result card
│   │   ├── AnswerCard.kt      # Single answer card
│   │   ├── CtaBar.kt          # CTA button (with model detection)
│   │   └── ...                # Other components
│   ├── pages/                 # Pages
│   │   ├── HomePage.kt        # Home page
│   │   ├── SettingsPage.kt    # Settings page
│   │   └── WebSearchPage.kt   # Web search settings
│   ├── dialogs/               # Dialogs
│   ├── icons/                 # Local icon definitions
│   └── theme/                 # Material3 theme
└── utils/
    ├── AppLog.kt              # Unified logging tool
    ├── ClipboardUtil.kt       # Clipboard utility
    ├── ImageCropUtil.kt       # Image cropping utility
    └── LanguageUtil.kt        # Language switching utility
```

### Changelog

See [CHANGELOG.md](CHANGELOG.md) or [变更日志](变更日志.md). Recent highlights:
- **Unreleased**: LLM Tool Calling web search mode + two-mode selector + floating window collapsible cards & WRAP_CONTENT adaptive height
- **v1.7.0**: Four-window floating architecture (A/B/C/D independent windows) + C/D lifecycle decoupling + configurable long-press duration + model config detection + pill badge removal + multi-window bug fixes
- **v1.6.2**: Dark mode button theme responsiveness + multi-version API URL fix (v2/v3/v4 compatibility)
### License
This project is released under the [GNU Affero General Public License v3.0](/LICENSE)