package io.github.sweetcornna.universe.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import org.jspecify.annotations.Nullable;

/** 循环声的小工具（ADR-0029）。 */
public final class Loops {
    private Loops() {
    }

    /**
     * 这条声音还在放。不能只看 {@code isStopped()}：换维度、重载资源时原版把所有声音一起停掉，
     * 声音自己的"已停"标记不会变，只看它就永远不会重开。
     */
    public static boolean playing(@Nullable SoundInstance sound) {
        if (sound == null || sound instanceof TickableSoundInstance tickable && tickable.isStopped()) {
            return false;
        }
        return Minecraft.getInstance().getSoundManager().isActive(sound);
    }
}
