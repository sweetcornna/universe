package io.github.sweetcornna.universe.poi;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import io.github.sweetcornna.universe.core.poi.Poi;
import io.github.sweetcornna.universe.core.poi.PoiLayout;
import io.github.sweetcornna.universe.core.poi.PoiType;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * 调试命令 {@code /universe poi}（ADR-0014）：
 * {@code list [半径]} 列出附近的兴趣点；{@code build <种类>} 在脚下放一个；{@code locate <种类>} 不花导航数据设航点；
 * {@code tp <种类>} 传送到最近的一个。
 */
public final class PoiCommand {
    private static final SimpleCommandExceptionType NOT_A_PLANET =
            new SimpleCommandExceptionType(Component.translatable("commands.universe.poi.not_planet"));
    private static final DynamicCommandExceptionType UNKNOWN_TYPE =
            new DynamicCommandExceptionType(name -> Component.translatable("commands.universe.poi.unknown_type", String.valueOf(name)));
    private static final SuggestionProvider<CommandSourceStack> TYPES = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(PoiType.values()).map(PoiType::serializedName), builder);

    private PoiCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("poi")
                .then(Commands.literal("list")
                        .executes(context -> list(context, 600))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(16, 4000))
                                .executes(context -> list(context, IntegerArgumentType.getInteger(context, "radius")))))
                .then(Commands.literal("build")
                        .then(Commands.argument("type", StringArgumentType.word()).suggests(TYPES)
                                .executes(PoiCommand::buildHere)))
                .then(Commands.literal("locate")
                        .then(Commands.argument("type", StringArgumentType.word()).suggests(TYPES)
                                .executes(PoiCommand::locate)))
                .then(Commands.literal("tp")
                        .then(Commands.argument("type", StringArgumentType.word()).suggests(TYPES)
                                .executes(PoiCommand::teleport)));
    }

    private static PoiType type(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "type");
        try {
            return PoiType.byName(name);
        } catch (IllegalArgumentException e) {
            throw UNKNOWN_TYPE.create(name);
        }
    }

    private static PoiLayout layout(ServerLevel level) throws CommandSyntaxException {
        return Pois.layout(level).orElseThrow(NOT_A_PLANET::create);
    }

    private static int list(CommandContext<CommandSourceStack> context, int radius) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PoiLayout layout = layout(player.level());
        PoiProgress progress = player.getData(UniverseAttachments.POI_PROGRESS);
        List<Poi> found = layout.within(player.getX(), player.getZ(), radius);
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.poi.list", radius, found.size()), false);
        for (Poi poi : found) {
            Component line = Component.literal("  ").append(Component.translatable(
                    progress.visited(poi.key()) ? "commands.universe.poi.list.visited" : "commands.universe.poi.list.entry",
                    Pois.name(poi.type()), poi.x(), layout.estimatedY(poi), poi.z(), Math.round(poi.distance(player.getX(), player.getZ()))));
            context.getSource().sendSuccess(() -> line, false);
        }
        return found.size();
    }

    private static int buildHere(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PoiType type = type(context);
        BlockPos feet = player.blockPosition();
        long seed = player.getRandom().nextLong();
        Poi poi = new Poi(type, feet.getX(), feet.getZ(), 0, 0, seed, seed, Math.floorMod(Math.round(player.getYRot() / 90F), 4));
        boolean built = PoiStructures.build(player.level(), poi);
        context.getSource().sendSuccess(() -> built ? Component.translatable("commands.universe.poi.built", Pois.name(type))
                : Component.translatable("commands.universe.poi.build.failed"), false);
        return built ? 1 : 0;
    }

    private static int locate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PoiType type = type(context);
        PoiLayout layout = layout(player.level());
        Optional<Poi> found = layout.nearest(player.getX(), player.getZ(), Pois.LOCATE_RADIUS, poi -> poi.type() == type);
        if (found.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.universe.poi.not_found", Pois.name(type), Pois.LOCATE_RADIUS));
            return 0;
        }
        Poi poi = found.get();
        BlockPos target = new BlockPos(poi.x(), Pois.groundY(player.level(), layout, poi), poi.z());
        player.setData(UniverseAttachments.WAYPOINT, new Waypoint(player.level().dimension(), target, type.serializedName()));
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.poi.waypoint", Pois.name(type),
                target.getX(), target.getY(), target.getZ()), false);
        return 1;
    }

    private static int teleport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PoiType type = type(context);
        ServerLevel level = player.level();
        PoiLayout layout = layout(level);
        Optional<Poi> found = layout.nearest(player.getX(), player.getZ(), 6000, poi -> poi.type() == type);
        if (found.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.universe.poi.not_found", Pois.name(type), 6000));
            return 0;
        }
        Poi poi = found.get();
        // 站在兴趣点前方（本地 -z）十几格的地上，看得见整个建筑
        int[][] fronts = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        int[] front = fronts[poi.rotation()];
        int distance = poi.type().radius() + 6;
        int x = poi.x() + front[0] * distance;
        int z = poi.z() + front[1] * distance;
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        float yaw = (float) Math.toDegrees(Math.atan2(-(poi.x() - x), poi.z() - z));
        player.teleport(new TeleportTransition(level, new Vec3(x + 0.5, y, z + 0.5), Vec3.ZERO, yaw, 10F,
                TeleportTransition.PLACE_PORTAL_TICKET));
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.poi.tp", Pois.name(type), x, y, z), false);
        return 1;
    }
}
