package io.github.sweetcornna.universe.world;

import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * 登录的配置阶段里、原版的 {@code PrepareSpawnTask} 之前：读玩家存档里的维度，是本模组管的、当前没加载的维度就先加载回来
 * （ADR-0004 的遗留问题）。不这样做的话，原版找不到维度，会把玩家放进世界出生点所在的维度、坐标却不变，
 * 常常卡在地下或者掉进虚空。
 *
 * <p>{@link #start} 可能在网络线程上被调用，所以活儿放在 {@link #tick} 里做：它在服务器 tick 末尾处理连接时调用，
 * 这时不在任何维度的 tick 里，可以创建维度。
 */
final class LoadPlayerDimensionTask implements ConfigurationTask {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final Type TYPE = new Type(Universe.id("load_player_dimension"));

    private final MinecraftServer server;
    private final NameAndId player;

    private LoadPlayerDimensionTask(MinecraftServer server, NameAndId player) {
        this.server = server;
        this.player = player;
    }

    static void register(RegisterConfigurationTasksEvent event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && event.getListener() instanceof ServerCommonPacketListenerImpl listener) {
            event.register(new LoadPlayerDimensionTask(server, new NameAndId(listener.getOwner())));
        }
    }

    @Override
    public void start(Consumer<Packet<?>> connection) {
    }

    @Override
    public boolean tick() {
        if (!RuntimeDimensions.canModifyLevels(this.server)) {
            return false;
        }
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(LOGGER)) {
            Optional<ResourceKey<Level>> saved = this.server.getPlayerList().loadPlayerData(this.player)
                    .map(tag -> TagValueInput.create(reporter, this.server.registryAccess(), tag))
                    .flatMap(input -> input.read(ServerPlayer.SavedPosition.MAP_CODEC))
                    .flatMap(ServerPlayer.SavedPosition::dimension);
            saved.filter(key -> this.server.getLevel(key) == null || RuntimeDimensions.isUnloading(key))
                    .filter(key -> RuntimeDimensions.isManaged(this.server, key))
                    .ifPresent(key -> {
                        RuntimeDimensions.load(this.server, key);
                        LOGGER.info("Loaded runtime dimension {} for joining player {}", key.identifier(), this.player.name());
                    });
        } catch (RuntimeException e) {
            // 加载失败就照原版的退路走，不要因此把玩家踢掉
            LOGGER.error("Failed to load the saved dimension of {}", this.player.name(), e);
        }
        return true;
    }

    @Override
    public Type type() {
        return TYPE;
    }
}
