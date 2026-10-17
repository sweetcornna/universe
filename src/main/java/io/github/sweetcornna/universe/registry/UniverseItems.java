package io.github.sweetcornna.universe.registry;

import java.util.List;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 物品注册。方块物品和方块同名。 */
public final class UniverseItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Universe.MOD_ID);

    public static final DeferredItem<BlockItem> PLANET_ROCK = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_ROCK);
    public static final DeferredItem<BlockItem> PLANET_COBBLESTONE = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_COBBLESTONE);
    public static final DeferredItem<BlockItem> PLANET_SOIL = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_SOIL);
    public static final DeferredItem<BlockItem> PLANET_TURF = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_TURF);
    public static final DeferredItem<BlockItem> PLANET_REGOLITH = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_REGOLITH);
    public static final DeferredItem<BlockItem> PLANET_SAND = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_SAND);
    public static final DeferredItem<BlockItem> PLANET_SNOW = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_SNOW);
    public static final DeferredItem<BlockItem> PLANET_ICE = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_ICE);
    public static final DeferredItem<BlockItem> PLANET_ASH = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_ASH);
    public static final DeferredItem<BlockItem> PLANET_CRYSTAL = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_CRYSTAL);
    public static final DeferredItem<BlockItem> PLANET_STRATA = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_STRATA);

    /** 创造模式物品栏里的顺序。 */
    public static final List<DeferredItem<?>> CREATIVE_ORDER = List.of(
            PLANET_ROCK, PLANET_COBBLESTONE, PLANET_SOIL, PLANET_TURF, PLANET_REGOLITH, PLANET_SAND,
            PLANET_SNOW, PLANET_ICE, PLANET_ASH, PLANET_STRATA, PLANET_CRYSTAL);

    private UniverseItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
