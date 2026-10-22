package io.github.sweetcornna.universe.client.exocraft;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.ship.ShipClient;
import io.github.sweetcornna.universe.exocraft.Submarine;
import io.github.sweetcornna.universe.network.ExocraftControlPayload;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
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
 * 潜艇的客户端部分（ADR-0026）：潜艇和鱼雷的渲染器；开艇时攻击键不挖方块、改成打鱼雷；上艇切到第三人称，镜头拉远；
 * 仪表盘写航速、深度、燃料、鱼雷装填、货舱和按键，第三人称画鱼雷的准星；推进器的嗡嗡声跟着航速变调。
 */
public final class SubmarineClient {
    private static final int PANEL_WIDTH = 140;
    private static @Nullable EngineSound engineSound;
    /** 上艇前的视角：上艇切到第三人称看得见艇，下艇切回来。 */
    private static @Nullable CameraType cameraBeforeBoarding;
    private static boolean sentFiring;
    private static boolean sentThirdPerson;

    private SubmarineClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(UniverseEntities.SUBMARINE.get(), SubmarineRenderer::new);
            event.registerEntityRenderer(UniverseEntities.TORPEDO.get(), TorpedoRenderer::new);
        });
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerAbove(VanillaGuiLayers.HOTBAR, Universe.id("submarine_hud"), SubmarineClient::renderHud));
        gameBus.addListener(ClientTickEvent.Post.class, event -> tick());
        gameBus.addListener(InputEvent.InteractionKeyMappingTriggered.class, SubmarineClient::interceptAttack);
        gameBus.addListener(CalculateDetachedCameraDistanceEvent.class, SubmarineClient::cameraDistance);
    }

    /** 本地玩家正开着的潜艇。 */
    public static @Nullable Submarine driving() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.getVehicle() instanceof Submarine submarine
                && submarine.getControllingPassenger() == minecraft.player ? submarine : null;
    }

    private static boolean thirdPerson(Minecraft minecraft) {
        CameraType type = minecraft.options.getCameraType();
        return !type.isFirstPerson() && !type.isMirrored();
    }

    private static void cameraDistance(CalculateDetachedCameraDistanceEvent event) {
        if (driving() != null && !event.isCameraFlipped()) {
            event.setDistance(Math.max(event.getDistance(), Submarine.CAMERA_DISTANCE));
        }
    }

    /** 开着潜艇：攻击键不打方块、不打实体（包括自己的艇），交给鱼雷。 */
    private static void interceptAttack(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack() && driving() != null) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    private static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        Submarine submarine = driving();
        if (submarine != null && cameraBeforeBoarding == null) {
            cameraBeforeBoarding = minecraft.options.getCameraType();
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (submarine == null && cameraBeforeBoarding != null) {
            minecraft.options.setCameraType(cameraBeforeBoarding);
            cameraBeforeBoarding = null;
        }
        if (submarine != null && (engineSound == null || engineSound.isStopped())) {
            engineSound = new EngineSound(submarine);
            minecraft.getSoundManager().play(engineSound);
        } else if (submarine == null) {
            engineSound = null;
        }
        boolean firing = submarine != null && minecraft.gui.screen() == null && (minecraft.options.keyAttack.isDown() || ShipClient.forceFire);
        boolean third = submarine != null && thirdPerson(minecraft);
        // 只在潜艇上发：下艇时把按着的攻击键松开一次，别的时候交给探索车自己发
        if ((firing != sentFiring || third != sentThirdPerson) && minecraft.getConnection() != null) {
            ClientPacketDistributor.sendToServer(new ExocraftControlPayload(firing, third));
            sentFiring = firing;
            sentThirdPerson = third;
        }
    }

    // ---- 仪表盘

    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        Submarine submarine = driving();
        if (submarine == null || minecraft.gui.hud.isHidden() || minecraft.player == null || minecraft.level == null) {
            return;
        }
        Font font = minecraft.font;
        Vec3 moved = submarine.position().subtract(submarine.xo, submarine.yo, submarine.zo);
        double speed = moved.length() * 20;
        boolean infinite = minecraft.player.hasInfiniteMaterials();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("hud.universe.exocraft.speed", String.format("%.1f", speed)));
        lines.add(depth(submarine, minecraft.level.getSeaLevel()));
        lines.add(Component.translatable("hud.universe.submarine.cargo", submarine.usedSlots(), Submarine.CARGO_SLOTS));
        Component fuelText = Component.translatable("hud.universe.exocraft.fuel", Math.round(submarine.fuel()), (int) Submarine.MAX_FUEL);
        int reload = submarine.reload();
        Component torpedoText = Component.translatable(reload > 0 ? "hud.universe.submarine.torpedo_reload" : "hud.universe.submarine.torpedo");
        Component keys = Component.translatable("hud.universe.submarine.keys", minecraft.options.keyAttack.getTranslatedKeyMessage(),
                minecraft.options.keyShift.getTranslatedKeyMessage());
        Component sonar = Component.translatable("hud.universe.submarine.sonar_hint", minecraft.options.keyUse.getTranslatedKeyMessage());
        Component hint = submarine.fuel() <= 0 && !infinite ? Component.translatable("hud.universe.exocraft.no_fuel") : null;

        Component title = Component.translatable("entity.universe.submarine");
        int panelWidth = PANEL_WIDTH;
        for (Component line : List.of(title, fuelText, torpedoText, keys, sonar)) {
            panelWidth = Math.max(panelWidth, font.width(line) + 6);
        }
        for (Component line : lines) {
            panelWidth = Math.max(panelWidth, font.width(line) + 6);
        }
        if (hint != null) {
            panelWidth = Math.max(panelWidth, font.width(hint) + 6);
        }
        int x = 6;
        int height = 13 + lines.size() * 10 + 16 + 16 + 20 + (hint != null ? 10 : 0);
        int y = graphics.guiHeight() / 2 - height / 2;
        graphics.fill(x - 3, y - 3, x + panelWidth, y + height, 0x90000000);
        graphics.text(font, title, x, y, 0xFF7FD8FF, false);
        y += 13;
        for (Component line : lines) {
            graphics.text(font, line, x, y, 0xFFE0E0E0, false);
            y += 10;
        }
        int barWidth = PANEL_WIDTH - 8;
        float fuelShare = Mth.clamp(submarine.fuel() / (float) Submarine.MAX_FUEL, 0, 1);
        y = bar(graphics, font, fuelText, x, y, barWidth, fuelShare, fuelShare > 0.3F ? 0xFF4FD16B : fuelShare > 0.1F ? 0xFFE8C341 : 0xFFE5533D);
        float loaded = 1 - Mth.clamp(reload / (float) Submarine.TORPEDO_RELOAD, 0, 1);
        y = bar(graphics, font, torpedoText, x, y, barWidth, loaded, reload > 0 ? 0xFFFF9F43 : 0xFF5AD2FF);
        graphics.text(font, keys, x, y, 0xFF9A9A9A, false);
        y += 10;
        graphics.text(font, sonar, x, y, 0xFF9A9A9A, false);
        y += 10;
        if (hint != null) {
            graphics.text(font, hint, x, y, 0xFFFFD27F, false);
        }
        // 第三人称原版不画准星：画鱼雷的准星，装填中变暗
        if (thirdPerson(minecraft)) {
            int cx = graphics.guiWidth() / 2;
            int cy = graphics.guiHeight() / 2;
            int color = reload > 0 ? 0x80FF9F43 : 0xC07FD8FF;
            graphics.fill(cx - 7, cy, cx - 2, cy + 1, color);
            graphics.fill(cx + 3, cy, cx + 8, cy + 1, color);
            graphics.fill(cx, cy - 7, cx + 1, cy - 2, color);
            graphics.fill(cx, cy + 3, cx + 1, cy + 8, color);
            graphics.fill(cx, cy, cx + 1, cy + 1, color);
        }
    }

    /** 深度：海平面以下几格；浮在水面、离了水另写。 */
    private static Component depth(Submarine submarine, int seaLevel) {
        if (!submarine.isInWater()) {
            return Component.translatable("hud.universe.submarine.beached");
        }
        if (!submarine.submerged()) {
            return Component.translatable("hud.universe.submarine.surfaced");
        }
        int depth = Math.max(0, (int) Math.floor(seaLevel - submarine.getY()));
        return Component.translatable("hud.universe.submarine.depth", depth);
    }

    private static int bar(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width, float share, int color) {
        graphics.text(font, text, x, y, 0xFFE0E0E0, false);
        y += 10;
        graphics.fill(x, y, x + width, y + 3, 0xFF303030);
        graphics.fill(x, y, x + Math.round(width * share), y + 3, color);
        return y + 6;
    }

    /** 推进器：低沉的嗡嗡声，越快调越高；停着时小声怠速。 */
    private static final class EngineSound extends AbstractTickableSoundInstance {
        private final Submarine submarine;

        EngineSound(Submarine submarine) {
            super(SoundEvents.BEACON_AMBIENT, SoundSource.NEUTRAL, RandomSource.create());
            this.submarine = submarine;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.pitch = 0.4F;
            this.relative = true;
        }

        @Override
        public void tick() {
            if (this.submarine.isRemoved() || driving() != this.submarine) {
                this.stop();
                return;
            }
            float speed = (float) this.submarine.position().subtract(this.submarine.xo, this.submarine.yo, this.submarine.zo).length();
            boolean running = (this.submarine.fuel() > 0 || Minecraft.getInstance().player.hasInfiniteMaterials()) && this.submarine.isInWater();
            float share = Mth.clamp(speed / 0.9F, 0, 1);
            float target = running ? 0.2F + 0.45F * share : 0;
            this.volume = Mth.lerp(0.15F, this.volume, target);
            this.pitch = Mth.lerp(0.15F, this.pitch, 0.4F + 0.5F * share);
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
