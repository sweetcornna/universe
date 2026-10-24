package io.github.sweetcornna.universe.fishing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.sweetcornna.universe.core.fishing.FishSpecies;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** 鱼的说明文字：名字、条件、重量（物品说明、图鉴、钓上来的卡片共用）。 */
public final class FishText {
    private FishText() {
    }

    public static MutableComponent name(FishSpecies species) {
        return Component.translatable("item.universe." + species.itemId());
    }

    /** 条件，比如"夜里 · 深水"；没有条件时是"随时"。 */
    public static MutableComponent conditions(FishSpecies species) {
        List<Component> parts = new ArrayList<>();
        if (species.time() != FishSpecies.Time.ANY) {
            parts.add(Component.translatable("fishing.universe.time." + species.time().key()));
        }
        if (species.weather() != FishSpecies.Weather.ANY) {
            parts.add(Component.translatable("fishing.universe.weather." + species.weather().key()));
        }
        if (species.depth() != FishSpecies.Depth.ANY) {
            parts.add(Component.translatable("fishing.universe.depth." + species.depth().key()));
        }
        if (parts.isEmpty()) {
            return Component.translatable("fishing.universe.anytime");
        }
        MutableComponent text = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                text.append(" · ");
            }
            text.append(parts.get(i));
        }
        return text;
    }

    /** 重量：10 千克以下两位小数，再重一位。 */
    public static MutableComponent weight(double kilograms) {
        String number = kilograms >= 10 ? String.format(Locale.ROOT, "%.1f", kilograms) : String.format(Locale.ROOT, "%.2f", kilograms);
        return Component.translatable("fishing.universe.kg", number);
    }
}
