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
import io.github.sweetcornna.universe.bio.BioRecipes;
import io.github.sweetcornna.universe.bio.BioreactorBlockEntity;
import io.github.sweetcornna.universe.chem.reactor.ChemicalReactorBlockEntity;
import io.github.sweetcornna.universe.chem.reactor.ReactorRecipes;
import io.github.sweetcornna.universe.core.chem.reaction.ItemCount;
import io.github.sweetcornna.universe.core.chem.reaction.Reaction;
import io.github.sweetcornna.universe.core.microbe.EarthMicrobes;
import io.github.sweetcornna.universe.core.microbe.Organism;
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
 * {@code /universe bench <belts> <machines> [chemistry]}：在玩家脚下搭一座测性能用的参照基地（ADR-0019），配合
 * {@code /universe perf} 用。
 *
 * <p>布局：每排 {@link #ROW} 台一型制造台做齿轮，前面一排分拣器从一圈环形传送带上取铁锭；电力感应塔和人造恒星
 * 架在制造台顶上。设备排数够了以后，剩下的传送带搭成空转的环线，上面放着铜锭一直转。区块全部强制加载。每秒补一次
 * 环线上被取走的铁锭、清空制造台的产物，这部分单独记作 bench，看报告时扣掉。{@code /universe bench stop} 停止补给并取消
 * 强制加载，方块留着。日志里的 {@code Bench:} 一行固定用中文，验收脚本靠它找结果。
 *
 * <p>{@code chemistry} 是化工设备的台数（ADR-0038）：化学反应釜（合成氨、铁催化剂）和生物反应器（产甲烷菌做甲烷）一台隔一台
 * 排在环线后面，每排前面一行人造恒星和感应塔。每秒把原料补满、产物清空。反应釜标成参照基地用的，合成氨没发现也照做，
 * 不往存档里记发现。
 */
public final class BenchCommand {
    /** 一排多长。 */
    private static final int ROW = 50;
    private static final String GEAR_RECIPE = "universe:assembling/gear";
    private static final List<Bench> BENCHES = new ArrayList<>();

    static final class Bench {
        final ServerLevel level;
        final List<BlockPos> machines;
        final List<BlockPos> chemistry;
        final List<BlockPos> feedBelts;
        final Set<ChunkPos> chunks;
        /** 一共放了几格传送带。 */
        final int belts;
        final long builtAt;
        /** 从搭好到现在一共清走了多少个产物。 */
        long produced;

        Bench(ServerLevel level, List<BlockPos> machines, List<BlockPos> chemistry, List<BlockPos> feedBelts, Set<ChunkPos> chunks, int belts) {
            this.level = level;
            this.machines = machines;
            this.chemistry = chemistry;
            this.feedBelts = feedBelts;
            this.chunks = chunks;
            this.belts = belts;
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
                                        IntegerArgumentType.getInteger(context, "machines"), 0))
                                .then(Commands.argument("chemistry", IntegerArgumentType.integer(0, 5_000))
                                        .executes(context -> build(context.getSource(), IntegerArgumentType.getInteger(context, "belts"),
                                                IntegerArgumentType.getInteger(context, "machines"), IntegerArgumentType.getInteger(context, "chemistry"))))));
    }

    private static int build(CommandSourceStack source, int belts, int machines, int chemistry) throws CommandSyntaxException {
        BlockPos origin = source.getPlayerOrException().blockPosition().offset(2, 0, 2);
        Bench bench = build(source.getLevel(), origin, belts, machines, chemistry);
        int beltCount = bench.belts;
        int machineCount = bench.machines.size();
        int chemistryCount = bench.chemistry.size();
        int chunkCount = bench.chunks.size();
        source.sendSuccess(() -> chemistryCount == 0
                ? Component.translatable("commands.universe.bench.built", beltCount, machineCount, machineCount, chunkCount)
                : Component.translatable("commands.universe.bench.built_chemistry", beltCount, machineCount, machineCount, chemistryCount, chunkCount),
                true);
        return machineCount;
    }

    /** 以 {@code origin}（平台上面一格的西北角）搭一座参照基地并开始补给。游戏内的性能测量也用它。 */
    static Bench build(ServerLevel level, BlockPos origin, int belts, int machines, int chemistry) {
        int rows = (machines + ROW - 1) / ROW;
        int loopBelts = Math.max(0, belts - rows * 2 * ROW);
        int loops = (loopBelts + 2 * ROW - 1) / (2 * ROW);
        int chemistryRows = (chemistry + ROW - 1) / ROW;
        int depth = rows * 4 + loops * 3 + chemistryRows * 3 + 1;
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
                star(level, new BlockPos(x, y + 1, z));
            }
        }
        // 空转的环线
        List<BlockPos> idleBelts = new ArrayList<>();
        for (int i = 0; i < loops; i++) {
            placedBelts += loop(level, x0, x1, y, z0 + rows * 4 + 1 + i * 3, idleBelts);
        }
        // 化工：每排前面一行人造恒星和感应塔（隔七格一座恒星，中间一座塔），后面一行设备
        List<BlockPos> chemistryPositions = new ArrayList<>();
        int chemistryZ = z0 + rows * 4 + 1 + loops * 3;
        for (int row = 0; row < chemistryRows; row++) {
            int z = chemistryZ + row * 3;
            for (int x = x0; x <= x1; x += 7) {
                star(level, new BlockPos(x, y, z));
                if (x + 3 <= x1) {
                    tall(level, new BlockPos(x + 3, y, z), UniverseBlocks.TESLA_TOWER.get().defaultBlockState());
                }
            }
            for (int x = x0; x <= x1 && chemistryPositions.size() < chemistry; x++) {
                BlockPos pos = new BlockPos(x, y, z + 1);
                if (chemistryPositions.size() % 2 == 0) {
                    level.setBlock(pos, UniverseBlocks.CHEMICAL_REACTOR.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                    if (level.getBlockEntity(pos) instanceof ChemicalReactorBlockEntity reactor) {
                        reactor.setBenchmark(true);
                        reactor.setReaction(Reaction.HABER);
                        reactor.setItem(ChemicalReactorBlockEntity.CATALYST_SLOT,
                                new ItemStack(ReactorRecipes.item(Reaction.HABER.catalyst().item()), 64));
                    }
                } else {
                    level.setBlock(pos, UniverseBlocks.BIOREACTOR.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                    if (level.getBlockEntity(pos) instanceof BioreactorBlockEntity reactor) {
                        CompoundTag settings = new CompoundTag();
                        settings.putString("Recipe", "universe:" + BioRecipes.DIR + "/biomethanation");
                        reactor.applyBlueprintSettings(settings);
                        reactor.inoculate(Organism.wild(EarthMicrobes.METHANOSARCINA));
                        reactor.setDensity(1);
                    }
                }
                chemistryPositions.add(pos);
            }
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
        Bench bench = new Bench(level, machinePositions, chemistryPositions, feedBelts, chunks, placedBelts);
        feedChemistry(bench);
        BENCHES.add(bench);

        String message = String.format("参照基地：传送带 %d 格，制造台 %d 台，分拣器 %d 个，化工设备 %d 台，强制加载 %d 个区块", placedBelts, placed, placed,
                chemistryPositions.size(), chunks.size());
        Universe.LOGGER.info("Bench: {}", message);
        return bench;
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

    /** 一座装了反物质燃料棒的人造恒星。 */
    private static void star(ServerLevel level, BlockPos pos) {
        tall(level, pos, UniverseBlocks.ARTIFICIAL_STAR.get().defaultBlockState());
        if (level.getBlockEntity(pos) instanceof FuelGeneratorBlockEntity generator) {
            generator.setItem(FuelGeneratorBlockEntity.FUEL_SLOT, new ItemStack(UniverseItems.ANTIMATTER_FUEL_ROD.get(), 8));
        }
    }

    /** 每秒一次：补上被分拣器取走的铁锭，清掉制造台做好的齿轮，化工设备补料清货。 */
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
            feedChemistry(bench);
        }
    }

    /** 化工设备：原料补满，产物清掉记进 {@link Bench#produced}。 */
    private static void feedChemistry(Bench bench) {
        List<ItemCount> haber = Reaction.HABER.inputs();
        for (BlockPos pos : bench.chemistry) {
            BlockEntity entity = bench.level.getBlockEntity(pos);
            if (entity instanceof ChemicalReactorBlockEntity reactor) {
                for (int i = 0; i < haber.size(); i++) {
                    reactor.setItem(i, new ItemStack(ReactorRecipes.item(haber.get(i)), 64));
                }
                for (int slot = ChemicalReactorBlockEntity.INPUTS; slot < ChemicalReactorBlockEntity.CATALYST_SLOT; slot++) {
                    bench.produced += reactor.removeItemNoUpdate(slot).getCount();
                }
            } else if (entity instanceof BioreactorBlockEntity reactor) {
                reactor.setItem(0, new ItemStack(UniverseItems.CARBON_DIOXIDE.get(), 64));
                reactor.setItem(1, new ItemStack(UniverseItems.HYDROGEN.get(), 64));
                for (int slot = BioreactorBlockEntity.INPUTS; slot < BioreactorBlockEntity.STRAIN_SLOT; slot++) {
                    bench.produced += reactor.removeItemNoUpdate(slot).getCount();
                }
            }
        }
    }

    /** 各座参照基地：开工的设备有几台，平均每分钟做出多少个。 */
    private static int status(CommandSourceStack source) {
        for (Bench bench : BENCHES) {
            int working = working(bench);
            int total = bench.machines.size() + bench.chemistry.size();
            double minutes = Math.max(1, bench.level.getGameTime() - bench.builtAt) / 1200.0;
            String message = String.format(Locale.ROOT, "参照基地：%d/%d 台在做，已做出 %d 个，每分钟 %.0f 个", working, total, bench.produced,
                    bench.produced / minutes);
            Universe.LOGGER.info("Bench: {}", message);
            Component text = Component.translatable("commands.universe.bench.status", working, total, bench.produced,
                    String.format(Locale.ROOT, "%.0f", bench.produced / minutes));
            source.sendSuccess(() -> text, false);
        }
        return BENCHES.size();
    }

    /** 这座参照基地里正在干活的设备台数（制造台和化工设备）。 */
    static int working(Bench bench) {
        int working = 0;
        for (List<BlockPos> list : List.of(bench.machines, bench.chemistry)) {
            for (BlockPos pos : list) {
                if (bench.level.getBlockEntity(pos) instanceof MachineBlockEntity machine && machine.working()) {
                    working++;
                }
            }
        }
        return working;
    }

    private static int stop(CommandSourceStack source) {
        int count = BENCHES.size();
        BENCHES.forEach(BenchCommand::release);
        BENCHES.clear();
        source.sendSuccess(() -> Component.translatable("commands.universe.bench.stopped", count), true);
        return count;
    }

    /** 停掉一座参照基地的补给，取消强制加载。 */
    static void stop(Bench bench) {
        release(bench);
        BENCHES.remove(bench);
    }

    private static void release(Bench bench) {
        for (ChunkPos chunk : bench.chunks) {
            bench.level.setChunkForced(chunk.x(), chunk.z(), false);
        }
    }
}
