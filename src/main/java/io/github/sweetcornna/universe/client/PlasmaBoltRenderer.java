package io.github.sweetcornna.universe.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.entity.PlasmaBolt;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * 等离子弹：一张始终朝着镜头的发光贴片（不受光照），外圈按弹体的颜色着色，中间叠一层白色的亮芯。拖尾是实体自己撒的粒子。
 */
public class PlasmaBoltRenderer extends EntityRenderer<PlasmaBolt, PlasmaBoltRenderer.State> {
    private static final Identifier TEXTURE = Universe.id("textures/entity/plasma_bolt.png");
    private static final RenderType RENDER_TYPE = RenderTypes.eyes(TEXTURE);

    public static class State extends EntityRenderState {
        int color = PlasmaBolt.PLAYER_COLOR;
    }

    public PlasmaBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected int getBlockLightLevel(PlasmaBolt entity, BlockPos blockPos) {
        return 15;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PlasmaBolt entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.color = entity.color();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0, 0.125, 0);
        poseStack.rotate(camera.orientation);
        int glow = 0xFF000000 | state.color;
        poseStack.pushPose();
        poseStack.scale(0.7F, 0.7F, 0.7F);
        submitNodeCollector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> quad(pose, buffer, glow));
        poseStack.popPose();
        poseStack.scale(0.3F, 0.3F, 0.3F);
        submitNodeCollector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> quad(pose, buffer, 0xFFFFFFFF));
        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        vertex(buffer, pose, -0.5F, -0.5F, 0, 1, color);
        vertex(buffer, pose, 0.5F, -0.5F, 1, 1, color);
        vertex(buffer, pose, 0.5F, 0.5F, 1, 0, color);
        vertex(buffer, pose, -0.5F, 0.5F, 0, 0, color);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float u, float v, int color) {
        buffer.addVertex(pose, x, y, 0)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(pose, 0, 1, 0);
    }
}
