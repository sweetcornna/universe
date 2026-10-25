package io.github.sweetcornna.universe.perf;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.RecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.factory.belt.BeltBlock;
import io.github.sweetcornna.universe.factory.belt.BeltNetwork;
import io.github.sweetcornna.universe.factory.belt.SorterBlock;
import io.github.sweetcornna.universe.factory.power.FuelGeneratorBlockEntity;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * {@code /universe bench <belts> <machines>}：在玩家脚下搭一座测性能用的参照基地（ADR-0019），配合
 * {@code /universe perf} 用。
 *
 * <p>布局：每排 {@link #ROW} 台一型制造台做齿轮，前面一排分拣器从一圈环形传送带上取铁锭；电力感应塔和人造恒星
 * 架在制造台顶上。设备排数够了以后，剩下的传送带搭成空转的环线，上面放着铜锭一直转。区块全部强制加载。每秒补一次
 * 环线上被取走的铁锭、清空制造台的产物，这部分单独记作 bench，看报告时扣掉。{@code /universe bench stop} 停止补给并取消
 * 强制加载，方块留着。日志里的 {@code Bench:} 一行固定用中文，验收脚本靠它找结果。
 */
public final class BenchCommand {
    /** 一排多长。 */
    private static final int ROW = 50;
    private static final String GEAR_RECIPE = "universe:assembling/gear";
    private static final List<Bench> BENCHES = new ArrayList<>();

    private static final class Bench {
        final ServerLevel level;
        final List<BlockPos> machines;
        final List<BlockPos> feedBelts;
        final Set<ChunkPos> chunks;
        final long builtAt;
        /** 从搭好到现在一共清走了多少个产物。 */
        long produced;

        Bench(ServerLevel level, List<BlockPos> machines, List<BlockPos> feedBelts, Set<ChunkPos> chunks) {
            this.level = level;
            this.machines = machines;
            this.feedBelts = feedBelts;
            this.chunks = chunks;
            this.builtAt = level.getGameTime();
        }
    }

    private BenchCommand() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Post.class, event -> {
            if (!BENCHES.isEmpty() && event.getServer().getTickCount() % 20 == 0) {
                ServerPerf.time("bench", BenchCommand::feed);
            }
        });
        gameBus.addListener(ServerStoppedEvent.class, event -> BENCHES.clear());
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("bench")
                .then(Commands.literal("stop").executes(context -> stop(context.getSource())))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.argument("belts", IntegerArgumentType.integer(0, 50_000))
                        .then(Commands.argument("machines", IntegerArgumentType.integer(0, 5_000))
                                .executes(context -> build(context.getSource(), IntegerArgumentType.getInteger(context, "belts"),
                                        IntegerArgumentType.getInteger(context, "machines")))));
    }

    private static int build(CommandSourceStack source, int belts, int machines) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        BlockPos origin = source.getPlayerOrException().blockPosition().offset(2, 0, 2);
        int rows = (machines + ROW - 1) / ROW;
        int loopBelts = Math.max(0, belts - rows * 2 * ROW);
        int loops = (loopBelts + 2 * ROW - 1) / (2 * ROW);
        int depth = rows * 4 + loops * 3 + 1;
        int x0 = origin.getX();
        int x1 = x0 + ROW - 1;
        int y = origin.getY();
        int z0 = origin.getZ();

        Set<ChunkPos> chunks = new LinkedHashSet<>();
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= (z0 + depth) >> 4; cz++) {
                chunks.add(new ChunkPos(cx, cz));
                level.setChunkForced(cx, cz, true);
            }
        }
        // 平台，上面清空
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z0 + depth; z++) {
                level.setBlock(new BlockPos(x, y - 1, z), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int dy = 0; dy < 4; dy++) {
                    level.setBlock(new BlockPos(x, y + dy, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }

        List<BlockPos> machinePositions = new ArrayList<>();
        List<BlockPos> feedBelts = new ArrayList<>();
        int placedBelts = 0;
        int placed = 0;
        for (int row = 0; row < rows; row++) {
            int z = z0 + row * 4;
            placedBelts += loop(level, x0, x1, y, z, feedBelts);
            for (int x = x0; x <= x1 && placed < machines; x++, placed++) {
                level.setBlock(new BlockPos(x, y, z + 2), UniverseBlocks.SORTER.get().defaultBlockState().setValue(SorterBlock.FACING, Direction.SOUTH),
                        Block.UPDATE_CLIENTS);
                BlockPos machine = new BlockPos(x, y, z + 3);
                level.setBlock(machine, UniverseBlocks.ASSEMBLER.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                if (level.getBlockEntity(machine) instanceof RecipeMachineBlockEntity assembler) {
                    CompoundTag settings = new CompoundTag();
                    settings.putString("Recipe", GEAR_RECIPE);
                    assembler.applyBlueprintSettings(settings);
                }
                machinePositions.add(machine);
            }
        }
        // 电：感应塔隔一排、隔七格架一座（覆盖半径 6，排尾的设备也够得着），人造恒星按用电量放在两座塔中间，
        // 每台一型制造台 13、分拣器 1
        int stars = machines == 0 ? 0 : (machines * 14 + 2999) / 3000;
        for (int row = 0; row < rows; row += 2) {
            int z = z0 + row * 4 + 3;
            for (int x = x0 + 3; x <= x1; x += 7) {
                tall(level, new BlockPos(x, y + 1, z), UniverseBlocks.TESLA_TOWER.get().defaultBlockState());
            }
            for (int x = x0; x <= x1 && stars > 0; x += 7, stars--) {
                BlockPos star = new BlockPos(x, y + 1, z);
                tall(level, star, UniverseBlocks.ARTIFICIAL_STAR.get().defaultBlockState());
                if (level.getBlockEntity(star) instanceof FuelGeneratorBlockEntity generator) {
                    generator.setItem(FuelGeneratorBlockEntity.FUEL_SLOT, new ItemStack(UniverseItems.ANTIMATTER_FUEL_ROD.get(), 8));
                }
            }
        }
        // 空转的环线
        List<BlockPos> idleBelts = new ArrayList<>();
        for (int i = 0; i < loops; i++) {
            placedBelts += loop(level, x0, x1, y, z0 + rows * 4 + 1 + i * 3, idleBelts);
        }

        BeltNetwork network = BeltNetwork.of(level);
        int ingot = BeltNetwork.id(Items.IRON_INGOT);
        for (BlockPos pos : feedBelts) {
            network.insertAt(pos, BeltNetwork.HALF, ingot);
        }
        int copper = BeltNetwork.id(Items.COPPER_INGOT);
        for (BlockPos pos : idleBelts) {
            network.insertAt(pos, 0, copper);
            network.insertAt(pos, BeltNetwork.HALF, copper);
        }
        BENCHES.add(new Bench(level, machinePositions, feedBelts, chunks));

        int beltCount = placedBelts;
        String message = String.format("参照基地：传送带 %d 格，制造台 %d 台，分拣器 %d 个，强制加载 %d 个区块", beltCount, placed, placed, chunks.size());
        Universe.LOGGER.info("Bench: {}", message);
        int machineCount = placed;
        source.sendSuccess(() -> Component.translatable("commands.universe.bench.built", beltCount, machineCount, machineCount, chunks.size()), true);
        return placed;
    }

    /** 搭一圈两格宽的环线：南边一排朝东，北边一排朝西。返回放了几格，南边那排（分拣器取货的那排）记进 {@code feed}。 */
    private static int loop(ServerLevel level, int x0, int x1, int y, int zNorth, List<BlockPos> feed) {
        BlockState belt = UniverseBlocks.CONVEYOR_BELT.get().defaultBlockState();
        int zSouth = zNorth + 1;
        for (int x = x0; x <= x1; x++) {
            BlockPos south = new BlockPos(x, y, zSouth);
            level.setBlock(south, belt.setValue(BeltBlock.FACING, x == x1 ? Direction.NORTH : Direction.EAST), Block.UPDATE_CLIENTS);
            feed.add(south);
            level.setBlock(new BlockPos(x, y, zNorth), belt.setValue(BeltBlock.FACING, x == x0 ? Direction.SOUTH : Direction.WEST),
                    Block.UPDATE_CLIENTS);
        }
        return 2 * (x1 - x0 + 1);
    }

    private static void tall(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS);
        level.setBlock(pos.above(), state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
    }

    /** 每秒一次：补上被分拣器取走的铁锭，清掉制造台做好的齿轮。 */
    private static void feed() {
        int ingot = BeltNetwork.id(Items.IRON_INGOT);
        for (Bench bench : BENCHES) {
            BeltNetwork network = BeltNetwork.of(bench.level);
            for (BlockPos pos : bench.feedBelts) {
                if (network.canInsertAt(pos, BeltNetwork.HALF)) {
                    network.insertAt(pos, BeltNetwork.HALF, ingot);
                }
            }
            for (BlockPos pos : bench.machines) {
                BlockEntity entity = bench.level.getBlockEntity(pos);
                if (entity instanceof RecipeMachineBlockEntity machine) {
                    for (int slot = machine.spec().inputs(); slot < machine.getContainerSize(); slot++) {
                        bench.produced += machine.removeItemNoUpdate(slot).getCount();
                    }
                }
            }
        }
    }

    /** 各座参照基地：开工的制造台有几台，平均每分钟做出多少个。 */
    private static int status(CommandSourceStack source) {
        for (Bench bench : BENCHES) {
            int working = 0;
            for (BlockPos pos : bench.machines) {
                if (bench.level.getBlockEntity(pos) instanceof MachineBlockEntity machine && machine.working()) {
                    working++;
                }
            }
            double minutes = Math.max(1, bench.level.getGameTime() - bench.builtAt) / 1200.0;
            String message = String.format(Locale.ROOT, "参照基地：%d/%d 台在做，已做出 %d 个，每分钟 %.0f 个", working, bench.machines.size(),
                    bench.produced, bench.produced / minutes);
            Universe.LOGGER.info("Bench: {}", message);
            Component text = Component.translatable("commands.universe.bench.status", working, bench.machines.size(), bench.produced,
                    String.format(Locale.ROOT, "%.0f", bench.produced / minutes));
            source.sendSuccess(() -> text, false);
        }
        return BENCHES.size();
    }

    private static int stop(CommandSourceStack source) {
        int count = BENCHES.size();
        for (Bench bench : BENCHES) {
            for (ChunkPos chunk : bench.chunks) {
                bench.level.setChunkForced(chunk.x(), chunk.z(), false);
            }
        }
        BENCHES.clear();
        source.sendSuccess(() -> Component.translatable("commands.universe.bench.stopped", count), true);
        return count;
    }
}
