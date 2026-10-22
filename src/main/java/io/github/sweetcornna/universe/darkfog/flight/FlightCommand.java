package io.github.sweetcornna.universe.darkfog.flight;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.darkfog.Flight;
import io.github.sweetcornna.universe.core.darkfog.FlightPlan;
import io.github.sweetcornna.universe.darkfog.hive.DarkFogHives;
import io.github.sweetcornna.universe.dyson.Dyson;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /universe darkfog flights ...}：调试巢穴派出去的中继站和种子（ADR-0028 §八）。
 *
 * <ul>
 *   <li>{@code list}：所有飞行：编号、种类、从哪到哪、在哪一段、还要多久、等级、血量。</li>
 *   <li>{@code relay [等级]}：在星球上往脚下这颗星球派一个；在太空里按巢穴的挑法派一个。从巢穴出发，没有巢穴就从执行者身边出发。</li>
 *   <li>{@code seed [恒星系] [等级]}：从执行者所在的恒星系放一颗种子，不写就去最近的没有巢穴的恒星系。</li>
 *   <li>{@code arrive <编号>}：跳到到达那一段（中继站开始降落，种子进入目标恒星系）；{@code arrive <编号> here}：中继站改落到面前 48 格。
 *       {@code land <编号>}：马上到，结算掉。</li>
 *   <li>{@code remove <编号>}、{@code clear}：收回一次、全部飞行（不掉东西）。</li>
 * </ul>
 */
public final class FlightCommand {
    /** 没有巢穴时用几级。 */
    private static final int DEFAULT_LEVEL = 3;
    /** {@code arrive <编号> here} 落在面前多远。 */
    private static final double HERE_DISTANCE = 48;

    private FlightCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> flights = Commands.literal("flights")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(Commands.literal("relay")
                        .executes(context -> relay(context.getSource(), -1))
                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 30))
                                .executes(context -> relay(context.getSource(), IntegerArgumentType.getInteger(context, "level")))))
                .then(Commands.literal("seed")
                        .executes(context -> seed(context.getSource(), -1, -1))
                        .then(Commands.argument("system", IntegerArgumentType.integer(0))
                                .executes(context -> seed(context.getSource(), IntegerArgumentType.getInteger(context, "system"), -1))
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, 30))
                                        .executes(context -> seed(context.getSource(), IntegerArgumentType.getInteger(context, "system"),
                                                IntegerArgumentType.getInteger(context, "level"))))))
                .then(Commands.literal("arrive").then(Commands.argument("id", IntegerArgumentType.integer(0))
                        .executes(context -> skip(context, Flight.Phase.ARRIVE))
                        .then(Commands.literal("here").executes(FlightCommand::arriveHere))))
                .then(Commands.literal("land").then(Commands.argument("id", IntegerArgumentType.integer(0))
                        .executes(context -> skip(context, Flight.Phase.DONE))))
                .then(Commands.literal("remove").then(Commands.argument("id", IntegerArgumentType.integer(0))
                        .executes(FlightCommand::remove)))
                .then(Commands.literal("clear").executes(context -> {
                    int count = DarkFogFlights.clear(context.getSource().getServer());
                    context.getSource().sendSuccess(() -> Component.translatable("commands.universe.darkfog.flights.cleared", count), true);
                    return count;
                }));
        dispatcher.register(Commands.literal(Universe.MOD_ID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("darkfog").then(flights)));
    }

    private static int list(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        List<Flight> flights = DarkFogFlightData.get(server).flights().stream().sorted(Comparator.comparingInt(Flight::id)).toList();
        if (flights.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.universe.darkfog.flights.empty"), false);
            return 0;
        }
        long now = server.overworld().getGameTime();
        for (Flight flight : flights) {
            Flight.Phase phase = flight.phase(now);
            long left = Math.max(0, (phase == Flight.Phase.ARRIVE ? flight.end() : nextStage(flight, now)) - now) / 20;
            Component phaseName = Component.translatable("commands.universe.darkfog.flights.phase." + phase.name().toLowerCase(Locale.ROOT));
            int health = Math.round(flight.health() * 100);
            Component line = flight.kind() == Flight.Kind.RELAY
                    ? Component.translatable("commands.universe.darkfog.flights.relay_line", flight.id(), flight.system(),
                            flight.siteChosen() ? flight.dimension() + " " + flight.siteX() + ", " + flight.siteZ() : flight.dimension(), phaseName,
                            left, flight.level(), health)
                    : Component.translatable("commands.universe.darkfog.flights.seed_line", flight.id(), flight.system(), flight.target(), phaseName,
                            left, flight.level(), health);
            source.sendSuccess(() -> line, false);
        }
        return flights.size();
    }

    /** 这一段到什么时候结束。 */
    private static long nextStage(Flight flight, long now) {
        return switch (flight.phase(now)) {
            case DEPART -> flight.departEnd();
            case CRUISE -> flight.arriveStart();
            default -> flight.end();
        };
    }

    /** 执行者所在的恒星系；不在任何恒星系里报错。 */
    private static OptionalInt here(CommandSourceStack source) {
        OptionalInt system = Dyson.systemOf(source.getLevel().dimension());
        if (system.isEmpty()) {
            source.sendFailure(Component.translatable("commands.universe.darkfog.flights.no_system"));
        }
        return system;
    }

    /** 从哪出发、几级：恒星系里有巢穴就从巢穴、按巢穴的等级；没有就从执行者身边（在太空里的话）出发。 */
    private static Optional<DarkFogHives.Summary> hive(MinecraftServer server, int system) {
        return DarkFogHives.summaries(server).stream().filter(summary -> summary.system() == system).findFirst();
    }

    private static Vec3 origin(CommandSourceStack source, int system) {
        Optional<DarkFogHives.Summary> hive = hive(source.getServer(), system);
        if (hive.isPresent()) {
            return new Vec3(hive.get().x(), hive.get().y(), hive.get().z());
        }
        return SpaceDimensions.isSpace(source.getLevel().dimension()) ? source.getPosition() : Vec3.ZERO;
    }

    private static int level(CommandSourceStack source, int system, int level) {
        return level >= 0 ? level : hive(source.getServer(), system).map(DarkFogHives.Summary::level).orElse(DEFAULT_LEVEL);
    }

    private static int relay(CommandSourceStack source, int requested) {
        OptionalInt here = here(source);
        if (here.isEmpty()) {
            return 0;
        }
        MinecraftServer server = source.getServer();
        int system = here.getAsInt();
        int level = level(source, system, requested);
        Vec3 from = origin(source, system);
        Optional<Flight> flight;
        if (SpaceDimensions.isSpace(source.getLevel().dimension())) {
            int before = DarkFogFlightData.get(server).flights().stream().mapToInt(Flight::id).max().orElse(-1);
            flight = DarkFogFlights.launchRelay(server, system, level, from)
                    ? DarkFogFlightData.get(server).flights().stream().filter(f -> f.id() > before).findFirst()
                    : Optional.empty();
        } else {
            flight = DarkFogFlights.launchRelayTo(server, system, level, from, source.getLevel().dimension());
        }
        if (flight.isEmpty()) {
            source.sendFailure(Component.translatable("commands.universe.darkfog.flights.no_planet"));
            return 0;
        }
        Flight launched = flight.get();
        source.sendSuccess(() -> Component.translatable("commands.universe.darkfog.flights.relay_launched", launched.id(), launched.dimension(),
                (launched.arriveStart() - launched.launched()) / 20), true);
        return launched.id() + 1;
    }

    private static int seed(CommandSourceStack source, int target, int requested) {
        OptionalInt here = here(source);
        if (here.isEmpty()) {
            return 0;
        }
        MinecraftServer server = source.getServer();
        int system = here.getAsInt();
        int level = level(source, system, requested);
        Vec3 from = origin(source, system);
        Optional<Flight> flight;
        if (target >= 0) {
            flight = DarkFogFlights.launchSeedTo(server, system, target, level, from);
        } else {
            int before = DarkFogFlightData.get(server).flights().stream().mapToInt(Flight::id).max().orElse(-1);
            flight = DarkFogFlights.launchSeed(server, system, level, from)
                    ? DarkFogFlightData.get(server).flights().stream().filter(f -> f.id() > before).findFirst()
                    : Optional.empty();
        }
        if (flight.isEmpty()) {
            source.sendFailure(Component.translatable("commands.universe.darkfog.flights.no_target", (int) FlightPlan.SEED_RANGE));
            return 0;
        }
        Flight launched = flight.get();
        source.sendSuccess(() -> Component.translatable("commands.universe.darkfog.flights.seed_launched", launched.id(), launched.system(),
                launched.target(), (launched.end() - launched.launched()) / 1200), true);
        return launched.id() + 1;
    }

    private static int skip(CommandContext<CommandSourceStack> context, Flight.Phase phase) {
        int id = IntegerArgumentType.getInteger(context, "id");
        if (!DarkFogFlights.skip(context.getSource().getServer(), id, phase)) {
            context.getSource().sendFailure(Component.translatable("commands.universe.darkfog.flights.unknown", id));
            return 0;
        }
        Component phaseName = Component.translatable("commands.universe.darkfog.flights.phase." + phase.name().toLowerCase(Locale.ROOT));
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.darkfog.flights.skipped", id, phaseName), true);
        return 1;
    }

    /** 中继站改落到执行者面前 {@link #HERE_DISTANCE} 格，马上开始降落（截图看降落用）。 */
    private static int arriveHere(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int id = IntegerArgumentType.getInteger(context, "id");
        Vec3 look = Vec3.directionFromRotation(0, source.getRotation().y);
        Vec3 at = source.getPosition().add(look.scale(HERE_DISTANCE));
        int x = Mth.floor(at.x);
        int z = Mth.floor(at.z);
        if (!DarkFogFlights.arriveAt(source.getServer(), id, source.getLevel().dimension(), x, z)) {
            source.sendFailure(Component.translatable("commands.universe.darkfog.flights.not_here", id));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.universe.darkfog.flights.arrive_here", id, x, z), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        int id = IntegerArgumentType.getInteger(context, "id");
        if (!DarkFogFlights.remove(context.getSource().getServer(), id)) {
            context.getSource().sendFailure(Component.translatable("commands.universe.darkfog.flights.unknown", id));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.darkfog.flights.removed", id), true);
        return 1;
    }
}
