package io.github.gohoski.numai.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import io.github.gohoski.numai.model.ModelInfo;

/** Pure-Java catalog operations shared by the UI, persistence and tests. */
public final class ModelCatalog {
    private ModelCatalog() {}

    public static boolean isFree(String id, String prompt, String completion,
            String input, String output) {
        if (id != null && id.toLowerCase(Locale.US).endsWith(":free")) {
            return true;
        }
        String[] prices = new String[]{prompt, completion, input, output};
        boolean foundPrice = false;
        for (int i = 0; i < prices.length; i++) {
            if (prices[i] == null || prices[i].trim().length() == 0) continue;
            foundPrice = true;
            if (!isZero(prices[i])) return false;
        }
        return foundPrice;
    }

    private static boolean isZero(String value) {
        try {
            return Double.parseDouble(value.trim()) == 0.0d;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static List<ModelInfo> filter(List<ModelInfo> source,
            String query, boolean freeOnly) {
        ArrayList<ModelInfo> result = new ArrayList<ModelInfo>();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.US);
        if (source == null) return result;
        for (int i = 0; i < source.size(); i++) {
            ModelInfo model = source.get(i);
            if (model == null) continue;
            if (freeOnly && !model.isFree()) continue;
            if (needle.length() > 0 &&
                    !model.getId().toLowerCase(Locale.US).contains(needle)) continue;
            result.add(model);
        }
        Collections.sort(result, new Comparator<ModelInfo>() {
            public int compare(ModelInfo first, ModelInfo second) {
                if (first.isFree() != second.isFree()) {
                    return first.isFree() ? -1 : 1;
                }
                return first.getId().compareToIgnoreCase(second.getId());
            }
        });
        return result;
    }

    public static String validSelection(String selected, List<ModelInfo> models,
            boolean thinking) {
        if (selected != null && selected.length() > 0 && models != null) {
            for (int i = 0; i < models.size(); i++) {
                if (selected.equals(models.get(i).getId())) return selected;
            }
        }
        if (models == null || models.isEmpty()) return "";
        String chosen = ModelSelector.selectThinkingModel(ids(models));
        if (!thinking) chosen = ModelSelector.selectChatModel(ids(models));
        return chosen == null ? models.get(0).getId() : chosen;
    }

    private static List<String> ids(List<ModelInfo> models) {
        ArrayList<String> ids = new ArrayList<String>();
        for (int i = 0; i < models.size(); i++) ids.add(models.get(i).getId());
        return ids;
    }
}
