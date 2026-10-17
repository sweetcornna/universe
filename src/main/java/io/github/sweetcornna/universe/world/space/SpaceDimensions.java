package io.github.sweetcornna.universe.world.space;

import java.util.OptionalInt;
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
 * 太空维度：每个恒星系一个，ID 是 {@code universe:s<恒星系>/space}（玩法设计 §6）。
 * 和星球维度一样在运行时按需创建（ADR-0004），里面是空的虚空。
 */
public final class SpaceDimensions {
    /** 太空的维度类型：没有昼夜，天空换成太空渲染器，上下各 256 格（数据生成里做好）。 */
    public static final ResourceKey<DimensionType> TYPE = ResourceKey.create(Registries.DIMENSION_TYPE, Universe.id("space"));
    private static final Pattern PATH = Pattern.compile("s(\\d+)/space");

    private SpaceDimensions() {
    }

    public static ResourceKey<Level> key(int system) {
        return ResourceKey.create(Registries.DIMENSION, Universe.id("s" + system + "/space"));
    }

    public static boolean isSpace(ResourceKey<Level> dimension) {
        return system(dimension).isPresent();
    }

    /** 太空维度属于哪个恒星系。 */
    public static OptionalInt system(ResourceKey<Level> dimension) {
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

    /** 取得（必要时创建）恒星系的太空维度。只能在服务端主线程、维度 tick 之外调用。 */
    public static ServerLevel getOrCreate(MinecraftServer server, int system) {
        return RuntimeDimensions.getOrCreate(server, key(system), DimensionTemplate.SPACE);
    }
}
