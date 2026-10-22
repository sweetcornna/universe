package io.github.sweetcornna.universe.client.darkfog;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.HudMarker;
import io.github.sweetcornna.universe.core.darkfog.Tactics;
import io.github.sweetcornna.universe.network.DarkFogPayloads;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.sentinel.SentinelHud;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * 黑雾的客户端部分（ADR-0028）：单位、建筑、弹体的渲染器，和来袭预警。
 *
 * <p>收到预警时聊天栏写一句从哪边来、多少个；HUD 顶上写"黑雾来袭"和倒计时、人数，屏幕里（或屏幕边上的箭头）标出进攻部队出现的地方。
 * 到了以后写"黑雾到了"，打完收到清除再提示一句，换了维度就不画。
 */
public final class DarkFogClient {
    private static final int RED = 0xFF3A4A;
    /** 到了以后多久没收到更新就当打完了（10 分钟）。 */
    private static final long STALE = 20 * 60 * 10;
    private static DarkFogPayloads.@Nullable Warning warning;
    private static long receivedAt;
    private static @Nullable ResourceKey<Level> dimension;

    private DarkFogClient() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(UniverseEntities.DARK_FOG_BUILDING.get(), DarkFogBuildingRenderer::new);
            event.registerEntityRenderer(UniverseEntities.DARK_FOG_RAIDER.get(), DarkFogUnitRenderer::raider);
            event.registerEntityRenderer(UniverseEntities.DARK_FOG_RANGER.get(), DarkFogUnitRenderer::ranger);
            event.registerEntityRenderer(UniverseEntities.DARK_FOG_GUARDIAN.get(), DarkFogUnitRenderer::guardian);
            event.registerEntityRenderer(UniverseEntities.DARK_FOG_BOLT.get(), DarkFogProjectileRenderer::bolt);
            event.registerEntityRenderer(UniverseEntities.DARK_FOG_SHELL.get(), DarkFogProjectileRenderer::shell);
        });
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(DarkFogPayloads.Warning.TYPE,
                (payload, context) -> context.enqueueWork(() -> receive(payload))));
        modBus.addListener(RegisterGuiLayersEvent.class, event -> {
            event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, Universe.id("dark_fog_warning"), DarkFogClient::renderBanner);
            event.registerBelow(VanillaGuiLayers.CROSSHAIR, Universe.id("dark_fog_marker"), DarkFogClient::renderMarker);
        });
    }

    private static void receive(DarkFogPayloads.Warning payload) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        DarkFogPayloads.Warning previous = active(minecraft);
        if (payload.ticks() < 0) {
            if (previous != null) {
                player.sendSystemMessage(Component.translatable("message.universe.dark_fog.over").withStyle(ChatFormatting.GRAY));
            }
            warning = null;
            return;
        }
        boolean fresh = previous == null || !previous.from().equals(payload.from()) || !previous.target().equals(payload.target());
        warning = payload;
        receivedAt = minecraft.level.getGameTime();
        dimension = minecraft.level.dimension();
        if (fresh && payload.ticks() > 0) {
            player.sendSystemMessage(Component.translatable("message.universe.dark_fog.warning", direction(payload),
                    payload.raiders(), payload.rangers(), Mth.ceil(payload.ticks() / 20F)).withStyle(ChatFormatting.RED));
        }
    }

    /** 进攻部队在目标的哪一边。 */
    private static Component direction(DarkFogPayloads.Warning payload) {
        int index = Tactics.compass(payload.from().getX() - payload.target().getX(), payload.from().getZ() - payload.target().getZ());
        return Component.translatable("hud.universe.dark_fog.dir." + index);
    }

    /** 还在这个维度里、没过期的预警。 */
    private static DarkFogPayloads.@Nullable Warning active(Minecraft minecraft) {
        DarkFogPayloads.Warning current = warning;
        if (current == null || minecraft.level == null || minecraft.level.dimension() != dimension) {
            return null;
        }
        if (remaining(minecraft, current) <= 0 && minecraft.level.getGameTime() - receivedAt - current.ticks() > STALE) {
            warning = null;
            return null;
        }
        return current;
    }

    private static int remaining(Minecraft minecraft, DarkFogPayloads.Warning current) {
        if (minecraft.level == null || current.ticks() <= 0) {
            return 0;
        }
        return (int) Math.max(0, current.ticks() - (minecraft.level.getGameTime() - receivedAt));
    }

    private static void renderBanner(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        DarkFogPayloads.Warning current = active(minecraft);
        if (player == null || current == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        Font font = minecraft.font;
        int cx = graphics.guiWidth() / 2;
        int y = top(player);
        int left = remaining(minecraft, current);
        float time = player.tickCount + delta.getGameTimeDeltaPartialTick(false);
        float pulse = left > 0 && left < 200 ? 0.7F + 0.3F * Mth.sin(time * 0.6F) : 1;
        Component title = left > 0
                ? Component.translatable("hud.universe.dark_fog.incoming", String.format(Locale.ROOT, "%d:%02d", left / 1200, left / 20 % 60))
                : Component.translatable("hud.universe.dark_fog.arrived");
        Component units = Component.translatable("hud.universe.dark_fog.units", current.raiders(), current.rangers());
        int width = Math.max(font.width(title), font.width(units));
        graphics.fill(cx - width / 2 - 4, y - 2, cx + width / 2 + 4, y + 21, 0x90000000);
        graphics.text(font, title, cx - font.width(title) / 2, y, 0xFF000000 | DarkFogUnitRenderer.scale(RED, pulse), true);
        graphics.text(font, units, cx - font.width(units) / 2, y + 11, 0xFFD8D0D4, true);
    }

    /** 守卫机的 HUD 也在顶上：有它时往下让。 */
    private static int top(LocalPlayer player) {
        SentinelHud hud = player.getData(UniverseAttachments.SENTINEL_HUD);
        if (hud.planet() == 0) {
            return 4;
        }
        if (hud.level() > 0) {
            return 44;
        }
        return hud.dormant() > 0 || hud.alert() > 0 ? 16 : 4;
    }

    private static void renderMarker(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        DarkFogPayloads.Warning current = active(minecraft);
        if (player == null || current == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        int left = remaining(minecraft, current);
        Vec3 from = Vec3.atCenterOf(current.from());
        int distance = (int) Math.round(player.position().distanceTo(from));
        Component label = left > 0
                ? Component.translatable("hud.universe.dark_fog.marker_eta", String.format(Locale.ROOT, "%d:%02d", left / 1200, left / 20 % 60), distance)
                : Component.translatable("hud.universe.dark_fog.marker", distance);
        float time = player.tickCount + delta.getGameTimeDeltaPartialTick(false);
        HudMarker.draw(graphics, minecraft, from, RED, label, 0.7F + 0.3F * Mth.sin(time * 0.3F));
    }
}
