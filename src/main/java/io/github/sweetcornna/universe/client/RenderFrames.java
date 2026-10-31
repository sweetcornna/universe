package io.github.sweetcornna.universe.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * 画面的帧号（ADR-0038）：每帧画完（{@link RenderFrameEvent.Post}）加一。按帧缓存的东西拿它判断换没换帧：下一帧的网络包、
 * 客户端刻、提取都在加一之后，同一次提取里读到的世界不会再变。全景截图这种不走完整一帧的提取帧号不变，用到插值的缓存要另外
 * 比一下插值。
 */
public final class RenderFrames {
    private static long frame;

    private RenderFrames() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(RenderFrameEvent.Post.class, event -> frame++);
    }

    /** 当前这一帧的帧号。 */
    public static long current() {
        return frame;
    }
}
