package io.github.sweetcornna.universe.client.deepspace;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * 牵引光束和引力束（ADR-0034 §四、§七）：朝着镜头的发光带子，沿途分几节、按时间轻轻扭动，看着是一股力场不是激光。
 * 和舰队、黑雾的光束用同一张渐变贴图。坐标都相对镜头。
 */
public final class Beams {
    private static final Identifier TEXTURE = Universe.id("textures/entity/dark_fog_beam.png");
    public static final RenderType RENDER_TYPE = RenderTypes.eyes(TEXTURE);

    private Beams() {
    }

    /**
     * 一股扭动的束：从 {@code from} 到 {@code to} 分 {@code segments} 节，中间按正弦摆动 {@code wobble} 格，两头不动。
     *
     * @param camera 镜头的位置（和 from、to 用同一套坐标）
     * @param time   刻数（带小数），扭动的相位
     */
    public static void wavy(PoseStack.Pose pose, VertexConsumer buffer, Vec3 from, Vec3 to, Vec3 camera, int rgb, float width, double wobble,
            int segments, double time) {
        Vec3 axis = to.subtract(from);
        double length = axis.length();
        if (length < 1e-4) {
            return;
        }
        Vec3 dir = axis.scale(1 / length);
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 n1 = dir.cross(up).normalize();
        Vec3 n2 = dir.cross(n1).normalize();
        Vec3 previous = from;
        for (int i = 1; i <= segments; i++) {
            double t = i / (double) segments;
            double envelope = Math.sin(Math.PI * t);
            double phase = time * 0.35 + t * length * 0.9;
            Vec3 point = from.add(axis.scale(t))
                    .add(n1.scale(Math.sin(phase) * wobble * envelope))
                    .add(n2.scale(Math.cos(phase * 1.3) * wobble * envelope));
            // 中间亮、两头淡
            float fade = (float) (0.55 + 0.45 * Math.sin(Math.PI * (t - 0.5 / segments)));
            ribbon(pose, buffer, previous, point, camera, width, 0xFF000000 | scale(rgb, fade));
            ribbon(pose, buffer, previous, point, camera, width * 0.3F, 0xFF000000 | scale(0xFFFFFF, 0.35F + 0.4F * fade));
            previous = point;
        }
    }

    static int scale(int rgb, float f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * f));
        int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((rgb & 0xFF) * f));
        return (r << 16) | (g << 8) | b;
    }

    /** 一段朝着镜头的带子。 */
    public static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 camera, float width, int color) {
        Vec3 axis = b.subtract(a);
        Vec3 view = camera.subtract(a.add(b).scale(0.5));
        Vec3 side = axis.cross(view);
        if (side.lengthSqr() < 1e-8) {
            side = axis.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < 1e-8) {
                side = new Vec3(1, 0, 0);
            }
        }
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
