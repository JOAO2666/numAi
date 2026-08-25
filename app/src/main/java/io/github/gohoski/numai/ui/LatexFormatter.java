package io.github.gohoski.numai.ui;

import java.util.HashMap;
import java.util.Map;

/** Converts the common LaTeX emitted by chat models into readable Unicode text. */
public final class LatexFormatter {
    private static final Map<String, String> SYMBOLS = new HashMap<String, String>();

    static {
        SYMBOLS.put("alpha", "α"); SYMBOLS.put("beta", "β");
        SYMBOLS.put("gamma", "γ"); SYMBOLS.put("delta", "δ");
        SYMBOLS.put("epsilon", "ε"); SYMBOLS.put("varepsilon", "ϵ");
        SYMBOLS.put("zeta", "ζ"); SYMBOLS.put("eta", "η");
        SYMBOLS.put("theta", "θ"); SYMBOLS.put("vartheta", "ϑ");
        SYMBOLS.put("iota", "ι"); SYMBOLS.put("kappa", "κ");
        SYMBOLS.put("lambda", "λ"); SYMBOLS.put("mu", "μ");
        SYMBOLS.put("nu", "ν"); SYMBOLS.put("xi", "ξ");
        SYMBOLS.put("pi", "π"); SYMBOLS.put("varpi", "ϖ");
        SYMBOLS.put("rho", "ρ"); SYMBOLS.put("sigma", "σ");
        SYMBOLS.put("tau", "τ"); SYMBOLS.put("upsilon", "υ");
        SYMBOLS.put("phi", "φ"); SYMBOLS.put("varphi", "ϕ");
        SYMBOLS.put("chi", "χ"); SYMBOLS.put("psi", "ψ");
        SYMBOLS.put("omega", "ω"); SYMBOLS.put("Gamma", "Γ");
        SYMBOLS.put("Delta", "Δ"); SYMBOLS.put("Theta", "Θ");
        SYMBOLS.put("Lambda", "Λ"); SYMBOLS.put("Xi", "Ξ");
        SYMBOLS.put("Pi", "Π"); SYMBOLS.put("Sigma", "Σ");
        SYMBOLS.put("Upsilon", "Υ"); SYMBOLS.put("Phi", "Φ");
        SYMBOLS.put("Psi", "Ψ"); SYMBOLS.put("Omega", "Ω");
        SYMBOLS.put("times", "×"); SYMBOLS.put("cdot", "·");
        SYMBOLS.put("circ", "°"); SYMBOLS.put("pm", "±");
        SYMBOLS.put("mp", "∓"); SYMBOLS.put("approx", "≈");
        SYMBOLS.put("sim", "∼"); SYMBOLS.put("neq", "≠");
        SYMBOLS.put("ne", "≠"); SYMBOLS.put("le", "≤");
        SYMBOLS.put("leq", "≤"); SYMBOLS.put("ge", "≥");
        SYMBOLS.put("geq", "≥"); SYMBOLS.put("infty", "∞");
        SYMBOLS.put("implies", "⇒"); SYMBOLS.put("to", "→");
        SYMBOLS.put("rightarrow", "→"); SYMBOLS.put("leftarrow", "←");
        SYMBOLS.put("leftrightarrow", "↔"); SYMBOLS.put("degree", "°");
        SYMBOLS.put("ell", "ℓ"); SYMBOLS.put("partial", "∂");
        SYMBOLS.put("nabla", "∇"); SYMBOLS.put("propto", "∝");
        SYMBOLS.put("sum", "Σ"); SYMBOLS.put("prod", "Π");
    }

    private LatexFormatter() {}

    public static String format(String source) {
        if (source == null || source.length() == 0) return source;
        if (!looksLikeLatex(source)) return source;
        return formatFragment(stripDelimiters(source));
    }

    private static boolean looksLikeLatex(String source) {
        if (source.indexOf('\\') >= 0 || source.indexOf("_{") >= 0 ||
                source.indexOf("^{") >= 0) return true;
        int dollarCount = 0;
        for (int index = 0; index < source.length(); index++) {
            if (source.charAt(index) == '$' &&
                    (index == 0 || source.charAt(index - 1) != '\\')) dollarCount++;
        }
        return dollarCount >= 2;
    }

    private static String formatFragment(String source) {
        StringBuilder result = new StringBuilder();
        int index = 0;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (current == '$') {
                result.append(current);
                index++;
                continue;
            }

            if (current == '\\') {
                if (index + 1 >= source.length()) {
                    index++;
                    continue;
                }
                char next = source.charAt(index + 1);
                if (!Character.isLetter(next)) {
                    if (next == '\\' || next == ' ' || next == ',' || next == ';' ||
                            next == ':' || next == '!') {
                        if (next == ' ' || next == ',' || next == ';' || next == ':') {
                            result.append(' ');
                        }
                        index += 2;
                        continue;
                    }
                    result.append(next);
                    index += 2;
                    continue;
                }

                int commandEnd = index + 1;
                while (commandEnd < source.length() &&
                        Character.isLetter(source.charAt(commandEnd))) commandEnd++;
                String command = source.substring(index + 1, commandEnd);

                if ("frac".equals(command)) {
                    Group numerator = readGroup(source, commandEnd);
                    Group denominator = readGroup(source, numerator.end);
                    if (numerator.valid && denominator.valid) {
                        result.append("(").append(formatFragment(numerator.value)).append(") / (")
                                .append(formatFragment(denominator.value)).append(")");
                        index = denominator.end;
                        continue;
                    }
                }

                if ("sqrt".equals(command)) {
                    int nextIndex = commandEnd;
                    String rootIndex = "";
                    if (nextIndex < source.length() && source.charAt(nextIndex) == '[') {
                        int close = source.indexOf(']', nextIndex + 1);
                        if (close != -1) {
                            rootIndex = formatFragment(source.substring(nextIndex + 1, close));
                            nextIndex = close + 1;
                        }
                    }
                    Group radicand = readGroup(source, nextIndex);
                    if (radicand.valid) {
                        result.append(rootIndex.length() == 0 ? "√" : rootIndex + "√")
                                .append("(").append(formatFragment(radicand.value)).append(")");
                        index = radicand.end;
                        continue;
                    }
                }

                if ("text".equals(command) || "mathrm".equals(command) ||
                        "mathbf".equals(command) || "mathit".equals(command) ||
                        "textbf".equals(command) || "operatorname".equals(command) ||
                        "boldsymbol".equals(command)) {
                    Group group = readGroup(source, commandEnd);
                    if (group.valid) {
                        result.append(formatFragment(group.value));
                        index = group.end;
                        continue;
                    }
                }

                if ("vec".equals(command) || "hat".equals(command) ||
                        "bar".equals(command) || "overline".equals(command) ||
                        "dot".equals(command)) {
                    Group group = readGroup(source, commandEnd);
                    if (group.valid) {
                        result.append(formatFragment(group.value));
                        if ("vec".equals(command)) result.append("⃗");
                        else if ("hat".equals(command)) result.append("̂");
                        else if ("bar".equals(command) || "overline".equals(command)) result.append("̄");
                        else result.append("̇");
                        index = group.end;
                        continue;
                    }
                }

                String symbol = SYMBOLS.get(command);
                if (symbol != null) {
                    result.append(symbol);
                    index = commandEnd;
                    continue;
                }

                if ("left".equals(command) || "right".equals(command) ||
                        "big".equals(command) || "Big".equals(command) ||
                        "bigg".equals(command) || "Bigg".equals(command) ||
                        "displaystyle".equals(command) || "limits".equals(command) ||
                        "quad".equals(command) || "qquad".equals(command)) {
                    if ("quad".equals(command) || "qquad".equals(command)) result.append(' ');
                    index = commandEnd;
                    continue;
                }

                result.append(command);
                index = commandEnd;
                continue;
            }

            if (current == '^' || current == '_') {
                boolean superscript = current == '^';
                Group group = readScript(source, index + 1);
                if (group.valid) {
                    String value = formatFragment(group.value);
                    String compact = superscript ? toSuperscript(value) : toSubscript(value);
                    if (superscript && "°".equals(value)) result.append(value);
                    else result.append(compact.length() > 0 ? compact :
                            (superscript ? "^(" : "_(") + value + ")");
                    index = group.end;
                    continue;
                }
            }

            if (current == '{') {
                Group group = readGroup(source, index);
                if (group.valid) {
                    result.append(formatFragment(group.value));
                    index = group.end;
                    continue;
                }
            }
            if (current == '}') {
                index++;
                continue;
            }

            result.append(current);
            index++;
        }
        return result.toString();
    }

    private static String stripDelimiters(String source) {
        String result = source.replace("\\(", "").replace("\\)", "")
                .replace("\\[", "").replace("\\]", "")
                .replace("$$", "");
        int dollarCount = 0;
        for (int index = 0; index < result.length(); index++) {
            if (result.charAt(index) == '$' &&
                    (index == 0 || result.charAt(index - 1) != '\\')) dollarCount++;
        }
        return dollarCount >= 2 ? result.replace("$", "") : result;
    }

    private static Group readScript(String source, int start) {
        if (start < source.length() && source.charAt(start) == '{') return readGroup(source, start);
        if (start >= source.length()) return Group.invalid(start);
        if (source.charAt(start) == '\\' && start + 1 < source.length() &&
                Character.isLetter(source.charAt(start + 1))) {
            int end = start + 2;
            while (end < source.length() && Character.isLetter(source.charAt(end))) end++;
            return new Group(source.substring(start, end), end, true);
        }
        return new Group(source.substring(start, start + 1), start + 1, true);
    }

    private static Group readGroup(String source, int start) {
        while (start < source.length() && Character.isWhitespace(source.charAt(start))) start++;
        if (start >= source.length() || source.charAt(start) != '{') return Group.invalid(start);
        int depth = 0;
        for (int index = start; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') depth++;
            else if (current == '}') {
                depth--;
                if (depth == 0) return new Group(source.substring(start + 1, index), index + 1, true);
            }
        }
        return Group.invalid(start);
    }

    private static String toSuperscript(String value) { return convertScript(value, true); }
    private static String toSubscript(String value) { return convertScript(value, false); }

    private static String convertScript(String value, boolean superscript) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            String converted = superscript ? superscript(value.charAt(index)) : subscript(value.charAt(index));
            if (converted == null) return "";
            result.append(converted);
        }
        return result.toString();
    }

    private static String superscript(char value) {
        switch (value) {
            case '0': return "⁰"; case '1': return "¹"; case '2': return "²";
            case '3': return "³"; case '4': return "⁴"; case '5': return "⁵";
            case '6': return "⁶"; case '7': return "⁷"; case '8': return "⁸";
            case '9': return "⁹"; case '+': return "⁺"; case '-': return "⁻";
            case '=': return "⁼"; case '(': return "⁽"; case ')': return "⁾";
            case 'n': return "ⁿ"; case 'i': return "ⁱ";
            default: return null;
        }
    }

    private static String subscript(char value) {
        switch (value) {
            case '0': return "₀"; case '1': return "₁"; case '2': return "₂";
            case '3': return "₃"; case '4': return "₄"; case '5': return "₅";
            case '6': return "₆"; case '7': return "₇"; case '8': return "₈";
            case '9': return "₉"; case '+': return "₊"; case '-': return "₋";
            case '=': return "₌"; case '(': return "₍"; case ')': return "₎";
            case 'a': return "ₐ"; case 'e': return "ₑ"; case 'h': return "ₕ";
            case 'i': return "ᵢ"; case 'j': return "ⱼ"; case 'k': return "ₖ";
            case 'l': return "ₗ"; case 'm': return "ₘ"; case 'n': return "ₙ";
            case 'o': return "ₒ"; case 'p': return "ₚ"; case 'r': return "ᵣ";
            case 's': return "ₛ"; case 't': return "ₜ"; case 'u': return "ᵤ";
            case 'v': return "ᵥ"; case 'x': return "ₓ";
            default: return null;
        }
    }

    private static class Group {
        final String value;
        final int end;
        final boolean valid;

        Group(String value, int end, boolean valid) {
            this.value = value;
            this.end = end;
            this.valid = valid;
        }

        static Group invalid(int position) { return new Group("", position, false); }
    }
}
