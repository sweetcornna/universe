package io.github.sweetcornna.universe.client.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.settlement.BuildingType;
import io.github.sweetcornna.universe.core.settlement.Decisions;
import io.github.sweetcornna.universe.core.settlement.Product;
import io.github.sweetcornna.universe.core.settlement.Settlement;
import io.github.sweetcornna.universe.core.settlement.SettlementRules;
import io.github.sweetcornna.universe.trade.Credits;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** 聚落界面上的字：建筑、产品、决定的效果、事件。 */
final class SettlementText {
    private SettlementText() {
    }

    static Component building(@Nullable BuildingType type) {
        return Component.translatable("settlement.universe.building." + (type == null ? "none" : type.id()));
    }

    static Component product(Product product) {
        return Component.translatable("settlement.universe.product." + product.id());
    }

    static Component kind(Decisions.Kind kind) {
        return Component.translatable("gui.universe.settlement.kind." + kind.id());
    }

    /** 建筑在这一档的作用，一条一行。 */
    static List<Component> effects(BuildingType type, int grade) {
        List<Component> lines = new ArrayList<>();
        add(lines, "housing", type.housing(grade));
        add(lines, "productivity", type.productivity(grade));
        add(lines, "happiness", type.happiness(grade));
        if (type.income(grade) > 0) {
            lines.add(Component.translatable("gui.universe.settlement.effect.income", Credits.format(Math.round(type.income(grade)))));
        }
        if (type.threatRelief(grade) > 0) {
            lines.add(Component.translatable("gui.universe.settlement.effect.relief", number(type.threatRelief(grade))));
        }
        if (type.defense(grade) > 0) {
            lines.add(Component.translatable("gui.universe.settlement.effect.defense", number(type.defense(grade))));
        }
        switch (type) {
            case OFFICE -> lines.add(Component.translatable("gui.universe.settlement.effect.office"));
            case FARM -> lines.add(Component.translatable("gui.universe.settlement.effect.food"));
            case WORKSHOP -> lines.add(Component.translatable("gui.universe.settlement.effect.parts"));
            case WATCHTOWER -> lines.add(Component.translatable("gui.universe.settlement.effect.nav_data", SettlementRules.navDataPerDay(grade)));
            case FISHING_POND -> lines.add(Component.translatable("gui.universe.settlement.effect.fish", SettlementRules.fishPerDay(grade)));
            case LANDING_PAD -> lines.add(Component.translatable("gui.universe.settlement.effect.landing"));
            case TURRET -> lines.add(Component.translatable("gui.universe.settlement.effect.turret"));
            default -> {
            }
        }
        return lines;
    }

    private static void add(List<Component> lines, String key, double value) {
        if (value > 0) {
            lines.add(Component.translatable("gui.universe.settlement.effect." + key, number(value)));
        }
    }

    /** 一行写完的作用："住房 +3 · 幸福 +2"。 */
    static Component effectLine(BuildingType type, int grade) {
        return join(effects(type, grade));
    }

    /** 决定的一个选项会怎样："信用点 −800 · 幸福 +8"。 */
    static Component effect(Decisions.Effect effect) {
        List<Component> parts = new ArrayList<>();
        signed(parts, "credits", effect.credits());
        signed(parts, "happy", effect.happiness());
        if (effect.boost() != 0) {
            parts.add(Component.translatable("gui.universe.settlement.change.boost", sign(effect.boost()), Math.max(1, effect.days())));
        }
        signed(parts, "threat", effect.threat());
        signed(parts, "reserve", effect.reserve());
        signed(parts, "people", effect.population());
        signed(parts, "standing", effect.standing());
        if (!effect.item().isEmpty() && effect.count() > 0) {
            parts.add(Component.translatable("gui.universe.settlement.change.item", effect.count(), Component.translatable(
                    "item." + effect.item().replace(':', '.'))));
        }
        if (effect.word()) {
            parts.add(Component.translatable("gui.universe.settlement.change.word"));
        }
        return parts.isEmpty() ? Component.translatable("gui.universe.settlement.change.none") : join(parts);
    }

    private static void signed(List<Component> parts, String key, int value) {
        if (value != 0) {
            parts.add(Component.translatable("gui.universe.settlement.change." + key, sign(value)));
        }
    }

    private static String sign(int value) {
        return value > 0 ? "+" + value : "−" + -value;
    }

    static String number(double value) {
        return Math.abs(value - Math.rint(value)) < 1e-6 ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    static Component join(List<Component> parts) {
        MutableComponent line = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                line.append(" · ");
            }
            line.append(parts.get(i));
        }
        return line;
    }

    /** 最近的一件事。 */
    static Component event(Settlement.Event event) {
        String key = "settlement.universe.event." + event.key();
        String arg = event.arg();
        return switch (event.key()) {
            case "built", "opened", "repaired" -> Component.translatable(key, building(type(arg)));
            case "upgraded" -> {
                String[] parts = arg.split(":", 2);
                yield Component.translatable(key, building(type(parts[0])), parts.length > 1 ? parts[1] : "");
            }
            case "decided" -> {
                String[] parts = arg.split(":", 2);
                yield Component.translatable(key, Component.translatable("settlement.universe.decision." + parts[0] + ".title"));
            }
            case "debt" -> Component.translatable(key, Credits.format(parse(arg)));
            default -> Component.translatable(key, arg);
        };
    }

    private static long parse(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static @Nullable BuildingType type(String id) {
        for (BuildingType type : BuildingType.values()) {
            if (type.id().equals(id)) {
                return type;
            }
        }
        return null;
    }

    /** 刻数写成"分:秒"。 */
    static String clock(long ticks) {
        long seconds = Math.max(0, ticks) / 20;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
