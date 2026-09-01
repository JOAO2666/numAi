# numAi

**English** · [Português (Brasil)](README.pt-BR.md) · [Русский](README.ru.md) · [简体中文](README.zh.md)

<p align="center">
  <img src="img/logo.png" alt="numAi logo" width="180">
</p>

<p align="center">
  A lightweight, multi-provider AI client built for <strong>Android 1.0 and newer</strong>.
</p>

<p align="center">
  <a href="artifacts/numAi-1.0.0-debug.apk?raw=1"><strong>Download the last published development APK (1.0.0)</strong></a>
  ·
  <a href="https://github.com/JOAO2666/numAi/releases">Releases</a>
  ·
  <a href="https://github.com/JOAO2666/numAi/issues">Report a bug</a>
</p>

> [!IMPORTANT]
> numAi is an independent client. It does not include API keys, credits, or subscriptions. Availability, pricing, quotas, and supported features are controlled by each provider and model.

## What it does

numAi brings modern AI chat to devices ranging from the first Android release to current versions, while keeping the APK small and the interface responsive.

- OpenAI-compatible chat with streaming responses
- Separate chat and reasoning model selection for every provider
- Multiple image attachments for vision-capable models
- Markdown rendering, tables, code blocks, and MathJax formulas
- Multiline display math with `$$ ... $$` and `\[ ... \]`
- Web search through Bing or DuckDuckGo
- Web-page fetching for supported Android versions
- Background generation with cancellation support
- Per-provider API keys, model choices, and cached model catalogs
- Custom system prompt and custom OpenAI-compatible base URL
- Import an API key from a local file
- Automatic compatibility fallback when a model rejects tools or reasoning options
- Remote MCP tools over OAuth 2.0/PKCE, with configurable server URL

Math formulas remain readable even when MathJax cannot load. Long display formulas can be scrolled horizontally instead of overflowing the screen.

## Supported providers

The app currently includes presets for these OpenAI-compatible services:

| Provider | Base URL |
|---|---|
| numAi Oracle | `https://129-148-23-167.nip.io/v1` |
| VoidAI | `https://api.voidai.app/v1` |
| Ollama Cloud | `https://ollama.com/v1` |
| OpenCode Zen | `https://opencode.ai/zen/v1` |
| NavyAI | `https://api.navy/v1` |
| OpenRouter | `https://openrouter.ai/api/v1` |
| NVIDIA NIM | `https://integrate.api.nvidia.com/v1` |
| TokenRouter | `https://api.tokenrouter.com/v1` |
| Groq | `https://api.groq.com/openai/v1` |
| Together AI | `https://api.together.ai/v1` |
| Fireworks AI | `https://api.fireworks.ai/inference/v1` |
| DeepInfra | `https://api.deepinfra.com/v1/openai` |
| Hugging Face | `https://router.huggingface.co/v1` |
| Google AI Studio | `https://generativelanguage.googleapis.com/v1beta/openai` |
| Z.ai | `https://api.z.ai/api/paas/v4` |
| BigModel (Z.ai China) | `https://open.bigmodel.cn/api/paas/v4` |
| Kilo Gateway | `https://api.kilo.ai/api/gateway` |

You can also enter a custom base URL. The service must expose OpenAI-style `/models` and `/chat/completions` endpoints for full compatibility.

> [!NOTE]
> numAi filters known non-chat models from provider catalogs. Vision, tool calling, and reasoning support still vary by individual model.

## Install

1. Download [`numAi-1.0.0-debug.apk`](artifacts/numAi-1.0.0-debug.apk?raw=1).
2. Transfer it to the Android device.
3. Allow installation from unknown sources when Android asks.
4. Install or update numAi.

The development APK is signed with a debug certificate. Stable packages, when available, are published on the [Releases page](https://github.com/JOAO2666/numAi/releases).

## Quick start

1. Open **Settings**.
2. Select a provider or enter a custom API base URL.
3. Enter that provider's API key.
4. Load the model catalog.
5. Choose a chat model and, optionally, a reasoning model.
6. Return to the conversation and send a message.

Choose a vision-capable model before attaching images. If a provider rejects web tools or provider-specific reasoning controls, numAi automatically retries with a simpler compatible request.

### MCP tools

Settings includes a configurable MCP connection. Enter the full address using `https://example.com/mcp` as a guide, then authenticate with **OAuth** or an optional Bearer token/password. Check **Enable MCP tools in chats** to make the connection available. Also check **Run tools automatically** to let the model execute tools without asking for confirmation for each action. When the second option is unchecked, MCP tools are not sent to the model. Connect only to servers you trust.

## API-key safety

- Create a separate key for numAi whenever the provider allows it.
- Never commit keys to Git, paste them into bug reports, or publish screenshots containing them.
- Revoke and replace a key immediately if it is exposed.
- Keys are stored locally and separately for each provider.
- MCP OAuth tokens and Bearer tokens/passwords are stored locally in a separate preferences file and are never used as chat API keys.
- Prefer HTTPS for every custom endpoint.

## Legacy Android notes

- Minimum SDK: Android API 1
- Target and compile SDK: Android API 25
- Some HTTPS providers require [Wolfius](https://github.com/gohoski/Wolfius) on old Android versions because the original TLS stack lacks modern protocol and SNI support.
- Bing search is the most compatible search option on very old devices.
- Web fetch and some modern providers may require a newer Android release or Wolfius.

## Screenshots

<details>
  <summary>Show screenshots</summary>
  <br>
  <img src="img/scr1.png" alt="numAi conversation" width="200">
  <img src="img/scr2.png" alt="numAi settings" width="200">
  <img src="img/scr3.png" alt="numAi image input" width="200">
  <img src="img/scr4.png" alt="numAi reasoning" width="200">
  <img src="img/scr5.png" alt="numAi Markdown" width="200">
  <img src="img/scr6.png" alt="numAi web search" width="200">
  <img src="img/scr7.png" alt="numAi model selection" width="200">
  <img src="img/scr8.png" alt="numAi legacy Android" width="200">
</details>

## Build and test

Recommended environment:

- JDK 8
- Android SDK Platform 25
- Android Build Tools 25.0.0
- Android Studio 2.3.2 for legacy-device development

Windows:

```powershell
.\gradlew.bat test assembleDebug
```

Linux or macOS:

```bash
./gradlew test assembleDebug
```

The debug APK is written to `app/build/outputs/apk/numAi-2.0-debug.apk`. Release builds are minified and resource-shrunk, but the generated release APK is unsigned.

The automated tests cover provider mappings, MCP tool-name mapping and result normalization, chat-model filtering, provider-specific fallbacks, generation cancellation, model selection, Markdown, MathJax block handling, and alternative streaming response formats.

## Contributing and bug reports

Open an [issue](https://github.com/JOAO2666/numAi/issues) and include:

- Android version and device model
- numAi version or commit
- Selected provider and model, without the API key
- Steps to reproduce the problem
- Screenshot or sanitized error message

Pull requests should preserve API 1 compatibility and avoid heavy dependencies unless the benefit clearly justifies the APK-size and performance cost.

## Community

- Telegram updates: [@AppDataApps](https://t.me/AppDataApps)
- Telegram group: [Retro Android Group](https://t.me/retroandroidgroup)
- Discord: [Android Afterlife](https://discord.gg/2JqfEkQyck)
- 4PDA: [numAi topic](https://4pda.to/forum/index.php?showtopic=1116157)

## Acknowledgments

- [How-to-develop-and-backport-for-Android-2.1-in-2020](https://github.com/Mik-el/How-to-develop-and-backport-for-Android-2.1-in-2020), project template by Michele
- [NNJSON](https://github.com/shinovon/NNJSON), by nnproject
- [ReOldAI](https://github.com/YMP-CO/ReOldAi), by YMP Yuri, for motivation around AI clients on older Android devices

## License

numAi is licensed under the Do What The Fuck You Want To Public License, Version 2. See [LICENSE](LICENSE).

The bundled NNJSON library is distributed under the MIT License. See [LICENSE-NNJSON](LICENSE-NNJSON).

The Android robot is reproduced or modified from work created and shared by Google and used according to the [Creative Commons 3.0 Attribution License](https://creativecommons.org/licenses/by/3.0/).
