package io.github.gohoski.numai.ui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MathMarkdownViewTest {
    @Test public void joinsDollarDelimitedDisplayMathAcrossLines() {
        String html = MathMarkdownView.buildDocument(
                "Before\n\n$$\nh=\\frac{1}{2}gt^2\n\\Rightarrow t=\\sqrt{2h/g}\n$$\n\nAfter");

        assertTrue(html.contains("\\[h=\\frac{1}{2}gt^2\n" +
                "\\Rightarrow t=\\sqrt{2h/g}\\]"));
        assertFalse(html.contains(">$$<"));
    }

    @Test public void joinsBracketDelimitedDisplayMathAcrossLines() {
        String html = MathMarkdownView.buildDocument(
                "\\[\nE=mc^2\n\\]");

        assertTrue(html.contains("\\[E=mc^2\\]"));
        assertFalse(html.contains(">\\[<"));
    }

    @Test public void leavesMathDelimitersInsideCodeBlocksUntouched() {
        String html = MathMarkdownView.buildDocument(
                "```\n$$\nx^2\n$$\n```");

        assertTrue(html.contains("<pre class=\"block-code\"><code>"));
        assertTrue(html.contains("$$\nx^2\n$$"));
    }
}
