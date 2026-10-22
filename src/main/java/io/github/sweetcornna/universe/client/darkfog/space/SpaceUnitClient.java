package io.github.sweetcornna.universe.client.darkfog.space;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.HudMarker;
import io.github.sweetcornna.universe.core.darkfog.Tactics;
import io.github.sweetcornna.universe.network.LancerPayloads;
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
 * 太空单位的客户端部分（ADR-0028 §八）：枪骑、座头鲸、炸弹的渲染器，和枪骑轰炸的预警。
 *
 * <p>收到预警时聊天栏写一句从哪边来、几架；HUD 顶上写"枪骑来袭"和倒计时、架数，屏幕里（或屏幕边上的箭头）标出它们出现的地方。
 * 到了以后写"枪骑在轰炸"，打完收到清除就不画了（结果服务端会发一句）。横幅画在地面黑雾预警的下面，两个同时有也不叠在一起。
 */
public final class SpaceUnitClient {
    private static final int ORANGE = 0xFF6A2A;
    /** 地面黑雾预警的横幅有多高：画在它下面。 */
    private static final int BELOW_GROUND_WARNING = 26;
    /** 到了以后多久没收到更新就当打完了（5 分钟）。 */
    private static final long STALE = 20 * 60 * 5;
    private static LancerPayloads.@Nullable Warning warning;
    private static long receivedAt;
    private static @Nullable ResourceKey<Level> dimension;

    private SpaceUnitClient() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(UniverseEntities.DARK_FOG_LANCER.get(), SpaceUnitRenderer::lancer);
            event.registerEntityRenderer(UniverseEntities.DARK_FOG_HUMPBACK.get(), SpaceUnitRenderer::humpback);
            event.registerEntityRenderer(UniverseEntities.LANCER_BOMB.get(), LancerBombRenderer::new);
        });
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(LancerPayloads.Warning.TYPE,
                (payload, context) -> context.enqueueWork(() -> receive(payload))));
        modBus.addListener(RegisterGuiLayersEvent.class, event -> {
            event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, Universe.id("lancer_warning"), SpaceUnitClient::renderBanner);
            event.registerBelow(VanillaGuiLayers.CROSSHAIR, Universe.id("lancer_marker"), SpaceUnitClient::renderMarker);
        });
    }

    private static void receive(LancerPayloads.Warning payload) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        if (payload.ticks() < 0) {
            warning = null;
            return;
        }
        LancerPayloads.Warning previous = active(minecraft);
        boolean fresh = previous == null || !previous.from().equals(payload.from()) || !previous.target().equals(payload.target());
        warning = payload;
        receivedAt = minecraft.level.getGameTime();
        dimension = minecraft.level.dimension();
        if (fresh && payload.ticks() > 0) {
            int index = Tactics.compass(payload.from().getX() - payload.target().getX(), payload.from().getZ() - payload.target().getZ());
            player.sendSystemMessage(Component.translatable("message.universe.dark_fog.lancers.warning",
                    Component.translatable("hud.universe.dark_fog.dir." + index), payload.count(), Mth.ceil(payload.ticks() / 20F))
                    .withStyle(ChatFormatting.RED));
        }
    }

    /** 还在这个维度里、没过期的预警。 */
    private static LancerPayloads.@Nullable Warning active(Minecraft minecraft) {
        LancerPayloads.Warning current = warning;
        if (current == null || minecraft.level == null || minecraft.level.dimension() != dimension) {
            return null;
        }
        if (remaining(minecraft, current) <= 0 && minecraft.level.getGameTime() - receivedAt - current.ticks() > STALE) {
            warning = null;
            return null;
        }
        return current;
    }

    private static int remaining(Minecraft minecraft, LancerPayloads.Warning current) {
        if (minecraft.level == null || current.ticks() <= 0) {
            return 0;
        }
        return (int) Math.max(0, current.ticks() - (minecraft.level.getGameTime() - receivedAt));
    }

    private static String clock(int ticks) {
        return String.format(Locale.ROOT, "%d:%02d", ticks / 1200, ticks / 20 % 60);
    }

    private static void renderBanner(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        LancerPayloads.Warning current = active(minecraft);
        if (player == null || current == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        Font font = minecraft.font;
        int cx = graphics.guiWidth() / 2;
        int y = top(player) + BELOW_GROUND_WARNING;
        int left = remaining(minecraft, current);
        float time = player.tickCount + delta.getGameTimeDeltaPartialTick(false);
        float pulse = left > 0 && left < 200 ? 0.7F + 0.3F * Mth.sin(time * 0.6F) : 1;
        Component title = left > 0
                ? Component.translatable("hud.universe.dark_fog.lancers.incoming", clock(left))
                : Component.translatable("hud.universe.dark_fog.lancers.arrived");
        Component units = Component.translatable("hud.universe.dark_fog.lancers.count", current.count());
        int width = Math.max(font.width(title), font.width(units));
        graphics.fill(cx - width / 2 - 4, y - 2, cx + width / 2 + 4, y + 21, 0x90000000);
        graphics.text(font, title, cx - font.width(title) / 2, y, 0xFF000000 | SpaceUnitRenderer.scale(ORANGE, pulse), true);
        graphics.text(font, units, cx - font.width(units) / 2, y + 11, 0xFFD8D0D4, true);
    }

    /** 守卫机的 HUD 也在顶上：有它时往下让（和地面黑雾预警一样）。 */
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

    /** 预警时标出它们出现的地方；到了以后枪骑自己看得见，不标。 */
    private static void renderMarker(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        LancerPayloads.Warning current = active(minecraft);
        if (player == null || current == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        int left = remaining(minecraft, current);
        if (left <= 0) {
            return;
        }
        Vec3 from = Vec3.atCenterOf(current.from());
        int distance = (int) Math.round(player.position().distanceTo(from));
        Component label = Component.translatable("hud.universe.dark_fog.lancers.marker_eta", clock(left), distance);
        float time = player.tickCount + delta.getGameTimeDeltaPartialTick(false);
        HudMarker.draw(graphics, minecraft, from, ORANGE, label, 0.7F + 0.3F * Mth.sin(time * 0.3F));
    }
}
