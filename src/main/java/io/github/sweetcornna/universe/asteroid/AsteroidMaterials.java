package io.github.sweetcornna.universe.asteroid;

import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.core.space.AsteroidField.Material;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 小行星的材质对应的方块（画出来的样子、碎石粒子）和产出的物品。岩石用星岩（太空里按中性的灰色染）和深板岩，
 * 矿点用星球上同种的矿石方块；金用原版金矿石，钛晶石用紫水晶块。
 */
public final class AsteroidMaterials {
    private AsteroidMaterials() {
    }

    public static BlockState block(Material material) {
        return switch (material) {
            case ROCK -> UniverseBlocks.PLANET_ROCK.get().defaultBlockState();
            case DARK_ROCK -> Blocks.DEEPSLATE.defaultBlockState();
            case IRON -> UniverseBlocks.ORES.get(OreKind.IRON).get().defaultBlockState();
            case COPPER -> UniverseBlocks.ORES.get(OreKind.COPPER).get().defaultBlockState();
            case SILICON -> UniverseBlocks.ORES.get(OreKind.SILICON).get().defaultBlockState();
            case TITANIUM -> UniverseBlocks.ORES.get(OreKind.TITANIUM).get().defaultBlockState();
            case GOLD -> Blocks.GOLD_ORE.defaultBlockState();
            case OPTICAL -> UniverseBlocks.ORES.get(OreKind.OPTICAL_CRYSTAL).get().defaultBlockState();
            case TITANIUM_CRYSTAL -> Blocks.AMETHYST_BLOCK.defaultBlockState();
        };
    }

    /** 矿点产出的物品；岩石不产出，返回空气。 */
    public static Item item(Material material) {
        return switch (material) {
            case ROCK, DARK_ROCK -> Items.AIR;
            case IRON -> Items.RAW_IRON;
            case COPPER -> Items.RAW_COPPER;
            case SILICON -> UniverseItems.oreDrop(OreKind.SILICON);
            case TITANIUM -> UniverseItems.oreDrop(OreKind.TITANIUM);
            case GOLD -> Items.RAW_GOLD;
            case OPTICAL -> UniverseItems.oreDrop(OreKind.OPTICAL_CRYSTAL);
            case TITANIUM_CRYSTAL -> UniverseItems.TITANIUM_CRYSTAL.get();
        };
    }
}
