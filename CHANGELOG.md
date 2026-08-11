# Changelog

All notable changes to AIAnswerer will be documented in this file.

## [Unreleased]

### Added
- Web search now supports LLM Tool Calling mode (function calling): the request carries a `web_search` tool definition, the model decides when to search and may ask follow-up rounds, with results fed back as tool messages
- Web search unified to LLM Tool Calling mode; the legacy pre-search injection mode and its settings selector have been removed (models without function-calling support search silently disabled)
- Non-tool-mode answering requests enable `response_format: json_object` for enforced JSON output (not sent in tool mode for compatibility)
- Settings page shows a hint when the active model cannot use web search (no function-calling support)
- WebSearchToolExecutor: unified search executor shared across all 10 search providers (Tavily/Zhipu/Bocha/Exa etc.)
- Real ExaMCP provider (MCP Streamable HTTP client: initialize session handshake + tools/call JSON-RPC + SSE/JSON dual transport parsing), removing the silent Tavily fallback
- Recording guard-rails: answers already fetched are shown immediately on stop; closing the window during recording/processing only hides it while keeping data; answer cleanup skipped while processing
- Real concurrency test entry in settings (fires N concurrent requests and reports success rate) with realistic test images (720x1280 with text) so results are meaningful
- VLM timeout degradation: 120s timeout now fails over to OCR instead of silently dropping the question
- Floating window: collapsible answer cards — expand/collapse toggle with elastic collapse animation and compact answer-summary view when collapsed
- Floating window C/D: WRAP_CONTENT adaptive height
- M14 landscape/portrait rotation support
- Settings page real-time refresh support

### Changed
- OpenAIClient: SSE parsing now handles `delta.tool_calls` (accumulated by index); in tool mode, answering requests run up to 3 rounds (2 tool rounds + final answer), each with an independent 60s timeout
- ChatMessage supports `tool_calls`/`tool_call_id` with nullable content; ChatRequest adds `tools`/`tool_choice`/`response_format` parameters
- Answer parsing hardened: tool pseudo-text is stripped block-wise preserving surrounding text (no more truncation at first `<`); question-number prefixes in answer values are stripped (e.g. `第2题：C` → `C`); Markdown answers after tool blocks are no longer discarded
- Recording mode: outer answer timeout fallback (relaxed to the tool-loop cap); progress denominator unified to max(screenshot count, recognized question count)
- Recording prompt explicitly forbids question-number prefixes in the answer/question fields (root-cause fix)
- Tool call arguments sanitized (256-char limit, control characters stripped); invalid arguments fall back to the original recognized text
- Full AI response logging downgraded to debug level to avoid unconditional log-file writes

### Fixed
- Warning log added when the tool loop hits its round cap, eliminating silent degradation
- reasoning_content is no longer promoted to content when tool_calls are present (compliant with the OpenAI requirement of content=null alongside tool_calls)
- Tool mode no longer performs duplicate pre-search injection (scheduling layer skips pre-search when tool mode is active)
- Fixed second consecutive recording not notifying results (notify idempotency flag reset on start)
- Fixed stale fallback notify coroutine stealing the new session's result after a quick stop→start (cancel leftover coroutine and reset processing flag)
- Fixed single-question outer 60s timeout leaving the floating window stuck at "fetching answer" (timeout now reports an error)
- Fixed empty answers rendering a blank answer card and clearing the clipboard (empty answers now report an error)
- Fixed late screenshots/text still processed after recording stop (late captures dropped, no more inflated counts)
- Fixed x/1 progress denominator for one-image-multi-question recordings (unified to max(screenshot count, recognized count))
- Removed plaintext API key logging in web-search settings save
- Fixed home answer button clickable in the half-configured state (model selected but API key missing); now requires both
- ThemeManager validates all 24 color fields and persists before mutating memory (import failure no longer silently lost on restart)
- Stealth mode now bypasses the D detail window for accessibility; Android 14 startForeground now passes the explicit foreground service type
- Floating window lifecycle crash chain and coordinate fixes
- Floating window recording count and drag gesture fixes
- Floating window state thread-safety and generation mechanism
- C window answer cropping (initial height, pre-measurement default, bottom crop, collapsed answer-summary invisible — multiple fixes)
- Front service notification sound removed
- POST_NOTIFICATIONS permission compatibility
- Unit test JVM explicit heap sizing and parallel limit (fixes full-suite OOM and CPU overload)

## [1.7.0] - 2026-07-27

### Added
- Four-window floating architecture: Split single floating window into A (Pill), B (Toggles), C (Status Card), D (Answer Detail) independent windows
- Constants: Added STEALTH_ALPHA, VISIBLE_ALPHA, HIDDEN_ALPHA named constants replacing hardcoded magic numbers
- WindowAContent / WindowBContent / WindowCContent / WindowDContent standalone Composables
- Configurable long-press duration slider in Settings (300ms~3000ms), user-customizable floating button hold time
- Model detection on CTA button: grays out "Configure AI Model First" when no language model is configured

### Fixed
- Notification content now hidden when stealth mode is enabled — shows app name only instead of detailed content
- Stealth mode reading consolidated through AppConfig.isStealthModeEnabled(), eliminating duplicate direct MMKV access
- Window D appearing before content ready: `imageCallbacks.onResult` now sets `showAnswer` AFTER data is populated
- Window C excess blank space: initial height reduced from 200dp to 60dp, matching compact card header size
- Windows persisting after close blocking touch: snapshotFlow observer now destroys C/D when `showAnswer=false`
- Mid-release during long-press triggering scan: `Bouncy` press logic aligned with animation duration, removed 200ms hardcoded commit window
- Card alpha not applied to Window D: added `cardAlpha` parameter propagation
- Removed Success/Error badges (✓/✗) from floating pill
- `removeWindowC()` no longer cascades to destroy Window D; their lifecycles are decoupled
- Window D positioning changed to relative-to-A (instead of relative-to-C), works correctly when C is hidden
- Floating window answer card now sits flush against the main button: card positioning compensates for the button's centered transparent margin inside Window A (pillEdgeMargin), reducing the visual gap from 16dp to 0

### Changed
- FloatingWindowManager fully rewritten for multi-window management: added attachA/B/C/D, detachA/B/C/D, per-window updateLayoutA/B/C/D
- FloatingWindowService heavily refactored for multi-window coordination and position synchronization
- FloatingWindowViewModel: Removed deprecated single-window methods (updateWindowPosition, updateWindowHeight, getCurrentWindowHeightPx, setCurrentWindowHeightPx, updateFloatingWindowHeight)
- RecordingResultCard refactored
- FloatingComponents: Added cardWidthDp (360.dp) as Window C/D fixed width
- Window C/D lifecycle decoupled: C for status messages only, D for answer content only
- Window D height fixed at 400% of Window C height, with vertical scrolling (capped at 560dp)
- Long-press threshold exactly aligned with animation completion progress
- HomePage model check uses `ProviderStorage.getEnabledProvidersFromUserConfigs()` to verify actual model selection
- FloatingWindowManagerTest: Added 669 lines of multi-window operation test coverage

### Removed
- PaginatedAnswerRegressionTest (obsolete after three-window architecture change)
- Deprecated ServiceContext single-window interface methods
- Floating pill Success/Error status badges (✓/✗)

## [1.6.2] - 2026-07-22

### Fixed
- **Multi-vendor page test connection bypassing model selection**: `ModelsPage` test button falls back to `availableModels.firstOrNull()` when `selectedModels` is empty, allowing testing without explicit model selection. Removed the fallback, now requires explicit model selection before testing
- **Floating pill drag freeze**: `pointerInput(pillW)` restarts gesture detector due to key change after pill's first layout measurement completes, causing `onDragCancel` to prevent re-dragging. Fixed to `pointerInput(Unit)` stable key
- **Recording progress card not showing**: `FloatingAnswerCard` extraction mistakenly used `!showCard` instead of `!showAnswer`, causing incorrect recording state judgment
- **Dark mode button theme responsiveness**: `AnimatedButton` Primary/Tonal variants now use `sandboxTheme()` `t.p` dynamic color, replacing hardcoded `PremiumPrimary`(#6C5CE7) and `darkAccentGradient`. Buttons now follow theme color changes instead of staying fixed purple
- **LLM provider URL dual-version concatenation bug**: `resolveApiUrl()` / `resolveVisionBaseUrl()` / `DynamicApiClient.testConnection()` fixed to recognize only `/v1` — now uses regex `.*/v\\d+\\w*$` to match any `/vN` version suffix (v1/v2/v3/v4/v1beta etc.), fixing incorrect `/v4/v1/chat/completions` concatenation for Zhipu(v4), Doubao(v3), Baidu Qianfan(v2)
- **DynamicApiClient URL double path**: `buildModelListUrl()` clears known endpoint suffixes before appending `/models`; `testConnection()` adds detection for hosts that are already complete chat URLs
- **SettingsPage quick test URL**: URL stripping logic upgraded to be version-aware, supporting `/v2/`, `/v3/` etc. paths
- **DynamicApiClient duplicate catch**: Removed duplicate `TimeoutCancellationException` catch block and duplicate `throw e` in `testConnection()`

### Changed
- Multi-vendor model test connection logic: Uses only explicitly selected models, no longer falls back to the first available model
- `ProviderConfigResolver.resolveApiUrl()` adds `endsWith("/chat/completions")` precondition check to avoid duplicate concatenation when URL is already complete
- Test `resolveApiUrl - host with trailing slash` case updated to match fixed expected value
- **LLM answer `maxTokens` 512 → 4096**, preventing truncation in reasoning mode / complex questions

### Refactored
- **Icon deduplication**: `IcCapture` (magnifier) merged into `LocalIcons.Search`; `IcVision` changed to `LocalIcons.Vision` alias. Removed 31 lines of duplicate path definitions
- **Theme compliance**: In `PillButtonCard.kt`, 6 hardcoded `Color(0xFF...)` values in `pillVisual()` and 3 in `StatusDot()` all replaced with theme color constants. Added `ImageCollectingPurple/Dark/Light` color constants
- **Animation testability**: Extracted `computeButtonScale()` pure function and `resolvePillClickAction()` pure function, added 8 unit tests

### Added
- `FloatingAnswerCard.kt` — independent answer card component, supporting pagination/progress/single-question three modes
- 18 regression tests (`resolvePillClickAction` 4 + `computeButtonScale` 4, expanded from original 10 to 18)

## [1.6.1] - 2026-07-19

### Added
- Multi-language output support: Home page output language dropdown expanded to 11 items, covering Chinese/English/Japanese/French/German/Spanish/Portuguese/Korean/Russian(VLM)/Arabic(VLM)/Other(VLM)
- First-launch dialog adds "Follow System" option — no longer overrides Android Locale when selected
- Added 11 language constants with full Locale/Prompt mapping
- `Constants.PROMPT_VERSION = 2`: Prompt version tracking constant + build-time AppLog logging
- `Constants.promptStr()` private helper function, unified prompt string loading entry point
- Output language dropdown marks Japanese/Korean with `(VLM)`, indicating vision mode requirement

### Changed
- LLM core system prompt restructured: Added role definition, behavior guidelines, 4 Few-shot examples
- App icon redesigned: New logo features book + AI circuit nodes + coral warm-tone question mark, brand cream background (#FAF9F5)
- Adaptive icon background changed from blue (#4A6CF7) to brand cream, unified with Claude Warm design system
- Play Store listing icon updated to 512px new logo
- Web search context instructions clarified: Added search result usage rules
- VLM vision prompts (single/multi-image) changed from hardcoded Chinese to bilingual Chinese/English resources
- `countQuestions()` prompt changed from hardcoded Chinese to bilingual resources
- Output language instruction "Please answer in X" changed from hardcoded to string resource
- SettingsCard question type labels no longer hardcoded in Chinese, uses `MyApplication.getString()` locale-aware
- LanguageUtil comments updated: from "supports Chinese/English" to "supports Chinese/English/Japanese/Korean/French/German/Spanish/Portuguese/Russian/Arabic etc."
- LanguageUtil: Skips `attachBaseContext` override in Follow System mode
- Constants: `promptLocale` supports all languages + system default
- PillButtonCard: Floating pill primary color changed to theme-responsive (`t.p→t.pe`), Idle state no longer hardcoded dark purple `#2D2B55`, fully matching DESIGN.md Claude Warm specification; shadows/glow/progress arc synced with theme palette
- **Test performance optimization**: Eliminated artificial delays (delay/sleep) in unit tests, full test suite latency reduced from ~28s to ~1.2s (24x speedup)
  - `CaptureHandlerTest` switched to `TestCoroutineScheduler` virtual time, 22 `delay()` calls replaced with `advanceUntilIdle()`
  - `RecordingCoordinatorTest` 25 `delay(500/300/100)` reduced to `delay(1)`, `coAnswers` delay retained but significantly reduced
  - `AnswerFetcherTest` 4 `Thread.sleep()` reduced to 1ms
  - `RecordingCoordinatorProgressTest` independent `delay()` reduced to 1ms, mock internal delays reduced to 50/30ms
  - `OpenAIClientTest` MockWebServer `setBodyDelay` 5s→2s, coroutine delay reduced
- All 96 tests passing, zero production code changes

### Removed
- Removed splash screen: SplashActivity removed, app launches directly to MainActivity with zero delay
- Removed old robot icon foreground (icon_foreground.png) and old mipmap webp fallback files

### Fixed
- Output language selection no longer overrides UI language: MergedCard removed `saveLanguage(code)` call, output language fully decoupled from UI language
- `promptResources` changed from lazy val to `getPromptResources()` function — prompts take effect immediately on language switch without restart
- LanguageUtil unknown language code fallback unified to ENGLISH (consistent with Constants prompt routing)
- VLM suffix `(VLM)` stripping logic extracted from 3 duplicate locations into `cleanOutputLang()` helper function
- `normalizeQuestionTypes`: Added "不定项" matching, supports locale-aware normalization for both Chinese and English question type labels
- FloatingWindowRegressionTest: `pillVisual` signature adapted for `Th` theme parameter
- LanguageUtilTest: unknown code fallback assertion updated from SIMPLIFIED_CHINESE to ENGLISH
- OpenAIClientTest: mock adapted for new `getPromptResources()` API
- Eliminated all 28 test compilation warnings: Robolectric/Android deprecated APIs, Kotlin `createTempDir`, missing `@OptIn`, redundant `is` checks
- ConfigStorageTest tautological assertion fixed: Changed to crash-free validation

## [1.6.0] - 2026-07-18

### Added
- Custom prompts: LLM system prompts and VLM vision prompts both customizable in settings page, leave empty for defaults
- Magnifier icon scaling: Settings page floating window appearance section adds icon scale slider (0.5x~2.0x), independently adjusts icon size
- Multi-image mode: Multiple screenshots automatically deduplicated and merged for LLM answering
- Theme options system: Multi-theme switching + JSON import/export interface

### Fixed
- RecordingCoordinator thread safety hardening (AtomicInteger + CopyOnWriteArrayList + synchronized)
- ImageCollector thread safety hardening
- Show answer summary instead of full question when floating window is collapsed
- Home page settings icon uses VectorDrawable instead of inaccurate inline ImageVector
- Home page dropdown cannot be closed on second click
- UIConfig hardcoded theme ID changed to constant reference

### Changed
- Vendor data source switched to OpenCode, sync manager adapted
- Added provider_data.json generation script
- Test coverage improved: Added RecordingCoordinator tests

## [1.5.1] - 2025-11

### Fixed
- Parallel mode answer order fix: Answers no longer out of order, now follow original question sequence

## [1.5.0] - 2025-10

### Changed
- Large file splitting: CommonComponents(1256 lines)→5 files, OpenAIClient(996 lines)→2 files, AppConfig(1005 lines)→8 files (Facade pattern)
- Added 38 unit tests covering JSON parsing, dialog queuing, prompt generation
- Vendor list expanded from 13 to 52
- System prompt condensed by ~80%
- maxTokens 1024→512, timeout 180s→60s
- Parallel mode enabled by default, concurrency defaults to 10

### Fixed
- Recording mode captureCount not incrementing causes results not to display
- Stealth mode / floating window appearance settings not taking effect immediately
- Floating window size slider not working
- Vendor URL / About page GitHub link unresponsive to clicks
- Search toggle state lost after restart
- Inconsistent question type translation causes AI to output wrong question types
- Answer card truncated when too long

### UI
- Custom app icon (neural network nodes + checkmark design)
- Android 12+ splash screen linked with new icon
- Floating window default size 56→40
- VLM quick toggle automatically enabled based on vision model configuration

## [1.4.1] - 2025-09

### Added
- Quick button layout setting: Arc layout / Horizontal layout

### Fixed
- Status card icon color adaptation in dark mode
- Settings page merges concurrency setting and connection test into the same card

## [1.4.0] - 2025-08

### Added
- Recording mode: Continuously capture multiple questions, auto-deduplicate, unified answer output
- Cancel search: HTTP requests switched to OkHttp async API, connections cancelled on coroutine cancellation
- Quick panel spring animation

## [1.3.1] - 2025-07

### Fixed
- 12 null-safety / thread-safety / resource leak / logic error fixes
- Security enhancements: EncryptedSharedPreferences downgrade protection, log sanitization, certificate pinning
- Build optimization: Signature fallback, abiFilters compatibility, AGP internal API replacement
- Floating window quick toggles (VLM, web search, deep thinking)
- Floating window interaction and position fixes

## [1.3.0] - 2025-06

### Changed
- Floating window UI refactored: Buttons displayed independently, unified dark gray color scheme
- Status messages integrated into title right side
- Concurrent test feedback fixes

## [1.2.1] - 2025-05

### Changed
- Global click feedback: Dark mode purple glow outline + light mode soft ripple
- CTA buttons redesigned as bright purple glow gradient
- Background color optimized (ivory warm tone)

## [1.2.0] - 2025-04

### Added
- Screen reading mode: Extracts on-screen text via AccessibilityService
- VLM vision model integration as OCR alternative

### Fixed
- Regex crash fixes, copy content optimization
- Dark mode UI fixes (double border, shadow system rewrite)
- Component rendering fixes (graphicsLayer order, icon colors, etc.)

## [1.1.0] - 2025-03

### Added
- Regex filter toggle (multi-question search control)
- Thinking mode toggle (DeepSeek-R1 and other reasoning models)

## [1.0.0] - 2025-02

### Fixed
- 10 bug fixes (VLM fallback, memory leaks, race conditions, etc.)
- Performance optimization: Global Gson instance, automatic HTTP connection closure
- JSON parsing 5-level fallback strategy

## [0.9.0] - 2025-01

### Added
- Parallel answering mode (configurable concurrency 1-10)
- API latency test button
- GitHub Actions auto-build

## [0.8.0] - 2024-12

### Changed
- Frosted glass material cards
- Gradient glow buttons
- Real-time theme switching (Follow System / Light / Dark)
- Color system refactoring

## [0.7.0] - 2024-11

### Added
- Vision Model (VLM) integration
- Multi-question mode per-question search optimization
- Image compression optimization (2048x2048 limit)
- `api/vision/` module separated

## [0.6.0] - 2024-10

### Added
- Tavily web search engine integration
- Floating window drag-and-drop + edge snap
- Floating window appearance customization (button size, opacity)

## [0.5.0] - 2024-09

### Changed
- API Key encrypted storage (EncryptedSharedPreferences)
- BaseActivity unified language configuration
- Internationalization improvements (Chinese/English switching)
- Network pre-check + auto-retry
- Removed redundant ML Kit dependencies (-10MB)

## [0.4.0] - 2024-08

### Fixed
- Prompt optimization
- GPT-5 markdown format compatibility

## [0.3.0] - 2024-07

### Added
- Pre-OCR cropping functionality

## [0.2.0] - 2024-06

### Fixed
- Release build unable to make AI API requests

## [0.1.0] - 2024-05

### Added
- Initial release
