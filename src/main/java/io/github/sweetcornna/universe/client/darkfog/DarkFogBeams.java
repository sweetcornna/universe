package io.github.sweetcornna.universe.client.darkfog;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * 黑雾的光束：激光塔烧人的光束、中继站往下送能量的光柱、突袭者的电弧。都是朝着镜头的发光带子，外面一层着色、里面一层白芯；
 * 电弧分成几段、每段随机错开一点，每隔两刻换一次形状。
 */
final class DarkFogBeams {
    private static final Identifier TEXTURE = Universe.id("textures/entity/dark_fog_beam.png");
    private static final RenderType RENDER_TYPE = RenderTypes.eyes(TEXTURE);

    private DarkFogBeams() {
    }

    /**
     * 从 {@code from} 画到 {@code to}，两个点都是相对于当前 {@code poseStack} 原点的位置。
     *
     * @param camera 镜头相对于原点的位置（带子朝着镜头）
     * @param jagged 电弧：分段错开
     */
    static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 from, Vec3 to, Vec3 camera, int rgb, float width, boolean jagged,
            float time) {
        Vec3 axis = to.subtract(from);
        double length = axis.length();
        if (length < 1e-3) {
            return;
        }
        int segments = jagged ? Math.max(3, (int) Math.ceil(length / 0.8)) : 1;
        Vec3[] points = new Vec3[segments + 1];
        long seed = (long) Math.floor(time / 2);
        for (int i = 0; i <= segments; i++) {
            Vec3 p = from.lerp(to, i / (double) segments);
            if (jagged && i > 0 && i < segments) {
                double jitter = 0.35;
                p = p.add(noise(seed, i, 0) * jitter, noise(seed, i, 1) * jitter, noise(seed, i, 2) * jitter);
            }
            points[i] = p;
        }
        int glow = 0xFF000000 | rgb;
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
            for (int i = 0; i < segments; i++) {
                ribbon(pose, buffer, points[i], points[i + 1], camera, width, glow);
                ribbon(pose, buffer, points[i], points[i + 1], camera, width * 0.35F, 0xFFFFFFFF);
            }
        });
    }

    private static double noise(long seed, int i, int axis) {
        long h = seed * 0x9E3779B97F4A7C15L + i * 0xBF58476D1CE4E5B9L + axis * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        h *= 0x7FB5D329728EA185L;
        h ^= h >>> 27;
        return ((h >>> 11) / (double) (1L << 53)) * 2 - 1;
    }

    /** 一段朝着镜头的带子。 */
    private static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 camera, float width, int color) {
        Vec3 axis = b.subtract(a);
        Vec3 view = camera.subtract(a.add(b).scale(0.5));
        Vec3 side = axis.cross(view);
        if (side.lengthSqr() < 1e-8) {
            side = axis.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < 1e-8) {
                side = new Vec3(1, 0, 0);
            }
        }
        // 贴图横着从透明到亮再到透明，竖着不变
        side = side.normalize().scale(width / 2);
        vertex(buffer, pose, a.subtract(side), 0, 0, color);
        vertex(buffer, pose, a.add(side), 1, 0, color);
        vertex(buffer, pose, b.add(side), 1, 1, color);
        vertex(buffer, pose, b.subtract(side), 0, 1, color);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, float u, float v, int color) {
        buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(pose, 0, 1, 0);
    }
}
