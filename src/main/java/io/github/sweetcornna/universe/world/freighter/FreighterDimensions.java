package io.github.sweetcornna.universe.world.freighter;

import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.world.DimensionTemplate;
import io.github.sweetcornna.universe.world.RuntimeDimensions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * 货船的船舱（ADR-0024）：每艘货船一个维度，ID 是 {@code universe:freighter/<编号>}。和星球、太空一样在运行时按需创建，
 * 没人在里面时卸载（ADR-0004）。
 */
public final class FreighterDimensions {
    /** 船舱的维度类型：照太空的来（没有昼夜，天空由太空渲染器画），只是床能设重生点。数据生成里做好。 */
    public static final ResourceKey<DimensionType> TYPE = ResourceKey.create(Registries.DIMENSION_TYPE, Universe.id("freighter"));
    private static final Pattern PATH = Pattern.compile("freighter/(\\d+)");
    /** 解析过的维度：每个玩家每刻都要问，维度键是驻留的，结果不会变（服务端和客户端的线程都会读）。 */
    private static final Map<ResourceKey<Level>, OptionalInt> IDS = new ConcurrentHashMap<>();

    private FreighterDimensions() {
    }

    public static ResourceKey<Level> key(int id) {
        return ResourceKey.create(Registries.DIMENSION, Universe.id("freighter/" + id));
    }

    /** 维度是哪艘货船的船舱。 */
    public static OptionalInt id(ResourceKey<Level> dimension) {
        return IDS.computeIfAbsent(dimension, FreighterDimensions::parseId);
    }

    private static OptionalInt parseId(ResourceKey<Level> dimension) {
        if (!dimension.identifier().getNamespace().equals(Universe.MOD_ID)) {
            return OptionalInt.empty();
        }
        Matcher matcher = PATH.matcher(dimension.identifier().getPath());
        if (!matcher.matches()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    public static boolean isInterior(ResourceKey<Level> dimension) {
        return id(dimension).isPresent();
    }

    /** 取得（必要时创建）货船的船舱。只能在服务端主线程、维度 tick 之外调用。 */
    public static ServerLevel getOrCreate(MinecraftServer server, int id) {
        return RuntimeDimensions.getOrCreate(server, key(id), DimensionTemplate.FREIGHTER);
    }
}
