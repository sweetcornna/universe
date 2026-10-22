package io.github.sweetcornna.universe.client.darkfog;

import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogBolt;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogShell;
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
import net.minecraft.world.entity.Entity;

/**
 * 黑雾的弹体：激光弹和等离子炮弹都是一张朝着镜头的发光贴片，外圈着色、中间一层白芯（和等离子弹一个画法，贴图也共用）。
 * 拖尾是实体自己撒的粒子。
 */
public class DarkFogProjectileRenderer<T extends Entity> extends EntityRenderer<T, DarkFogProjectileRenderer.State> {
    private static final Identifier TEXTURE = Universe.id("textures/entity/plasma_bolt.png");
    private static final RenderType RENDER_TYPE = RenderTypes.eyes(TEXTURE);
    private final ToIntFunction<T> color;
    private final ToDoubleFunction<T> size;

    public static class State extends EntityRenderState {
        int color;
        float size;
    }

    private DarkFogProjectileRenderer(EntityRendererProvider.Context context, ToIntFunction<T> color, ToDoubleFunction<T> size) {
        super(context);
        this.color = color;
        this.size = size;
    }

    public static DarkFogProjectileRenderer<DarkFogBolt> bolt(EntityRendererProvider.Context context) {
        return new DarkFogProjectileRenderer<>(context, DarkFogBolt::color, DarkFogBolt::size);
    }

    public static DarkFogProjectileRenderer<DarkFogShell> shell(EntityRendererProvider.Context context) {
        return new DarkFogProjectileRenderer<>(context, shell -> 0xFF5A2A, shell -> 1.1);
    }

    @Override
    protected int getBlockLightLevel(T entity, BlockPos blockPos) {
        return 15;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.color = this.color.applyAsInt(entity);
        state.size = (float) this.size.applyAsDouble(entity);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0, 0.125, 0);
        poseStack.rotate(camera.orientation);
        int glow = 0xFF000000 | state.color;
        poseStack.pushPose();
        poseStack.scale(state.size, state.size, state.size);
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> quad(pose, buffer, glow));
        poseStack.popPose();
        float core = state.size * 0.4F;
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
