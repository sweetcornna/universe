package io.github.sweetcornna.universe.terraform;

import java.util.Locale;

import io.github.sweetcornna.universe.core.terraform.Terraform;

/** 星球改造的门槛（ADR-0036 §六）写成字：名字的语言键、要到的范围、现在的值。界面和调试命令共用，不碰游戏的类。 */
public final class ThresholdText {
    private ThresholdText() {
    }

    /** 名字的语言键。 */
    public static String labelKey(Terraform.Threshold.Kind kind) {
        return "gui.universe.terraform.threshold." + kind.name().toLowerCase(Locale.ROOT);
    }

    /** 要到的范围，例如"253–313 K""≤ 1""≥ 1.0×"。 */
    public static String range(Terraform.Threshold threshold) {
        return switch (threshold.kind()) {
            case TEMPERATURE -> number(threshold.min(), 0) + "–" + number(threshold.max(), 0) + " K";
            case TOXIC_GASES -> "≤ " + number(threshold.max(), 0);
            case PRESSURE -> "≥ " + number(threshold.min(), 1) + "×";
            case BREATHING_PRESSURE -> number(threshold.min(), 1) + "–" + number(threshold.max(), 0) + " atm";
            case OXYGEN -> number(threshold.min(), 0) + "–" + number(threshold.max(), 0) + " kPa";
            case CARBON_DIOXIDE -> "≤ " + number(threshold.max(), 0) + " ppm";
        };
    }

    /** 现在的值。 */
    public static String value(Terraform.Threshold threshold) {
        double v = threshold.value();
        return switch (threshold.kind()) {
            case TEMPERATURE -> number(v, 0) + " K";
            case TOXIC_GASES -> v >= 100 ? number(v, 0) : number(v, 2);
            case PRESSURE -> (v >= 10 ? number(v, 1) : number(v, 2)) + "×";
            case BREATHING_PRESSURE -> (v >= 10 ? number(v, 1) : v >= 0.1 ? number(v, 2) : number(v, 4)) + " atm";
            case OXYGEN -> (v >= 100 ? number(v, 0) : number(v, 1)) + " kPa";
            case CARBON_DIOXIDE -> (v >= 1_000_000 ? String.format(Locale.ROOT, "%.2e", v) : String.format(Locale.ROOT, "%,.0f", v)) + " ppm";
        };
    }

    private static String number(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }
}
