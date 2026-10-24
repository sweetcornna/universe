package io.github.sweetcornna.universe.client.mecha;

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
 * 舰队单位的激光：朝着镜头的发光带子，外面一层着色、里面一层白芯，开火后几刻里慢慢变细变暗。和黑雾的光束用同一张渐变贴图。
 */
final class FleetBeams {
    private static final Identifier TEXTURE = Universe.id("textures/entity/dark_fog_beam.png");
    private static final RenderType RENDER_TYPE = RenderTypes.eyes(TEXTURE);

    private FleetBeams() {
    }

    /**
     * 从 {@code from} 画到 {@code to}，两个点都是相对于当前 {@code poseStack} 原点的位置。
     *
     * @param camera 镜头相对于原点的位置（带子朝着镜头）
     * @param fade   1 是刚打出去，往 0 走
     */
    static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 from, Vec3 to, Vec3 camera, int rgb, float width, float fade) {
        if (to.subtract(from).lengthSqr() < 1e-6 || fade <= 0) {
            return;
        }
        int glow = 0xFF000000 | scale(rgb, 0.4F + 0.6F * fade);
        int core = 0xFF000000 | scale(0xFFFFFF, 0.5F + 0.5F * fade);
        float w = width * (0.5F + 0.5F * fade);
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
            ribbon(pose, buffer, from, to, camera, w, glow);
            ribbon(pose, buffer, from, to, camera, w * 0.35F, core);
        });
    }

    static int scale(int rgb, float f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * f));
        int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((rgb & 0xFF) * f));
        return (r << 16) | (g << 8) | b;
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
