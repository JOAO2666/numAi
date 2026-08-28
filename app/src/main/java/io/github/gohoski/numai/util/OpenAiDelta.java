package io.github.gohoski.numai.util;

import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

/** Normalizes text fields used by OpenAI-compatible streaming providers. */
public final class OpenAiDelta {
    private OpenAiDelta() {}

    public static String text(JSONObject delta, String name) {
        if (delta == null || name == null) return null;
        return textValue(delta.getNullable(name));
    }

    private static String textValue(Object value) {
        if (value == null) return null;
        if (value instanceof String) return (String) value;
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < array.size(); i++) {
                String part = textValue(array.get(i, null));
                if (part != null) result.append(part);
            }
            return result.length() == 0 ? null : result.toString();
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            String result = textValue(object.getNullable("text"));
            if (result == null) result = textValue(object.getNullable("thinking"));
            if (result == null) result = textValue(object.getNullable("content"));
            return result;
        }
        return null;
    }
}
