package io.github.sweetcornna.universe.client.civ;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.core.civ.AlienTongue;
import io.github.sweetcornna.universe.core.civ.CivState;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * 客户端看到的外星文明（ADR-0027）：自己的声望和词（服务端同步来的附件），主导种族和外星写法按星系种子在本地算，不用同步。
 */
public final class ClientCiv {
    private static final Map<Race, AlienTongue> TONGUES = new EnumMap<>(Race.class);
    private static long tonguesSeed;

    private ClientCiv() {
    }

    /** 自己的状态；还没进世界时是初始状态。 */
    public static CivState state() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? new CivState() : player.getData(UniverseAttachments.CIVILIZATION);
    }

    /** 这个世界里一个种族的语言；还没收到星系时为空。 */
    public static Optional<AlienTongue> tongue(Race race) {
        Optional<Galaxy> galaxy = ClientGalaxy.galaxy();
        if (galaxy.isEmpty()) {
            return Optional.empty();
        }
        long seed = galaxy.get().seed();
        if (seed != tonguesSeed) {
            TONGUES.clear();
            tonguesSeed = seed;
        }
        return Optional.of(TONGUES.computeIfAbsent(race, r -> AlienTongue.of(seed, r)));
    }

    /** 恒星系的主导种族。 */
    public static Optional<Race> dominant(int system) {
        return ClientGalaxy.galaxy().flatMap(galaxy -> galaxy.system(system)).map(Race::dominant);
    }

    /** 所在恒星系的主导种族；不在任何恒星系里时为空。 */
    public static Optional<Race> dominantHere() {
        OptionalInt system = ClientGalaxy.currentSystem();
        return system.isPresent() ? dominant(system.getAsInt()) : Optional.empty();
    }
}
