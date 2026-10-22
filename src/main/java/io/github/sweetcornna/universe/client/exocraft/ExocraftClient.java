package io.github.sweetcornna.universe.client.exocraft;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.ship.ShipClient;
import io.github.sweetcornna.universe.core.survival.LaserHeat;
import io.github.sweetcornna.universe.exocraft.Exocraft;
import io.github.sweetcornna.universe.exocraft.HeavyRover;
import io.github.sweetcornna.universe.exocraft.HoverCar;
import io.github.sweetcornna.universe.network.ExocraftControlPayload;
import io.github.sweetcornna.universe.network.VehiclePayloads;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * 探索车的客户端部分（ADR-0017）：渲染器；开车时攻击键不挖方块、改成开车载激光；上车切到第三人称，镜头抬高拉远，车落在准星下面；
 * 仪表盘写速度、燃料、激光热量、货箱和激光对着的方块，第三人称画激光准星；发动机的嗡嗡声跟着车速变调。
 * 悬浮车和重型车（ADR-0026）是探索车的子类，共用这一套：仪表盘另写悬浮高度、车尾精炼机的状态。
 */
public final class ExocraftClient {
    private static final int PANEL_WIDTH = 140;
    private static @Nullable EngineSound engineSound;
    /** 上车前的视角：上车切到第三人称看得见车，下车切回来。 */
    private static @Nullable CameraType cameraBeforeBoarding;
    private static boolean sentFiring;
    private static boolean sentThirdPerson;

    private ExocraftClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(UniverseEntities.EXOCRAFT.get(), ExocraftRenderer::new);
            event.registerEntityRenderer(UniverseEntities.HOVER_CAR.get(), ExocraftRenderer::hoverCar);
            event.registerEntityRenderer(UniverseEntities.HEAVY_ROVER.get(), ExocraftRenderer::heavyRover);
        });
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerAbove(VanillaGuiLayers.HOTBAR, Universe.id("exocraft_hud"), ExocraftClient::renderHud));
        // 载具召唤台的列表
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(VehiclePayloads.Listing.TYPE,
                (payload, context) -> context.enqueueWork(() -> Minecraft.getInstance().gui.setScreen(new VehicleSummonerScreen(payload)))));
        gameBus.addListener(ClientTickEvent.Post.class, event -> tick());
        gameBus.addListener(InputEvent.InteractionKeyMappingTriggered.class, ExocraftClient::interceptAttack);
        gameBus.addListener(CalculateDetachedCameraDistanceEvent.class, ExocraftClient::cameraDistance);
    }

    /** 本地玩家正开着的探索车。 */
    public static @Nullable Exocraft driving() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.getVehicle() instanceof Exocraft exocraft
                && exocraft.getControllingPassenger() == minecraft.player ? exocraft : null;
    }

    /** 开车时用的是背后的第三人称：镜头和瞄准线抬高。 */
    private static boolean thirdPerson(Minecraft minecraft) {
        CameraType type = minecraft.options.getCameraType();
        return !type.isFirstPerson() && !type.isMirrored();
    }

    private static void cameraDistance(CalculateDetachedCameraDistanceEvent event) {
        Exocraft exocraft = driving();
        if (exocraft != null && !event.isCameraFlipped()) {
            event.setDistance(Math.max(event.getDistance(), exocraft.cameraDistance()));
        }
    }

    /** 开车时第三人称镜头往上抬多少格（{@code CameraMixin} 调用），和服务端激光的瞄准线一致；头顶有方块时少抬。 */
    public static float cameraLift(Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        if (driving() == null || !camera.isDetached() || !thirdPerson(minecraft) || minecraft.level == null) {
            return 0;
        }
        Vec3 eye = camera.position();
        return (float) Exocraft.aimOrigin(minecraft.level, minecraft.player, eye, new Vec3(camera.upVector()), true).distanceTo(eye);
    }

    /** 开着车：攻击键不打方块、不打实体（包括自己的车），交给激光。 */
    private static void interceptAttack(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack() && driving() != null) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    private static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        Exocraft exocraft = driving();
        if (exocraft != null && cameraBeforeBoarding == null) {
            cameraBeforeBoarding = minecraft.options.getCameraType();
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (exocraft == null && cameraBeforeBoarding != null) {
            minecraft.options.setCameraType(cameraBeforeBoarding);
            cameraBeforeBoarding = null;
        }
        if (exocraft != null && (engineSound == null || engineSound.isStopped())) {
            engineSound = new EngineSound(exocraft);
            minecraft.getSoundManager().play(engineSound);
        } else if (exocraft == null) {
            engineSound = null;
        }
        boolean firing = exocraft != null && minecraft.gui.screen() == null && (minecraft.options.keyAttack.isDown() || ShipClient.forceFire);
        boolean third = exocraft != null && thirdPerson(minecraft);
        if ((firing != sentFiring || third != sentThirdPerson) && minecraft.getConnection() != null) {
            ClientPacketDistributor.sendToServer(new ExocraftControlPayload(firing, third));
            sentFiring = firing;
            sentThirdPerson = third;
        }
    }

    // ---- 仪表盘

    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        Exocraft exocraft = driving();
        if (exocraft == null || minecraft.gui.hud.isHidden() || minecraft.player == null) {
            return;
        }
        Font font = minecraft.font;
        Vec3 moved = exocraft.position().subtract(exocraft.xo, exocraft.yo, exocraft.zo);
        double speed = Math.sqrt(moved.x * moved.x + moved.z * moved.z) * 20;
        boolean infinite = minecraft.player.hasInfiniteMaterials();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("hud.universe.exocraft.speed", String.format("%.1f", speed)));
        lines.add(Component.translatable("hud.universe.exocraft.cargo", exocraft.usedSlots(), exocraft.cargoSlots()));
        if (exocraft instanceof HoverCar hoverCar) {
            lines.add(hoverCar.clearance() <= 1.5 ? Component.translatable("hud.universe.hover_car.hover", String.format("%.1f", hoverCar.clearance()))
                    : Component.translatable("hud.universe.hover_car.falling"));
        } else if (exocraft instanceof HeavyRover rover) {
            lines.add(Component.translatable(rover.refining() ? "hud.universe.heavy_rover.refining" : "hud.universe.heavy_rover.refiner_idle",
                    minecraft.options.keyShift.getTranslatedKeyMessage(), minecraft.options.keyUse.getTranslatedKeyMessage()));
        }
        boolean third = thirdPerson(minecraft);
        Component target = target(minecraft, delta.getGameTimeDeltaPartialTick(true), third);
        Component fuelText = Component.translatable("hud.universe.exocraft.fuel", Math.round(exocraft.fuel()), (int) Exocraft.MAX_FUEL);
        boolean overheated = exocraft.overheated();
        Component laserText = Component.translatable(overheated ? "hud.universe.exocraft.laser_overheated" : "hud.universe.exocraft.laser");
        Component keys = Component.translatable("hud.universe.exocraft.keys", minecraft.options.keyAttack.getTranslatedKeyMessage(),
                minecraft.options.keyShift.getTranslatedKeyMessage());
        Component hint = exocraft.fuel() <= 0 && !infinite ? Component.translatable("hud.universe.exocraft.no_fuel") : null;

        Component title = exocraft.getType().getDescription();
        int panelWidth = PANEL_WIDTH;
        for (Component line : List.of(title, fuelText, laserText, keys)) {
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
        graphics.text(font, title, x, y, 0xFFFFB866, false);
        y += 13;
        for (Component line : lines) {
            graphics.text(font, line, x, y, 0xFFE0E0E0, false);
            y += 10;
        }
        int barWidth = PANEL_WIDTH - 8;
        float fuelShare = Mth.clamp(exocraft.fuel() / (float) Exocraft.MAX_FUEL, 0, 1);
        y = bar(graphics, font, fuelText, x, y, barWidth, fuelShare, fuelShare > 0.3F ? 0xFF4FD16B : fuelShare > 0.1F ? 0xFFE8C341 : 0xFFE5533D);
        float heat = Mth.clamp(exocraft.heat() / LaserHeat.MAX, 0, 1);
        y = bar(graphics, font, laserText, x, y, barWidth, heat, overheated ? 0xFFE5533D : heat > 0.7F ? 0xFFFF9F43 : 0xFF5AD2FF);
        graphics.text(font, keys, x, y, 0xFF9A9A9A, false);
        y += 10;
        if (hint != null) {
            graphics.text(font, hint, x, y, 0xFFFFD27F, false);
        }
        // 第三人称原版不画准星：画激光的方框准星，开着发亮，过热变红
        if (third) {
            int cx = graphics.guiWidth() / 2;
            int cy = graphics.guiHeight() / 2;
            int color = overheated ? 0xFFE5533D : exocraft.laserOn() ? 0xFF9BE8FF : 0xC05AD2FF;
            graphics.fill(cx - 5, cy - 5, cx + 6, cy - 4, color);
            graphics.fill(cx - 5, cy + 5, cx + 6, cy + 6, color);
            graphics.fill(cx - 5, cy - 4, cx - 4, cy + 5, color);
            graphics.fill(cx + 5, cy - 4, cx + 6, cy + 5, color);
            graphics.fill(cx, cy, cx + 1, cy + 1, color);
        }
        // 激光对着的方块：写在准星下面
        if (target != null) {
            int w = font.width(target);
            graphics.text(font, target, graphics.guiWidth() / 2 - w / 2, graphics.guiHeight() / 2 + 12, 0xFFBFEFFF, true);
        }
    }

    private static int bar(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width, float share, int color) {
        graphics.text(font, text, x, y, 0xFFE0E0E0, false);
        y += 10;
        graphics.fill(x, y, x + width, y + 3, 0xFF303030);
        graphics.fill(x, y, x + Math.round(width * share), y + 3, color);
        return y + 6;
    }

    /** 激光射程里准星对着的方块和距离，没有就是 null。瞄准线和服务端的激光一样算。 */
    private static @Nullable Component target(Minecraft minecraft, float partialTick, boolean third) {
        LocalPlayer player = minecraft.player;
        BlockHitResult blockHit = Exocraft.aim(minecraft.level, player, player.getEyePosition(partialTick), player.getViewVector(partialTick),
                player.getUpVector(partialTick), third);
        if (blockHit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        HitResult hit = blockHit;
        BlockState state = minecraft.level.getBlockState(blockHit.getBlockPos());
        int distance = (int) Math.round(minecraft.player.getEyePosition().distanceTo(hit.getLocation()));
        return Component.translatable("hud.universe.exocraft.target", state.getBlock().getName(), distance);
    }

    /** 发动机：低沉的嗡嗡声，车越快调越高；停着时小声怠速。 */
    private static final class EngineSound extends AbstractTickableSoundInstance {
        private final Exocraft exocraft;

        EngineSound(Exocraft exocraft) {
            super(SoundEvents.BEACON_AMBIENT, SoundSource.NEUTRAL, RandomSource.create());
            this.exocraft = exocraft;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.pitch = 0.5F;
            this.relative = true;
        }

        @Override
        public void tick() {
            if (this.exocraft.isRemoved() || driving() != this.exocraft) {
                this.stop();
                return;
            }
            double dx = this.exocraft.getX() - this.exocraft.xo;
            double dz = this.exocraft.getZ() - this.exocraft.zo;
            float speed = (float) Math.sqrt(dx * dx + dz * dz);
            boolean running = this.exocraft.fuel() > 0 || Minecraft.getInstance().player.hasInfiniteMaterials();
            float target = running ? 0.25F + 0.5F * Mth.clamp(speed / 0.6F, 0, 1) : 0;
            this.volume = Mth.lerp(0.15F, this.volume, target);
            this.pitch = Mth.lerp(0.15F, this.pitch, 0.5F + 0.6F * Mth.clamp(speed / 0.6F, 0, 1));
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
