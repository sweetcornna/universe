package io.github.sweetcornna.universe.client.darkfog.hive;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.darkfog.hive.HiveBuilding;
import io.github.sweetcornna.universe.darkfog.hive.HivePlasmaShell;
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
 * 巢穴等离子炮弹：一大一小两张朝着镜头的发光贴片，外圈橙红、中间白芯，一闪一闪（贴图和地面黑雾的弹体共用）。
 * 拖尾是炮弹自己撒的粒子。太空里要隔得很远就看得见，炮弹比别的弹体画得大。
 */
public class HiveShellRenderer extends EntityRenderer<HivePlasmaShell, HiveShellRenderer.State> {
    private static final Identifier TEXTURE = Universe.id("textures/entity/plasma_bolt.png");
    private static final RenderType RENDER_TYPE = RenderTypes.eyes(TEXTURE);
    private static final float SIZE = 1.8F;

    public static class State extends EntityRenderState {
        float flicker;
    }

    public HiveShellRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected int getBlockLightLevel(HivePlasmaShell entity, BlockPos blockPos) {
        return 15;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(HivePlasmaShell entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.flicker = 0.85F + 0.15F * Mth.sin((entity.tickCount + partialTicks) * 1.3F);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0, 0.3, 0);
        poseStack.rotate(camera.orientation);
        int glow = 0xFF000000 | HiveBeams.scale(HiveBuilding.PLASMA_COLOR, state.flicker);
        poseStack.pushPose();
        float size = SIZE * state.flicker;
        poseStack.scale(size, size, size);
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> quad(pose, buffer, glow));
        poseStack.popPose();
        float core = SIZE * 0.4F;
        poseStack.scale(core, core, core);
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> quad(pose, buffer, 0xFFFFFFFF));
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
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
