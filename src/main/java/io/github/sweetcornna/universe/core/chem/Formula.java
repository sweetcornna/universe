package io.github.sweetcornna.universe.core.chem;

import java.util.Map;
import java.util.TreeMap;

/**
 * 数化学式里的原子（ADR-0037 §二），原子守恒的检查用。认得的写法：元素符号（大写字母加小写字母），下标数字（Unicode 或 ASCII），
 * 括号加下标，结晶水（{@code CH₄·5.75H₂O}），聚合物的下标 ₙ（按一节算）。
 */
public final class Formula {
    private Formula() {
    }

    /** 每种元素几个原子，按元素符号排序。 */
    public static Map<String, Double> atoms(String formula) {
        Map<String, Double> atoms = new TreeMap<>();
        for (String part : formula.split("·")) {
            int start = 0;
            int end = 0;
            while (end < part.length() && (Character.isDigit(part.charAt(end)) || part.charAt(end) == '.')) {
                end++;
            }
            double multiplier = end > start ? Double.parseDouble(part.substring(start, end)) : 1;
            Parser parser = new Parser(part, end);
            Map<String, Double> group = parser.group();
            if (parser.pos != part.length()) {
                throw new IllegalArgumentException("Bad formula: " + formula);
            }
            group.forEach((element, n) -> atoms.merge(element, n * multiplier, Double::sum));
        }
        return atoms;
    }

    /** 一组物质（化学式和个数）的原子数加起来。 */
    public static void add(Map<String, Double> into, String formula, double count) {
        atoms(formula).forEach((element, n) -> into.merge(element, n * count, Double::sum));
    }

    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text, int pos) {
            this.text = text;
            this.pos = pos;
        }

        Map<String, Double> group() {
            Map<String, Double> atoms = new TreeMap<>();
            while (this.pos < this.text.length()) {
                char c = this.text.charAt(this.pos);
                if (c == '(') {
                    this.pos++;
                    Map<String, Double> inner = this.group();
                    if (this.pos >= this.text.length() || this.text.charAt(this.pos) != ')') {
                        throw new IllegalArgumentException("Unclosed bracket: " + this.text);
                    }
                    this.pos++;
                    double n = this.count();
                    inner.forEach((element, k) -> atoms.merge(element, k * n, Double::sum));
                } else if (c == ')') {
                    return atoms;
                } else if (Character.isUpperCase(c)) {
                    int start = this.pos++;
                    while (this.pos < this.text.length() && Character.isLowerCase(this.text.charAt(this.pos))) {
                        this.pos++;
                    }
                    String element = this.text.substring(start, this.pos);
                    atoms.merge(element, this.count(), Double::sum);
                } else {
                    throw new IllegalArgumentException("Bad formula: " + this.text);
                }
            }
            return atoms;
        }

        /** 元素或括号后面的下标，没有为 1；ₙ 按 1。 */
        private double count() {
            if (this.pos < this.text.length() && this.text.charAt(this.pos) == 'ₙ') {
                this.pos++;
                return 1;
            }
            int n = 0;
            int start = this.pos;
            while (this.pos < this.text.length()) {
                int digit = digit(this.text.charAt(this.pos));
                if (digit < 0) {
                    break;
                }
                n = n * 10 + digit;
                this.pos++;
            }
            return this.pos > start ? n : 1;
        }

        private static int digit(char c) {
            if (c >= '0' && c <= '9') {
                return c - '0';
            }
            if (c >= '₀' && c <= '₉') {
                return c - '₀';
            }
            return -1;
        }
    }
}
