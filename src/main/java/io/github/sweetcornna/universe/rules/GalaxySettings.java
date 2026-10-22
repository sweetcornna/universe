package io.github.sweetcornna.universe.rules;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.registry.UniverseGameRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * 建世界时定下、以后不变的星系设置（ADR-0028 §七），存在 {@code data/universe/galaxy_settings.dat}。
 *
 * <p>恒星系数量在第一次打开存档时从游戏规则 {@code star_systems} 读一次记下来，以后改规则不变（改了会换掉整个星系）。
 * 这个版本以前的存档没有这份记录，第一次打开时规则还是默认的 64，记下的也是 64，和以前一样。
 *
 * <p>星系在世界生成的后台线程里也要用，所以数量在服务器启动前就读好、缓存起来。
 */
public final class GalaxySettings extends SavedData {
    private static final Codec<GalaxySettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("star_systems").forGetter(settings -> settings.starSystems)
    ).apply(i, GalaxySettings::new));
    private static final SavedDataType<GalaxySettings> TYPE =
            new SavedDataType<>(Universe.id("galaxy_settings"), GalaxySettings::new, CODEC);

    /** 先写数量再写服务器，读的时候先看服务器：看到的是这个服务器，数量就是写好的。 */
    private static volatile @Nullable MinecraftServer cachedServer;
    private static volatile int cachedCount;

    /** 没读过游戏规则时是 0。 */
    private int starSystems;

    private GalaxySettings() {
    }

    private GalaxySettings(int starSystems) {
        this.starSystems = starSystems;
    }

    public static void registerEvents(IEventBus gameBus) {
        // 在服务器线程上先读好：世界生成的线程用到星系时不用碰存档
        gameBus.addListener(ServerAboutToStartEvent.class, event -> starSystems(event.getServer()));
        gameBus.addListener(ServerStoppedEvent.class, event -> clear());
    }

    /** 这个存档的恒星系数量。 */
    public static int starSystems(MinecraftServer server) {
        if (cachedServer == server) {
            return cachedCount;
        }
        synchronized (GalaxySettings.class) {
            if (cachedServer != server) {
                GalaxySettings settings = server.getDataStorage().computeIfAbsent(TYPE);
                if (settings.starSystems <= 0) {
                    settings.starSystems = server.getGameRules().get(UniverseGameRules.STAR_SYSTEMS.get());
                    settings.setDirty();
                }
                cachedCount = Math.max(1, Math.min(GalaxyGenerator.MAX_SYSTEM_COUNT, settings.starSystems));
                cachedServer = server;
            }
            return cachedCount;
        }
    }

    private static synchronized void clear() {
        cachedServer = null;
        cachedCount = 0;
    }
}
