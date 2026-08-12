# numAi
[English](README.md) / [русский](README.ru.md) / **简体中文** 

一款兼容 **Android 1.0+** 的 AI 应用，支持深度思考、图像视觉和网页搜索。只需一个简单应用，即可在您的老旧设备上访问 ChatGPT、DeepSeek、Gemini、Qwen、GLM、Kimi 及其他大语言模型 (LLM)。
* **Telegram 更新频道**：[@AppDataApps](https://t.me/AppDataApps)
* 加入我们的 Telegram **[Retro Android Group](https://t.me/retroandroidgroup)** 群组！
* Discord 服务器：[Android Afterlife](https://discord.gg/2JqfEkQyck)

![numAi](img/logo.png "老旧 Android 设备的 AI 客户端")

<img src="img/scr1.png" alt="截图" width="200"/> <img src="img/scr2.png" alt="截图" width="200"/> <img src="img/scr3.png" alt="截图" width="200"/> <img src="img/scr4.png" alt="截图" width="200"/> <img src="img/scr5.png" alt="截图" width="200"/> <img src="img/scr6.png" alt="截图" width="200"/> <img src="img/scr7.png" alt="截图" width="200"/> <img src="img/scr8.png" alt="截图" width="200"/>

## 📥 下载
* [GitHub Releases](https://github.com/gohoski/numAi/releases)
* [4PDA](https://4pda.to/forum/index.php?showtopic=1116157)
* Telegram（链接位于 README 顶部）

## 功能特性
* 支持各种兼容 OpenAI 格式的 API 和模型（即大多数 LLM API）
* 思考模式（在聊天模型和思考模型之间切换）
* 视觉（图像附件）
* 支持更改系统提示词 (System Prompt)
* 从文件导入 API 密钥
* 支持 Markdown 格式（包括表格）
* 通过 Bing（Android 1.0+，推荐）和 DuckDuckGo（Android 1.6+，需要 Wolfius，可能会受到限制）进行网页搜索
* 网页抓取（Android 1.6+，需要 [Wolfius](https://github.com/gohoski/Wolfius)）
### 待办事项 (TODO)
* 文件附件

## 推荐模型
> [!WARNING]  
> 并非所有模型都支持视觉功能。请提前检查该模型是否原生支持图像附件。
### VoidAI
* 聊天模型：`deepseek-v3.2`（如需视觉功能，可使用 `gemini-3.5-flash-lite`/`kimi-k3`）
* 思考模型：`deepseek-v4-flash`（如需视觉功能，可使用 `gemini-3.6-flash`/`kimi-k3`）
### Ollama Cloud
* `gemma4:31b` — 支持聊天、推理和视觉功能
### OpenCode Zen
* `deepseek-v4-flash-free` — 仅支持聊天和推理功能。如需视觉功能，请使用其他 API。

## 错误报告
**请在 [Issues](https://github.com/gohoski/numAi/issues) 标签页中报告错误！** 请务必说明您遇到该错误时所使用的 Android 版本。

## API 密钥设置指南
以下所有 API 均提供免费额度——无需付费。
### VoidAI (Android 1.6+)
> [!WARNING]  
> 在 Android 3.0 以下的版本中，此 API 需要 [Wolfius](https://github.com/gohoski/Wolfius) 才能正常工作。

1. 在现代浏览器中，访问 [voidai.app/register](https://voidai.app/register) 并创建账户。
2. 登录后，在控制面板中导航至 **API Keys**（API 密钥）部分。
3. 点击 **Generate New API Key**（生成新 API 密钥）。
4. 复制生成的密钥并将其传输到您的设备上。

### Ollama Cloud
> [!TIP]  
> 建议在 Android 1.0+ 上使用此 API，因为它仍然支持不带 SNI 的 TLS 1.0。

1. 在现代浏览器中，访问 [ollama.com](https://ollama.com/) 并创建账户。
2. 登录后，前往 [ollama.com/settings/keys](https://ollama.com/settings/keys)。
3. 点击 **Add API Key**（添加 API 密钥），然后点击 **Generate API Key**（生成 API 密钥）。
4. 复制密钥并将其传输到您的设备。在下拉菜单中选择 Ollama Cloud，而不是 VoidAI。

### OpenCode Zen (Android 1.6+)
> [!WARNING]  
> 在 Android 4.4 以下的版本中，此 API 需要 [Wolfius](https://github.com/gohoski/Wolfius) 才能正常工作。

1. 在现代浏览器中，访问 [opencode.ai/auth](https://opencode.ai/auth) 并创建账户。
2. 登录后，在控制面板中导航至 **API Keys**（API 密钥）部分。
3. 点击 **Create API Key**（创建 API 密钥）并输入任意密钥名称。
4. 复制生成的密钥并将其传输到您的设备上。

## 构建
本项目在以下构建环境中开发。
* Android Studio 2.3.2 [`下载`](https://developer.android.com/studio/archive)
  * Android Studio 1.0–3.1.2 可能支持 Android 2.2 以下版本，但推荐使用 2.3.2 进行开发，因为它既老旧又仍受支持。
  * 最新版本的 AS 仍然支持 Android 2.2 及更高版本（尽管它们主要针对 4.1+ 设计）——如果您不优先考虑老旧 Android 版本，也可以使用它们。
* 任意版本的 Android SDK *（推荐 25）*
  * 开发老旧应用并不强制要求使用旧版 SDK。
* 来自 SDK 的 Android 1.0 模拟器 [`下载`](https://developer.android.com/sdk/older_releases#release-1.0-r1)

建议在贡献代码时使用 AS（Android Studio）；不过，您也可以使用其他 IDE，只要确保项目在 AS 中仍然可用即可。

## 致谢
* Michele 的 [How-to-develop-and-backport-for-Android-2.1-in-2020](https://github.com/Mik-el/How-to-develop-and-backport-for-Android-2.1-in-2020) 项目模板
* nnproject 的 [NNJSON](https://github.com/shinovon/NNJSON) 库
* YMP Yuri 的 [ReOldAI](https://github.com/YMP-CO/ReOldAi) —— 尽管未被用作灵感来源或代码库，但这款利用 Gemini API 的类似应用为本项目提供了动力。
## 许可证
**numAi** 项目基于 WTFPL (Do What The Fuck You Want To Public License) 第 2 版许可证开源。详情请参阅 [LICENSE](LICENSE)。*如果您愿意，可以在您项目的 README 中注明我的贡献。*  

但是，NNJSON 库基于 MIT 许可证。详情请参阅 [LICENSE-NNJSON](LICENSE-NNJSON)。

Android 机器人图像复制或修改自 Google 创作和分享的作品，并根据 [知识共享署名 3.0 许可协议 (Creative Commons 3.0 Attribution License)](https://creativecommons.org/licenses/by/3.0/) 的条款使用。