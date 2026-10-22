package io.github.sweetcornna.universe.factory.resource;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.core.factory.Veins;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.Resource;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.rules.UniverseRules;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * 矿脉节点（ADR-0011）：采矿机眼里的矿石方块。原版矿石和星球矿石都算，每个方块有几百的储量；采完变回当地的石头。
 *
 * <p>没开采过的节点不存档，储量由世界种子和位置算出来（{@link Veins}）；开采过的在所在区块的附件里记开采了多少。
 * 原油涌泉也记在这里，记的是抽了多少，产量随之衰减。储量和产量乘建世界时定的资源倍率（ADR-0028 §七），倍率 0 时采不完，
 * 也不再记开采了多少。
 */
public final class VeinNodes {
    private static final Map<Block, Node> VANILLA = new IdentityHashMap<>();
    private static volatile @Nullable Map<Block, Node> planetOres;

    static {
        vanilla(Blocks.IRON_ORE, Items.RAW_IRON, Blocks.STONE);
        vanilla(Blocks.DEEPSLATE_IRON_ORE, Items.RAW_IRON, Blocks.DEEPSLATE);
        vanilla(Blocks.COPPER_ORE, Items.RAW_COPPER, Blocks.STONE);
        vanilla(Blocks.DEEPSLATE_COPPER_ORE, Items.RAW_COPPER, Blocks.DEEPSLATE);
        vanilla(Blocks.COAL_ORE, Items.COAL, Blocks.STONE);
        vanilla(Blocks.DEEPSLATE_COAL_ORE, Items.COAL, Blocks.DEEPSLATE);
        vanilla(Blocks.GOLD_ORE, Items.RAW_GOLD, Blocks.STONE);
        vanilla(Blocks.DEEPSLATE_GOLD_ORE, Items.RAW_GOLD, Blocks.DEEPSLATE);
        vanilla(Blocks.REDSTONE_ORE, Items.REDSTONE, Blocks.STONE);
        vanilla(Blocks.DEEPSLATE_REDSTONE_ORE, Items.REDSTONE, Blocks.DEEPSLATE);
        vanilla(Blocks.LAPIS_ORE, Items.LAPIS_LAZULI, Blocks.STONE);
        vanilla(Blocks.DEEPSLATE_LAPIS_ORE, Items.LAPIS_LAZULI, Blocks.DEEPSLATE);
        vanilla(Blocks.DIAMOND_ORE, Items.DIAMOND, Blocks.STONE);
        vanilla(Blocks.DEEPSLATE_DIAMOND_ORE, Items.DIAMOND, Blocks.DEEPSLATE);
        vanilla(Blocks.EMERALD_ORE, Items.EMERALD, Blocks.STONE);
        vanilla(Blocks.DEEPSLATE_EMERALD_ORE, Items.EMERALD, Blocks.DEEPSLATE);
        vanilla(Blocks.NETHER_QUARTZ_ORE, Items.QUARTZ, Blocks.NETHERRACK);
        vanilla(Blocks.NETHER_GOLD_ORE, Items.GOLD_NUGGET, Blocks.NETHERRACK);
    }

    private VeinNodes() {
    }

    /**
     * 一个节点开采出来是什么、采完变成什么。
     *
     * @param resource 星球资源（原版矿石为空），用来按星球的丰度调整储量
     */
    public record Node(Item item, BlockState depleted, @Nullable Resource resource) {
    }

    private static void vanilla(Block ore, Item item, Block depleted) {
        VANILLA.put(ore, new Node(item, depleted.defaultBlockState(), null));
    }

    /** 这个方块是不是矿脉节点。 */
    public static @Nullable Node node(BlockState state) {
        Node vanilla = VANILLA.get(state.getBlock());
        return vanilla != null ? vanilla : planetOres().get(state.getBlock());
    }

    /** 星球矿石：注册完才能取方块，第一次用到时再建表。 */
    private static Map<Block, Node> planetOres() {
        Map<Block, Node> ores = planetOres;
        if (ores == null) {
            Map<Block, Node> built = new IdentityHashMap<>();
            BlockState rock = UniverseBlocks.PLANET_ROCK.get().defaultBlockState();
            UniverseBlocks.ORES.forEach((kind, block) -> built.put(block.get(), new Node(UniverseItems.oreDrop(kind), rock, kind.resource())));
            ores = built;
            planetOres = ores;
        }
        return ores;
    }

    /** 节点的初始储量：星球上按资源丰度调整（丰度 0 到 1 对应 0.5 到 1.5 倍），再乘资源倍率；倍率 0 时是 {@link Veins#INFINITE}。 */
    public static int initialReserve(ServerLevel level, BlockPos pos, Node node) {
        double richness = 1;
        if (node.resource() != null) {
            Planet planet = Planets.planet(level).orElse(null);
            if (planet != null) {
                for (Resource.Deposit deposit : planet.resources()) {
                    if (deposit.resource() == node.resource()) {
                        richness = 0.5 + deposit.abundance();
                    }
                }
            }
        }
        return Veins.initialReserve(level.getSeed(), pos.getX(), pos.getY(), pos.getZ(), richness, multiplier(level));
    }

    private static double multiplier(ServerLevel level) {
        return UniverseRules.resourceMultiplier(level.getServer());
    }

    /** 节点还剩多少。 */
    public static int remaining(ServerLevel level, BlockPos pos, Node node) {
        return Math.max(0, initialReserve(level, pos, node) - used(level, pos));
    }

    /**
     * 开采一个。
     *
     * @return 这个节点采完了（已经换成石头）
     */
    public static boolean extract(ServerLevel level, BlockPos pos, Node node) {
        int reserve = initialReserve(level, pos, node);
        if (reserve == Veins.INFINITE) {
            return false;
        }
        int used = used(level, pos) + 1;
        if (used >= reserve) {
            setUsed(level, pos, 0);
            level.setBlock(pos, node.depleted(), 3);
            return true;
        }
        setUsed(level, pos, used);
        return false;
    }

    // ---- 原油涌泉

    /** 涌泉现在的产量（个/秒）。 */
    public static double seepRate(ServerLevel level, BlockPos pos) {
        double initial = Veins.initialSeepRate(level.getSeed(), pos.getX(), pos.getY(), pos.getZ());
        return Veins.seepRate(initial, used(level, pos), multiplier(level));
    }

    /** 从涌泉抽出一个。 */
    public static void extractSeep(ServerLevel level, BlockPos pos) {
        if (!Veins.infinite(multiplier(level))) {
            setUsed(level, pos, used(level, pos) + 1);
        }
    }

    // ---- 区块附件

    private static int used(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        Map<Long, Integer> usage = chunk.getExistingDataOrNull(UniverseAttachments.VEIN_USAGE.get());
        return usage == null ? 0 : usage.getOrDefault(pos.asLong(), 0);
    }

    private static void setUsed(ServerLevel level, BlockPos pos, int used) {
        LevelChunk chunk = level.getChunkAt(pos);
        Map<Long, Integer> usage = chunk.getExistingDataOrNull(UniverseAttachments.VEIN_USAGE.get());
        if (usage == null) {
            if (used == 0) {
                return;
            }
            usage = new HashMap<>();
            chunk.setData(UniverseAttachments.VEIN_USAGE.get(), usage);
        }
        if (used == 0) {
            usage.remove(pos.asLong());
        } else {
            usage.put(pos.asLong(), used);
        }
        chunk.markUnsaved();
    }
}
