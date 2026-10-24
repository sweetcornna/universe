package io.github.sweetcornna.universe.settlement;

import java.util.Locale;
import java.util.Optional;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.poi.Landmark;
import io.github.sweetcornna.universe.core.settlement.BuildingType;
import io.github.sweetcornna.universe.core.settlement.Cost;
import io.github.sweetcornna.universe.core.settlement.Decisions;
import io.github.sweetcornna.universe.core.settlement.Plot;
import io.github.sweetcornna.universe.core.settlement.Settlement;
import io.github.sweetcornna.universe.core.settlement.SettlementRules;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * {@code /universe settlement ...}：调试聚落（ADR-0034 §二）。除了 {@code claim} 和 {@code create}，都对执行者离得最近的、自己管的聚落。
 *
 * <ul>
 *   <li>{@code claim}：接管最近的聚落地标，不看声望、不收材料。{@code create [种族]}：不要地标，在脚下立一个聚落并接管（会整平一片地）。</li>
 *   <li>{@code skip}：施工、升级都不用再等。{@code finish}：动工了的地基直接盖完（不开张）。{@code build <地基> <建筑>}：在这块地基上盖好一座。</li>
 *   <li>{@code day [天数]}：提前结算几天。{@code raid}：海盗现在就来。{@code decision <编号>}：今天的决定换成这个。</li>
 *   <li>{@code set <幸福> <威胁> <储备>}：直接改数值。{@code info}：看数值。{@code open}：打开管理界面。{@code resync}：照数值把方块对一遍。</li>
 *   <li>{@code goto}：传送到最近的聚落地标的广场边上。</li>
 * </ul>
 */
public final class SettlementCommand {
    /** 接管多远内的聚落地标。 */
    private static final double CLAIM_RANGE = 512;
    /** {@code goto} 找多远内的聚落地标。 */
    private static final double GOTO_RANGE = 8000;

    private SettlementCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> settlement = Commands.literal("settlement")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("claim").executes(context -> claim(context.getSource())))
                .then(Commands.literal("create")
                        .executes(context -> create(context.getSource(), Race.TRADER))
                        .then(Commands.argument("race", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        java.util.Arrays.stream(Race.values()).map(Race::id), builder))
                                .executes(context -> create(context.getSource(), race(StringArgumentType.getString(context, "race"))))))
                .then(Commands.literal("skip").executes(context -> skip(context.getSource())))
                .then(Commands.literal("finish").executes(context -> finish(context.getSource())))
                .then(Commands.literal("build")
                        .then(Commands.argument("plot", IntegerArgumentType.integer(1, 8))
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                java.util.Arrays.stream(BuildingType.values()).map(BuildingType::id), builder))
                                        .executes(context -> build(context.getSource(), IntegerArgumentType.getInteger(context, "plot") - 1,
                                                StringArgumentType.getString(context, "type"))))))
                .then(Commands.literal("day")
                        .executes(context -> day(context.getSource(), 1))
                        .then(Commands.argument("days", IntegerArgumentType.integer(1, 30))
                                .executes(context -> day(context.getSource(), IntegerArgumentType.getInteger(context, "days")))))
                .then(Commands.literal("raid").executes(context -> raid(context.getSource())))
                .then(Commands.literal("decision")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        Decisions.ALL.stream().map(Decisions.Decision::id), builder))
                                .executes(context -> decision(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("happiness", DoubleArgumentType.doubleArg(0, 100))
                                .then(Commands.argument("threat", DoubleArgumentType.doubleArg(0, 100))
                                        .then(Commands.argument("reserve", DoubleArgumentType.doubleArg(0, 1000))
                                                .executes(context -> set(context.getSource(), DoubleArgumentType.getDouble(context, "happiness"),
                                                        DoubleArgumentType.getDouble(context, "threat"),
                                                        DoubleArgumentType.getDouble(context, "reserve")))))))
                .then(Commands.literal("info").executes(context -> info(context.getSource())))
                .then(Commands.literal("open").executes(context -> open(context.getSource())))
                .then(Commands.literal("goto").executes(context -> teleport(context.getSource())))
                .then(Commands.literal("resync").executes(context -> {
                    int count = Settlements.resync(context.getSource().getServer());
                    context.getSource().sendSuccess(() -> Component.translatable("commands.universe.settlement.resynced", count), true);
                    return count;
                }));
        dispatcher.register(Commands.literal(Universe.MOD_ID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(settlement));
    }

    private static Race race(String id) {
        return Race.byId(id.toLowerCase(Locale.ROOT)).orElse(Race.TRADER);
    }

    private static Optional<BuildingType> type(String id) {
        for (BuildingType type : BuildingType.values()) {
            if (type.id().equals(id.toLowerCase(Locale.ROOT))) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /** 执行者离得最近的、自己管的聚落；没有就报错。 */
    private static Optional<SettlementData.Entry> mine(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<SettlementData.Entry> entry = Settlements.nearestOwned(player);
        if (entry.isEmpty()) {
            source.sendFailure(Component.translatable("commands.universe.settlement.none"));
        }
        return entry;
    }

    private static int claim(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        Optional<Landmark> landmark = Settlements.landmarkNear(level, player.getX(), player.getZ(), CLAIM_RANGE);
        if (landmark.isEmpty()) {
            source.sendFailure(Component.translatable("commands.universe.settlement.no_landmark"));
            return 0;
        }
        Component problem = Settlements.claim(player, level, Settlements.site(level, landmark.get()), true);
        if (problem != null) {
            source.sendFailure(problem);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.universe.settlement.claimed", landmark.get().x(), landmark.get().z()), true);
        return 1;
    }

    private static int create(CommandSourceStack source, Race race) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        BlockPos feet = player.blockPosition();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ()) - 1;
        long key = BlockPos.asLong(feet.getX(), 0, feet.getZ());
        Settlement s = Settlements.testSite(level, new BlockPos(feet.getX(), y, feet.getZ()), key, race);
        if (SettlementData.get(source.getServer()).entry(s.id()).isPresent()) {
            source.sendFailure(Component.translatable("message.universe.settlement.already_yours"));
            return 0;
        }
        Component problem = Settlements.claim(player, level, s, true);
        if (problem != null) {
            source.sendFailure(problem);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.universe.settlement.claimed", feet.getX(), feet.getZ()), true);
        return 1;
    }

    private static int skip(CommandSourceStack source) throws CommandSyntaxException {
        Optional<SettlementData.Entry> entry = mine(source);
        if (entry.isEmpty()) {
            return 0;
        }
        long now = Settlements.now(source.getServer());
        int count = 0;
        Settlement s = entry.get().settlement();
        for (int i = 0; i < s.plotCount(); i++) {
            Plot plot = s.plot(i);
            if (plot.readyAt() > now) {
                plot.skipWait(now);
                count++;
            }
        }
        int skipped = count;
        source.sendSuccess(() -> Component.translatable("commands.universe.settlement.skipped", skipped), true);
        return count;
    }

    private static int finish(CommandSourceStack source) throws CommandSyntaxException {
        Optional<SettlementData.Entry> entry = mine(source);
        if (entry.isEmpty()) {
            return 0;
        }
        Settlement s = entry.get().settlement();
        int count = 0;
        for (int i = 0; i < s.plotCount(); i++) {
            Plot plot = s.plot(i);
            if (plot.type() != null && (plot.stage() < BuildingType.STAGES || plot.upgrading() || plot.damaged())) {
                complete(source.getServer(), entry.get(), i);
                count++;
            }
        }
        int done = count;
        source.sendSuccess(() -> Component.translatable("commands.universe.settlement.finished", done), true);
        return count;
    }

    private static int build(CommandSourceStack source, int index, String typeId) throws CommandSyntaxException {
        Optional<SettlementData.Entry> entry = mine(source);
        if (entry.isEmpty()) {
            return 0;
        }
        Optional<BuildingType> type = type(typeId);
        Settlement s = entry.get().settlement();
        if (type.isEmpty() || s.plot(index).type() != null || !s.start(index, type.get())) {
            source.sendFailure(Component.translatable("message.universe.settlement.cannot_start"));
            return 0;
        }
        complete(source.getServer(), entry.get(), index);
        s.open(index);
        ServerLevel level = Settlements.level(source.getServer(), s);
        if (level != null) {
            SettlementBuilder.sync(level, entry.get(), index, Settlements.now(source.getServer()), true);
        }
        SettlementData.get(source.getServer()).changed();
        source.sendSuccess(() -> Component.translatable("commands.universe.settlement.built", index + 1), true);
        return 1;
    }

    /** 交齐材料、跳过等待，一段一段盖到底（升级、修理也一样）。 */
    private static void complete(MinecraftServer server, SettlementData.Entry entry, int index) {
        Plot plot = entry.settlement().plot(index);
        long now = Settlements.now(server);
        for (int guard = 0; guard < 8 && !plot.costs().isEmpty(); guard++) {
            for (Cost cost : plot.costs()) {
                plot.deliver(cost, plot.missing(cost), now);
            }
            plot.skipWait(now);
            plot.tick(now);
        }
        ServerLevel level = Settlements.level(server, entry.settlement());
        if (level != null) {
            SettlementBuilder.sync(level, entry, index, now, true);
        }
        SettlementData.get(server).changed();
    }

    private static int day(CommandSourceStack source, int days) throws CommandSyntaxException {
        Optional<SettlementData.Entry> entry = mine(source);
        if (entry.isEmpty()) {
            return 0;
        }
        long income = 0;
        for (int i = 0; i < days; i++) {
            income += Settlements.advance(source.getServer(), entry.get()).income();
        }
        long total = income;
        source.sendSuccess(() -> Component.translatable("commands.universe.settlement.days", days, total), true);
        return days;
    }

    private static int raid(CommandSourceStack source) throws CommandSyntaxException {
        Optional<SettlementData.Entry> entry = mine(source);
        if (entry.isEmpty()) {
            return 0;
        }
        if (SettlementRaids.active(entry.get().settlement().id())) {
            source.sendFailure(Component.translatable("commands.universe.settlement.raid_busy"));
            return 0;
        }
        entry.get().settlement().scheduleRaid(Settlements.now(source.getServer()));
        SettlementData.get(source.getServer()).changed();
        source.sendSuccess(() -> Component.translatable("commands.universe.settlement.raid"), true);
        return 1;
    }

    private static int decision(CommandSourceStack source, String id) throws CommandSyntaxException {
        Optional<SettlementData.Entry> entry = mine(source);
        if (entry.isEmpty()) {
            return 0;
        }
        if (Decisions.byId(id).isEmpty()) {
            source.sendFailure(Component.translatable("commands.universe.settlement.no_such_decision", id));
            return 0;
        }
        entry.get().settlement().forceDecision(id);
        SettlementData.get(source.getServer()).changed();
        source.sendSuccess(() -> Component.translatable("commands.universe.settlement.decision", id), true);
        return 1;
    }

    private static int set(CommandSourceStack source, double happiness, double threat, double reserve) throws CommandSyntaxException {
        Optional<SettlementData.Entry> entry = mine(source);
        if (entry.isEmpty()) {
            return 0;
        }
        entry.get().settlement().debugSet(happiness, threat, reserve);
        SettlementData.get(source.getServer()).changed();
        return info(source);
    }

    private static int info(CommandSourceStack source) throws CommandSyntaxException {
        Optional<SettlementData.Entry> entry = mine(source);
        if (entry.isEmpty()) {
            return 0;
        }
        Settlement s = entry.get().settlement();
        source.sendSuccess(() -> Component.translatable("commands.universe.settlement.info", Settlements.displayName(entry.get()),
                SettlementRules.classLetter(s.settlementClass()), s.population(), s.housing(), Math.round(s.happiness()), Math.round(s.threat()),
                String.format(Locale.ROOT, "%.1f", s.productivity()), Math.round(s.defense()), (int) Math.floor(s.reserve()), s.reserveCap(),
                s.treasury(), s.debt(), s.day()), false);
        for (int i = 0; i < s.plotCount(); i++) {
            Plot plot = s.plot(i);
            if (plot.type() == null) {
                continue;
            }
            int index = i + 1;
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "  #%d %s %s %d/%d %s", index, plot.type().id(),
                    BuildingType.letter(plot.grade()), plot.stage(), BuildingType.STAGES, plot.phase().name().toLowerCase(Locale.ROOT))), false);
        }
        return 1;
    }

    /** 传送到最近的聚落地标：站在广场南边，朝北看着广场。 */
    private static int teleport(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        Optional<Landmark> landmark = Settlements.landmarkNear(level, player.getX(), player.getZ(), GOTO_RANGE);
        if (landmark.isEmpty()) {
            source.sendFailure(Component.translatable("commands.universe.settlement.none_within", (int) GOTO_RANGE));
            return 0;
        }
        int x = landmark.get().x();
        int z = landmark.get().z() + 9;
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        player.teleportTo(level, x + 0.5, y, z + 0.5, java.util.Set.of(), 180, 15, true);
        source.sendSuccess(() -> Component.translatable("commands.universe.settlement.went", landmark.get().x(), landmark.get().z()), true);
        return 1;
    }

    private static int open(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        Optional<SettlementData.Entry> entry = Settlements.nearestOwned(player);
        if (entry.isPresent()) {
            Settlements.open(player, level, entry.get().settlement(), 0, -1);
            return 1;
        }
        Optional<Landmark> landmark = Settlements.landmarkNear(level, player.getX(), player.getZ(), Settlements.ACT_RANGE);
        if (landmark.isPresent()) {
            Settlements.open(player, level, Settlements.site(level, landmark.get()), 0, -1);
            return 1;
        }
        source.sendFailure(Component.translatable("commands.universe.settlement.none"));
        return 0;
    }
}
