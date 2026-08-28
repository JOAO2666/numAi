package io.github.gohoski.numai.model;

/**
 * A model returned by a provider's catalog. Pricing is kept as text because
 * OpenRouter sometimes returns decimal strings and sometimes numbers.
 */
public class ModelInfo {
    private final String id;
    private final String promptPrice;
    private final String completionPrice;
    private final String inputPrice;
    private final String outputPrice;

    public ModelInfo(String id) {
        this(id, null, null, null, null);
    }

    public ModelInfo(String id, String promptPrice, String completionPrice,
            String inputPrice, String outputPrice) {
        this.id = id == null ? "" : id;
        this.promptPrice = promptPrice;
        this.completionPrice = completionPrice;
        this.inputPrice = inputPrice;
        this.outputPrice = outputPrice;
    }

    public String getId() { return id; }
    public String getPromptPrice() { return promptPrice; }
    public String getCompletionPrice() { return completionPrice; }
    public String getInputPrice() { return inputPrice; }
    public String getOutputPrice() { return outputPrice; }

    public boolean isFree() {
        return io.github.gohoski.numai.util.ModelCatalog.isFree(
                id, promptPrice, completionPrice, inputPrice, outputPrice);
    }

    public String toString() {
        return id;
    }
}
