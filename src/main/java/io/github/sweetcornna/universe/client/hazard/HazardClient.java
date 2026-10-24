package io.github.sweetcornna.universe.client.hazard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.dev.DevScript;
import io.github.sweetcornna.universe.core.hazard.SporeDrift;
import io.github.sweetcornna.universe.core.hazard.Vortex;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.hazard.DustDevil;
import io.github.sweetcornna.universe.hazard.HazardLook;
import io.github.sweetcornna.universe.hazard.HazardPayloads;
import io.github.sweetcornna.universe.hazard.SporeCloud;
import io.github.sweetcornna.universe.network.OpenTechPayload;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * 局部危害的客户端部分（ADR-0034 §六）：熔岩弹、孢子云、尘卷风的渲染器；喷发和间歇泉的镜头抖动；
 * 进了孢子云画面发绿、雾拉近；本机玩家被尘卷风卷着转（人的移动在客户端，服务端只卷掉落物和生物）。
 *
 * <p>抖动的幅度乘上原版的"屏幕效果强度"选项。开发脚本（{@link DevScript}）多几个命令：
 * {@code hazard <geyser|eruption|spore_cloud|dust_devil|clear>} 在面前马上放一处，{@code techscreen} 打开科技界面，
 * {@code hazardlog} 把抖动、孢子云的浓淡、有没有被卷着写进日志。
 */
public final class HazardClient {
    /** 孢子云里的雾色、画面的绿：暗黄绿。 */
    private static final int SPORE_FOG = 0x6F8A3C;
    /** 雾最近拉到多远（格）。 */
    private static final float SPORE_FOG_END = 12;
    /** 离云边多远开始有一点绿（格）。 */
    private static final double SPORE_EDGE = 2.5;

    /** 正在抖的：{强度（度）, 还剩几刻}。 */
    private static final List<float[]> shakes = new ArrayList<>();
    private static float amplitude;
    private static float amplitudeOld;
    /** 孢子云的浓淡 0–1，平滑过的。 */
    private static float spores;
    private static float sporesOld;
    /** 正卷着本机玩家的尘卷风（实体 ID，-1 是没有）、卷了几刻、到几刻甩出去、甩出去以后到哪一刻之前不再卷。 */
    private static int caughtBy = -1;
    private static int caughtTicks;
    private static int caughtLimit;
    private static long cooldownUntil;

    private HazardClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(UniverseEntities.LAVA_BOMB.get(), LavaBombRenderer::new);
            event.registerEntityRenderer(UniverseEntities.SPORE_CLOUD.get(), SporeCloudRenderer::new);
            event.registerEntityRenderer(UniverseEntities.DUST_DEVIL.get(), DustDevilRenderer::new);
        });
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(HazardPayloads.Shake.TYPE,
                (payload, context) -> context.enqueueWork(() -> shake(payload.strength(), payload.ticks()))));
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerBelow(VanillaGuiLayers.CROSSHAIR, Universe.id("spore_tint"), HazardClient::renderTint));
        HazardLook.sand = level -> ClientGalaxy.currentPlanet().map(planet -> planet.palette().sand()).orElse(0xC8A878);
        HazardLook.shake = HazardClient::shake;
        gameBus.addListener(ClientTickEvent.Post.class, event -> tick());
        gameBus.addListener(ViewportEvent.ComputeCameraAngles.class, HazardClient::angles);
        gameBus.addListener(ViewportEvent.ComputeFogColor.class, HazardClient::fogColor);
        gameBus.addListener(ViewportEvent.RenderFog.class, HazardClient::fog);
        if (DevScript.enabled()) {
            devVerbs();
        }
    }

    /** 镜头抖 {@code ticks} 刻，幅度 {@code strength} 度；同时有几处在抖时按最猛的那处。 */
    public static void shake(float strength, int ticks) {
        if (strength > 0 && ticks > 0) {
            shakes.add(new float[] {strength, ticks});
        }
    }

    private static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) {
            return;
        }
        amplitudeOld = amplitude;
        sporesOld = spores;
        float target = 0;
        for (int i = shakes.size() - 1; i >= 0; i--) {
            float[] shake = shakes.get(i);
            target = Math.max(target, shake[0]);
            if (--shake[1] <= 0) {
                shakes.remove(i);
            }
        }
        // 起得快、停得慢
        amplitude += (target - amplitude) * (target > amplitude ? 0.5F : 0.12F);
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            shakes.clear();
            amplitude = 0;
            spores = 0;
            caughtBy = -1;
            return;
        }
        spores += ((float) sporeDensity(level, player.getEyePosition()) - spores) * 0.08F;
        carry(level, player);
    }

    /** 眼睛那里孢子云有多浓：在云里按云的浓淡，云边外面 {@value #SPORE_EDGE} 格以内淡一些。 */
    private static double sporeDensity(ClientLevel level, Vec3 eye) {
        double best = 0;
        double reach = SporeDrift.RADIUS + SPORE_EDGE;
        for (SporeCloud cloud : level.getEntitiesOfClass(SporeCloud.class, new AABB(eye, eye).inflate(reach + 2))) {
            double dx = eye.x - cloud.getX();
            double dz = eye.z - cloud.getZ();
            double dy = eye.y - cloud.getY();
            if (dy < -SporeDrift.BELOW - SPORE_EDGE || dy > SporeDrift.ABOVE + SPORE_EDGE) {
                continue;
            }
            double out = Math.max(0, Math.sqrt(dx * dx + dz * dz) - SporeDrift.RADIUS);
            out = Math.max(out, Math.max(-SporeDrift.BELOW - dy, dy - SporeDrift.ABOVE));
            double near = SporeDrift.inside(dx, dy, dz) ? 1 : Math.max(0, 1 - out / SPORE_EDGE) * 0.5;
            best = Math.max(best, cloud.density() * near);
        }
        return best;
    }

    /**
     * 尘卷风卷本机玩家：和服务端卷生物的算法一样（{@link Vortex}），人的重算 1。飞着的、旁观的、坐着载具的不卷。
     */
    private static void carry(ClientLevel level, LocalPlayer player) {
        if (player.isSpectator() || player.getAbilities().flying || player.isPassenger()) {
            caughtBy = -1;
            return;
        }
        long time = level.getGameTime();
        if (time < cooldownUntil) {
            return;
        }
        double reach = Vortex.radiusAt(Vortex.HEIGHT) + 1;
        AABB box = player.getBoundingBox().inflate(reach, Vortex.HEIGHT + 1, reach);
        for (DustDevil devil : level.getEntitiesOfClass(DustDevil.class, box)) {
            double strength = devil.strength();
            double dx = player.getX() - devil.getX();
            double dy = player.getY() - devil.getY();
            double dz = player.getZ() - devil.getZ();
            if (strength <= 0.3 || !Vortex.reaches(dx, dy, dz)) {
                continue;
            }
            if (caughtBy != devil.getId()) {
                caughtBy = devil.getId();
                caughtTicks = 0;
                caughtLimit = Vortex.catchTicks(Rng.of(time ^ (long) devil.getId() << 20));
            }
            boolean fling = Vortex.flings(caughtTicks, caughtLimit, dy);
            double[] v = fling ? Vortex.fling(dx, dz, 1) : Vortex.carry(dx, dy, dz, 1);
            player.setDeltaMovement(v[0] * strength, v[1] * strength, v[2] * strength);
            if (fling) {
                caughtBy = -1;
                cooldownUntil = time + Vortex.COOLDOWN;
            } else {
                caughtTicks++;
            }
            return;
        }
        caughtBy = -1;
    }

    private static void angles(ViewportEvent.ComputeCameraAngles event) {
        float a = Mth.lerp(event.getPartialTick(), amplitudeOld, amplitude);
        Minecraft minecraft = Minecraft.getInstance();
        if (a <= 0.005F || minecraft.level == null) {
            return;
        }
        float scale = minecraft.options.screenEffectScale().get().floatValue();
        if (scale <= 0) {
            return;
        }
        a *= scale;
        // 游戏刻取个余数，免得 float 精度不够、抖动一顿一顿的；地震是低沉的晃，比巨型生物的脚步慢一些
        float time = minecraft.level.getGameTime() % 10000 + event.getPartialTick();
        event.setPitch(event.getPitch() + a * (Mth.sin(time * 1.9F) + 0.5F * Mth.sin(time * 3.7F + 1.3F)) / 1.5F);
        event.setRoll(event.getRoll() + a * 0.7F * (Mth.sin(time * 2.3F + 2.1F) + 0.5F * Mth.sin(time * 4.6F)) / 1.5F);
        event.setYaw(event.getYaw() + a * 0.4F * Mth.sin(time * 1.6F + 0.4F));
    }

    private static float sporeTint(float partialTick) {
        return Mth.lerp(partialTick, sporesOld, spores);
    }

    private static void fogColor(ViewportEvent.ComputeFogColor event) {
        float s = sporeTint(event.getPartialTick());
        if (s <= 0.01F || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        float k = s * 0.75F;
        event.setRed(Mth.lerp(k, event.getRed(), (SPORE_FOG >> 16 & 0xFF) / 255F));
        event.setGreen(Mth.lerp(k, event.getGreen(), (SPORE_FOG >> 8 & 0xFF) / 255F));
        event.setBlue(Mth.lerp(k, event.getBlue(), (SPORE_FOG & 0xFF) / 255F));
    }

    private static void fog(ViewportEvent.RenderFog event) {
        float s = sporeTint(event.getPartialTick());
        if (s <= 0.01F || event.getType() != FogType.NONE) {
            return;
        }
        float end = Math.min(event.getFarPlaneDistance(), Mth.lerp(s, event.getFarPlaneDistance(), SPORE_FOG_END));
        event.setFarPlaneDistance(end);
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), Mth.lerp(s, event.getNearPlaneDistance(), 0)));
    }

    /** 孢子云里画面蒙一层黄绿，四边更浓。 */
    private static void renderTint(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        float s = sporeTint(delta.getGameTimeDeltaPartialTick(false));
        if (s <= 0.01F || minecraft.player == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int rgb = 0x5E7A26;
        graphics.fill(0, 0, width, height, (int) (s * 54) << 24 | rgb);
        int edge = height / 4;
        int strong = (int) (s * 96) << 24 | rgb;
        graphics.fillGradient(0, 0, width, edge, strong, rgb);
        graphics.fillGradient(0, height - edge, width, height, rgb, strong);
    }

    private static void devVerbs() {
        DevScript.EXTRA_VERBS.put("hazard", args -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null && args.length > 0) {
                player.connection.sendCommand("universe hazard " + args[0]);
            }
        });
        DevScript.EXTRA_VERBS.put("techscreen", args -> ClientPacketDistributor.sendToServer(OpenTechPayload.INSTANCE));
        DevScript.EXTRA_VERBS.put("hazardlog", args -> {
            LocalPlayer player = Minecraft.getInstance().player;
            Vec3 at = player == null ? Vec3.ZERO : player.position();
            Vec3 v = player == null ? Vec3.ZERO : player.getDeltaMovement();
            Universe.LOGGER.info("Dev script: hazards shake {} spores {} caught {} at {} moving {}",
                    String.format(Locale.ROOT, "%.2f", amplitude), String.format(Locale.ROOT, "%.2f", spores), caughtBy,
                    String.format(Locale.ROOT, "%.1f %.1f %.1f", at.x, at.y, at.z), String.format(Locale.ROOT, "%.2f %.2f %.2f", v.x, v.y, v.z));
        });
    }
}
