package io.github.gohoski.numai;

import java.util.ArrayList;
import java.util.List;

import io.github.gohoski.numai.model.ModelInfo;
import io.github.gohoski.numai.ui.LatexFormatter;
import io.github.gohoski.numai.util.ModelCatalog;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModelCatalogTest {
    private List<ModelInfo> models() {
        ArrayList<ModelInfo> models = new ArrayList<ModelInfo>();
        models.add(new ModelInfo("google/gemini-paid", "0.001", "0.002", null, null));
        models.add(new ModelInfo("qwen/qwen-free:free"));
        models.add(new ModelInfo("deepseek/deepseek-zero", "0", "0", "0", "0"));
        return models;
    }

    @Test public void searchesGeminiCaseInsensitively() {
        assertEquals(1, ModelCatalog.filter(models(), "GeMiNi", false).size());
    }

    @Test public void searchesQwen() {
        assertEquals("qwen/qwen-free:free",
                ModelCatalog.filter(models(), "qwen", false).get(0).getId());
    }

    @Test public void freeOnlyFiltersAndCanBeDisabled() {
        assertEquals(2, ModelCatalog.filter(models(), "", true).size());
        assertEquals(3, ModelCatalog.filter(models(), "", false).size());
    }

    @Test public void explicitFreeSuffixIsFree() {
        assertTrue(ModelCatalog.isFree("anything:FREE", null, null, null, null));
    }

    @Test public void zeroPricingIsFree() {
        assertTrue(ModelCatalog.isFree("provider/model", "0", "0", "0", "0"));
    }

    @Test public void paidPricingIsNotFree() {
        assertFalse(ModelCatalog.isFree("provider/model", "0", "0.001", null, null));
    }

    @Test public void latexBecomesReadableWithoutRawCommands() {
        String formatted = LatexFormatter.format(
                "H - 9,10 = \\frac{37,21}{19,6} \\approx 1,90\\text{ m}");
        assertEquals("H - 9,10 = (37,21) / (19,6) ≈ 1,90 m", formatted);
    }

    @Test public void latexVectorAndIndicesBecomeReadable() {
        String formatted = LatexFormatter.format(
                "\\vec{v} = (7,60\\hat{i} + 6,10\\hat{j})\\text{ m/s}, \\theta_0 = 45^\\circ");
        assertEquals("v⃗ = (7,60î + 6,10ĵ) m/s, θ₀ = 45°", formatted);
    }
}
