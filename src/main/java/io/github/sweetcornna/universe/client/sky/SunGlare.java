package io.github.sweetcornna.universe.client.sky;

import org.joml.Matrix3x2fStack;
import org.joml.Vector3fc;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.ScreenProjection;
import io.github.sweetcornna.universe.core.procgen.Colors;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.common.Tags;

/**
 * 太空里看着恒星时镜头上的眩光（ADR-0021）：恒星周围的一大团光晕、六条衍射星芒、一道横向的光带，
 * 沿恒星和画面中心的连线排开的几个鬼影，恒星越靠近画面中心，整个画面越泛白。
 *
 * <p>画天空时记下恒星的方向、视大小、颜色和没被星球挡住的比例（日食）；每刻再从摄像机朝恒星打五条射线，
 * 看有多少被方块挡住（玻璃不挡）。两样乘起来就是眩光的强度，星球从恒星前面经过时眩光跟着变暗、熄灭。
 */
public final class SunGlare {
    private static final Identifier GLOW = Universe.id("textures/gui/flare/glow.png");
    private static final Identifier SPIKES = Universe.id("textures/gui/flare/spikes.png");
    private static final Identifier GHOST = Universe.id("textures/gui/flare/ghost.png");
    private static final Identifier RING = Universe.id("textures/gui/flare/ring.png");
    private static final Identifier STREAK = Universe.id("textures/gui/flare/streak.png");
    /** 叠加混合的界面贴图：颜色乘透明度后加到屏幕上。 */
    static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Universe.id("pipeline/gui_flare"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .build();
    /** 天空这么久没更新，就当恒星看不见了（换了维度、天空没画）。 */
    private static final long STALE_NANOS = 250_000_000L;
    private static final double RAY_LENGTH = 384;
    /** 鬼影：沿恒星到画面中心的连线的位置（0 是恒星，1 是画面中心，2 是对面）、大小（占屏幕高的比例）、颜色、亮度。 */
    private static final double[][] GHOSTS = {
            {0.55, 0.035, 0xFFE6B0, 0.16},
            {1.28, 0.07, 0x9ADCFF, 0.10},
            {1.55, 0.03, 0xC8FFC0, 0.14},
            {1.9, 0.12, 0xFFB890, 0.07},
            {2.35, 0.2, 0xB8C8FF, 0.05},
    };

    private static float dirX;
    private static float dirY;
    private static float dirZ = 1;
    private static float angularDegrees;
    private static int color = 0xFFFFFF;
    private static float unoccluded;
    private static long updated = Long.MIN_VALUE;
    /** 没被方块挡住的比例，按刻平滑。 */
    private static float clear = 1;

    private SunGlare() {
    }

    static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterRenderPipelinesEvent.class, event -> event.registerPipeline(PIPELINE));
        modBus.addListener(RegisterGuiLayersEvent.class, event -> event.registerBelowAll(Universe.id("sun_glare"), SunGlare::render));
        gameBus.addListener(ClientTickEvent.Post.class, event -> tick());
    }

    /**
     * 画太空天空的时候调用：恒星在哪个方向、多大、什么颜色，露出多少。
     *
     * @param direction       从摄像机指向恒星的单位向量
     * @param angularDiameter 恒星的视直径（度）
     * @param visible         没被星球挡住的比例
     */
    static void update(Vector3fc direction, float angularDiameter, int starColor, float visible) {
        dirX = direction.x();
        dirY = direction.y();
        dirZ = direction.z();
        angularDegrees = angularDiameter;
        color = starColor;
        unoccluded = visible;
        updated = Util.getNanos();
    }

    private static boolean active(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        return level != null && SpaceDimensions.isSpace(level.dimension()) && Util.getNanos() - updated < STALE_NANOS;
    }

    /** 每刻朝恒星的圆面打五条射线：中心一条，上下左右各一条。 */
    private static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active(minecraft)) {
            clear = 1;
            return;
        }
        ClientLevel level = minecraft.level;
        Vec3 eye = minecraft.gameRenderer.mainCamera().position();
        Vec3 forward = new Vec3(dirX, dirY, dirZ);
        Vec3 reference = Math.abs(forward.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = forward.cross(reference).normalize();
        Vec3 up = right.cross(forward).normalize();
        double spread = Math.tan(Math.toRadians(angularDegrees) / 2) * 0.6;
        int open = 0;
        for (int i = 0; i < 5; i++) {
            Vec3 d = switch (i) {
                case 1 -> forward.add(right.scale(spread));
                case 2 -> forward.subtract(right.scale(spread));
                case 3 -> forward.add(up.scale(spread));
                case 4 -> forward.subtract(up.scale(spread));
                default -> forward;
            };
            if (reachesSky(level, eye, d.normalize())) {
                open++;
            }
        }
        float target = open / 5.0F;
        clear += (target - clear) * 0.5F;
    }

    /** 射线一路没碰到不透光的方块；玻璃和玻璃板让光过去。 */
    private static boolean reachesSky(ClientLevel level, Vec3 eye, Vec3 direction) {
        Vec3 from = eye;
        Vec3 to = eye.add(direction.scale(RAY_LENGTH));
        for (int pass = 0; pass < 4; pass++) {
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.MISS) {
                return true;
            }
            BlockState state = level.getBlockState(hit.getBlockPos());
            if (!state.is(Tags.BlockItems.GLASS_BLOCKS.block()) && !state.is(Tags.BlockItems.GLASS_PANES.block())) {
                return false;
            }
            from = hit.getLocation().add(direction.scale(0.75));
        }
        return false;
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active(minecraft)) {
            return;
        }
        float strength = unoccluded * clear;
        if (strength < 0.01F) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        ScreenProjection projection = ScreenProjection.of(minecraft, width, height);
        double[] sun = projection.screen(projection.eye().add(dirX * 1000.0, dirY * 1000.0, dirZ * 1000.0));
        if (sun == null) {
            return;
        }
        double sx = sun[0];
        double sy = sun[1];
        // 恒星出了画面还有一段余光，离得越远越淡
        double outside = Math.max(Math.max(-sx, sx - width), Math.max(-sy, sy - height));
        float onScreen = (float) Mth.clamp(1 - outside / (height * 0.35), 0, 1);
        float k = strength * onScreen * onScreen;
        if (k < 0.01F) {
            return;
        }
        double cx = width / 2.0;
        double cy = height / 2.0;
        float centered = (float) Mth.clamp(1 - Math.hypot(sx - cx, sy - cy) / Math.hypot(cx, cy), 0, 1);
        int tint = Colors.lerp(color, 0xFFFFFF, 0.6);
        // 视直径越大光晕越大；画面高度的一半左右
        float size = height * Mth.clamp(0.55F + angularDegrees * 0.04F, 0.55F, 1.1F);

        // 整个画面泛白：恒星越靠近中间越明显
        int veil = (int) (255 * 0.10F * k * centered * centered);
        if (veil > 0) {
            graphics.fill(0, 0, width, height, ARGB.color(veil, tint));
        }
        draw(graphics, GLOW, sx, sy, size, size, 0, tint, 0.95F * k);
        draw(graphics, SPIKES, sx, sy, size * 1.5F, size * 1.5F, 0.26F, 0xFFFFFF, 0.8F * k);
        draw(graphics, STREAK, sx, sy, width * 1.1F, height * 0.05F, 0, 0x9CC4FF, 0.35F * k);
        for (double[] ghost : GHOSTS) {
            double gx = sx + (cx - sx) * ghost[0];
            double gy = sy + (cy - sy) * ghost[0];
            float g = (float) (ghost[1] * height);
            draw(graphics, GHOST, gx, gy, g, g, 0.26F, (int) ghost[2], (float) ghost[3] * k * (0.4F + 0.6F * centered));
        }
        // 对面一道淡淡的彩色大圈
        double rx = sx + (cx - sx) * 1.7;
        double ry = sy + (cy - sy) * 1.7;
        draw(graphics, RING, rx, ry, height * 0.42F, height * 0.42F, 0, 0xFFFFFF, 0.06F * k * centered);
    }

    /** 以 (x, y) 为中心画一张眩光贴图，转 {@code angle} 弧度。 */
    private static void draw(GuiGraphicsExtractor graphics, Identifier texture, double x, double y, float w, float h, float angle, int rgb,
            float alpha) {
        int a = Math.round(Mth.clamp(alpha, 0, 1) * 255);
        if (a <= 0) {
            return;
        }
        int iw = Math.max(1, Math.round(w));
        int ih = Math.max(1, Math.round(h));
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate((float) x, (float) y);
        if (angle != 0) {
            pose.rotate(angle);
        }
        graphics.blit(PIPELINE, texture, -iw / 2, -ih / 2, 0, 0, iw, ih, iw, ih, ARGB.color(a, rgb));
        pose.popMatrix();
    }
}
