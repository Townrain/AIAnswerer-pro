# AI Answer Assistant (AIAnswerer)

> 📌 **Project Origin**: This project is forked and independently developed from [wb-hwang/AIAnswerer-Android](https://github.com/wb-hwang/AIAnswerer-Android) (MIT License). The upstream has been inactive since October 2025. This repository continues active development. Thanks to the original author [@wb-hwang](https://github.com/wb-hwang) for the excellent foundational work.

[中文](README.md) | [English](#english-user-guide)

## English User Guide

### App Introduction
AI Answer Assistant is an Android answering tool based on OCR/vision models and large language models. It captures screenshots through a floating window to recognize questions and calls DeepSeek AI and other OpenAI-compatible models to quickly provide answers. It's suitable for practice, gap-filling, or self-assessment scenarios.

### Feature Highlights
- 🖼️ Quick Screen Capture: One-click screenshot of the current screen, automatically focusing on the question area
- 📖 Screen Reading Mode: Directly read screen text through accessibility services without screenshots, faster speed
- 📝 Smart Text Recognition: Supports Chinese and English recognition, with editing and correction before submission
- 👁️ Vision Model Support: Can use vision models instead of OCR, suitable for noisy pages
- 🔍 Web Search Enhancement: Automatically search related materials as answering references
- 🤖 AI Real-time Answering: Generate analysis based on question types and automatically copy answers
- 📋 Batch Answering: When multiple questions are in a screenshot, search each question and return all answers
- ⚡ Parallel Answering: Support concurrent processing in multi-question mode, significantly improving answering speed
- 💬 Floating Window Operation: Complete screenshots, preview, and submission without switching apps
- 🔒 Local Control: Custom API Key, start/stop network requests anytime
- 🌐 Bilingual Support: Support Chinese and English interface switching

### Tech Stack
| Category | Technology |
|----------|------------|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material3 |
| OCR | Google ML Kit (Chinese + Latin) |
| Vision Model | OpenAI Compatible API (DeepSeek/GPT-4o, etc.) |
| Web Search | Tavily API |
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
├── BaseActivity.kt           # Unified language configuration base class
├── MyApplication.kt          # Application initialization
├── MainActivity.kt           # Main interface (permission management, answer settings)
├── FloatingWindowService.kt  # Floating window core service
├── ScreenReaderService.kt    # Accessibility screen reading service
├── ScreenCaptureManager.kt   # Screenshot management (MediaProjection)
├── TextRecognitionManager.kt # OCR text recognition
├── ConfirmTextActivity.kt    # Recognition text confirmation/editing
├── ImageCropActivity.kt      # Image cropping (four-corner drag)
├── SettingsActivity.kt       # General settings
├── ModelSettingsActivity.kt  # API model configuration
├── AboutActivity.kt          # About page
├── Constants.kt              # Constants and system prompts
├── api/
│   ├── OpenAIClient.kt       # OpenAI compatible API client
│   ├── TavilyClient.kt       # Tavily web search client
│   └── vision/               # Vision model module
│       ├── VisionProvider.kt
│       ├── VisionProviderFactory.kt
│       ├── VisionFilterResult.kt
│       └── OpenAIVisionProvider.kt
├── config/
│   └── AppConfig.kt          # Configuration management (MMKV + encrypted storage)
├── models/                   # Data models
├── ui/
│   ├── components/           # Shared Compose components
│   ├── dialogs/              # Dialogs
│   ├── icons/                # Local icon definitions
│   └── theme/                # Material3 theme
└── utils/
    ├── AppLog.kt             # Unified logging tool
    ├── ClipboardUtil.kt      # Clipboard utility
    ├── ImageCropUtil.kt      # Image cropping utility
    └── LanguageUtil.kt       # Language switching utility
```

### Changelog

#### v1.2 (Screen Reading Mode & UI Fixes)
* **Screen Reading Mode (New Feature)**
  - Added capture mode switching: Screenshot Recognition / Screen Reading, chip selection in settings page
  - Screen reading obtains screen text nodes directly through AccessibilityService without screenshot permissions, faster speed
  - When selecting screen reading, if accessibility service is not enabled, automatically jumps to system accessibility settings
  - Accessibility status indicator (green/red) shows service enabled status in real-time
  - Automatically refreshes status when returning from system settings (LifecycleEventObserver monitors ON_RESUME)
  - Accessibility prompt text is clickable, directly jumps to system settings
  - Added ScreenReaderService, accessibility_service_config.xml, AndroidManifest registers service
* **Bug Fixes**
  - Fixed regex `\\{[^{}]*}` crashing on some Android devices (changed to `\\{[^\\{\\}]*\\}` explicit escaping)
  - Fixed automatic copy to clipboard copying both question and answer, changed to copy only answer text
* **Dark Mode UI Fixes**
  - Fixed dark mode buttons showing right-angle border overlapping with large rounded border
  - Rewrote Glass.kt and Shadow.kt: unified use of drawBehind to draw background + border + shadow, eliminating double borders
  - Shadow system changed from Modifier.shadow() to drawIntoCanvas + setShadowLayer native shadow
  - Shadow parameters changed from Shape to Dp (cornerRadius), unified all call points
* **Component Fixes**
  - Fixed AnimatedButton graphicsLayer ordering causing rendering issues
  - Fixed Tonal button right-angle border, simplified to Modifier.background + shape
  - Fixed icons not displaying due to Color.Unspecified, restored to Color.Black
  - Unified all shadow call parameters to adapt to new Dp type

#### v1.1 (Regex Filtering & Thinking Mode)
* **Regex Filter Toggle**
  - Added "Multi-question Regex Filter" toggle in web search settings (default on)
  - When off, OCR detecting multiple questions will still perform web search
* **Thinking Mode Toggle**
  - Added "Enable Thinking Mode" toggle in LLM model settings (default off)
  - When on, sends `reasoning_effort: "medium"` parameter to API
  - Suitable for o1, DeepSeek-R1 and other reasoning models

#### v1.0 (Stability & Quality Improvements)
* **Bug Fixes (10)**
  - Fixed VLM failure causing OCR fallback to fail
  - Fixed VLM configuration changes not taking effect
  - Fixed Bitmap memory leak during screenshots
  - Fixed back button accidentally stopping floating window service
  - Fixed concurrent request race conditions
  - Fixed temporary file residue in crop Activity
  - Fixed ghost notifications remaining in notification bar after language switching
  - Fixed HTTP error responses not properly closing connections
  - Fixed VLM connection test format error
  - Fixed JSON parsing regex greedy matching issue
* **Performance Optimization**
  - Global shared Gson instance, reducing memory allocation
  - HTTP responses use use{} auto-close, preventing connection leaks
  - LLM Temperature parameter configurable
  - Compose recomposition delays extracted as constants
* **Code Quality**
  - Added Mutex lock to prevent concurrent requests
  - Unified error handling and resource release
  - Improved ProGuard rules
* **Version** v1.0.0

#### v0.9 (Parallel Answering & Performance Optimization)
* **Parallel Answering Mode**
  - Added concurrent answering settings card, support enable/disable parallel mode
  - Configurable maximum concurrency (1-10), default 3
  - High concurrency (>5) shows warning to avoid API rate limiting
  - Test buttons for LLM, VLM, and web search respectively, can test each API latency
  - Automatic parallel processing in multi-question mode, significantly improving answering speed
* **Progress Display Optimization**
  - Parallel mode shows `Answering (3/8)` format, real-time progress update
  - When some questions fail, shows warning, only returns successful results
* **Enhanced Test Functionality**
  - Test buttons show actual latency time (milliseconds)
  - Support testing LLM, VLM, Tavily three APIs concurrent performance
  - Test results display independently, no interference
* **GitHub Actions Auto-build**
  - Added CI/CD workflow, support automatic Debug APK build
  - Auto-release when pushing `v*` tags
* **Version** v0.0.9

#### v0.8 (UI Premium Feel Optimization)
* **Frosted Glass Material**
  - All cards use micro-gradient + semi-transparent + gradient border, farewell to solid flat colors
  - Floating window answer card supports frosted glass effect, transparency adjustable in settings
  - Dark mode cards have "floating" glass texture
* **Gradient Glow Buttons**
  - Bottom answer button upgraded from solid color to horizontal gradient (primary → secondary)
  - With glow shadow effect, more prominent visual focus
* **Real-time Theme Switching**
  - Added appearance mode in settings: Follow System / Light Mode / Dark Mode
  - Switch takes effect immediately, no app restart needed
  - Status bar style automatically syncs
* **Color System Refactoring**
  - Light mode: Bright blue primary `#4A6CF7`, warm cream background `#F6F5F3`
  - Dark mode: Deep blue primary `#2563EB`, dark gray-blue background `#0F1118`
  - Notification bar colors unified with theme
* **Rounded Corners and Spacing**
  - Unified rounded corners 16-24dp, more breathing room
  - Button rounded corners 18dp, capsule-shaped FilterChip
* **Status Bar Adaptation**
  - Use `enableEdgeToEdge` for status bar adaptation
  - Light mode dark icons, dark mode light icons
* **Cleanup Redundant Features**
  - Removed "Question Content Range" input box and related code
* **Version** v0.0.8

#### v0.7 (Vision Model Integration & Multi-question Optimization)
* **Vision Model (VLM) Integration**
  - Support using vision model to directly analyze screenshots, replacing OCR recognition
  - Abstract VisionProvider interface, supporting OpenAI compatible format
  - Factory pattern for creating Providers, easy to extend
  - Settings page can configure vision model API address, Key, model name
  - Support testing vision model connection
  - VLM failure automatically falls back to OCR mode
* **Multi-question Mode Optimization**
  - VLM automatically separates each question in multi-question screenshots
  - Each question searches web separately, improving search accuracy
  - Call LLM for each question individually, ensuring each question gets an answer
  - Status display optimization: `Searching (1/8)`, `Getting answers (2/8)`, etc.
* **Image Compression Optimization**
  - Fixed image size exceeding API limit (2048x2048)
  - Simultaneously limit width and height, proportional scaling
* **Timeout Optimization**
  - Vision model API timeout increased from 60 seconds to 120 seconds
* **Architecture Optimization**
  - Added `api/vision/` module, independently managing vision model related code
  - VisionFilterResult supports separating question list
  - AppConfig extended vision model configuration items
* **Version** v0.0.8

#### v0.6 (Web Search Enhancement & Floating Window Optimization)
* **Tavily Web Search**
  - Integrated Tavily search engine API, automatically search related materials in single question mode and inject into LLM context
  - Search results as answering references, improving accuracy for uncommon questions
  - Settings page can configure Tavily API Key (encrypted storage) and enable toggle
  - Support testing Tavily connection
  - Smart search keyword extraction: extract question stem and options from OCR text, filter UI noise
  - Multi-question mode automatically skips search, avoiding invalid API calls
* **Floating Window Interaction Refactoring**
  - Floating button supports free drag movement, can snap to screen left/right edges
  - Click to screenshot, drag to move, one button two functions
  - Card appears directly below button, not blocking button position
  - Button position not affected by card visibility, stable when window width changes
  - Fixed floating window blocking touch events of underlying apps (WRAP_CONTENT window)
* **Floating Window Appearance Customization**
  - Settings page added floating window appearance configuration: button size (32~80dp), button transparency, card transparency
  - Takes effect immediately, no need to restart Service
* **Bug Fixes**
  - Fixed second screenshot outputting first answer (wait for Compose recompletion + clear ImageReader old frames)
* **UI Optimization**
  - Settings page supports vertical scrolling
  - About page updated GitHub address, removed email card
* **Version** v0.0.6

#### v0.5 (Code Quality Optimization)
* **Security Enhancement**
  - API Key uses EncryptedSharedPreferences encrypted storage, no longer stored in plaintext
  - Release build removes HTTP logs, preventing API Key leakage to logcat
  - Added OkHttp CertificatePinner certificate pinning, preventing man-in-the-middle attacks
  - Even in Debug mode, Authorization header is desensitized
* **Architecture Optimization**
  - Extracted BaseActivity for unified language configuration, eliminating 6 duplicate code locations
  - Floating window Composable components in separate files, Service responsibilities clearer
  - Unified coroutine scope, fixed CancellationException being swallowed
* **Internationalization Improvement**
  - All floating window status messages support Chinese and English switching
  - Notification channel names, clipboard labels, etc. all use string resources
* **Network Enhancement**
  - Added network connection pre-check, quick prompt when no network
  - API requests support automatic retry (exponential backoff)
  - Automatically cancel ongoing network requests when Service is destroyed
* **Build Optimization**
  - Removed redundant ML Kit dependencies (-10MB package size)
  - All dependency versions unified to Version Catalog
  - Tightened ProGuard rules, improving R8 obfuscation effect
* **Code Quality**
  - Unified logging tool AppLog, Release build silent
  - Eliminated all `e.printStackTrace()` calls
  - Fixed `savedCropRect!!` null safety risk
  - Added core unit tests (extractJsonPayload, isApiConfigValid)
* **JSON Parsing Optimization**
  - Support batch answering: return all answers when screenshot contains multiple questions
  - 5-level fallback parsing strategy: direct parse → extract fix → regex array → regex object → text extraction
  - Fixed Chinese quotes causing JSON truncation
  - System prompt optimization: force AI to fill answer field, cannot be empty

#### v0.4
* Optimized prompts
* Compatible with GPT-5 markdown format

#### v0.3
* Added pre-OCR cropping feature, improving question recognition ability

#### v0.2
* Fixed release package unable to request AI API issue

#### v0.1
* Initial release

### License
This project is released under the [MIT License](/LICENSE)