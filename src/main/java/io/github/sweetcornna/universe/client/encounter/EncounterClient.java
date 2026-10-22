package io.github.sweetcornna.universe.client.encounter;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.HudMarker;
import io.github.sweetcornna.universe.client.ship.AutopilotScreen;
import io.github.sweetcornna.universe.network.EncounterPayloads;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * 太空遭遇的客户端部分（ADR-0024）：收到的信号在 HUD 上标出来。废弃货船标在机库口外面，遇险的货船标在船身中间，
 * 海盗露面以后标签上写着货船还剩多少船体。
 */
public final class EncounterClient {
    private static final int DERELICT_COLOR = 0x8FD8E8;
    private static final int DISTRESS_COLOR = 0xFFB040;
    private static EncounterPayloads.Signal signal = EncounterPayloads.Signal.EMPTY;

    private EncounterClient() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(EncounterPayloads.Signal.TYPE,
                (payload, context) -> context.enqueueWork(() -> signal = payload)));
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerBelow(VanillaGuiLayers.CROSSHAIR, Universe.id("encounter_signal"), EncounterClient::renderHud));
    }

    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        EncounterPayloads.Signal current = signal;
        if (player == null || current.kind() == EncounterPayloads.Signal.NONE || minecraft.gui.hud.isHidden()
                || SpaceDimensions.system(player.level().dimension()).orElse(-1) != current.system()) {
            return;
        }
        Vec3 target = new Vec3(current.x(), current.y(), current.z());
        String distance = AutopilotScreen.distance((int) Math.round(player.position().distanceTo(target)));
        boolean derelict = current.kind() == EncounterPayloads.Signal.DERELICT;
        Component label;
        if (derelict) {
            label = Component.translatable("hud.universe.encounter.derelict", distance);
        } else if (current.hull() >= 0) {
            label = Component.translatable("hud.universe.encounter.distress_hull", current.hull(), distance);
        } else {
            label = Component.translatable("hud.universe.encounter.distress", distance);
        }
        float time = player.tickCount + delta.getGameTimeDeltaPartialTick(false);
        float pulse = 0.7F + 0.3F * Mth.sin(time * (derelict ? 0.1F : 0.3F));
        HudMarker.draw(graphics, minecraft, target, derelict ? DERELICT_COLOR : DISTRESS_COLOR, label, pulse);
    }
}
