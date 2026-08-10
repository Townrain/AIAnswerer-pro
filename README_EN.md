# AI Answer Assistant (FloatyAnswer)

<p align="center">
<a href="https://github.com/Townrain/FloatyAnswer/releases"><img alt="GitHub Release" src="https://img.shields.io/github/v/release/Townrain/FloatyAnswer"></a>
<a href="https://github.com/Townrain/FloatyAnswer/stargazers"><img alt="GitHub Stars" src="https://img.shields.io/github/stars/Townrain/FloatyAnswer"></a>
<a href="https://github.com/Townrain/FloatyAnswer/actions"><img alt="Build Status" src="https://img.shields.io/github/actions/workflow/status/Townrain/FloatyAnswer/android_build.yml"></a>
<a href="/LICENSE"><img alt="License" src="https://img.shields.io/badge/License-AGPLv3-blue"></a>
</p>


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
- 🔍 Web Search Enhancement: LLM Tool Calling mode (the model decides when to search and may ask follow-up rounds)
- 🤖 AI Real-time Answering: Generate analysis based on question types and automatically copy answers
- 📋 Batch Answering: When multiple questions are in a screenshot, search each question and return all answers
- ⚡ Parallel Answering: Support concurrent processing in multi-question mode, significantly improving answering speed
- 💬 Floating Window Operation: Complete screenshots, preview, and submission without switching apps
- 🔒 Local Control: Custom API Key, start/stop network requests anytime
- 🎨 8 Built-in Themes: Including Claude Warm coral tones + JSON import for custom themes, floating button auto-colors with theme
- 🌐 Multilingual UI: Chinese/English interface + 11 AI output languages (CN/EN/JP/FR/DE/ES/PT/KO/RU/AR + adaptive)

### How It Works

```
┌──────────┐   ┌──────────────────┐   ┌──────────────┐   ┌───────────────┐   ┌──────────┐
│ Floating │──▶│ Capture: Screenshot│──▶│ OCR / VLM    │──▶│ LLM Answer    │──▶│ Answer   │
│  Pill    │   │ / Screen Read     │   │ (Text Recog.)│   │ + Web Search  │   │ Card     │
│ (Always) │   │ / Record / Multi  │   │              │   │               │   │(Collapse)│
└──────────┘   └──────────────────┘   └──────────────┘   └───────────────┘   └──────────┘
```

### Installation and Preparation

1. Use a device with Android 11 or above and keep the network connected.
2. Install the provided APK file; first installation requires allowing apps from unknown sources according to system prompts.
3. On first launch, follow the on-screen prompts to grant necessary permissions such as floating window, screen recording (or accessibility), and notifications.
4. Configure your LLM model in Settings (see "Quick Start" below).

### Quick Start

**Step 1: Configure your model (one-time setup)**

1. Open the app and tap the **Settings** icon in the top-right corner of the home screen.
2. Open **Model Settings** (the LLM answering model):
   - Pick a provider from the list (DeepSeek, OpenAI-compatible services, etc. — 50+ provider presets built in);
   - Expand the provider card and enter your **API Key** (the API URL is usually pre-filled);
   - Tap **Test Connection** to verify it works;
   - Select the model to use from the model list.
3. (Optional) Open **Vision Model Settings**, enable the vision model toggle, and configure its API Key and model — with this enabled, image-heavy or noisy questions are handled by the vision model first.
4. Return to the home screen. If the **Start Answer Mode** button at the bottom is enabled (not greyed out), your model is configured.

**Step 2: Start answering**

1. Back on the home screen, tap **Start Answer Mode**.
2. On first use, grant permissions as prompted:
   - **Floating window** permission: required to show the floating pill and answer cards;
   - **Screen recording** permission: needed for screenshot mode (one-time grant, usable long-term);
   - Or enable the **Accessibility service**: required for "Screen Reading" mode.
3. A floating pill appears on the edge of the screen.

**Step 3: Answer questions**

1. Stay on the question page (works in any app).
2. Tap the floating pill → the screen is captured and the question is recognized → an answer card pops up.
3. The answer is copied to your clipboard automatically (per settings), so you can paste it directly; tap the card to expand the full explanation.

> 💡 If you need to correct the recognized text manually, edit it on the confirmation page before submitting.

### Four Answering Modes

#### 1. Screenshot Mode (default)

Tap the floating pill → capture the current screen → OCR recognition → LLM answering. Suitable for most scenarios.

Combine with **crop modes** (Settings → Answer Settings):

| Crop Mode | Behavior |
|-----------|----------|
| Full Screen | Recognize the whole screen; best when the question fills most of it |
| Per-question Crop | Select each question separately; the selection is saved for reuse |
| Single Crop | Select a region once; every capture only recognizes that fixed region |

#### 2. Screen Reading Mode (accessibility, faster)

Reads the on-screen text directly through the **accessibility service** — no screenshot needed, faster, and no screen-recording permission required.

How to enable: switch the capture mode card on the home screen to "Screen Reading" → jump to system settings and enable the accessibility service for AI Answer Assistant → return.

> Note: This mode reads text nodes, so it cannot handle image-based questions; enable the Vision Model (VLM) as a supplement in that case.

#### 3. Recording Mode (batch answering)

For multi-question pages / continuous practice: capture multiple screens in a row, automatically deduplicated and recognized per question, then get all answers at once when you stop.

1. Long-press the floating pill to open the quick panel → tap **Record** to start;
2. Stay on the question page; each pill tap captures one screen — capture as many questions as you like;
3. Tap **Record** again to stop (or close the floating window) and wait for the unified analysis;
4. All answers are displayed in order in the answer card, and the full answer set is copied to the clipboard.

**Parallel answering** is automatically enabled in multi-question mode (concurrency 1–10, adjustable in Settings) for a significant speed boost.

#### 4. Multi-image Mode

Combine multiple screenshots into a single question: useful for cross-page questions or sending several related questions to the AI at once.

1. Tap **Multi-image** in the quick panel to start;
2. Each pill tap adds one screen; repeat until done;
3. Tap **Multi-image** again to finish and submit — the AI merges and analyzes everything, returning all answers together.

### Floating Window Guide

| Gesture | Action |
|---------|--------|
| Tap the pill | Capture and answer (or capture one question in recording mode) |
| Long-press the pill | Open/close the quick panel (long-press duration configurable, 300ms–3000ms) |
| Drag the pill | Move it anywhere; snaps to the screen edge when released |
| Quick panel | Toggles for VLM, Web Search, Deep Thinking, Record, Multi-image |

Answer cards are **collapsible**: collapsed shows only the answer summary (e.g. choice letters); tap to expand the full explanation.

### Supported Question Types

- Multiple Choice: Identify question stem and options, mark recommended answers and provide reasons
- Fill-in-the-blank: Generate concise answers, suitable for multi-blank questions
- Essay Questions: Provide structured solutions or key-point analysis

### Web Search

Supports LLM Tool Calling web search: when enabled, the model decides whether to search on its own (for current-affairs or recent-news questions), with multi-round follow-ups. 10 search providers are built in (Tavily / Bocha / Zhipu / Exa etc.), configurable under Settings → Web Search.

> If the active model does not support function calling, web search is automatically disabled and a hint is shown in Settings.

### Usage Tips

- Keep screenshots clear and centered, avoid complex backgrounds to improve OCR accuracy.
- If you need to pause network requests, temporarily disconnect from the network or disable AI answers in the settings page.
- After answer generation, you can tap the floating pill again to refresh the question for continuous practice.
- Enable the Vision Model (VLM) for image-based or noisy questions.
- In Settings → Appearance, you can adjust pill size, opacity, icon scale, and choose from 8 built-in themes or import a custom JSON theme.

### FAQ

- **Missing permissions prompt?** Go to system settings and search for "floating window", "screen recording", "accessibility", etc., and enable the relevant permissions manually.
- **The "Start Answer Mode" button is greyed out?** Your model is not fully configured: you need at least one provider with an API Key entered and a model selected. Go to Settings → Model Settings.
- **Inaccurate recognition?** Manually correct the text on the confirmation page, or take a new screenshot; enable the Vision Model (VLM) for noisy images.
- **AI not responding?** Check the network, confirm the API Key is valid, and make sure your model account has sufficient balance; use "Test Connection" in Settings to diagnose quickly.
- **Screen Reading mode not working?** Confirm the accessibility service is enabled and not restricted by the system's battery-saving policy.
- **Web search not working?** Check the search toggle and provider API Key in Settings; if the model doesn't support function calling, this feature is automatically disabled.
- **The answer card is blocking the screen?** Tap it to collapse to a summary, or close it; the floating pill can be dragged anywhere.
- **Worried about privacy?** All configuration is stored locally (API Keys use encrypted storage). No account, no usage-data upload; recognized content is only sent to the AI service you configured, when answering.

### Privacy and Disclaimer

- The application will send recognized text to the selected AI service. Please avoid uploading sensitive or restricted content.
- API requests may incur charges (e.g., DeepSeek), please pay attention to usage frequency.
- This application is only for learning and research purposes. Please comply with exam discipline and laws and regulations. Any consequences of illegal use are at your own risk.

### Tech Stack

| Category | Technology |
|----------|------------|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material3 |
| OCR | Google ML Kit (Chinese/Latin/Japanese/Korean) + VLM fallback |
| Vision Model | OpenAI Compatible API (DeepSeek/GPT-4o, etc.) |
| Web Search | Multi-provider (Tavily/Bocha/Zhipu/ExaMCP etc., 10 providers) |
| Network | OkHttp 4.12.0 |
| Storage | MMKV + EncryptedSharedPreferences |
| Build | Gradle (AGP 8.13.0) |

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
- **Unreleased**: Web search unified to LLM Tool Calling mode (pre-search injection removed) + recording-mode reliability fixes (consecutive recordings / quick restart / progress denominator / timeout fallback) + answer-parsing hardening (question-number prefix stripping / enforced JSON output) + floating window collapsible cards & WRAP_CONTENT adaptive height
- **v1.7.0**: Four-window floating architecture (A/B/C/D independent windows) + C/D lifecycle decoupling + configurable long-press duration + model config detection + pill badge removal + multi-window bug fixes
- **v1.6.2**: Dark mode button theme responsiveness + multi-version API URL fix (v2/v3/v4 compatibility)

### License

This project is released under the [GNU Affero General Public License v3.0](/LICENSE)
