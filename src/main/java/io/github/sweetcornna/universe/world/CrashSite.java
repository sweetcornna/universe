package io.github.sweetcornna.universe.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 主世界里坠毁的探测器（玩法设计 §4、路线图 M2）：新世界第一个玩家进入时，在出生点外 60–110 格的地面上砸出一个小陨坑，
 * 坑里是船体板拼成的残骸、一个冒着烟的信号篝火，和一只箱子：损坏的多功能工具、探测器日志，以及一点起步物资。
 *
 * <p>不改主世界的地形生成，只在这一处放方块（立项书的约束）。放过一次就记在主世界的存档数据里，不会重复。
 */
public final class CrashSite extends SavedData {
    private static final Codec<CrashSite> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.optionalFieldOf("pos").forGetter(site -> Optional.ofNullable(site.pos)),
            Codec.BOOL.optionalFieldOf("attempted", false).forGetter(site -> site.attempted)
    ).apply(i, (pos, attempted) -> new CrashSite(pos.orElse(null), attempted)));
    private static final SavedDataType<CrashSite> TYPE = new SavedDataType<>(Universe.id("crash_site"),
            level -> new CrashSite(null, false), level -> CODEC);
    /** 日志的页数（语言文件里 book.universe.probe_log.page.1…）。 */
    public static final int JOURNAL_PAGES = 6;
    private static final int RADIUS = 5;
    /** 冲击波吹倒树木、烧焦地面的半径。 */
    private static final int BLAST = 12;
    /** 坑外那道沟的长度。 */
    private static final int TRAIL = 16;

    private BlockPos pos;
    /** 试过放没有：找不到地方（超平坦、海岛）也只试一次，免得每次登录都去加载区块。 */
    private boolean attempted;

    private CrashSite(BlockPos pos, boolean attempted) {
        this.pos = pos;
        this.attempted = attempted || pos != null;
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ServerLevel overworld = player.level().getServer().overworld();
                CrashSite site = overworld.getDataStorage().computeIfAbsent(TYPE);
                if (!site.attempted) {
                    site.attempted = true;
                    site.pos = place(overworld);
                    site.setDirty();
                    if (site.pos != null) {
                        announce(player, site.pos);
                    }
                }
            }
        });
    }

    /** 坠毁点的位置（还没放时为空）。 */
    public static Optional<BlockPos> location(ServerLevel overworld) {
        return Optional.ofNullable(overworld.getDataStorage().computeIfAbsent(TYPE).pos);
    }

    private static void announce(ServerPlayer player, BlockPos site) {
        if (player.level().dimension() != Level.OVERWORLD) {
            return;
        }
        double dx = site.getX() - player.getX();
        double dz = site.getZ() - player.getZ();
        int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz) / 10) * 10;
        // 八个方位：东 0°，按逆时针
        String[] directions = {"east", "southeast", "south", "southwest", "west", "northwest", "north", "northeast"};
        int index = Math.floorMod(Math.round((float) Math.toDegrees(Math.atan2(dz, dx)) / 45F), 8);
        player.sendSystemMessage(Component.translatable("message.universe.crash_site.smoke",
                Component.translatable("direction.universe." + directions[index]), distance));
    }

    /**
     * 选一块干燥、比较平的地面放下坠毁点：在出生点外随机试 24 处，取最平的一处，够平（起伏不超过 2 格）就提前停下。
     * 四周全是水（海岛）或者是超平坦世界时放弃。
     */
    private static BlockPos place(ServerLevel level) {
        BlockPos spawn = level.getRespawnData().globalPos().pos();
        RandomSource random = RandomSource.create(level.getSeed() ^ 0x5EED_C2A5L);
        BlockPos best = null;
        int bestRoughness = Integer.MAX_VALUE;
        for (int attempt = 0; attempt < 24 && bestRoughness > 2; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 60 + random.nextDouble() * 50;
            int x = spawn.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = spawn.getZ() + (int) Math.round(Math.sin(angle) * distance);
            int y = groundY(level, x, z);
            BlockPos ground = new BlockPos(x, y, z);
            if (!level.getFluidState(ground).isEmpty() || y <= level.getMinY() + 8) {
                continue;
            }
            int roughness = roughness(level, ground);
            if (roughness >= 0 && roughness < bestRoughness) {
                best = ground;
                bestRoughness = roughness;
            }
        }
        if (best != null) {
            build(level, best, random);
        }
        return best;
    }

    /** 地面方块的 y：从最高的方块往下找，跳过树干、树叶和花草；碰到水也算地面（由调用方判断）。 */
    private static int groundY(ServerLevel level, int x, int z) {
        // 没加载的区块 getHeight 会返回世界底部，先加载上
        level.getChunk(x >> 4, z >> 4);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
        while (pos.getY() > level.getMinY()) {
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty() || state.is(BlockTags.BLOCKS_MOTION) && !isTree(state)) {
                return pos.getY();
            }
            pos.move(Direction.DOWN);
        }
        return pos.getY();
    }

    private static boolean isTree(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
    }

    /** 坑周围一圈地面的起伏（格）；附近有水时返回 -1。 */
    private static int roughness(ServerLevel level, BlockPos center) {
        int roughness = 0;
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * (RADIUS + 3));
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * (RADIUS + 3));
            int y = groundY(level, x, z);
            if (!level.getFluidState(new BlockPos(x, y, z)).isEmpty()) {
                return -1;
            }
            roughness = Math.max(roughness, Math.abs(y - center.getY()));
        }
        return roughness;
    }

    private static void build(ServerLevel level, BlockPos center, RandomSource random) {
        // 先把周围的区块都加载上（残骸拖出的痕迹最远到 RADIUS + TRAIL）
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.getChunk((center.getX() >> 4) + dx, (center.getZ() >> 4) + dz);
            }
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // 冲击波：吹倒附近的树，地面烧焦一片。记下树干的样子，后面摆几根倒下的树
        BlockState log = null;
        for (int dx = -BLAST; dx <= BLAST; dx++) {
            for (int dz = -BLAST; dz <= BLAST; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz) + random.nextDouble() * 1.5;
                if (d >= BLAST) {
                    continue;
                }
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int ground = groundY(level, x, z);
                BlockState cut = clearAbove(level, pos, x, ground, z);
                if (cut != null && log == null) {
                    log = cut;
                }
                pos.set(x, ground, z);
                if (d > RADIUS && random.nextFloat() < 0.85F * (1 - (float) ((d - RADIUS) / (BLAST - RADIUS)))
                        && level.getBlockState(pos).is(BlockTags.SUBSTRATE_OVERWORLD)) {
                    level.setBlock(pos, random.nextInt(3) == 0 ? Blocks.ROOTED_DIRT.defaultBlockState()
                            : Blocks.COARSE_DIRT.defaultBlockState(), 2);
                }
            }
        }
        // 陨坑：中间深 3 格，坑底是焦黑的碎石、玄武岩、粗泥，坑沿堆起一圈土
        List<BlockState> scorched = List.of(Blocks.BLACKSTONE.defaultBlockState(), Blocks.SMOOTH_BASALT.defaultBlockState(),
                Blocks.COARSE_DIRT.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(), Blocks.COAL_BLOCK.defaultBlockState());
        for (int dx = -RADIUS - 2; dx <= RADIUS + 2; dx++) {
            for (int dz = -RADIUS - 2; dz <= RADIUS + 2; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz) + random.nextDouble() * 0.6;
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int ground = groundY(level, x, z);
                if (d < RADIUS) {
                    int depth = (int) Math.round(3 * (1 - d * d / (RADIUS * RADIUS)));
                    int floor = center.getY() - depth;
                    // 坑里清空到地表以上 2 格；坡上的一侧会削出一道坎
                    for (int y = floor + 1; y <= Math.max(ground, center.getY()) + 2; y++) {
                        level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                    level.setBlock(pos.set(x, floor, z), scorched.get(random.nextInt(scorched.size())), 2);
                } else if (d < RADIUS + 1.6 && random.nextFloat() < 0.8F) {
                    level.setBlock(pos.set(x, ground + 1, z), random.nextBoolean() ? Blocks.COARSE_DIRT.defaultBlockState()
                            : Blocks.GRAVEL.defaultBlockState(), 2);
                }
            }
        }
        trail(level, center, random, List.of(Blocks.COARSE_DIRT.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(),
                Blocks.ROOTED_DIRT.defaultBlockState(), Blocks.COARSE_DIRT.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState()), pos);
        if (log != null) {
            fallenTrees(level, center, random, log, pos);
        }
        // 残骸：一团斜插在坑底的船体板，带一个摔坏的推进器
        int floorY = center.getY() - 3;
        BlockState light = UniverseBlocks.HULL_PLATING.get(DyeColor.LIGHT_GRAY).get().defaultBlockState();
        BlockState dark = UniverseBlocks.HULL_PLATING.get(DyeColor.GRAY).get().defaultBlockState();
        BlockState orange = UniverseBlocks.HULL_PLATING.get(DyeColor.ORANGE).get().defaultBlockState();
        int[][] wreck = {
                {0, 1, 0}, {1, 1, 0}, {0, 1, 1}, {-1, 1, 0}, {0, 2, 0}, {1, 2, 0}, {1, 2, 1}, {0, 3, 1},
                {2, 1, 1}, {1, 1, 1}, {0, 2, 1}, {2, 2, 1}, {1, 3, 1}, {2, 3, 2}, {-1, 1, -1}};
        for (int[] offset : wreck) {
            BlockState state = random.nextInt(5) == 0 ? orange : random.nextBoolean() ? light : dark;
            level.setBlock(pos.set(center.getX() + offset[0], floorY + offset[1], center.getZ() + offset[2]), state, 2);
        }
        level.setBlock(pos.set(center.getX() + 2, floorY + 4, center.getZ() + 2),
                UniverseBlocks.SHIP_THRUSTER.get().defaultBlockState(), 2);
        level.setBlock(pos.set(center.getX() - 1, floorY + 2, center.getZ() + 1), Blocks.IRON_BARS.defaultBlockState(), 2);
        // 两个信号篝火：下面垫干草块，烟柱又高又粗，远远就能看到
        for (BlockPos fire : List.of(new BlockPos(center.getX() - 2, floorY + 1, center.getZ() - 1),
                new BlockPos(center.getX() + 2, floorY + 1, center.getZ()))) {
            level.setBlock(fire.below(), Blocks.HAY_BLOCK.defaultBlockState(), 2);
            level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, true), 2);
        }
        // 箱子
        BlockPos chestPos = new BlockPos(center.getX() + 1, floorY + 1, center.getZ() - 2);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), 2);
        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            List<ItemStack> loot = loot();
            for (int slot = 0; slot < loot.size(); slot++) {
                chest.setItem(slot * 2 % chest.getContainerSize(), loot.get(slot));
            }
        }
    }

    /**
     * 清掉地面以上的树和花草，返回砍掉的第一段树干（没有树时为空）。用完整的方块更新，
     * 这样被砍断的树剩下的树叶会自然凋落。
     */
    private static BlockState clearAbove(ServerLevel level, BlockPos.MutableBlockPos pos, int x, int ground, int z) {
        BlockState cut = null;
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        for (int y = ground + 1; y < top; y++) {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (state.isAir()) {
                continue;
            }
            if (isTree(state) || !state.is(BlockTags.BLOCKS_MOTION) && state.getFluidState().isEmpty()) {
                if (cut == null && state.is(BlockTags.LOGS)) {
                    cut = state;
                }
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        return cut;
    }

    /** 探测器砸下来之前在地上犁出的一道沟，沿途散落着碎片。 */
    private static void trail(ServerLevel level, BlockPos center, RandomSource random, List<BlockState> furrow,
                              BlockPos.MutableBlockPos pos) {
        double angle = random.nextDouble() * Math.PI * 2;
        double ux = Math.cos(angle);
        double uz = Math.sin(angle);
        List<BlockState> debris = List.of(UniverseBlocks.HULL_PLATING.get(DyeColor.LIGHT_GRAY).get().defaultBlockState(),
                UniverseBlocks.HULL_PLATING.get(DyeColor.GRAY).get().defaultBlockState(), Blocks.IRON_BARS.defaultBlockState(),
                Blocks.IRON_CHAIN.defaultBlockState());
        for (int step = RADIUS; step <= RADIUS + TRAIL; step++) {
            // 越远沟越窄
            int half = step < RADIUS + TRAIL / 2 ? 1 : 0;
            for (int side = -half; side <= half; side++) {
                int x = center.getX() + (int) Math.round(ux * step - uz * side);
                int z = center.getZ() + (int) Math.round(uz * step + ux * side);
                int ground = groundY(level, x, z);
                if (!level.getFluidState(pos.set(x, ground, z)).isEmpty()) {
                    return;
                }
                clearAbove(level, pos, x, ground, z);
                // 沟底比地面低一格
                level.setBlock(pos.set(x, ground, z), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(pos.set(x, ground - 1, z), furrow.get(random.nextInt(furrow.size())), 2);
                if (side == 0 && step % 4 == 2 && random.nextFloat() < 0.8F) {
                    // 碎片摆在沟边的地面上
                    int ex = center.getX() + (int) Math.round(ux * step - uz * (half + 1) * (random.nextBoolean() ? 1 : -1));
                    int ez = center.getZ() + (int) Math.round(uz * step + ux * (half + 1) * (random.nextBoolean() ? 1 : -1));
                    int ey = groundY(level, ex, ez);
                    if (level.getFluidState(pos.set(ex, ey, ez)).isEmpty()) {
                        level.setBlock(pos.set(ex, ey + 1, ez), debris.get(random.nextInt(debris.size())), 2);
                    }
                }
            }
        }
    }

    /** 冲击波边上几棵被推倒的树：树干朝外横躺在地上。 */
    private static void fallenTrees(ServerLevel level, BlockPos center, RandomSource random, BlockState log,
                                    BlockPos.MutableBlockPos pos) {
        int count = 3 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double ux = Math.cos(angle);
            double uz = Math.sin(angle);
            Direction.Axis axis = Math.abs(ux) > Math.abs(uz) ? Direction.Axis.X : Direction.Axis.Z;
            BlockState lying = log.hasProperty(RotatedPillarBlock.AXIS) ? log.setValue(RotatedPillarBlock.AXIS, axis) : log;
            int length = 3 + random.nextInt(3);
            double start = RADIUS + 3 + random.nextDouble() * (BLAST - RADIUS - 5);
            int sx = center.getX() + (int) Math.round(ux * start);
            int sz = center.getZ() + (int) Math.round(uz * start);
            for (int j = 0; j < length; j++) {
                int x = axis == Direction.Axis.X ? sx + (int) Math.signum(ux) * j : sx;
                int z = axis == Direction.Axis.Z ? sz + (int) Math.signum(uz) * j : sz;
                int ground = groundY(level, x, z);
                if (!level.getFluidState(pos.set(x, ground, z)).isEmpty() || !level.getBlockState(pos.set(x, ground + 1, z)).isAir()) {
                    break;
                }
                level.setBlock(pos, lying, 2);
            }
        }
    }

    private static List<ItemStack> loot() {
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(UniverseItems.DAMAGED_MULTITOOL.get()));
        loot.add(journal());
        loot.add(new ItemStack(UniverseItems.GUIDE.get()));
        loot.add(new ItemStack(Items.COPPER_INGOT, 6));
        loot.add(new ItemStack(Items.IRON_INGOT, 4));
        loot.add(new ItemStack(Items.REDSTONE, 4));
        loot.add(new ItemStack(UniverseItems.OXYGEN_CAPSULE.get(), 2));
        loot.add(new ItemStack(UniverseItems.HAZARD_CELL.get(), 2));
        loot.add(new ItemStack(Items.BREAD, 3));
        return loot;
    }

    /** 探测器日志：成书，每页是一条可翻译的文字，客户端按自己的语言显示。 */
    public static ItemStack journal() {
        List<Filterable<Component>> pages = new ArrayList<>();
        for (int page = 1; page <= JOURNAL_PAGES; page++) {
            pages.add(Filterable.passThrough(Component.translatable("book.universe.probe_log.page." + page)));
        }
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("Probe Log / 探测器日志"), "UNV-7", 0, pages, true));
        book.set(DataComponents.CUSTOM_NAME, Component.translatable("book.universe.probe_log.title"));
        return book;
    }

    /** 调试命令：{@code /universe crash_site} 在脚下的地面（不算树）放一个，{@code /universe crash_site locate} 报告开局放的那个在哪。 */
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("crash_site")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    BlockPos feet = player.blockPosition();
                    BlockPos ground = new BlockPos(feet.getX(), groundY(player.level(), feet.getX(), feet.getZ()), feet.getZ());
                    build(player.level(), ground, RandomSource.create(ground.asLong()));
                    context.getSource().sendSuccess(() -> Component.literal("已在 " + ground.toShortString() + " 放置坠毁点"), false);
                    return 1;
                })
                .then(Commands.literal("locate").executes(context -> {
                    Optional<BlockPos> site = location(context.getSource().getServer().overworld());
                    context.getSource().sendSuccess(() -> Component.literal(site.map(p -> "坠毁点在主世界 " + p.toShortString())
                            .orElse("还没有放置坠毁点")), false);
                    return site.isPresent() ? 1 : 0;
                }));
    }
}
