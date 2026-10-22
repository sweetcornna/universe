package io.github.sweetcornna.universe.client.exocraft;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.exocraft.ExoMech;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/** 机甲的渲染器（ADR-0026）：身子朝机甲的偏航，腿按走过的路迈；面罩、驾驶舱的仪表、喷口和炮口叠一层发光；拿镐敲时晃一下。 */
public class ExoMechRenderer extends EntityRenderer<ExoMech, ExoMechRenderState> {
    private static final Identifier TEXTURE = Universe.id("textures/entity/exo_mech.png");
    private static final Identifier GLOW = Universe.id("textures/entity/exo_mech_glow.png");
    private final ExoMechModel model = ExoMechModel.create();

    public ExoMechRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.0F;
    }

    @Override
    public ExoMechRenderState createRenderState() {
        return new ExoMechRenderState();
    }

    @Override
    public void extractRenderState(ExoMech entity, ExoMechRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yRot = entity.getYRot(partialTicks);
        state.stride = Mth.lerp(partialTicks, entity.strideO, entity.stride);
        state.strideAmount = entity.strideAmount;
        state.thrusting = entity.thrusting();
        state.grounded = entity.onGround();
        state.hurtTime = entity.getHurtTime() - partialTicks;
        state.hurtDir = entity.getHurtDir();
    }

    @Override
    protected AABB getBoundingBoxForCulling(ExoMech entity, float partialTicks) {
        // 炮管和天线伸出碰撞箱
        return super.getBoundingBoxForCulling(entity, partialTicks).inflate(0.6, 0.5, 0.6);
    }

    @Override
    public void submit(ExoMechRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0, 1.501F, 0);
        poseStack.rotate(Axis.YP.rotationDegrees(180 - state.yRot));
        if (state.hurtTime > 0) {
            poseStack.rotate(Axis.ZP.rotationDegrees(Mth.sin(state.hurtTime) * state.hurtTime * 0.3F * state.hurtDir));
        }
        poseStack.scale(-1, -1, 1);
        collector.submitModel(this.model, state, poseStack, RenderTypes.entityCutout(TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY,
                state.outlineColor);
        collector.order(1).submitModel(this.model, state, poseStack, RenderTypes.eyes(GLOW), state.lightCoords, OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF, null, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
