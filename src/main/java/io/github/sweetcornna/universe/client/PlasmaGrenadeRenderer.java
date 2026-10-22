package io.github.sweetcornna.universe.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.survival.ToolWeapon;
import io.github.sweetcornna.universe.entity.PlasmaGrenade;
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
import net.minecraft.util.Mth;

/**
 * 等离子榴弹：和等离子弹同一张发光贴片，紫色，一胀一缩地闪；快炸的时候闪得更快、亮芯更大。
 */
public class PlasmaGrenadeRenderer extends EntityRenderer<PlasmaGrenade, PlasmaGrenadeRenderer.State> {
    private static final Identifier TEXTURE = Universe.id("textures/entity/plasma_bolt.png");
    private static final RenderType RENDER_TYPE = RenderTypes.eyes(TEXTURE);

    public static class State extends EntityRenderState {
        float pulse;
        float urgency;
    }

    public PlasmaGrenadeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected int getBlockLightLevel(PlasmaGrenade entity, BlockPos blockPos) {
        return 15;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PlasmaGrenade entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        float age = entity.tickCount + partialTicks;
        state.urgency = Mth.clamp(age / ToolWeapon.GRENADE_FUSE, 0, 1);
        // 前一秒半慢闪，最后半秒快闪
        float speed = state.urgency > 0.75F ? 1.6F : 0.6F;
        state.pulse = 0.5F + 0.5F * Mth.sin(age * speed);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0, 0.15, 0);
        poseStack.rotate(camera.orientation);
        int glow = 0xFF000000 | ToolWeapon.PLASMA_LAUNCHER.color();
        float outer = 0.75F + 0.25F * state.pulse;
        poseStack.pushPose();
        poseStack.scale(outer, outer, outer);
        submitNodeCollector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> quad(pose, buffer, glow));
        poseStack.popPose();
        float core = 0.25F + 0.2F * state.urgency * state.pulse;
        poseStack.scale(core, core, core);
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
