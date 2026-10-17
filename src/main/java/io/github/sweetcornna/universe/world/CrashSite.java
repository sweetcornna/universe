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

    /** 选一块干燥的地面放下坠毁点；试 16 个方向都不行时放弃（比如出生在海岛上）。 */
    private static BlockPos place(ServerLevel level) {
        BlockPos spawn = level.getRespawnData().globalPos().pos();
        RandomSource random = RandomSource.create(level.getSeed() ^ 0x5EED_C2A5L);
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 60 + random.nextDouble() * 50;
            int x = spawn.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = spawn.getZ() + (int) Math.round(Math.sin(angle) * distance);
            level.getChunk(x >> 4, z >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos ground = new BlockPos(x, y - 1, z);
            BlockState surface = level.getBlockState(ground);
            if (!level.getFluidState(ground).isEmpty() || !level.getFluidState(ground.above()).isEmpty()
                    || surface.is(BlockTags.LEAVES) || y <= level.getMinY() + 8) {
                continue;
            }
            build(level, ground, random);
            return ground;
        }
        return null;
    }

    private static void build(ServerLevel level, BlockPos center, RandomSource random) {
        // 先把周围的区块都加载上
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.getChunk((center.getX() >> 4) + dx, (center.getZ() >> 4) + dz);
            }
        }
        // 陨坑：中间深 3 格，坑底是焦黑的碎石、玄武岩、粗泥，坑沿堆起一圈土
        List<BlockState> scorched = List.of(Blocks.BLACKSTONE.defaultBlockState(), Blocks.SMOOTH_BASALT.defaultBlockState(),
                Blocks.COARSE_DIRT.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(), Blocks.COAL_BLOCK.defaultBlockState());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS - 2; dx <= RADIUS + 2; dx++) {
            for (int dz = -RADIUS - 2; dz <= RADIUS + 2; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz) + random.nextDouble() * 0.6;
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (d < RADIUS) {
                    int depth = (int) Math.round(3 * (1 - d * d / (RADIUS * RADIUS)));
                    int floor = center.getY() - depth;
                    // 坑里清空到地表以上 4 格（砸断的树、草都清掉）
                    for (int y = floor + 1; y <= Math.max(top, center.getY()) + 4; y++) {
                        level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                    level.setBlock(pos.set(x, floor, z), scorched.get(random.nextInt(scorched.size())), 2);
                } else if (d < RADIUS + 1.6 && random.nextFloat() < 0.8F) {
                    level.setBlock(pos.set(x, top + 1, z), random.nextBoolean() ? Blocks.COARSE_DIRT.defaultBlockState()
                            : Blocks.GRAVEL.defaultBlockState(), 2);
                }
            }
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
        // 信号篝火：下面垫干草块，烟柱很高，远远就能看到
        BlockPos fire = new BlockPos(center.getX() - 2, floorY + 1, center.getZ() - 1);
        level.setBlock(fire.below(), Blocks.HAY_BLOCK.defaultBlockState(), 2);
        level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, true), 2);
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

    private static List<ItemStack> loot() {
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(UniverseItems.DAMAGED_MULTITOOL.get()));
        loot.add(journal());
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

    /** 调试命令：{@code /universe crash_site} 在脚下放一个，{@code /universe crash_site locate} 报告开局放的那个在哪。 */
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("crash_site")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    BlockPos ground = player.blockPosition().below();
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
