package io.github.sweetcornna.universe.client.mecha;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.mecha.combat.PlasmaShot;
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

/** 舰队和机甲的等离子团：和炮塔的一样，朝着镜头的三层发光贴片，颜色、大小按种类（攻击无人机、机甲、反物质、驱逐舰主炮）。 */
public class PlasmaShotRenderer extends EntityRenderer<PlasmaShot, PlasmaShotRenderer.State> {
    private static final Identifier TEXTURE = Universe.id("textures/entity/plasma_bolt.png");
    private static final RenderType RENDER_TYPE = RenderTypes.eyes(TEXTURE);

    public static class State extends EntityRenderState {
        int color;
        float size;
    }

    public PlasmaShotRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected int getBlockLightLevel(PlasmaShot entity, BlockPos blockPos) {
        return 15;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PlasmaShot entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.color = entity.color();
        state.size = entity.size();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0, 0.2, 0);
        poseStack.rotate(camera.orientation);
        layer(poseStack, collector, state.size, 0xFF000000 | state.color);
        layer(poseStack, collector, state.size * 0.6F, 0xFF000000 | mix(state.color, 0xFFFFFF, 0.55));
        layer(poseStack, collector, state.size * 0.3F, 0xFFFFFFFF);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    private static void layer(PoseStack poseStack, SubmitNodeCollector collector, float size, int color) {
        poseStack.pushPose();
        poseStack.scale(size, size, size);
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
            vertex(buffer, pose, -0.5F, -0.5F, 0, 1, color);
            vertex(buffer, pose, 0.5F, -0.5F, 1, 1, color);
            vertex(buffer, pose, 0.5F, 0.5F, 1, 0, color);
            vertex(buffer, pose, -0.5F, 0.5F, 0, 0, color);
        });
        poseStack.popPose();
    }

    private static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return r << 16 | g << 8 | bl;
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
