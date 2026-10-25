package io.github.sweetcornna.universe.dyson;

import java.util.Locale;
import java.util.OptionalInt;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import io.github.sweetcornna.universe.core.dyson.DysonGrid;
import io.github.sweetcornna.universe.core.dyson.DysonLayer;
import io.github.sweetcornna.universe.core.dyson.DysonSphere;
import io.github.sweetcornna.universe.core.dyson.SwarmOrbit;
import io.github.sweetcornna.universe.core.research.Upgrades;
import io.github.sweetcornna.universe.factory.research.Research;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

/**
 * 调试戴森球的命令（{@code /universe dyson ...}，ADR-0016），作用于执行者所在的恒星系：
 *
 * <ul>
 * <li>{@code info}：轨道、层、建造进度和发电
 * <li>{@code orbit add <半径> [倾角] [升交点]}、{@code layer add <半径> [网格 0-2] [密度]}：新建，和编辑器一样受上限约束
 * <li>{@code layer complete <层>}：把这一层能建的部分直接建满
 * <li>{@code sails <轨道> <数量>}：往戴森云轨道里加刚发射的帆
 * <li>{@code clear}：删掉全部轨道和层
 * </ul>
 */
public final class DysonCommand {
    private static final SimpleCommandExceptionType NO_STAR = new SimpleCommandExceptionType(Component.translatable("commands.universe.dyson.no_star"));
    private static final SimpleCommandExceptionType FAILED = new SimpleCommandExceptionType(Component.translatable("commands.universe.dyson.failed"));

    private DysonCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("dyson")
                .executes(DysonCommand::info)
                .then(Commands.literal("info")
                        .executes(DysonCommand::info))
                .then(Commands.literal("orbit")
                        .then(Commands.literal("add")
                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0))
                                        .executes(context -> addOrbit(context, 0, 0))
                                        .then(Commands.argument("inclination", DoubleArgumentType.doubleArg(0, 90))
                                                .executes(context -> addOrbit(context, DoubleArgumentType.getDouble(context, "inclination"), 0))
                                                .then(Commands.argument("node", DoubleArgumentType.doubleArg(-360, 360))
                                                        .executes(context -> addOrbit(context, DoubleArgumentType.getDouble(context, "inclination"),
                                                                DoubleArgumentType.getDouble(context, "node"))))))))
                .then(Commands.literal("layer")
                        .then(Commands.literal("add")
                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0))
                                        .executes(context -> addLayer(context, 0, 1))
                                        .then(Commands.argument("template", IntegerArgumentType.integer(0, DysonGrid.Template.values().length - 1))
                                                .executes(context -> addLayer(context, IntegerArgumentType.getInteger(context, "template"), 1))
                                                .then(Commands.argument("density", IntegerArgumentType.integer(1, 8))
                                                        .executes(context -> addLayer(context, IntegerArgumentType.getInteger(context, "template"),
                                                                IntegerArgumentType.getInteger(context, "density")))))))
                        .then(Commands.literal("complete")
                                .then(Commands.argument("layer", IntegerArgumentType.integer(1))
                                        .executes(DysonCommand::complete))))
                .then(Commands.literal("sails")
                        .then(Commands.argument("orbit", IntegerArgumentType.integer(1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 10_000_000))
                                        .executes(DysonCommand::sails))))
                .then(Commands.literal("clear")
                        .executes(DysonCommand::clear));
    }

    private static int system(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        OptionalInt system = Dyson.systemOf(context.getSource().getLevel().dimension());
        if (system.isEmpty()) {
            throw NO_STAR.create();
        }
        return system.getAsInt();
    }

    private static DysonSphere sphere(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return Dyson.sphere(context.getSource().getServer(), system(context));
    }

    /** 改完存盘，马上同步给玩家。 */
    private static void changed(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        DysonData.get(server).setDirty();
        Dyson.sync(server, server.overworld().getGameTime(), true);
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int system = system(context);
        DysonSphere sphere = Dyson.sphere(context.getSource().getServer(), system);
        double limit = Dyson.latitudeLimit(context.getSource().getServer());
        MutableComponent text = Component.translatable("commands.universe.dyson.info",
                system, number(sphere.lightFactor(), 2), number(sphere.maxRadius(), 3), number(limit, 0), number(sphere.generation(), 1),
                number(sphere.swarmPower(), 1), number(sphere.shellPower(), 1), number(sphere.lastDrawn(), 1), sphere.rockets().size());
        for (SwarmOrbit orbit : sphere.orbits()) {
            text.append("\n  ").append(Component.translatable("commands.universe.dyson.info.orbit", orbit.id(), number(orbit.radius(), 3),
                    number(orbit.inclination(), 0), number(orbit.ascendingNode(), 0), orbit.sails()));
        }
        for (DysonLayer layer : sphere.layers()) {
            text.append("\n  ").append(Component.translatable("commands.universe.dyson.info.layer", layer.id(), number(layer.radius(), 3),
                    Component.translatable("gui.universe.dyson.template." + layer.grid().template().ordinal()), layer.grid().density(), layer.structurePoints(),
                    layer.structureCapacity(limit), layer.cellPoints(), layer.cellCapacity(limit)));
        }
        context.getSource().sendSuccess(() -> text, false);
        return 1;
    }

    private static int addOrbit(CommandContext<CommandSourceStack> context, double inclination, double node) throws CommandSyntaxException {
        int id = sphere(context).addOrbit(DoubleArgumentType.getDouble(context, "radius"), inclination, node);
        if (id <= 0) {
            throw FAILED.create();
        }
        changed(context);
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.dyson.orbit_added", id), true);
        return id;
    }

    private static int addLayer(CommandContext<CommandSourceStack> context, int template, int density) throws CommandSyntaxException {
        DysonGrid.Template kind = DysonGrid.Template.byOrdinal(template);
        int id = sphere(context).addLayer(DoubleArgumentType.getDouble(context, "radius"), 0, 0, kind, Math.min(density, kind.maxDensity()));
        if (id <= 0) {
            throw FAILED.create();
        }
        changed(context);
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.dyson.layer_added", id), true);
        return id;
    }

    private static int complete(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int layer = IntegerArgumentType.getInteger(context, "layer");
        if (!sphere(context).complete(layer, Dyson.latitudeLimit(context.getSource().getServer()))) {
            throw FAILED.create();
        }
        changed(context);
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.dyson.completed", layer), true);
        return 1;
    }

    private static int sails(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int orbit = IntegerArgumentType.getInteger(context, "orbit");
        int count = IntegerArgumentType.getInteger(context, "count");
        MinecraftServer server = context.getSource().getServer();
        long lifetime = Upgrades.sailLifetime(Research.level(server.overworld(), Upgrades.SAIL_LIFETIME));
        if (!sphere(context).addSails(orbit, count, server.overworld().getGameTime(), lifetime)) {
            throw FAILED.create();
        }
        changed(context);
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.dyson.sails", orbit, count), true);
        return count;
    }

    private static int clear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        DysonSphere sphere = sphere(context);
        sphere.orbits().stream().map(SwarmOrbit::id).toList().forEach(sphere::removeOrbit);
        sphere.layers().stream().map(DysonLayer::id).toList().forEach(sphere::removeLayer);
        changed(context);
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.dyson.cleared"), true);
        return 1;
    }

    private static String number(double value, int digits) {
        return String.format(Locale.ROOT, "%." + digits + "f", value);
    }
}
