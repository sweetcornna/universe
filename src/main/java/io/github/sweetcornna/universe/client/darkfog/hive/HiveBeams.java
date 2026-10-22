package io.github.sweetcornna.universe.client.darkfog.hive;

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
 * 巢穴的光束：光子接收器往恒星伸出去的细光束。和地面黑雾的光束一样，是朝着镜头的发光带子，外面一层着色、里面一层白芯；
 * 这一道越往远处越淡（分几段，透明度一段段降下去），看上去像伸进了黑暗里。核心连到各座建筑的系索也在这里画。
 */
final class HiveBeams {
    private static final Identifier TEXTURE = Universe.id("textures/entity/dark_fog_beam.png");
    private static final RenderType RENDER_TYPE = RenderTypes.eyes(TEXTURE);
    private static final int SEGMENTS = 6;
    /** 系索上的亮点隔多远一个、每刻往外走多远（格）。 */
    private static final double PULSE_SPACING = 16;
    private static final double PULSE_SPEED = 0.4;

    private HiveBeams() {
    }

    /**
     * 从 {@code from} 画到 {@code to}，两个点都是相对于当前 {@code poseStack} 原点的位置。
     *
     * @param camera 镜头相对于原点的位置（带子朝着镜头）
     */
    static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 from, Vec3 to, Vec3 camera, int rgb, float width) {
        if (to.subtract(from).lengthSqr() < 1e-6) {
            return;
        }
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
            for (int i = 0; i < SEGMENTS; i++) {
                Vec3 a = from.lerp(to, i / (double) SEGMENTS);
                Vec3 b = from.lerp(to, (i + 1) / (double) SEGMENTS);
                // 发光的渲染类型按颜色的亮度算透明：越远越暗就是越淡
                float fade = 1 - i / (float) SEGMENTS;
                ribbon(pose, buffer, a, b, camera, width, 0xFF000000 | scale(rgb, fade));
                ribbon(pose, buffer, a, b, camera, width * 0.35F, 0xFF000000 | scale(0xFFFFFF, fade * 0.8F));
            }
        });
    }

    /**
     * 核心连到别的建筑的系索：暗色的底子上一个个亮点从核心往外流，隔 {@link #PULSE_SPACING} 格一个。
     *
     * @param time 刻数（带小数），亮点按它往外走
     */
    static void tether(PoseStack poseStack, SubmitNodeCollector collector, Vec3 from, Vec3 to, Vec3 camera, int rgb, float width, float time) {
        double length = to.subtract(from).length();
        if (length < 1e-3) {
            return;
        }
        int segments = Math.max(4, Math.min(24, (int) (length / 3)));
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
            for (int i = 0; i < segments; i++) {
                Vec3 a = from.lerp(to, i / (double) segments);
                Vec3 b = from.lerp(to, (i + 1) / (double) segments);
                double s = (i + 0.5) / segments * length - time * PULSE_SPEED;
                double pulse = Math.pow(0.5 + 0.5 * Math.cos(s / PULSE_SPACING * Math.PI * 2), 6);
                ribbon(pose, buffer, a, b, camera, width, 0xFF000000 | scale(rgb, (float) (0.3 + 0.7 * pulse)));
            }
        });
    }

    static int scale(int rgb, float f) {
        int r = (int) (((rgb >> 16) & 0xFF) * f);
        int g = (int) (((rgb >> 8) & 0xFF) * f);
        int b = (int) ((rgb & 0xFF) * f);
        return r << 16 | g << 8 | b;
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
