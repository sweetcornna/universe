package io.github.sweetcornna.universe.world.planet;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.ResourcePlantBlock;
import io.github.sweetcornna.universe.core.ecology.ResourcePlant;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 资源植物的调试命令（ADR-0026），验收和排查用：
 * <ul>
 * <li>{@code /universe resources scan [半径]}：数一数周围已加载区块里各种资源植物有多少株
 * <li>{@code /universe resources goto <植物> [半径]}：走到最近的一株旁边，面朝它
 * </ul>
 */
public final class ResourcesCommand {
    private static final int RADIUS = 48;
    private static final SuggestionProvider<CommandSourceStack> PLANTS = (context, builder) -> SharedSuggestionProvider.suggest(
            Arrays.stream(ResourcePlant.values()).map(ResourcePlant::id), builder);
    private static final DynamicCommandExceptionType UNKNOWN =
            new DynamicCommandExceptionType(id -> Component.translatable("commands.universe.resources.unknown", String.valueOf(id)));

    private ResourcesCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Universe.MOD_ID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("resources")
                        .then(Commands.literal("scan")
                                .executes(context -> scan(context.getSource(), RADIUS))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                                        .executes(context -> scan(context.getSource(), IntegerArgumentType.getInteger(context, "radius")))))
                        .then(Commands.literal("goto")
                                .then(Commands.argument("plant", StringArgumentType.word()).suggests(PLANTS)
                                        .executes(context -> go(context.getSource(), StringArgumentType.getString(context, "plant"), RADIUS))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                                                .executes(context -> go(context.getSource(), StringArgumentType.getString(context, "plant"),
                                                        IntegerArgumentType.getInteger(context, "radius"))))))));
    }

    private static int scan(CommandSourceStack source, int radius) {
        ServerLevel level = source.getLevel();
        BlockPos center = BlockPos.containing(source.getPosition());
        Map<ResourcePlant, Integer> counts = new EnumMap<>(ResourcePlant.class);
        int[] ripe = {0};
        forEachPlant(level, center, radius, (pos, block, isRipe) -> {
            counts.merge(block.plant(), 1, Integer::sum);
            if (isRipe) {
                ripe[0]++;
            }
            return false;
        });
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        String list = counts.entrySet().stream().map(e -> e.getKey().id() + " " + e.getValue()).collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.translatable("commands.universe.resources.scan", radius, total, ripe[0], list.isEmpty() ? "-" : list),
                false);
        return total;
    }

    private static int go(CommandSourceStack source, String id, int radius) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ResourcePlant plant = Arrays.stream(ResourcePlant.values()).filter(p -> p.id().equals(id.toLowerCase(Locale.ROOT)))
                .findFirst().orElseThrow(() -> UNKNOWN.create(id));
        ServerLevel level = player.level();
        BlockPos center = player.blockPosition();
        BlockPos[] best = {null};
        double[] bestDistance = {Double.MAX_VALUE};
        forEachPlant(level, center, radius, (pos, block, isRipe) -> {
            double d = pos.distSqr(center);
            if (block.plant() == plant && d < bestDistance[0]) {
                bestDistance[0] = d;
                best[0] = pos.immutable();
            }
            return false;
        });
        BlockPos found = best[0];
        if (found == null) {
            source.sendFailure(Component.translatable("commands.universe.resources.not_found", radius, id));
            return 0;
        }
        BlockPos stand = stand(level, found);
        double dx = found.getX() - stand.getX();
        double dz = found.getZ() - stand.getZ();
        double dy = found.getY() + 0.3 - (stand.getY() + player.getEyeHeight());
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.teleportTo(level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, Set.of(), yaw, pitch, true);
        source.sendSuccess(() -> Component.translatable("commands.universe.resources.goto", id, found.toShortString()), false);
        return 1;
    }

    /** 植物旁边两格、能站人的地方；找不到就站在植物上。 */
    private static BlockPos stand(ServerLevel level, BlockPos plant) {
        for (int[] offset : new int[][] {{0, -2}, {2, 0}, {0, 2}, {-2, 0}, {1, -1}, {1, 1}, {-1, 1}, {-1, -1}}) {
            for (int dy = 1; dy >= -2; dy--) {
                BlockPos feet = plant.offset(offset[0], dy, offset[1]);
                if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
                        && level.getBlockState(feet.below()).isSolid()) {
                    return feet;
                }
            }
        }
        return plant;
    }

    private interface Visitor {
        /** 返回 true 就停下。 */
        boolean visit(BlockPos pos, ResourcePlantBlock block, boolean ripe);
    }

    /** 已加载区块里每一列从地表往下找资源植物（洞里的髓球也算）。 */
    private static void forEachPlant(ServerLevel level, BlockPos center, int radius, Visitor visitor) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                for (int y = top; y >= level.getMinY(); y--) {
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).getBlock() instanceof ResourcePlantBlock block
                            && visitor.visit(pos, block, level.getBlockState(pos).getValue(ResourcePlantBlock.RIPE))) {
                        return;
                    }
                }
            }
        }
    }
}
