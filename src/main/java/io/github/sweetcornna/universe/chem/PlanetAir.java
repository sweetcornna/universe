package io.github.sweetcornna.universe.chem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.sweetcornna.universe.core.chem.AtmosphereMix;
import io.github.sweetcornna.universe.core.chem.Gas;
import io.github.sweetcornna.universe.core.terraform.EffectiveEnvironment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 星球现在的大气（ADR-0036 §二）：成分和气压，读的是改造过的现用环境（§六）。服务端用
 * {@link io.github.sweetcornna.universe.terraform.Terraforming#environment}，客户端用 {@code ClientGalaxy.environment} 取环境。
 */
public final class PlanetAir {
    /** 一个标准大气压是多少千帕。 */
    public static final double KPA_PER_ATM = 101.325;
    /** 界面上列出前几种气体。 */
    public static final int SHOWN = 4;

    private PlanetAir() {
    }

    /** 现在的大气成分；没有大气为空。 */
    public static AtmosphereMix mix(EffectiveEnvironment environment) {
        return environment.pressure() > 0 ? environment.mix() : AtmosphereMix.EMPTY;
    }

    /** 现在的地表气压（标准大气压）。 */
    public static double pressure(EffectiveEnvironment environment) {
        return environment.pressure();
    }

    /** 氧分压（千帕）。 */
    public static double oxygenKpa(EffectiveEnvironment environment) {
        return environment.oxygenKpa();
    }

    /** 有毒气体的接触指数（{@link AtmosphereMix#exposureIndex}）：各种毒气的分压折成 ppm 除以限值再加起来，大于 1 有毒。 */
    public static double exposureIndex(EffectiveEnvironment environment) {
        return environment.toxicIndex();
    }

    /** 一种气体的名字，按界面的语言取。 */
    public static Component gasName(Gas gas) {
        return Component.translatable("item.universe." + gas.item());
    }

    /**
     * 一行"大气：二氧化碳 95%、氮气 2.8%、氩气 1.9%、氧气 0.15%"，一行"氧分压 0.02 kPa"，再一行"毒性指数 0.12 / 1"。
     * 没有大气时为空。
     *
     * @param separator 两种气体之间的分隔（中文"、"，英文", "）
     */
    public static List<Component> lines(EffectiveEnvironment planet, String separator) {
        AtmosphereMix mix = mix(planet);
        if (mix.isEmpty()) {
            return List.of();
        }
        MutableComponent gases = Component.empty();
        boolean first = true;
        for (Map.Entry<Gas, Double> entry : mix.top(SHOWN)) {
            if (!first) {
                gases.append(separator);
            }
            first = false;
            gases.append(gasName(entry.getKey())).append(" " + percent(entry.getValue()));
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.universe.atmosphere.mix", gases));
        lines.add(Component.translatable("gui.universe.atmosphere.oxygen", kpa(oxygenKpa(planet))));
        lines.add(toxicity(planet));
        return lines;
    }

    /** 窄的地方（分析目镜）用的一行：化学式代替名字，"CO₂ 95% · N₂ 2.8% · Ar 1.9% · O₂ 0.15%"，再一行氧分压和毒性指数。 */
    public static List<Component> compactLines(EffectiveEnvironment planet) {
        AtmosphereMix mix = mix(planet);
        if (mix.isEmpty()) {
            return List.of();
        }
        StringBuilder gases = new StringBuilder();
        for (Map.Entry<Gas, Double> entry : mix.top(SHOWN)) {
            if (!gases.isEmpty()) {
                gases.append(" · ");
            }
            gases.append(entry.getKey().formula()).append(' ').append(percent(entry.getValue()));
        }
        return List.of(Component.literal(gases.toString()), Component.translatable("gui.universe.atmosphere.oxygen_and_toxicity",
                kpa(oxygenKpa(planet)), index(exposureIndex(planet))));
    }

    /** "毒性指数 0.12 / 1"，过了 1 标红。 */
    public static Component toxicity(EffectiveEnvironment planet) {
        double index = exposureIndex(planet);
        MutableComponent line = Component.translatable("gui.universe.atmosphere.toxicity", index(index));
        return index > 1 ? line.withStyle(ChatFormatting.RED) : line;
    }

    /** 接触指数：1 以下留两位小数，再大的少留几位。 */
    public static String index(double index) {
        if (index < 1) {
            return String.format(Locale.ROOT, "%.2f", index);
        }
        return String.format(Locale.ROOT, index < 10 ? "%.1f" : "%.0f", index);
    }

    /** 摩尔分数写成百分数：大的不带小数，小的留两三位有效数字。 */
    public static String percent(double fraction) {
        double value = fraction * 100;
        if (value >= 10) {
            return String.format(Locale.ROOT, "%.0f%%", value);
        }
        if (value >= 1) {
            return String.format(Locale.ROOT, "%.1f%%", value);
        }
        if (value >= 0.1) {
            return String.format(Locale.ROOT, "%.2f%%", value);
        }
        return String.format(Locale.ROOT, "%.3f%%", value);
    }

    /** 千帕：大的不带小数。 */
    public static String kpa(double kpa) {
        if (kpa >= 10) {
            return String.format(Locale.ROOT, "%.0f kPa", kpa);
        }
        if (kpa >= 0.1) {
            return String.format(Locale.ROOT, "%.1f kPa", kpa);
        }
        return String.format(Locale.ROOT, "%.3f kPa", kpa);
    }
}
