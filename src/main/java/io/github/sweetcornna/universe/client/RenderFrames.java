package io.github.sweetcornna.universe.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * 画面的帧号（ADR-0038）：每帧开始画之前加一。按帧缓存的东西拿它判断换没换帧。这一帧的网络包、客户端刻在加一之前就处理完了，
 * 同一帧里提取、提交时读到的世界不会再变。
 */
public final class RenderFrames {
    private static long frame;

    private RenderFrames() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(RenderFrameEvent.Pre.class, event -> frame++);
    }

    /** 当前这一帧的帧号。 */
    public static long current() {
        return frame;
    }
}
