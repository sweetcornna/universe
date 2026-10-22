package io.github.sweetcornna.universe.client.exocraft;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.exocraft.Submarine;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * 潜艇的渲染器（ADR-0026）：艇头朝艇的偏航，绕驾驶舱俯仰（驾驶员的头一直在玻璃罩里），螺旋桨按推进器转；
 * 艇身用不透明的皮肤，驾驶舱的玻璃另画一遍半透明的，灯、光柱、侧推喷口和仪表叠一层发光；拿镐敲时左右晃一下。
 */
public class SubmarineRenderer extends EntityRenderer<Submarine, SubmarineRenderState> {
    private static final Identifier TEXTURE = Universe.id("textures/entity/submarine.png");
    private static final Identifier GLOW = Universe.id("textures/entity/submarine_glow.png");
    private final SubmarineModel model = SubmarineModel.create();
    private final SubmarineModel.Glass glass = SubmarineModel.glass();

    public SubmarineRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.2F;
    }

    @Override
    public SubmarineRenderState createRenderState() {
        return new SubmarineRenderState();
    }

    @Override
    public void extractRenderState(Submarine entity, SubmarineRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yRot = entity.getYRot(partialTicks);
        state.xRot = entity.getXRot(partialTicks);
        state.propellerSpin = Mth.lerp(partialTicks, entity.propellerSpinO, entity.propellerSpin);
        state.lights = entity.isVehicle() && entity.isInWater();
        state.hurtTime = entity.getHurtTime() - partialTicks;
        state.hurtDir = entity.getHurtDir();
    }

    @Override
    protected AABB getBoundingBoxForCulling(Submarine entity, float partialTicks) {
        // 艇身、螺旋桨和光柱都伸出碰撞箱外面
        return super.getBoundingBoxForCulling(entity, partialTicks).inflate(3.5, 1.0, 3.5);
    }

    @Override
    public void submit(SubmarineRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        // 轴心在驾驶舱：先挪到那儿（艇身中心往艇头方向、往上），绕它转，再挪回模型的原点
        float pivotUp = 1.501F - SubmarineModel.PIVOT_Y / 16F;
        float pivotForward = -SubmarineModel.PIVOT_Z / 16F;
        float yaw = state.yRot * Mth.DEG_TO_RAD;
        poseStack.translate(-Mth.sin(yaw) * pivotForward, pivotUp, Mth.cos(yaw) * pivotForward);
        poseStack.rotate(Axis.YP.rotationDegrees(180 - state.yRot));
        poseStack.rotate(Axis.XP.rotationDegrees(-state.xRot));
        if (state.hurtTime > 0) {
            poseStack.rotate(Axis.ZP.rotationDegrees(Mth.sin(state.hurtTime) * state.hurtTime * 0.4F * state.hurtDir));
        }
        poseStack.translate(0, SubmarineModel.PIVOT_Y / 16F, pivotForward);
        poseStack.scale(-1, -1, 1);
        collector.submitModel(this.model, state, poseStack, RenderTypes.entityCutout(TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY,
                state.outlineColor);
        collector.order(1).submitModel(this.model, state, poseStack, RenderTypes.eyes(GLOW), state.lightCoords, OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF, null, state.outlineColor);
        collector.order(2).submitModel(this.glass, state, poseStack, RenderTypes.entityTranslucent(TEXTURE), state.lightCoords,
                OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
