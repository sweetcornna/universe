package io.github.sweetcornna.universe.darkfog.hive;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.darkfog.HiveState;
import io.github.sweetcornna.universe.core.darkfog.SpaceUnitKind;
import io.github.sweetcornna.universe.core.dyson.DysonSphere;
import io.github.sweetcornna.universe.dyson.Dyson;
import io.github.sweetcornna.universe.rules.UniverseRules;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /universe darkfog hive ...}：调试黑雾的太空巢穴（ADR-0028 §八）。不写恒星系时管命令执行者所在的恒星系。
 *
 * <ul>
 *   <li>{@code list}：所有巢穴：恒星系、状态、等级、威胁、物质、建筑数、港口里的单位。</li>
 *   <li>{@code info [恒星系]}：一座巢穴的详细情况，连同偷了多少戴森球的电。</li>
 *   <li>{@code tp [恒星系] [距离]}：传到巢穴外边（默认离核心 {@value #VIEW_DISTANCE} 格），面朝它。</li>
 *   <li>{@code create [等级]}：在这个恒星系按种子的位置马上建一座（换掉原来的）；{@code create here [等级]} 建在面前 160 格。</li>
 *   <li>{@code threat}、{@code level}、{@code matter}、{@code units}：直接改。{@code grow <分钟>}：多长这么久。</li>
 *   <li>{@code raid}：威胁设满，下一秒派枪骑。{@code relay}、{@code seed}：物质和等级给够，下一秒派中继站、放种子。</li>
 *   <li>{@code kill}：打掉核心（不掉东西）。{@code remove}、{@code clear}：收拾掉一座、全部巢穴。</li>
 * </ul>
 */
public final class HiveCommand {
    /** {@code tp} 默认离核心多远。 */
    private static final int VIEW_DISTANCE = 110;

    private HiveCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> hive = Commands.literal("hive")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(withSystem(Commands.literal("info"), HiveCommand::info))
                .then(withSystem(Commands.literal("tp"), (source, system) -> tp(source, system, VIEW_DISTANCE))
                        .then(Commands.argument("system", IntegerArgumentType.integer(0))
                                .then(Commands.argument("distance", IntegerArgumentType.integer(8, 1024))
                                        .executes(context -> tp(context.getSource(), IntegerArgumentType.getInteger(context, "system"),
                                                IntegerArgumentType.getInteger(context, "distance"))))))
                .then(Commands.literal("create")
                        .executes(context -> create(context, -1, false))
                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 30))
                                .executes(context -> create(context, IntegerArgumentType.getInteger(context, "level"), false)))
                        .then(Commands.literal("here")
                                .executes(context -> create(context, -1, true))
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, 30))
                                        .executes(context -> create(context, IntegerArgumentType.getInteger(context, "level"), true)))))
                .then(Commands.literal("threat").then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                        .executes(context -> edit(context, hiveState -> hiveState.setThreat(IntegerArgumentType.getInteger(context, "percent") / 100.0),
                                "threat", IntegerArgumentType.getInteger(context, "percent")))))
                .then(Commands.literal("level").then(Commands.argument("level", IntegerArgumentType.integer(0, 30))
                        .executes(context -> edit(context, hiveState -> hiveState.setLevel(IntegerArgumentType.getInteger(context, "level")),
                                "level", IntegerArgumentType.getInteger(context, "level")))))
                .then(Commands.literal("matter").then(Commands.argument("amount", IntegerArgumentType.integer(0, (int) HiveState.MAX_MATTER))
                        .executes(context -> edit(context, hiveState -> hiveState.setMatter(IntegerArgumentType.getInteger(context, "amount")),
                                "matter", IntegerArgumentType.getInteger(context, "amount")))))
                .then(Commands.literal("units")
                        .then(Commands.literal("lancer").then(count(SpaceUnitKind.LANCER)))
                        .then(Commands.literal("humpback").then(count(SpaceUnitKind.HUMPBACK))))
                .then(Commands.literal("grow").then(Commands.argument("minutes", IntegerArgumentType.integer(1, 720))
                        .executes(context -> edit(context, hiveState -> hiveState.backdate(IntegerArgumentType.getInteger(context, "minutes") * 1200L),
                                "grow", IntegerArgumentType.getInteger(context, "minutes")))))
                .then(Commands.literal("raid").executes(context -> edit(context, hiveState -> hiveState.setThreat(1), "raid", 0)))
                .then(Commands.literal("relay").executes(context -> edit(context, hiveState -> {
                    hiveState.setLevel(Math.max(hiveState.level(), HiveState.RELAY_MIN_LEVEL));
                    hiveState.setMatter(Math.max(hiveState.matter(), HiveState.RELAY_COST + 20));
                    hiveState.readyFlights();
                }, "relay", 0)))
                .then(Commands.literal("seed").executes(context -> edit(context, hiveState -> {
                    hiveState.setLevel(Math.max(hiveState.level(), HiveState.SEED_MIN_LEVEL));
                    hiveState.setMatter(Math.max(hiveState.matter(), HiveState.SEED_COST + 20));
                    hiveState.readyFlights();
                }, "seed", 0)))
                .then(withSystem(Commands.literal("kill"), HiveCommand::kill))
                .then(withSystem(Commands.literal("remove"), HiveCommand::remove))
                .then(Commands.literal("clear").executes(context -> clear(context.getSource())));
        dispatcher.register(Commands.literal(Universe.MOD_ID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("darkfog").then(hive)));
    }

    private interface SystemAction {
        int run(CommandSourceStack source, int system) throws CommandSyntaxException;
    }

    /** 不写恒星系时用执行者所在的恒星系。 */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withSystem(T node, SystemAction action) {
        return node.executes(context -> {
            OptionalInt system = here(context.getSource());
            return system.isPresent() ? action.run(context.getSource(), system.getAsInt()) : 0;
        }).then(Commands.argument("system", IntegerArgumentType.integer(0))
                .executes(context -> action.run(context.getSource(), IntegerArgumentType.getInteger(context, "system"))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> count(SpaceUnitKind kind) {
        return Commands.argument("count", IntegerArgumentType.integer(0, 64))
                .executes(context -> edit(context, hiveState -> hiveState.setUnits(kind, IntegerArgumentType.getInteger(context, "count")),
                        "units", IntegerArgumentType.getInteger(context, "count")));
    }

    private static OptionalInt here(CommandSourceStack source) {
        OptionalInt system = Dyson.systemOf(source.getLevel().dimension());
        if (system.isEmpty()) {
            source.sendFailure(Component.translatable("commands.universe.darkfog.hive.no_system"));
        }
        return system;
    }

    private static Optional<HiveState> hive(CommandSourceStack source, int system) {
        Optional<HiveState> hive = DarkFogHives.data(source.getServer()).hive(system);
        if (hive.isEmpty()) {
            source.sendFailure(Component.translatable("commands.universe.darkfog.hive.unknown", system));
        }
        return hive;
    }

    private static Component status(HiveState hive) {
        Component status = Component.translatable("commands.universe.darkfog.hive.status." + hive.status().name().toLowerCase(Locale.ROOT));
        return DarkFogHives.raiding(hive.system()) ? Component.translatable("commands.universe.darkfog.hive.status.raiding", status) : status;
    }

    private static int list(CommandSourceStack source) {
        var hives = DarkFogHives.data(source.getServer()).hives().values().stream()
                .sorted((a, b) -> Integer.compare(a.system(), b.system())).toList();
        if (hives.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.universe.darkfog.hive.none"), false);
            return 0;
        }
        for (HiveState hive : hives) {
            int threat = (int) Math.round(hive.threat() * 100);
            source.sendSuccess(() -> Component.translatable("commands.universe.darkfog.hive.line", hive.system(), status(hive), hive.level(), threat,
                    (int) hive.matter(), hive.buildings(), hive.units(SpaceUnitKind.LANCER), hive.units(SpaceUnitKind.HUMPBACK)), false);
        }
        return hives.size();
    }

    private static int info(CommandSourceStack source, int system) {
        return hive(source, system).map(hive -> {
            double siphon = Dyson.existing(source.getServer(), system).map(DysonSphere::siphon).orElse(0.0) * 20;
            source.sendSuccess(() -> Component.translatable("commands.universe.darkfog.hive.info", hive.system(), status(hive), hive.level(),
                    (int) Math.round(hive.threat() * 100), (int) hive.matter(), hive.buildings(), DarkFogHives.placedBuildings(system),
                    hive.units(SpaceUnitKind.LANCER), hive.units(SpaceUnitKind.HUMPBACK),
                    String.format(Locale.ROOT, "%.0f %.0f %.0f", hive.x(), hive.y(), hive.z()), hive.receivers(),
                    String.format(Locale.ROOT, "%.0f", siphon), DarkFogHives.relays(source.getServer(), system),
                    hive.relayDelay(), hive.seedDelay(), hive.rebuildDelay()), false);
            return 1;
        }).orElse(0);
    }

    private static int tp(CommandSourceStack source, int system, int distance) throws CommandSyntaxException {
        Entity entity = source.getEntityOrException();
        Optional<HiveState> found = hive(source, system);
        if (found.isEmpty()) {
            return 0;
        }
        HiveState hive = found.get();
        ServerLevel space = SpaceDimensions.getOrCreate(source.getServer(), system);
        // 从恒星那一侧看过去：默认离核心 110 格（整个巢穴都在视野里），往上抬一点
        Vec3 center = DarkFogHives.center(hive);
        Vec3 out = new Vec3(-center.x, 0, -center.z);
        out = out.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : out.normalize();
        Vec3 at = center.add(out.scale(distance)).add(0, distance * 0.25, 0);
        Vec3 look = center.subtract(at);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        entity.teleport(new TeleportTransition(space, at, Vec3.ZERO, yaw, pitch, TeleportTransition.PLACE_PORTAL_TICKET));
        source.sendSuccess(() -> Component.translatable("commands.universe.darkfog.hive.tp", system), false);
        return 1;
    }

    private static int create(CommandContext<CommandSourceStack> context, int level, boolean here) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        OptionalInt system = here(source);
        if (system.isEmpty()) {
            return 0;
        }
        int lvl = level >= 0 ? level : UniverseRules.darkFog(source.getServer()).initialLevel();
        HiveState hive;
        if (here) {
            ServerPlayer player = source.getPlayerOrException();
            if (!SpaceDimensions.isSpace(source.getLevel().dimension())) {
                source.sendFailure(Component.translatable("commands.universe.darkfog.hive.not_space"));
                return 0;
            }
            Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(160));
            hive = DarkFogHives.createAt(source.getServer(), system.getAsInt(), at, lvl);
        } else {
            hive = DarkFogHives.create(source.getServer(), system.getAsInt(), lvl);
        }
        HiveState created = hive;
        source.sendSuccess(() -> Component.translatable("commands.universe.darkfog.hive.created", created.system(), created.level(),
                String.format(Locale.ROOT, "%.0f %.0f %.0f", created.x(), created.y(), created.z())), true);
        return 1;
    }

    private interface Edit {
        void apply(HiveState hive);
    }

    private static int edit(CommandContext<CommandSourceStack> context, Edit edit, String what, int value) {
        CommandSourceStack source = context.getSource();
        OptionalInt system = here(source);
        if (system.isEmpty()) {
            return 0;
        }
        return hive(source, system.getAsInt()).map(hive -> {
            edit.apply(hive);
            DarkFogHives.data(source.getServer()).setDirty();
            source.sendSuccess(() -> Component.translatable("commands.universe.darkfog.hive." + what, hive.system(), value), true);
            return 1;
        }).orElse(0);
    }

    private static int kill(CommandSourceStack source, int system) {
        if (!DarkFogHives.destroyCore(source.getServer(), system)) {
            source.sendFailure(Component.translatable("commands.universe.darkfog.hive.unknown", system));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.universe.darkfog.hive.killed", system), true);
        return 1;
    }

    private static int remove(CommandSourceStack source, int system) {
        if (!DarkFogHives.remove(source.getServer(), system)) {
            source.sendFailure(Component.translatable("commands.universe.darkfog.hive.unknown", system));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.universe.darkfog.hive.removed", system), true);
        return 1;
    }

    private static int clear(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        var systems = DarkFogHives.data(server).hives().keySet().stream().toList();
        systems.forEach(system -> DarkFogHives.remove(server, system));
        source.sendSuccess(() -> Component.translatable("commands.universe.darkfog.hive.cleared", systems.size()), true);
        return systems.size();
    }
}
