package io.github.sweetcornna.universe.client.station;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.HudMarker;
import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.client.trade.TradeScreen;
import io.github.sweetcornna.universe.core.space.StationLayout.Site;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.station.Stations;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 空间站的客户端部分（ADR-0017）：太空里 HUD 上一直标着空间站的方向和距离；进了站显示欢迎和信用点；
 * 站外失重时跳跃键上升、潜行键下降，有一点阻尼。
 */
public final class StationClient {
    private static final int COLOR = 0xF2C94C;
    /** 失重时的升降加速度、最快速度和松开按键后的阻尼。 */
    private static final double THRUST = 0.03;
    private static final double MAX_SPEED = 0.3;
    private static final double DAMPING = 0.9;

    private static boolean wasInside;

    private StationClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerBelow(VanillaGuiLayers.CROSSHAIR, Universe.id("station"), StationClient::renderHud));
        gameBus.addListener(PlayerTickEvent.Pre.class, event -> {
            if (event.getEntity() == Minecraft.getInstance().player) {
                tick((LocalPlayer) event.getEntity());
            }
        });
    }

    /** 玩家所在太空维度的空间站。 */
    public static Optional<Site> site(Minecraft minecraft) {
        if (minecraft.level == null) {
            return Optional.empty();
        }
        OptionalInt system = SpaceDimensions.system(minecraft.level.dimension());
        if (system.isEmpty()) {
            return Optional.empty();
        }
        return ClientGalaxy.galaxy().flatMap(galaxy -> Stations.site(galaxy, system.getAsInt()));
    }

    /** 太空里、空间站外面、没坐在船上：失重（服务端把重力设成了 0）。 */
    public static boolean weightless(LocalPlayer player) {
        return SpaceDimensions.isSpace(player.level().dimension()) && !player.isPassenger()
                && player.getAttributeValue(Attributes.GRAVITY) < 1e-4;
    }

    private static void tick(LocalPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean inside = site(minecraft).map(site -> site.inside(player.getX(), player.getY(), player.getZ())).orElse(false);
        if (inside && !wasInside) {
            OptionalInt system = SpaceDimensions.system(player.level().dimension());
            ClientGalaxy.galaxy().flatMap(galaxy -> galaxy.system(system.orElse(-1))).ifPresent(star ->
                    minecraft.gui.hud.setOverlayMessage(Component.translatable("message.universe.station.welcome", GalaxyText.system(star)), false));
        }
        wasInside = inside;
        if (!weightless(player) || player.getAbilities().flying) {
            return;
        }
        Vec3 v = player.getDeltaMovement();
        double vy = v.y;
        if (player.input.keyPresses.jump()) {
            vy = Math.min(vy + THRUST, MAX_SPEED);
        } else if (player.input.keyPresses.shift()) {
            vy = Math.max(vy - THRUST, -MAX_SPEED);
        } else {
            vy *= DAMPING;
        }
        player.setDeltaMovement(v.x, vy, v.z);
    }

    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        Optional<Site> found = site(minecraft);
        if (found.isEmpty()) {
            return;
        }
        Site site = found.get();
        if (site.inside(player.getX(), player.getY(), player.getZ())) {
            // 站内：右上角显示信用点
            Component credits = Component.translatable("hud.universe.station.credits", TradeScreen.credits(player.getData(UniverseAttachments.CREDITS)));
            int w = minecraft.font.width(credits);
            graphics.fill(graphics.guiWidth() - w - 10, 4, graphics.guiWidth() - 4, 17, 0x90000000);
            graphics.text(minecraft.font, credits, graphics.guiWidth() - w - 7, 7, 0xFF000000 | COLOR, true);
            return;
        }
        double[] center = site.center();
        Vec3 target = new Vec3(center[0], center[1] + 6, center[2]);
        int distance = (int) Math.round(player.position().distanceTo(target));
        float pulse = 0.75F + 0.25F * (float) Math.sin((minecraft.level.getGameTime() + delta.getGameTimeDeltaPartialTick(false)) * 0.15);
        Component label = Component.translatable("hud.universe.station", String.format(Locale.ROOT, "%,d", distance));
        HudMarker.draw(graphics, minecraft, target, COLOR, label, pulse);
    }
}
