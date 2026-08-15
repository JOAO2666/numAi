package io.github.gohoski.numai.util;

import java.util.Arrays;
import java.util.List;

public class ModelSelector {
    private static final List<String> CHAT_MODELS = Arrays.asList(
            "deepseek-v3.2", "gemma4:", "deepseek-v4-flash-free", "deepseek-v4-flash-0731", "deepseek-v4-flash", "gemini-3.5-flash-lite", "gemini-3.6-flash", "gemini-3.5-flash", "qwen3.8", "qwen3.7", "qwen3.6", "qwen3.5", "gpt-5.6-luna", "gpt-5.6"
    );
    private static final List<String> THINKING_MODELS = Arrays.asList(
            // OpenCode Zen
            "deepseek-v4-flash-free", "big-pickle",
            // Ollama Cloud
            "minimax-m3", "gemma4:",
            // Everything else (VoidAI, etc.)
            "deepseek-v4-flash-0731", "deepseek-v4-flash", "qwen3.8", "qwen3.7", "qwen3.6", "qwen3.5", "gemini-3.6-flash", "gemini-3.5-flash", "gpt-5.6-luna", "gpt-5.6", "ling-3.0-flash", "step-3.7-flash"
    );

    public static String selectChatModel(List<String> availableModels) {
        return selectPreferredModel(availableModels, CHAT_MODELS);
    }

    public static String selectThinkingModel(List<String> availableModels) {
        return selectPreferredModel(availableModels, THINKING_MODELS);
    }

    private static String selectPreferredModel(List<String> availableModels, List<String> prefModels) {
        System.out.println(availableModels);

        if (availableModels == null || availableModels.size() == 0) {
            return null;
        }
        if (prefModels == null || prefModels.size() == 0) {
            return availableModels.get(0);
        }

        for (String prefModel : prefModels) {
            for (String availableModel : availableModels) {
                if (availableModel != null && prefModel != null &&
                        availableModel.toLowerCase().contains(prefModel)) {
                    return availableModel;
                }
            }
        }

        return availableModels.get(0);
    }
}
