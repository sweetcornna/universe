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
    private static final SimpleCommandExceptionType NO_STAR = new SimpleCommandExceptionType(Component.literal("这里没有恒星"));
    private static final SimpleCommandExceptionType FAILED = new SimpleCommandExceptionType(Component.literal("没有改成：编号不存在或超出上限"));

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
        StringBuilder text = new StringBuilder(String.format(Locale.ROOT,
                "恒星系 %d：光照系数 %.2f，半径上限 %.3f AU，能建纬度 ±%.0f°，发电 %.1f 焦/刻（戴森云 %.1f，戴森壳 %.1f），接收站取走 %.1f，在飞火箭 %d",
                system, sphere.lightFactor(), sphere.maxRadius(), limit, sphere.generation(), sphere.swarmPower(), sphere.shellPower(),
                sphere.lastDrawn(), sphere.rockets().size()));
        for (SwarmOrbit orbit : sphere.orbits()) {
            text.append(String.format(Locale.ROOT, "\n  轨道 %d：半径 %.3f，倾角 %.0f°，升交点 %.0f°，%d 张帆", orbit.id(), orbit.radius(),
                    orbit.inclination(), orbit.ascendingNode(), orbit.sails()));
        }
        for (DysonLayer layer : sphere.layers()) {
            text.append(String.format(Locale.ROOT, "\n  第 %d 层：半径 %.3f，%s×%d，结构点 %d/%d，单元点 %d/%d", layer.id(), layer.radius(),
                    layer.grid().template().name().toLowerCase(Locale.ROOT), layer.grid().density(), layer.structurePoints(),
                    layer.structureCapacity(limit), layer.cellPoints(), layer.cellCapacity(limit)));
        }
        context.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
        return 1;
    }

    private static int addOrbit(CommandContext<CommandSourceStack> context, double inclination, double node) throws CommandSyntaxException {
        int id = sphere(context).addOrbit(DoubleArgumentType.getDouble(context, "radius"), inclination, node);
        if (id <= 0) {
            throw FAILED.create();
        }
        changed(context);
        context.getSource().sendSuccess(() -> Component.literal("新建了戴森云轨道 " + id), true);
        return id;
    }

    private static int addLayer(CommandContext<CommandSourceStack> context, int template, int density) throws CommandSyntaxException {
        DysonGrid.Template kind = DysonGrid.Template.byOrdinal(template);
        int id = sphere(context).addLayer(DoubleArgumentType.getDouble(context, "radius"), 0, 0, kind, Math.min(density, kind.maxDensity()));
        if (id <= 0) {
            throw FAILED.create();
        }
        changed(context);
        context.getSource().sendSuccess(() -> Component.literal("新建了戴森壳第 " + id + " 层"), true);
        return id;
    }

    private static int complete(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int layer = IntegerArgumentType.getInteger(context, "layer");
        if (!sphere(context).complete(layer, Dyson.latitudeLimit(context.getSource().getServer()))) {
            throw FAILED.create();
        }
        changed(context);
        context.getSource().sendSuccess(() -> Component.literal("第 " + layer + " 层能建的部分已经建满"), true);
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
        context.getSource().sendSuccess(() -> Component.literal("往轨道 " + orbit + " 加了 " + count + " 张帆"), true);
        return count;
    }

    private static int clear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        DysonSphere sphere = sphere(context);
        sphere.orbits().stream().map(SwarmOrbit::id).toList().forEach(sphere::removeOrbit);
        sphere.layers().stream().map(DysonLayer::id).toList().forEach(sphere::removeLayer);
        changed(context);
        context.getSource().sendSuccess(() -> Component.literal("戴森球已清空"), true);
        return 1;
    }
}
