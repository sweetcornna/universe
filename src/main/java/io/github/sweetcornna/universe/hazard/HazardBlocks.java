package io.github.sweetcornna.universe.hazard;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import io.github.sweetcornna.universe.core.hazard.GeyserKind;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 间歇泉的方块（ADR-0034 §六）：注册时由 {@code UniverseBlocks}、{@code UniverseItems} 调这里，四种各一个喷口、一个结壳。 */
public final class HazardBlocks {
    private HazardBlocks() {
    }

    public static Map<GeyserKind, DeferredBlock<GeyserVentBlock>> vents(DeferredRegister.Blocks blocks) {
        Map<GeyserKind, DeferredBlock<GeyserVentBlock>> map = new EnumMap<>(GeyserKind.class);
        for (GeyserKind kind : GeyserKind.values()) {
            map.put(kind, blocks.registerBlock(kind.id() + "_geyser_vent", p -> new GeyserVentBlock(kind, p),
                    p -> p.mapColor(color(kind)).strength(1.8F, 6.0F).requiresCorrectToolForDrops().sound(vent(kind))
                            .lightLevel(state -> kind == GeyserKind.STRANGE ? 5 : 0)));
        }
        return Collections.unmodifiableMap(map);
    }

    public static Map<GeyserKind, DeferredBlock<GeyserCrustBlock>> crusts(DeferredRegister.Blocks blocks) {
        Map<GeyserKind, DeferredBlock<GeyserCrustBlock>> map = new EnumMap<>(GeyserKind.class);
        for (GeyserKind kind : GeyserKind.values()) {
            map.put(kind, blocks.registerBlock(kind.id() + "_geyser_crust", p -> new GeyserCrustBlock(kind, p),
                    p -> p.mapColor(color(kind)).strength(0.9F, 2.0F).requiresCorrectToolForDrops().sound(crust(kind))
                            .lightLevel(state -> kind == GeyserKind.STRANGE ? 3 : 0)));
        }
        return Collections.unmodifiableMap(map);
    }

    public static <B extends net.minecraft.world.level.block.Block> Map<GeyserKind, DeferredItem<BlockItem>> items(DeferredRegister.Items items,
            Map<GeyserKind, DeferredBlock<B>> blocks) {
        Map<GeyserKind, DeferredItem<BlockItem>> map = new EnumMap<>(GeyserKind.class);
        blocks.forEach((kind, block) -> map.put(kind, items.registerSimpleBlockItem(block)));
        return Collections.unmodifiableMap(map);
    }

    private static MapColor color(GeyserKind kind) {
        return switch (kind) {
            case HOT -> MapColor.COLOR_YELLOW;
            case TOXIC -> MapColor.TERRACOTTA_LIGHT_GREEN;
            case COLD -> MapColor.ICE;
            case STRANGE -> MapColor.COLOR_MAGENTA;
        };
    }

    private static SoundType vent(GeyserKind kind) {
        return kind == GeyserKind.COLD ? SoundType.GLASS : SoundType.TUFF;
    }

    private static SoundType crust(GeyserKind kind) {
        return switch (kind) {
            case HOT, TOXIC -> SoundType.CALCITE;
            case COLD -> SoundType.GLASS;
            case STRANGE -> SoundType.AMETHYST;
        };
    }
}
