package io.github.sweetcornna.universe.world;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import io.github.sweetcornna.universe.core.dimension.IdleTimer;
import io.github.sweetcornna.universe.data.RuntimeDimensionsData;
import io.github.sweetcornna.universe.network.DimensionListPayload;
import io.github.sweetcornna.universe.perf.ServerPerf;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.ForcedChunkManager;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;

/**
 * 在运行时创建、卸载维度，并在服务器重启后恢复（ADR-0004，M1 原型 S1）。
 *
 * <p>原版只在启动时按 {@code LEVEL_STEM} 注册表建好全部维度。这里照 {@code MinecraftServer#createLevels}
 * 的做法自己构造 {@link ServerLevel}，放进 {@code MinecraftServer#levels}（由访问转换器开放）。
 * 不改 {@code LEVEL_STEM} 注册表，也不写 {@code world_gen_settings.dat}：26.3 在启动之后不再读这个注册表，
 * 维度清单和生成参数由 {@link RuntimeDimensionsData} 保存。
 *
 * <p>只能在服务端主线程调用。服务器 tick 各维度时直接遍历 {@code levels}，所以维度 tick 期间不能增删维度：
 * 创建会检查这一点，卸载一律排到 tick 末尾分步执行。
 *
 * <p>没有玩家、没有强制加载区块的维度空闲 {@link #IDLE_UNLOAD_TICKS} 刻后自动卸载。玩家上线时，配置阶段先把他下线时
 * 所在的维度加载回来（{@link LoadPlayerDimensionTask}）；死亡后重生点所在的维度没加载时，重生前加载它。
 */
public final class RuntimeDimensions {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** 卸载时最多等这么多 tick 让区块卸完、存完，超时就直接关闭。 */
    private static final int MAX_DRAIN_TICKS = 30 * 20;
    /** 空闲多久自动卸载：5 分钟。 */
    static final int IDLE_UNLOAD_TICKS = 5 * 60 * 20;
    /** 每隔多少刻检查一次空闲。 */
    private static final int IDLE_CHECK_INTERVAL = 20;
    private static final IdleTimer<ResourceKey<Level>> IDLE = new IdleTimer<>(IDLE_UNLOAD_TICKS);

    private static final Map<ResourceKey<Level>, PendingUnload> PENDING_UNLOADS = new LinkedHashMap<>();
    /** 服务端是否正在 tick 某个维度（{@link LevelTickEvent.Pre} 与 {@link LevelTickEvent.Post} 之间）。 */
    private static boolean tickingLevel;

    private RuntimeDimensions() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterConfigurationTasksEvent.class, LoadPlayerDimensionTask::register);
        gameBus.addListener(PlayerRespawnPositionEvent.class, RuntimeDimensions::loadRespawnDimension);
        gameBus.addListener(EventPriority.HIGHEST, LevelTickEvent.Pre.class, event -> {
            if (!event.getLevel().isClientSide()) {
                tickingLevel = true;
            }
        });
        gameBus.addListener(EventPriority.LOWEST, LevelTickEvent.Post.class, event -> {
            if (!event.getLevel().isClientSide()) {
                tickingLevel = false;
            }
        });
        // ServerStartingEvent 在原版维度建好之后、第一个 tick 之前，玩家还连不进来
        gameBus.addListener(ServerStartingEvent.class, event -> restore(event.getServer()));
        gameBus.addListener(EventPriority.LOWEST, ServerTickEvent.Post.class, event -> {
            long start = ServerPerf.begin();
            checkIdle(event.getServer());
            processUnloads(event.getServer());
            ServerPerf.end("runtime_dimensions", start);
        });
        gameBus.addListener(ServerStoppedEvent.class, event -> {
            // 单人游戏退出存档后 JVM 还在，下次进入别的存档前要清空
            PENDING_UNLOADS.clear();
            IDLE.clear();
            tickingLevel = false;
        });
    }

    public static ResourceKey<Level> key(Identifier id) {
        return ResourceKey.create(Registries.DIMENSION, id);
    }

    /** 是否是本模组创建、记录在案的维度（不论当前是否加载）。 */
    public static boolean isManaged(MinecraftServer server, ResourceKey<Level> key) {
        return RuntimeDimensionsData.get(server).get(key.identifier()).isPresent();
    }

    public static boolean isUnloading(ResourceKey<Level> key) {
        return PENDING_UNLOADS.containsKey(key);
    }

    /** 现在能不能创建、加载维度：服务端主线程上，而且不在某个维度的 tick 里。 */
    static boolean canModifyLevels(MinecraftServer server) {
        return server.isSameThread() && !tickingLevel;
    }

    /**
     * 取得维度，必要时创建或加载。已有同名维度（包括原版和数据包维度）时直接返回它。
     *
     * @throws IllegalStateException 在维度 tick 期间调用，或者记录里的模板已不存在
     */
    public static ServerLevel getOrCreate(MinecraftServer server, ResourceKey<Level> key, DimensionTemplate template) {
        ServerLevel existing = load(server, key);
        if (existing != null) {
            return existing;
        }
        checkCanModifyLevels(server, key);
        ServerLevel level = createLevel(server, key, template);
        RuntimeDimensionsData.get(server).put(new RuntimeDimensionsData.Entry(key.identifier(), template.serializedName(), true));
        return level;
    }

    /**
     * 取得已加载的维度，或者加载一个已记录、但当前没加载的维度。正在卸载的维度会取消卸载。
     *
     * @return 维度；既没加载、也不在记录里时返回 {@code null}
     */
    public static @Nullable ServerLevel load(MinecraftServer server, ResourceKey<Level> key) {
        RuntimeDimensionsData data = RuntimeDimensionsData.get(server);
        ServerLevel level = server.getLevel(key);
        if (level != null) {
            PendingUnload pending = PENDING_UNLOADS.remove(key);
            if (pending != null) {
                if (pending.ticketsDeactivated) {
                    reactivateTickets(level);
                }
                data.setLoaded(key.identifier(), true);
                LOGGER.info("Cancelled unloading of runtime dimension {}", key.identifier());
            }
            return level;
        }

        Optional<RuntimeDimensionsData.Entry> entry = data.get(key.identifier());
        if (entry.isEmpty()) {
            return null;
        }
        checkCanModifyLevels(server, key);
        level = createLevel(server, key, template(entry.get()));
        data.setLoaded(key.identifier(), true);
        return level;
    }

    /**
     * 申请卸载本模组创建的维度。本 tick 末尾把里面的玩家送回世界出生点并停用区块票据，
     * 等区块卸完、存完再关闭维度，通常要几秒。维度仍留在记录里，之后可以再加载。
     *
     * @return 是否受理；维度不归本模组管、没有加载、已在卸载，或者是世界出生点所在的维度时返回 {@code false}
     */
    public static boolean requestUnload(MinecraftServer server, ResourceKey<Level> key) {
        ServerLevel level = server.getLevel(key);
        if (level == null || !isManaged(server, key) || PENDING_UNLOADS.containsKey(key)) {
            return false;
        }
        if (server.findRespawnDimension() == level) {
            // 玩家会被送回世界出生点，出生点就在这个维度里时没有地方可送
            return false;
        }
        RuntimeDimensionsData.get(server).setLoaded(key.identifier(), false);
        PENDING_UNLOADS.put(key, new PendingUnload(server.getTickCount()));
        return true;
    }

    /** 服务器启动时重建上次处于加载状态的维度。 */
    private static void restore(MinecraftServer server) {
        PENDING_UNLOADS.clear();
        IDLE.clear();
        int restored = 0;
        long startNanos = System.nanoTime();
        for (RuntimeDimensionsData.Entry entry : List.copyOf(RuntimeDimensionsData.get(server).entries())) {
            if (!entry.loaded()) {
                continue;
            }
            ResourceKey<Level> key = key(entry.id());
            if (server.getLevel(key) != null) {
                LOGGER.warn("Runtime dimension {} clashes with an existing dimension, skipped", entry.id());
                continue;
            }
            try {
                createLevel(server, key, template(entry));
                restored++;
            } catch (RuntimeException e) {
                // 一个维度坏了不应该拖垮整个服务器：记下来，跳过
                LOGGER.error("Failed to restore runtime dimension {}", entry.id(), e);
            }
        }
        if (restored > 0) {
            LOGGER.info("Restored {} runtime dimensions in {} ms", restored, (System.nanoTime() - startNanos) / 1_000_000);
        }
    }

    private static DimensionTemplate template(RuntimeDimensionsData.Entry entry) {
        return DimensionTemplate.byName(entry.template()).orElseThrow(() -> new IllegalStateException(
                "Unknown template '" + entry.template() + "' for runtime dimension " + entry.id()));
    }

    private static void checkCanModifyLevels(MinecraftServer server, ResourceKey<Level> key) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Runtime dimension " + key.identifier() + " must be created on the server thread");
        }
        if (tickingLevel) {
            throw new IllegalStateException("Cannot create runtime dimension " + key.identifier()
                    + " while a level is ticking; defer it to ServerTickEvent");
        }
    }

    private static ServerLevel createLevel(MinecraftServer server, ResourceKey<Level> key, DimensionTemplate template) {
        long startNanos = System.nanoTime();
        LevelStem stem = template.createLevelStem(server, key);
        WorldData worldData = server.getWorldData();
        long seed = server.getWorldGenSettings().options().seed();
        // 参数与 MinecraftServer#createLevels 创建下界、末地时相同；executor 就是那里用的 Util.backgroundExecutor()
        ServerLevel level = new ServerLevel(
                server,
                Util.backgroundExecutor(),
                server.storageSource,
                new DerivedLevelData(worldData, worldData.overworldData()),
                key,
                stem,
                worldData.isDebugWorld(),
                BiomeManager.obfuscateSeed(seed),
                List.of(),
                false);
        server.levels.put(key, level);
        NeoForge.EVENT_BUS.post(new LevelEvent.Load(level));
        level.getWorldBorder().setAbsoluteMaxSize(server.getAbsoluteMaxWorldSize());
        server.getPlayerList().addWorldborderListener(level);
        // 以下两步原版在 MinecraftServer#prepareLevels 里对启动时的维度做
        reactivateTickets(level);
        level.setSpawnSettings(level.isSpawningMonsters());
        PacketDistributor.sendToAllPlayers(DimensionListPayload.added(key));
        LOGGER.info("Created runtime dimension {} in {} ms", key.identifier(), (System.nanoTime() - startNanos) / 1_000_000);
        return level;
    }

    /** 恢复上次关闭维度时停用的区块票据（强制加载、传送门等），和 {@code MinecraftServer#prepareLevels} 相同。 */
    private static void reactivateTickets(ServerLevel level) {
        TicketStorage savedTickets = level.getDataStorage().get(TicketStorage.TYPE);
        if (savedTickets != null) {
            savedTickets.activateAllDeactivatedTickets();
            ForcedChunkManager.activateAllDeactivatedTickets(level, savedTickets);
        }
    }

    /** 每秒检查一次：本模组管的、已加载的维度，空闲够久就申请卸载。世界出生点所在的维度不卸载。 */
    private static void checkIdle(MinecraftServer server) {
        if (server.getTickCount() % IDLE_CHECK_INTERVAL != 0) {
            return;
        }
        ServerLevel respawn = server.findRespawnDimension();
        List<ResourceKey<Level>> candidates = new ArrayList<>();
        for (RuntimeDimensionsData.Entry entry : RuntimeDimensionsData.get(server).entries()) {
            ResourceKey<Level> key = key(entry.id());
            ServerLevel level = server.getLevel(key);
            if (level != null && level != respawn && !PENDING_UNLOADS.containsKey(key)) {
                candidates.add(key);
            }
        }
        for (ResourceKey<Level> key : IDLE.advance(candidates, key -> isBusy(server.getLevel(key)), IDLE_CHECK_INTERVAL)) {
            if (requestUnload(server, key)) {
                LOGGER.info("Unloading runtime dimension {} after {} s without players", key.identifier(), IDLE_UNLOAD_TICKS / 20);
            }
        }
    }

    /** 维度在用：有玩家，或者有强制加载的区块（原版的 /forceload，以及模组的方块、实体加载器）。 */
    private static boolean isBusy(ServerLevel level) {
        if (!level.players().isEmpty() || !level.getForceLoadedChunks().isEmpty()) {
            return true;
        }
        TicketStorage tickets = level.getDataStorage().get(TicketStorage.TYPE);
        return tickets != null && (!tickets.getBlockForcedChunks().isEmpty() || !tickets.getEntityForcedChunks().isEmpty());
    }

    /**
     * 重生前：重生点（床、重生锚）在一个已卸载的维度里时先把它加载回来，再重新算重生位置。
     * 不处理的话原版找不到维度，会把玩家送回世界出生点。
     */
    private static void loadRespawnDimension(PlayerRespawnPositionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getRespawnConfig() == null) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        ResourceKey<Level> key = ServerPlayer.RespawnConfig.getDimensionOrDefault(player.getRespawnConfig());
        if (server.getLevel(key) != null || !isManaged(server, key) || !canModifyLevels(server)) {
            return;
        }
        try {
            load(server, key);
        } catch (RuntimeException e) {
            LOGGER.error("Failed to load respawn dimension {} for {}", key.identifier(), player.getGameProfile().name(), e);
            return;
        }
        TeleportTransition transition = player.findRespawnPositionAndUseSpawnBlock(!event.isFromEndFight(), TeleportTransition.DO_NOTHING);
        event.setTeleportTransition(transition);
        // 和原版一样：床或重生锚没了的话，新玩家不继承这个重生点
        event.setCopyOriginalSpawnPosition(!transition.missingRespawnBlock());
    }

    /** 每个服务器 tick 末尾推进待卸载的维度：送走玩家、停用票据、等区块卸完，最后保存并关闭。 */
    private static void processUnloads(MinecraftServer server) {
        if (PENDING_UNLOADS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<ResourceKey<Level>, PendingUnload>> iterator = PENDING_UNLOADS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ResourceKey<Level>, PendingUnload> entry = iterator.next();
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) {
                iterator.remove();
                continue;
            }
            PendingUnload pending = entry.getValue();
            evacuatePlayers(level);
            if (!pending.ticketsDeactivated) {
                // 和 MinecraftServer#stopServer 一样：停用的票据存进 TicketStorage，下次加载时恢复；
                // 维度留在 levels 里照常 tick，区块按原版流程卸载、保存
                level.noSave = false;
                level.getChunkSource().deactivateTicketsOnClosing();
                pending.ticketsDeactivated = true;
            }
            int waitedTicks = server.getTickCount() - pending.requestedTick;
            boolean drained = !level.getChunkSource().chunkMap.hasWork();
            if (!drained && waitedTicks < MAX_DRAIN_TICKS) {
                continue;
            }
            iterator.remove();
            if (!drained) {
                LOGGER.warn("Runtime dimension {} still has chunk work after {} ticks, closing it anyway",
                        level.dimension().identifier(), waitedTicks);
            }
            close(server, level, waitedTicks);
        }
    }

    private static void evacuatePlayers(ServerLevel level) {
        for (ServerPlayer player : List.copyOf(level.players())) {
            // 送回世界出生点。重生点设在这个维度的玩家，死亡时原版找不到维度，会退回世界出生点
            player.teleport(TeleportTransition.createDefault(player, TeleportTransition.DO_NOTHING));
        }
    }

    private static void close(MinecraftServer server, ServerLevel level, int waitedTicks) {
        ResourceKey<Level> key = level.dimension();
        long startNanos = System.nanoTime();
        server.levels.remove(key);
        try {
            // close() 里也会保存区块和实体；先调 save() 是为了和关服流程一样触发 LevelEvent.Save
            level.save(null, true, false);
            level.close();
        } catch (IOException e) {
            LOGGER.error("Failed to close runtime dimension {}", key.identifier(), e);
        }
        // NeoForge 在这个事件里清理该维度的假玩家（FakePlayerFactory），不发会泄漏整个维度
        NeoForge.EVENT_BUS.post(new LevelEvent.Unload(level));
        // 按维度记的 tick 耗时，原版不会删
        server.perWorldTickTimes.remove(key);
        PacketDistributor.sendToAllPlayers(DimensionListPayload.removed(key));
        LOGGER.info("Unloaded runtime dimension {} after {} ticks (closing took {} ms)",
                key.identifier(), waitedTicks, (System.nanoTime() - startNanos) / 1_000_000);
    }

    private static final class PendingUnload {
        private final int requestedTick;
        private boolean ticketsDeactivated;

        private PendingUnload(int requestedTick) {
            this.requestedTick = requestedTick;
        }
    }
}
