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
import io.github.sweetcornna.universe.network.JetpackPayload;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.station.Stations;
import io.github.sweetcornna.universe.survival.SuitHud;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 空间站的客户端部分（ADR-0017）：太空里 HUD 上一直标着空间站的方向和距离；进了站显示欢迎和信用点；
 * 站外失重时跳跃键上升、潜行键下降，有一点阻尼。
 *
 * <p>出舱（ADR-0034 §四）也是这样失重；穿着外骨骼时按住疾跑键用喷气背包往六个方向推：前后顺着视线（带俯仰），
 * 左右平移，跳跃、潜行上下。喷气能量照常扣（{@link JetpackPayload}）。
 */
public final class StationClient {
    private static final int COLOR = 0xF2C94C;
    /** 失重时的升降加速度、最快速度和松开按键后的阻尼。 */
    private static final double THRUST = 0.03;
    private static final double MAX_SPEED = 0.3;
    private static final double DAMPING = 0.9;
    /** 出舱时喷气背包每刻加的速度、最快速度（再乘推进器模块的推力）。 */
    private static final double EVA_THRUST = 0.035;
    private static final double EVA_MAX_SPEED = 0.6;

    /** 失重时喷气背包在推。 */
    private static boolean evaJet;

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
            evaJet(false);
            return;
        }
        if (evaThrust(player)) {
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

    /** 失重时按住疾跑键：喷气背包往按着的方向推。返回推了没有。 */
    private static boolean evaThrust(LocalPlayer player) {
        SuitHud hud = player.getData(UniverseAttachments.SUIT_HUD);
        var keys = player.input.keyPresses;
        boolean any = keys.forward() || keys.backward() || keys.left() || keys.right() || keys.jump() || keys.shift();
        boolean ready = keys.sprint() && any && hud.jet() > 0 && player.getItemBySlot(EquipmentSlot.CHEST).is(UniverseItems.EXOSUIT_CHESTPLATE);
        evaJet(ready);
        if (!ready) {
            return false;
        }
        Vec3 look = player.getLookAngle();
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        Vec3 dir = Vec3.ZERO;
        if (keys.forward()) {
            dir = dir.add(look);
        }
        if (keys.backward()) {
            dir = dir.subtract(look);
        }
        if (keys.right()) {
            dir = dir.add(right);
        }
        if (keys.left()) {
            dir = dir.subtract(right);
        }
        if (keys.jump()) {
            dir = dir.add(0, 1, 0);
        }
        if (keys.shift()) {
            dir = dir.add(0, -1, 0);
        }
        if (dir.lengthSqr() < 1e-6) {
            return false;
        }
        float boost = Math.max(1, hud.jetThrust());
        Vec3 v = player.getDeltaMovement().add(dir.normalize().scale(EVA_THRUST * boost));
        double max = EVA_MAX_SPEED * boost;
        if (v.length() > max) {
            v = v.normalize().scale(max);
        }
        player.setDeltaMovement(v);
        // 背后、脚下喷出的小火焰：和推的方向相反
        Vec3 out = dir.normalize().scale(-0.12);
        Vec3 back = player.position().add(0, 1.0, 0).subtract(look.multiply(0.3, 0, 0.3));
        player.level().addParticle(ParticleTypes.SMALL_FLAME, back.x, back.y, back.z, out.x, out.y, out.z);
        if (player.getRandom().nextInt(3) == 0) {
            player.level().addParticle(ParticleTypes.SMOKE, back.x, back.y, back.z, out.x * 0.5, out.y * 0.5, out.z * 0.5);
        }
        return true;
    }

    private static void evaJet(boolean on) {
        if (on != evaJet) {
            evaJet = on;
            ClientPacketDistributor.sendToServer(new JetpackPayload(on));
        }
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
