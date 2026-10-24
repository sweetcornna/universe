package io.github.sweetcornna.universe.client.audio;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/** 最近放过的声音，开发脚本的 {@code sounds} 写进日志，验收时看声音真的放了（ADR-0029 §六）。 */
public final class SoundLog {
    private static final int CAPACITY = 512;

    private record Entry(long time, String id, double x, double y, double z) {
    }

    private static final Deque<Entry> RECENT = new ArrayDeque<>();

    private SoundLog() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(PlaySoundEvent.class, event -> record(event.getSound()));
    }

    private static void record(SoundInstance sound) {
        if (sound == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        long time = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        RECENT.addLast(new Entry(time, sound.getIdentifier().toString(), sound.getX(), sound.getY(), sound.getZ()));
        while (RECENT.size() > CAPACITY) {
            RECENT.removeFirst();
        }
    }

    /** 最近 seconds 秒里放过的声音，按事件数次数。 */
    public static String describe(int seconds) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        Map<String, Integer> counts = new TreeMap<>();
        for (Entry entry : RECENT) {
            if (now - entry.time() <= seconds * 20L) {
                counts.merge(entry.id(), 1, Integer::sum);
            }
        }
        return "sounds in last " + seconds + "s: " + counts + "; " + minecraft.getSoundManager().getChannelDebugString();
    }
}
