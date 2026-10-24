package io.github.sweetcornna.universe.hazard;

import java.util.function.ToIntFunction;

import net.minecraft.world.level.Level;

/**
 * 局部危害在客户端的样子要用到的东西，客户端启动时换上真的（{@code client.hazard.HazardClient}），服务端用默认值，
 * 免得实体类直接引用客户端的类。
 */
public final class HazardLook {
    /** 当前星球的沙土颜色（RGB），尘卷风的尘用。 */
    public static volatile ToIntFunction<Level> sand = level -> 0xC8A878;
    /** 镜头晃一晃：强度（度）、几刻。 */
    public static volatile Shake shake = (strength, ticks) -> {
    };

    public interface Shake {
        void shake(float strength, int ticks);
    }

    private HazardLook() {
    }

    public static int sand(Level level) {
        return sand.applyAsInt(level);
    }
}
