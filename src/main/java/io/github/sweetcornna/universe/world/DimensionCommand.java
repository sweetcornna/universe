package io.github.sweetcornna.universe.world;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import io.github.sweetcornna.universe.asteroid.AsteroidCommand;
import io.github.sweetcornna.universe.dyson.DysonCommand;
import io.github.sweetcornna.universe.mission.MissionCommand;
import io.github.sweetcornna.universe.perf.BenchCommand;
import io.github.sweetcornna.universe.perf.PerfCommand;
import io.github.sweetcornna.universe.pirate.PirateCommand;
import io.github.sweetcornna.universe.sentinel.SentinelCommand;
import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.data.RuntimeDimensionsData;
import io.github.sweetcornna.universe.entity.ship.ShipCommand;
import io.github.sweetcornna.universe.factory.research.ResearchCommand;
import io.github.sweetcornna.universe.poi.PoiCommand;
import io.github.sweetcornna.universe.station.StationCommand;
import io.github.sweetcornna.universe.story.StoryCommand;
import io.github.sweetcornna.universe.trade.CreditsCommand;
import io.github.sweetcornna.universe.world.planet.PlanetCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * 调试命令 {@code /universe dim}（S1 原型），需要 2 级权限。只给开发和测试用，提示文字不做翻译。
 *
 * <ul>
 * <li>{@code create <名称> [数量]}：创建平坦测试维度 {@code universe:<名称>}；给了数量就创建
 *     {@code <名称>_1} 到 {@code <名称>_<数量>}
 * <li>{@code tp <维度>}：把执行者传送到维度的 (0, 0) 地表；已记录但没加载的维度先加载。
 *     对主世界则送回世界出生点
 * <li>{@code load <维度>}：加载已记录但没加载的维度，不传送（服务器控制台、RCON 用）
 * <li>{@code unload <维度>|all}：卸载本模组创建的维度，仍保留记录和存档
 * <li>{@code list}：本模组创建的维度及其状态
 * <li>{@code stats [gc]}：内存和维度统计；{@code gc} 为 true 时先请求一次完整 GC，便于比较内存占用
 * </ul>
 */
public final class DimensionCommand {
    private static final DynamicCommandExceptionType UNKNOWN_DIMENSION =
            new DynamicCommandExceptionType(id -> Component.literal("未知维度：" + id));
    private static final DynamicCommandExceptionType INVALID_NAME =
            new DynamicCommandExceptionType(name -> Component.literal("维度名称只能包含小写字母、数字和 _-./：" + name));

    private static final SuggestionProvider<CommandSourceStack> ALL_DIMENSIONS = (context, builder) -> {
        Set<Identifier> ids = new LinkedHashSet<>();
        MinecraftServer server = context.getSource().getServer();
        server.levelKeys().forEach(key -> ids.add(key.identifier()));
        RuntimeDimensionsData.get(server).entries().forEach(entry -> ids.add(entry.id()));
        return SharedSuggestionProvider.suggestResource(ids, builder);
    };
    private static final SuggestionProvider<CommandSourceStack> LOADED_RUNTIME_DIMENSIONS = (context, builder) -> {
        MinecraftServer server = context.getSource().getServer();
        return SharedSuggestionProvider.suggestResource(RuntimeDimensionsData.get(server).entries().stream()
                .map(RuntimeDimensionsData.Entry::id)
                .filter(id -> server.getLevel(RuntimeDimensions.key(id)) != null), builder);
    };

    private DimensionCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Universe.MOD_ID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(PlanetCommand.build())
                .then(ShipCommand.build())
                .then(CrashSite.command())
                .then(PoiCommand.build())
                .then(SentinelCommand.build())
                .then(ResearchCommand.build())
                .then(DysonCommand.build())
                .then(StationCommand.build())
                .then(MissionCommand.build())
                .then(CreditsCommand.build())
                .then(PerfCommand.build())
                .then(BenchCommand.build())
                .then(PirateCommand.build())
                .then(AsteroidCommand.build())
                .then(StoryCommand.build())
                .then(Commands.literal("dim")
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> create(context, 0))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                                .executes(context -> create(context, IntegerArgumentType.getInteger(context, "count"))))))
                        .then(Commands.literal("tp")
                                .then(Commands.argument("dimension", IdentifierArgument.id())
                                        .suggests(ALL_DIMENSIONS)
                                        .executes(DimensionCommand::teleport)))
                        .then(Commands.literal("load")
                                .then(Commands.argument("dimension", IdentifierArgument.id())
                                        .suggests(ALL_DIMENSIONS)
                                        .executes(DimensionCommand::load)))
                        .then(Commands.literal("unload")
                                .then(Commands.literal("all")
                                        .executes(DimensionCommand::unloadAll))
                                .then(Commands.argument("dimension", IdentifierArgument.id())
                                        .suggests(LOADED_RUNTIME_DIMENSIONS)
                                        .executes(DimensionCommand::unload)))
                        .then(Commands.literal("list")
                                .executes(DimensionCommand::list))
                        .then(Commands.literal("stats")
                                .executes(context -> stats(context, false))
                                .then(Commands.argument("gc", BoolArgumentType.bool())
                                        .executes(context -> stats(context, BoolArgumentType.getBool(context, "gc")))))));
    }

    /** @param count 0 表示只创建一个、名称不加序号 */
    private static int create(CommandContext<CommandSourceStack> context, int count) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        if (!Identifier.isValidPath(name)) {
            throw INVALID_NAME.create(name);
        }
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        List<String> names = new ArrayList<>();
        if (count == 0) {
            names.add(name);
        } else {
            for (int i = 1; i <= count; i++) {
                names.add(name + "_" + i);
            }
        }

        int created = 0;
        long startNanos = System.nanoTime();
        for (String path : names) {
            ResourceKey<Level> key = RuntimeDimensions.key(Universe.id(path));
            if (server.getLevel(key) == null && !RuntimeDimensions.isManaged(server, key)) {
                created++;
            }
            RuntimeDimensions.getOrCreate(server, key, DimensionTemplate.FLAT);
        }
        long millis = (System.nanoTime() - startNanos) / 1_000_000;
        int total = names.size();
        int newCount = created;
        source.sendSuccess(() -> Component.literal("新建 " + newCount + " 个维度，已有或重新加载 " + (total - newCount)
                + " 个，用时 " + millis + " ms"), true);
        return total;
    }

    private static int teleport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(context, "dimension");
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Entity entity = source.getEntityOrException();
        ServerLevel level = RuntimeDimensions.load(server, RuntimeDimensions.key(id));
        if (level == null) {
            throw UNKNOWN_DIMENSION.create(id);
        }

        TeleportTransition transition;
        if (level.dimension() == Level.OVERWORLD && entity instanceof ServerPlayer player) {
            transition = TeleportTransition.createDefault(player, TeleportTransition.DO_NOTHING);
        } else {
            int y = level.getChunkSource().getGenerator().getBaseHeight(
                    0, 0, Heightmap.Types.MOTION_BLOCKING, level, level.getChunkSource().randomState());
            // 传送门票据让目标区块保持加载 15 秒，非玩家实体传过去后也会照常 tick、存盘
            transition = new TeleportTransition(level, new Vec3(0.5, y, 0.5), Vec3.ZERO,
                    entity.getYRot(), entity.getXRot(), TeleportTransition.PLACE_PORTAL_TICKET);
        }
        if (entity.teleport(transition) == null) {
            source.sendFailure(Component.literal("传送失败：" + id));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("已传送到 " + id), false);
        return 1;
    }

    private static int load(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(context, "dimension");
        CommandSourceStack source = context.getSource();
        long startNanos = System.nanoTime();
        if (RuntimeDimensions.load(source.getServer(), RuntimeDimensions.key(id)) == null) {
            throw UNKNOWN_DIMENSION.create(id);
        }
        long millis = (System.nanoTime() - startNanos) / 1_000_000;
        source.sendSuccess(() -> Component.literal("已加载 " + id + "，用时 " + millis + " ms"), true);
        return 1;
    }

    private static int unload(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(context, "dimension");
        CommandSourceStack source = context.getSource();
        if (!RuntimeDimensions.requestUnload(source.getServer(), RuntimeDimensions.key(id))) {
            source.sendFailure(Component.literal("无法卸载 " + id + "：不是本模组创建的、没有加载、已在卸载，或者是世界出生点所在的维度"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("开始卸载 " + id + "，区块卸完后关闭"), true);
        return 1;
    }

    private static int unloadAll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        int requested = 0;
        for (RuntimeDimensionsData.Entry entry : List.copyOf(RuntimeDimensionsData.get(server).entries())) {
            if (RuntimeDimensions.requestUnload(server, RuntimeDimensions.key(entry.id()))) {
                requested++;
            }
        }
        int count = requested;
        source.sendSuccess(() -> Component.literal("开始卸载 " + count + " 个维度"), true);
        return requested;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        List<RuntimeDimensionsData.Entry> entries = List.copyOf(RuntimeDimensionsData.get(server).entries());
        StringBuilder text = new StringBuilder("本模组创建的维度 " + entries.size() + " 个：");
        for (RuntimeDimensionsData.Entry entry : entries) {
            ResourceKey<Level> key = RuntimeDimensions.key(entry.id());
            ServerLevel level = server.getLevel(key);
            text.append("\n  ").append(entry.id()).append("  ").append(entry.template()).append("  ");
            if (level == null) {
                text.append("未加载");
            } else {
                text.append(RuntimeDimensions.isUnloading(key) ? "卸载中" : "已加载")
                        .append("，区块 ").append(level.getChunkSource().getLoadedChunksCount())
                        .append("，玩家 ").append(level.players().size());
            }
        }
        source.sendSuccess(() -> Component.literal(text.toString()), false);
        return entries.size();
    }

    private static int stats(CommandContext<CommandSourceStack> context, boolean gc) {
        if (gc) {
            System.gc();
        }
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) >> 20;
        long committedMb = runtime.totalMemory() >> 20;
        long maxMb = runtime.maxMemory() >> 20;
        int levels = 0;
        int runtimeLevels = 0;
        int chunks = 0;
        for (ServerLevel level : server.getAllLevels()) {
            levels++;
            chunks += level.getChunkSource().getLoadedChunksCount();
            if (RuntimeDimensions.isManaged(server, level.dimension())) {
                runtimeLevels++;
            }
        }
        String text = "堆内存已用 " + usedMb + " MB（已分配 " + committedMb + " MB，上限 " + maxMb + " MB）"
                + (gc ? "，已请求 GC" : "")
                + "；维度 " + levels + " 个，其中运行时维度 " + runtimeLevels + " 个；已加载区块 " + chunks + " 个";
        source.sendSuccess(() -> Component.literal(text), false);
        return (int) usedMb;
    }
}
