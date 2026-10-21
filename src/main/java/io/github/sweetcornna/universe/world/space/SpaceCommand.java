package io.github.sweetcornna.universe.world.space;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.sky.PlanetAppearance;
import io.github.sweetcornna.universe.core.space.SpaceLayout;
import io.github.sweetcornna.universe.entity.ship.ShipTransit;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * 太空的调试命令（ADR-0021）：
 * <ul>
 * <li>{@code /universe space bodies <恒星系>}：列出每个天体在太空里的位置、地表半径和大气球半径，还有画法、两种主色（线性）和曝光
 * <li>{@code /universe space view <天体> <距离> [仰角] [方位] [偏航偏移] [俯仰]}：传送到离天体中心若干个地表半径的地方，
 *     默认面朝它；仰角是离黄道面的角度，方位绕 y 轴转。偏移用来看地平线、看恒星，截图对照 NASA 的照片时用
 * <li>{@code /universe space rings}：列出能登陆、带环的岩石行星，验收"站在地上看环"时找地方用
 * </ul>
 */
public final class SpaceCommand {
    private static final DynamicCommandExceptionType UNKNOWN_BODY =
            new DynamicCommandExceptionType(id -> Component.translatable("commands.universe.space.unknown", String.valueOf(id)));

    private SpaceCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var pitch = Commands.argument("pitch", FloatArgumentType.floatArg(-90, 90))
                .executes(context -> view(context, FloatArgumentType.getFloat(context, "elevation"), FloatArgumentType.getFloat(context, "azimuth"),
                        FloatArgumentType.getFloat(context, "yaw"), FloatArgumentType.getFloat(context, "pitch")));
        var azimuth = Commands.argument("azimuth", FloatArgumentType.floatArg())
                .executes(context -> view(context, FloatArgumentType.getFloat(context, "elevation"), FloatArgumentType.getFloat(context, "azimuth"), 0, 0))
                .then(Commands.argument("yaw", FloatArgumentType.floatArg()).then(pitch));
        var elevation = Commands.argument("elevation", FloatArgumentType.floatArg(-90, 90))
                .executes(context -> view(context, FloatArgumentType.getFloat(context, "elevation"), 0, 0, 0))
                .then(azimuth);
        var distance = Commands.argument("distance", DoubleArgumentType.doubleArg(1.0001))
                .executes(context -> view(context, 20, 0, 0, 0))
                .then(elevation);
        dispatcher.register(Commands.literal(Universe.MOD_ID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("space")
                        .then(Commands.literal("bodies")
                                .then(Commands.argument("system", IntegerArgumentType.integer(0))
                                        .executes(context -> bodies(context.getSource(), IntegerArgumentType.getInteger(context, "system")))))
                        .then(Commands.literal("rings")
                                .executes(context -> rings(context.getSource())))
                        .then(Commands.literal("view")
                                .then(Commands.argument("body", StringArgumentType.string()).then(distance)))));
    }

    private static int bodies(CommandSourceStack source, int system) {
        List<SpaceLayout.Body> layout = ShipTransit.layout(source.getServer(), system);
        StringBuilder text = new StringBuilder();
        for (SpaceLayout.Body body : layout) {
            PlanetAppearance look = PlanetAppearance.of(body.planet());
            text.append(String.format(Locale.ROOT, "%s %s (%.0f, %.0f, %.0f) r=%.0f/%.0f %s a=%s b=%s exp=%.2f T=%.0f%n", body.id().path(),
                    body.planet().type().serializedName(), body.x(), body.y(), body.z(), body.surface(), body.radius(),
                    look.style(), rgb(look.a()), rgb(look.b()), look.exposure(), body.planet().temperature()));
        }
        String result = text.toString().trim();
        source.sendSuccess(() -> Component.literal(result), false);
        return layout.size();
    }

    private static int rings(CommandSourceStack source) {
        StringBuilder text = new StringBuilder();
        int count = 0;
        for (StarSystem system : Planets.galaxy(source.getServer()).systems()) {
            for (Planet planet : system.bodies()) {
                if (planet.rings() && planet.landable() && planet.type() != PlanetType.GAS_GIANT) {
                    text.append(String.format(Locale.ROOT, "%s %s %s%n", planet.id().path(), planet.type().serializedName(), planet.name().en()));
                    count++;
                }
            }
        }
        String result = text.toString().trim();
        source.sendSuccess(() -> Component.literal(result), false);
        return count;
    }

    private static String rgb(double[] linear) {
        return String.format(Locale.ROOT, "%.2f/%.2f/%.2f", linear[0], linear[1], linear[2]);
    }

    private static int view(CommandContext<CommandSourceStack> context, float elevation, float azimuth, float yawOffset, float pitch)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String path = StringArgumentType.getString(context, "body");
        Optional<BodyId> id = BodyId.parse(path);
        if (id.isEmpty()) {
            throw UNKNOWN_BODY.create(path);
        }
        Optional<SpaceLayout.Body> found = SpaceLayout.find(ShipTransit.layout(source.getServer(), id.get().system()), id.get());
        if (found.isEmpty()) {
            throw UNKNOWN_BODY.create(path);
        }
        SpaceLayout.Body body = found.get();
        double distance = DoubleArgumentType.getDouble(context, "distance") * body.surface();
        double el = Math.toRadians(elevation);
        double az = Math.toRadians(azimuth);
        // 方位 0 在星球朝恒星的一侧，看到的是亮面
        double toStar = Math.atan2(-body.z(), -body.x());
        double x = body.x() + Math.cos(el) * Math.cos(toStar + az) * distance;
        double y = body.y() + Math.sin(el) * distance;
        double z = body.z() + Math.cos(el) * Math.sin(toStar + az) * distance;
        double dx = body.x() - x;
        double dy = body.y() - y;
        double dz = body.z() - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)) + yawOffset;
        float lookPitch = pitch != 0 ? pitch : (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        ServerLevel level = SpaceDimensions.getOrCreate(source.getServer(), id.get().system());
        Entity entity = source.getEntityOrException();
        entity.teleport(new TeleportTransition(level, new Vec3(x, y, z), Vec3.ZERO, yaw, lookPitch, TeleportTransition.PLACE_PORTAL_TICKET));
        source.sendSuccess(() -> Component.translatable("commands.universe.space.view", path,
                String.format(Locale.ROOT, "%.0f", distance - body.surface())), false);
        return 1;
    }
}
