package io.github.sweetcornna.universe.fleet;

import java.util.Locale;
import java.util.OptionalInt;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.fleet.Fleet;
import io.github.sweetcornna.universe.core.fleet.FreighterLayout;
import io.github.sweetcornna.universe.core.fleet.FreighterRules;
import io.github.sweetcornna.universe.core.fleet.Frigate;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * 舰队的调试命令（ADR-0024），验收和排查用：
 * <ul>
 * <li>{@code /universe fleet grant [等级]}：在所在的恒星系给自己一艘货船（要在太空里或船舱里，还没有货船）
 * <li>{@code /universe fleet hire <艘数>}：随机雇几艘护卫舰，满了为止
 * <li>{@code /universe fleet hurry}：在外面的远征都马上回来
 * <li>{@code /universe fleet view}：传送到货船外面，从左前上方看着整艘船
 * <li>{@code /universe fleet info}：货船在哪里，几艘护卫舰，几支远征
 * </ul>
 */
public final class FleetCommand {
    private static final SimpleCommandExceptionType NOT_IN_SPACE =
            new SimpleCommandExceptionType(Component.translatable("commands.universe.fleet.not_in_space"));
    private static final SimpleCommandExceptionType NO_FREIGHTER =
            new SimpleCommandExceptionType(Component.translatable("message.universe.fleet.problem.no_freighter"));
    private static final SimpleCommandExceptionType HAVE_FREIGHTER =
            new SimpleCommandExceptionType(Component.translatable("message.universe.dealer.have_freighter"));
    private static final DynamicCommandExceptionType BAD_GRADE =
            new DynamicCommandExceptionType(grade -> Component.translatable("commands.universe.fleet.bad_grade", String.valueOf(grade)));

    private FleetCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Universe.MOD_ID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("fleet")
                        .then(Commands.literal("grant")
                                .executes(context -> grant(context.getSource(), Grade.C))
                                .then(Commands.argument("grade", StringArgumentType.word())
                                        .executes(context -> grant(context.getSource(), grade(StringArgumentType.getString(context, "grade"))))))
                        .then(Commands.literal("hire")
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, FreighterRules.MAX_FRIGATES))
                                        .executes(context -> hire(context.getSource(), IntegerArgumentType.getInteger(context, "count")))))
                        .then(Commands.literal("hurry")
                                .executes(context -> hurry(context.getSource())))
                        .then(Commands.literal("view")
                                .executes(context -> view(context.getSource())))
                        .then(Commands.literal("info")
                                .executes(context -> info(context.getSource())))));
    }

    private static Grade grade(String name) throws CommandSyntaxException {
        try {
            return Grade.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw BAD_GRADE.create(name);
        }
    }

    private static Fleet fleet(ServerPlayer player) throws CommandSyntaxException {
        Fleet fleet = FleetData.get(player.level().getServer()).entry(player.getUUID()).fleet();
        if (fleet.freighter().isEmpty()) {
            throw NO_FREIGHTER.create();
        }
        return fleet;
    }

    private static int grant(CommandSourceStack source, Grade grade) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        if (FleetData.get(server).entry(player.getUUID()).fleet().freighter().isPresent()) {
            throw HAVE_FREIGHTER.create();
        }
        OptionalInt system = SpaceDimensions.system(player.level().dimension());
        if (system.isEmpty()) {
            throw NOT_IN_SPACE.create();
        }
        Fleet.Freighter freighter = Freighters.grant(server, player.getUUID(), player.getRandom().nextLong(), grade, system.getAsInt(), null);
        source.sendSuccess(() -> Component.translatable("commands.universe.fleet.granted", freighter.id(), grade.letter()), false);
        return 1;
    }

    private static int hire(CommandSourceStack source, int count) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        Fleet fleet = fleet(player);
        Rng rng = Rng.of(player.getRandom().nextLong());
        int hired = 0;
        while (hired < count && fleet.canHire() == null) {
            fleet.hire(Frigate.roll(0, rng));
            hired++;
        }
        FleetData.get(server).changed();
        Freighters.later(() -> Freighters.refreshFrigates(server, player.getUUID()));
        int done = hired;
        source.sendSuccess(() -> Component.translatable("commands.universe.fleet.hired", done, fleet.frigates().size()), false);
        return hired;
    }

    private static int hurry(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Fleet fleet = fleet(player);
        int voyages = fleet.voyages().size();
        fleet.hurry(FleetTerminal.now(source.getServer()));
        FleetData.get(source.getServer()).changed();
        source.sendSuccess(() -> Component.translatable("commands.universe.fleet.hurried", voyages), false);
        return voyages;
    }

    private static int view(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        Fleet fleet = fleet(player);
        FleetData.Placed placed = FleetData.get(server).entry(player.getUUID()).placed().orElseThrow(NO_FREIGHTER::create);
        FreighterLayout.Exterior exterior = Freighters.exterior(fleet.freighter().orElseThrow().seed());
        int[] b = Freighters.bounds(placed, exterior);
        double cx = (b[0] + b[3]) / 2.0;
        double cy = (b[1] + b[4]) / 2.0;
        double cz = (b[2] + b[5]) / 2.0;
        double size = b[3] - b[0];
        double x = b[0] - size * 0.4;
        double y = cy + size * 0.3;
        double z = cz - size * 0.9;
        double dx = cx - x;
        double dy = cy - y;
        double dz = cz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        ServerLevel level = SpaceDimensions.getOrCreate(server, placed.system());
        player.teleport(new TeleportTransition(level, new Vec3(x, y, z), Vec3.ZERO, yaw, pitch, TeleportTransition.PLACE_PORTAL_TICKET));
        return 1;
    }

    private static int info(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        Fleet fleet = fleet(player);
        Fleet.Freighter freighter = fleet.freighter().orElseThrow();
        long now = FleetTerminal.now(server);
        String where = FleetData.get(server).entry(player.getUUID()).placed()
                .map(p -> String.format(Locale.ROOT, "s%d (%d, %d, %d)", p.system(), p.x(), p.y(), p.z())).orElse("-");
        long back = fleet.voyages().stream().filter(v -> v.done(now)).count();
        String text = String.format(Locale.ROOT, "#%d %s seed=%d range=%.0f | %s | frigates %d (docked %d) | voyages %d (back %d)",
                freighter.id(), freighter.grade().letter(), freighter.seed(), freighter.range(), where, fleet.frigates().size(),
                fleet.docked().size(), fleet.voyages().size(), back);
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
