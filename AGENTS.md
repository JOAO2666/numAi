# numAi — Agent Guide
An AI client made for Android 1.0+ with support for thinking, vision and web search.
## Architecture

- **3 activities**: `MainActivity`, `FirstTimeActivity`, `SettingsActivity` (root package). No Fragments, no support libs.
- **6 packages** under `io.github.gohoski.numai`:
  - Root package — Activities (`MainActivity`, `FirstTimeActivity`, `SettingsActivity`) and `Search` interface
  - `api/` — `ApiClient` (raw `HttpURLConnection`), `ApiService` (builds requests), `ApiManager`, `ApiCallback`, `ApiError`, `ApiRequest`, `ApiResponse`, `ApiResult`
  - `model/` — `Chat`, `Config`, `Message`, `Role`
  - `data/` — `ChatManager`, `ConfigManager`, `MessageManager`
  - `ui/` — `Loading`, `MarkdownParser`, `MessageAdapter`, `SettingsHelper`
  - `util/` — `Base64`, `ConnectionInputStream`, `ModelSelector`, `SSLDisabler`
- **No dependencies**. NNJSON (`cc.nnproject.json.*`) is bundled source under `app/src/main/java/cc/nnproject/json/`.
- **Threading**: Raw `Thread` + `Handler(Looper.getMainLooper())`. Never use `AsyncTask` (API 1).
- **Config**: `ConfigManager` wraps `SharedPreferences`. Always use `editor.commit()` (not `apply()` — doesn't exist on API 1).

## API 1 / Java 1.5 constraints

- No `android.app.Fragment`, `AsyncTask`, `android.content.ClipboardManager` (use `android.text.ClipboardManager`).
- No `Calendar.getInstance().toInstant()`, `String.join()`, `Build.VERSION_CODES` constants above API 1.
- No `try-with-resources` (Java 7).
- Use `new Thread(...).start()`, `runOnUiThread`, or `Handler`.
- `SSLDisabler.disableSSLCertificateChecking()` must remain called at startup (in `MainActivity.onCreate`).

## Code conventions

- Classes are `public` (needed across packages). Members remain package-private unless accessed from other packages.
- `Config` is a plain POJO with getters/setters; `ConfigManager` is the singleton facade — always use `ConfigManager.getInstance(context)`.
- Streaming SSE parser in `MainActivity.readStream()` reads `data:` lines from `InputStream`.

## Key quirks
- **Thinking flags** in `ApiService.chatCompletion()` are hardcoded per base URL (`api/ApiService.java:82-98`): APIs use different reasoning fields. Default falls back to `reasoning_effort: "high"`.
- **Image attachment** scales to max 1080×1080, JPEG 80%, then base64 inlines into the OpenAI message content array.
- **Model selector** (`ModelSelector`) fetches `/models` endpoint and filters for `/v1/chat/completions` capability if the response includes `endpoints`.

## Build
**Gradle 2.3.2**, `compileSdk 25`, `minSdk 1`, `targetSdk 25`. Do not execute build commands.
