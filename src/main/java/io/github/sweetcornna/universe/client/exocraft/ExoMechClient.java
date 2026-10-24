package io.github.sweetcornna.universe.client.exocraft;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.HudRegions;
import io.github.sweetcornna.universe.client.ship.ShipClient;
import io.github.sweetcornna.universe.client.audio.Loops;
import io.github.sweetcornna.universe.exocraft.ExoMech;
import io.github.sweetcornna.universe.network.ExocraftControlPayload;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.sound.ShipSounds;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 机甲的客户端部分（ADR-0026）：渲染器；坐进去时攻击键不挖方块、改成开爆能炮；上机切到第三人称，镜头拉远；
 * 仪表盘写速度、燃料、喷气、货舱和按键，第三人称画炮的准星；喷气时喷口呼呼响。
 */
public final class ExoMechClient {
    private static final int PANEL_WIDTH = 140;
    private static @Nullable JetSound jetSound;
    /** 上机前的视角：上机切到第三人称看得见机甲，下机切回来。 */
    private static @Nullable CameraType cameraBeforeBoarding;
    private static boolean sentFiring;
    private static boolean sentThirdPerson;

    private ExoMechClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerEntityRenderer(UniverseEntities.EXO_MECH.get(), ExoMechRenderer::new));
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerAbove(VanillaGuiLayers.HOTBAR, Universe.id("exo_mech_hud"), ExoMechClient::renderHud));
        gameBus.addListener(ClientTickEvent.Post.class, event -> tick());
        gameBus.addListener(InputEvent.InteractionKeyMappingTriggered.class, ExoMechClient::interceptAttack);
        gameBus.addListener(CalculateDetachedCameraDistanceEvent.class, ExoMechClient::cameraDistance);
    }

    /** 本地玩家正开着的机甲。 */
    public static @Nullable ExoMech driving() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.getVehicle() instanceof ExoMech mech
                && mech.getControllingPassenger() == minecraft.player ? mech : null;
    }

    private static boolean thirdPerson(Minecraft minecraft) {
        CameraType type = minecraft.options.getCameraType();
        return !type.isFirstPerson() && !type.isMirrored();
    }

    private static void cameraDistance(CalculateDetachedCameraDistanceEvent event) {
        if (driving() != null && !event.isCameraFlipped()) {
            event.setDistance(Math.max(event.getDistance(), ExoMech.CAMERA_DISTANCE));
        }
    }

    /** 坐在机甲里：攻击键不打方块、不打实体（包括自己的机甲），交给爆能炮。 */
    private static void interceptAttack(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack() && driving() != null) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    private static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ExoMech mech = driving();
        if (mech != null && cameraBeforeBoarding == null) {
            cameraBeforeBoarding = minecraft.options.getCameraType();
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (mech == null && cameraBeforeBoarding != null) {
            minecraft.options.setCameraType(cameraBeforeBoarding);
            cameraBeforeBoarding = null;
        }
        if (mech != null && (!Loops.playing(jetSound))) {
            jetSound = new JetSound(mech);
            minecraft.getSoundManager().play(jetSound);
        } else if (mech == null) {
            jetSound = null;
        }
        boolean firing = mech != null && minecraft.gui.screen() == null && (minecraft.options.keyAttack.isDown() || ShipClient.forceFire);
        boolean third = mech != null && thirdPerson(minecraft);
        // 只在机甲上发：下机时把按着的攻击键松开一次，别的时候交给探索车、潜艇自己发
        if ((firing != sentFiring || third != sentThirdPerson) && minecraft.getConnection() != null) {
            ClientPacketDistributor.sendToServer(new ExocraftControlPayload(firing, third));
            sentFiring = firing;
            sentThirdPerson = third;
        }
    }

    // ---- 仪表盘

    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        ExoMech mech = driving();
        if (mech == null || minecraft.gui.hud.isHidden() || minecraft.player == null) {
            return;
        }
        Font font = minecraft.font;
        Vec3 moved = mech.position().subtract(mech.xo, mech.yo, mech.zo);
        double speed = Math.sqrt(moved.x * moved.x + moved.z * moved.z) * 20;
        boolean infinite = minecraft.player.hasInfiniteMaterials();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("hud.universe.exocraft.speed", String.format("%.1f", speed)));
        lines.add(Component.translatable("hud.universe.exocraft.cargo", mech.usedSlots(), mech.cargoSlots()));
        lines.add(Component.translatable("hud.universe.exo_mech.sealed"));
        Component fuelText = Component.translatable("hud.universe.exocraft.fuel", Math.round(mech.fuel()), (int) ExoMech.MAX_FUEL);
        float jet = mech.jetShare();
        Component jetText = Component.translatable(jet <= 0.01F ? "hud.universe.exo_mech.jet_empty" : "hud.universe.exo_mech.jet",
                minecraft.options.keyJump.getTranslatedKeyMessage());
        Component keys = Component.translatable("hud.universe.exo_mech.keys", minecraft.options.keyAttack.getTranslatedKeyMessage(),
                minecraft.options.keyShift.getTranslatedKeyMessage());
        Component hint = mech.fuel() <= 0 && !infinite ? Component.translatable("hud.universe.exocraft.no_fuel") : null;

        Component title = mech.getType().getDescription();
        int panelWidth = PANEL_WIDTH;
        for (Component line : List.of(title, fuelText, jetText, keys)) {
            panelWidth = Math.max(panelWidth, font.width(line) + 6);
        }
        for (Component line : lines) {
            panelWidth = Math.max(panelWidth, font.width(line) + 6);
        }
        if (hint != null) {
            panelWidth = Math.max(panelWidth, font.width(hint) + 6);
        }
        int x = 6;
        int height = 13 + lines.size() * 10 + 16 + 16 + 10 + (hint != null ? 10 : 0);
        int y = graphics.guiHeight() / 2 - height / 2;
        graphics.fill(x - 3, y - 3, x + panelWidth, y + height, 0x90000000);
        HudRegions.claim("exo_mech", x - 3, y - 3, x + panelWidth, y + height);
        graphics.text(font, title, x, y, 0xFFFFB866, false);
        y += 13;
        for (Component line : lines) {
            graphics.text(font, line, x, y, 0xFFE0E0E0, false);
            y += 10;
        }
        int barWidth = PANEL_WIDTH - 8;
        float fuelShare = Mth.clamp(mech.fuel() / (float) ExoMech.MAX_FUEL, 0, 1);
        y = bar(graphics, font, fuelText, x, y, barWidth, fuelShare, fuelShare > 0.3F ? 0xFF4FD16B : fuelShare > 0.1F ? 0xFFE8C341 : 0xFFE5533D);
        y = bar(graphics, font, jetText, x, y, barWidth, jet, mech.thrusting() ? 0xFFFF9F43 : jet < 0.3F ? 0xFFE5533D : 0xFF5AD2FF);
        graphics.text(font, keys, x, y, 0xFF9A9A9A, false);
        y += 10;
        if (hint != null) {
            graphics.text(font, hint, x, y, 0xFFFFD27F, false);
        }
        // 第三人称原版不画准星：画炮的准星（一个圈加中心点）
        if (thirdPerson(minecraft)) {
            int cx = graphics.guiWidth() / 2;
            int cy = graphics.guiHeight() / 2;
            int color = 0xC0FFB04A;
            graphics.fill(cx - 4, cy - 6, cx + 5, cy - 5, color);
            graphics.fill(cx - 4, cy + 6, cx + 5, cy + 7, color);
            graphics.fill(cx - 6, cy - 4, cx - 5, cy + 5, color);
            graphics.fill(cx + 6, cy - 4, cx + 7, cy + 5, color);
            graphics.fill(cx, cy, cx + 1, cy + 1, color);
        }
    }

    private static int bar(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width, float share, int color) {
        graphics.text(font, text, x, y, 0xFFE0E0E0, false);
        y += 10;
        graphics.fill(x, y, x + width, y + 3, 0xFF303030);
        graphics.fill(x, y, x + Math.round(width * share), y + 3, color);
        return y + 6;
    }

    /** 喷气：开着时呼呼响，关了很快静下来；储量见底时音调往下掉。 */
    private static final class JetSound extends AbstractTickableSoundInstance {
        private final ExoMech mech;

        JetSound(ExoMech mech) {
            super(ShipSounds.MECH_JET.get(), SoundSource.NEUTRAL, RandomSource.create());
            this.mech = mech;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.pitch = 1.0F;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public void tick() {
            if (this.mech.isRemoved() || driving() != this.mech) {
                this.stop();
                return;
            }
            this.volume = Mth.lerp(0.3F, this.volume, this.mech.thrusting() ? 0.9F : 0);
            this.pitch = Mth.lerp(0.2F, this.pitch, 0.9F + 0.15F * this.mech.jetShare());
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
