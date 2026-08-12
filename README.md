# numAi
**English** / [русский](README.ru.md) / [简体中文](README.zh.md)

An AI app compatible with **Android 1.0+** with support for deep thinking, image vision, and web search. Access ChatGPT, DeepSeek, Gemini, Qwen, GLM, Kimi, and other LLMs in one simple app on your legacy device.
* **Telegram channel with updates**: [@AppDataApps](https://t.me/AppDataApps)
* Join our **[Retro Android Group](https://t.me/retroandroidgroup)** on Telegram!
* Discord server: [Android Afterlife](https://discord.gg/2JqfEkQyck)

![numAi](img/logo.png "AI client for legacy Android devices")

<img src="img/scr1.png" alt="Screenshot" width="200"/> <img src="img/scr2.png" alt="Screenshot" width="200"/> <img src="img/scr3.png" alt="Screenshot" width="200"/> <img src="img/scr4.png" alt="Screenshot" width="200"/> <img src="img/scr5.png" alt="Screenshot" width="200"/> <img src="img/scr6.png" alt="Screenshot" width="200"/> <img src="img/scr7.png" alt="Screenshot" width="200"/> <img src="img/scr8.png" alt="Screenshot" width="200"/>

## 📥 Download
* [GitHub Releases](https://github.com/gohoski/numAi/releases)
* [4PDA](https://4pda.to/forum/index.php?showtopic=1116157)
* Telegram (link at the top of the README)

## Features
* Support of various APIs and models that support the OpenAI format (i.e. most LLM APIs)
* Thinking mode (switch between chat and thinking model)
* Vision (image attachments)
* Ability to change the system prompt
* Importing API key from file
* Markdown formatting support (including tables)
* Web search through Bing (Android 1.0+, recommended) and DuckDuckGo (Android 1.6+, requires Wolfius, may get limited)
* Web fetch (Android 1.6+, requires [Wolfius](https://github.com/gohoski/Wolfius))
### TODO
* File attachments

## Recommended models
> [!WARNING]  
> Not all models support vision. Please check if the model supports image attachments natively beforehand.
### VoidAI
* Chat model: `deepseek-v3.2` (or `gemini-3.5-flash-lite`/`kimi-k3` for vision)
* Thinking model: `deepseek-v4-flash` (or `gemini-3.6-flash`/`kimi-k3` for vision)
### Ollama Cloud
* `gemma4:31b` — supports chat, thinking and vision
### OpenCode Zen
* `deepseek-v4-flash-free` — supports chat and thinking only. For vision, please use another API.

## Reporting bugs
**Report bugs in the [Issues](https://github.com/gohoski/numAi/issues) tab!** Don't forget to specify which version of Android you encountered the bug on.

## API key setup guide
All of the following APIs have free quotas—no payment is required.
### VoidAI (Android 1.6+)
> [!WARNING]  
> This API requires [Wolfius](https://github.com/gohoski/Wolfius) to work correctly on Android <3.0.

1. On a modern browser, go to [voidai.app/register](https://voidai.app/register) and create an account.
2. After logging in, navigate to the **API Keys** section in your dashboard.
3. Click **Generate New API Key**.
4. Copy the key that appears and transfer it to your device.

### Ollama Cloud
> [!TIP]  
> This API is recommended to use on Android 1.0+, as it still supports TLS 1.0 w/o SNI.

1. On a modern browser, go to [ollama.com](https://ollama.com/) and create an account.
2. After logging in, go to [ollama.com/settings/keys](https://ollama.com/settings/keys).
3. Click **Add API Key**, then **Generate API Key**.
4. Copy the key and transfer it to your device. Instead of VoidAI, choose Ollama Cloud in the dropdown menu.

### OpenCode Zen (Android 1.6+)
> [!WARNING]  
> This API requires [Wolfius](https://github.com/gohoski/Wolfius) to work correctly on Android <4.4.

1. On a modern browser, go to [opencode.ai/auth](https://opencode.ai/auth) and create an account.
2. After logging in, navigate to the **API Keys** section in your dashboard.
3. Click **Create API Key** and enter any key name.
4. Copy the key that appears and transfer it to your device.

## Build
The project is developed under the following build environment.
* Android Studio 2.3.2 [`Download`](https://developer.android.com/studio/archive)
  * Android Studio 1.0–3.1.2 may support Android <2.2, but 2.3.2 is recommended for development as it's simultaneously old and supported.
  * Latest AS versions still support Android 2.2 and later (though they are made with 4.1+ in mind)—you can use them if you don't prioritize old Android versions.
* Android SDK of any version *(25 recommended)*
  * It is not required to use an old SDK for developing legacy apps.
* Android 1.0 emulator from the SDK [`Download`](https://developer.android.com/sdk/older_releases#release-1.0-r1)

It is recommended to use AS while contributing; however, you may use another IDE as long as you make the project still usable in AS.

## Acknowledgments
* [How-to-develop-and-backport-for-Android-2.1-in-2020](https://github.com/Mik-el/How-to-develop-and-backport-for-Android-2.1-in-2020) project template by Michele
* [NNJSON](https://github.com/shinovon/NNJSON) library by nnproject
* [ReOldAI by YMP Yuri](https://github.com/YMP-CO/ReOldAi) — Although not used as inspiration or a codebase, this similar app, which utilizes the Gemini API, provided motivation for the project
## License
The **numAi** project is licensed under the Do What The Fuck You Want To Public License, Version 2. See [LICENSE](LICENSE) for details. *If you want, you may credit me in the README of your project.*  

HOWEVER, the NNJSON library is licensed under the MIT license. See [LICENSE-NNJSON](LICENSE-NNJSON) for details.

The Android robot is reproduced or modified from work created and shared by Google and used according to terms described in the [Creative Commons 3.0 Attribution License](https://creativecommons.org/licenses/by/3.0/).