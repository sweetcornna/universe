package io.github.sweetcornna.universe.client.hazard;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.hazard.LavaBomb;
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
 * 熔岩弹（ADR-0034 §六）：一团翻滚着的熔岩，外面一层发黑的硬壳，裂缝里透着红光；外面一圈忽明忽暗的热光。
 * 都是朝着镜头的贴片，壳不受光照（自己在发光）。拖尾的烟和火星是实体自己撒的粒子。
 */
public class LavaBombRenderer extends EntityRenderer<LavaBomb, LavaBombRenderer.State> {
    private static final Identifier TEXTURE = Universe.id("textures/entity/hazard/lava_bomb.png");
    private static final Identifier GLOW = Universe.id("textures/entity/hazard/lava_bomb_glow.png");
    private static final Identifier HALO = Universe.id("textures/entity/hazard/lava_bomb_halo.png");
    private static final RenderType BODY = RenderTypes.entityCutout(TEXTURE);
    private static final RenderType BODY_GLOW = RenderTypes.eyes(GLOW);
    private static final RenderType HALO_GLOW = RenderTypes.eyes(HALO);

    public static class State extends EntityRenderState {
        /** 翻滚的角度（度）。 */
        float spin;
        /** 热光的明暗 0–1。 */
        float pulse = 1;
    }

    public LavaBombRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected int getBlockLightLevel(LavaBomb entity, BlockPos blockPos) {
        return 15;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(LavaBomb entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        // 每颗起始的角度、转的方向不一样
        int id = entity.getId();
        float direction = (id & 1) == 0 ? 1 : -1;
        state.spin = id * 47 % 360 + direction * state.ageInTicks * 13;
        state.pulse = 0.8F + 0.2F * Mth.sin(state.ageInTicks * 0.9F + id) * Mth.sin(state.ageInTicks * 0.37F);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0, 0.35, 0);
        poseStack.rotate(camera.orientation);
        poseStack.pushPose();
        float halo = 2.0F + 0.25F * state.pulse;
        poseStack.scale(halo, halo, halo);
        int haloColor = fade(state.pulse);
        submitNodeCollector.submitCustomGeometry(poseStack, HALO_GLOW, (pose, buffer) -> quad(pose, buffer, haloColor));
        poseStack.popPose();
        poseStack.rotateDegrees(Axis.ZP, state.spin);
        poseStack.scale(0.95F, 0.95F, 0.95F);
        submitNodeCollector.submitCustomGeometry(poseStack, BODY, (pose, buffer) -> quad(pose, buffer, 0xFFFFFFFF));
        int glow = fade(0.75F + 0.25F * state.pulse);
        submitNodeCollector.submitCustomGeometry(poseStack, BODY_GLOW, (pose, buffer) -> quad(pose, buffer, glow));
        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    /** 白色，不透明度 {@code k}（发光层按不透明度混合）。 */
    private static int fade(float k) {
        return (int) (Mth.clamp(k, 0, 1) * 255) << 24 | 0xFFFFFF;
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
