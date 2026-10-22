package io.github.sweetcornna.universe.world.space;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.StarClass;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.space.BlackHoles;
import io.github.sweetcornna.universe.entity.ship.ShipBlackHole;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * 黑洞的调试命令（ADR-0024），验收和排查用：
 * <ul>
 * <li>{@code /universe blackhole list}：星团里哪些恒星系有黑洞
 * <li>{@code /universe blackhole here}：这个系的黑洞在哪、视界多大、离我多远
 * <li>{@code /universe blackhole goto [恒星系] [距离]}：传送到那个系的太空里，离视界这么远（默认 1500 格），面朝黑洞；
 * 不给恒星系（或者写 {@code nearest <距离>}）就去离这里最近、有另外放的黑洞的那个系
 * </ul>
 */
public final class BlackHoleCommand {
    private static final SimpleCommandExceptionType NOT_IN_SPACE =
            new SimpleCommandExceptionType(Component.translatable("commands.universe.fleet.not_in_space"));
    private static final DynamicCommandExceptionType NO_HOLE =
            new DynamicCommandExceptionType(system -> Component.translatable("commands.universe.blackhole.none", String.valueOf(system)));
    private static final int LISTED = 12;

    private BlackHoleCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Universe.MOD_ID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("blackhole")
                        .then(Commands.literal("list").executes(context -> list(context.getSource())))
                        .then(Commands.literal("here").executes(context -> here(context.getSource())))
                        .then(Commands.literal("goto")
                                .executes(context -> go(context.getSource(), nearest(context.getSource()), 1500))
                                .then(Commands.literal("nearest")
                                        .then(Commands.argument("distance", DoubleArgumentType.doubleArg(0))
                                                .executes(context -> go(context.getSource(), nearest(context.getSource()),
                                                        DoubleArgumentType.getDouble(context, "distance")))))
                                .then(Commands.argument("system", IntegerArgumentType.integer(0))
                                        .executes(context -> go(context.getSource(), IntegerArgumentType.getInteger(context, "system"), 1500))
                                        .then(Commands.argument("distance", DoubleArgumentType.doubleArg(0))
                                                .executes(context -> go(context.getSource(), IntegerArgumentType.getInteger(context, "system"),
                                                        DoubleArgumentType.getDouble(context, "distance"))))))));
    }

    private static int list(CommandSourceStack source) {
        Galaxy galaxy = Planets.galaxy(source.getServer());
        StringBuilder result = new StringBuilder();
        int count = 0;
        for (StarSystem system : galaxy.systems()) {
            if (ShipBlackHole.in(source.getServer(), system.index()).isEmpty()) {
                continue;
            }
            if (count < LISTED) {
                boolean star = system.star().starClass() == StarClass.BLACK_HOLE;
                result.append(String.format(Locale.ROOT, "%n#%d %s%s", system.index(), system.name().en(), star ? " (star)" : ""));
            }
            count++;
        }
        int total = count;
        source.sendSuccess(() -> Component.translatable("commands.universe.blackhole.list", total, galaxy.systems().size())
                .append(Component.literal(result.toString())), false);
        return count;
    }

    private static int here(CommandSourceStack source) throws CommandSyntaxException {
        Entity entity = source.getEntityOrException();
        OptionalInt system = SpaceDimensions.system(entity.level().dimension());
        if (system.isEmpty()) {
            throw NOT_IN_SPACE.create();
        }
        BlackHoles.BlackHole hole = ShipBlackHole.in(source.getServer(), system.getAsInt())
                .orElseThrow(() -> NO_HOLE.create(system.getAsInt()));
        double horizon = BlackHoles.horizon(Planets.galaxy(source.getServer()).system(system.getAsInt()).orElseThrow());
        double distance = hole.distanceTo(entity.getX(), entity.getY(), entity.getZ()) - horizon;
        source.sendSuccess(() -> Component.translatable("commands.universe.blackhole.here",
                String.format(Locale.ROOT, "%.0f %.0f %.0f", hole.x(), hole.y(), hole.z()), (int) horizon, (int) Math.max(0, distance)), false);
        return 1;
    }

    /** 离所在恒星系最近、有另外放的黑洞（不是黑洞恒星）的系；不在任何恒星系里就从母星系算。 */
    private static int nearest(CommandSourceStack source) throws CommandSyntaxException {
        Galaxy galaxy = Planets.galaxy(source.getServer());
        Entity entity = source.getEntityOrException();
        int from = SpaceDimensions.system(entity.level().dimension()).orElse(galaxy.home().index());
        StarSystem origin = galaxy.system(from).orElseThrow();
        StarSystem best = null;
        for (StarSystem system : galaxy.systems()) {
            if (system.star().starClass() == StarClass.BLACK_HOLE || ShipBlackHole.in(source.getServer(), system.index()).isEmpty()) {
                continue;
            }
            if (best == null || origin.distanceTo(system) < origin.distanceTo(best)) {
                best = system;
            }
        }
        if (best == null) {
            throw NO_HOLE.create(from);
        }
        return best.index();
    }

    private static int go(CommandSourceStack source, int index, double distance) throws CommandSyntaxException {
        Optional<StarSystem> system = Planets.galaxy(source.getServer()).system(index);
        BlackHoles.BlackHole hole = ShipBlackHole.in(source.getServer(), index).orElseThrow(() -> NO_HOLE.create(index));
        double horizon = BlackHoles.horizon(system.orElseThrow());
        // 站在黑洞和恒星之间（恒星在背后照着吸积盘）；黑洞就是恒星时站在黄道面上
        double length = Math.hypot(hole.x(), hole.z());
        double ux = length < 1 ? 1 : -hole.x() / length;
        double uz = length < 1 ? 0 : -hole.z() / length;
        double away = horizon + distance;
        Vec3 pos = new Vec3(hole.x() + ux * away, hole.y(), hole.z() + uz * away);
        float yaw = (float) Math.toDegrees(Math.atan2(ux, -uz));
        ServerLevel level = SpaceDimensions.getOrCreate(source.getServer(), index);
        Entity entity = source.getEntityOrException();
        entity.teleport(new TeleportTransition(level, pos, Vec3.ZERO, yaw, 0, TeleportTransition.PLACE_PORTAL_TICKET));
        source.sendSuccess(() -> Component.translatable("commands.universe.blackhole.went", index, (int) distance), false);
        return 1;
    }
}
