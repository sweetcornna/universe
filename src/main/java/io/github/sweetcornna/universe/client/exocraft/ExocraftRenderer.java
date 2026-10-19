package io.github.sweetcornna.universe.client.exocraft;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.exocraft.Exocraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** 探索车的渲染器：车头朝车的偏航，轮子按走过的路转，前轮跟着转弯偏；大灯、炮口和天线顶上的灯叠一层发光；拿镐敲时晃一下。 */
public class ExocraftRenderer extends EntityRenderer<Exocraft, ExocraftRenderState> {
    private static final Identifier TEXTURE = Universe.id("textures/entity/exocraft.png");
    private static final Identifier GLOW = Universe.id("textures/entity/exocraft_glow.png");
    private final ExocraftModel model = ExocraftModel.create();

    public ExocraftRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.1F;
    }

    @Override
    public ExocraftRenderState createRenderState() {
        return new ExocraftRenderState();
    }

    @Override
    public void extractRenderState(Exocraft entity, ExocraftRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yRot = entity.getYRot(partialTicks);
        state.wheelSpin = Mth.lerp(partialTicks, entity.wheelSpinO, entity.wheelSpin);
        // 这一刻偏航变了多少，看成方向盘打了多少
        float turning = Mth.wrapDegrees(entity.getYRot() - entity.yRotO);
        state.steer = Mth.clamp(turning * 0.12F, -0.45F, 0.45F);
        state.laser = entity.laserOn();
        state.hurtTime = entity.getHurtTime() - partialTicks;
        state.hurtDir = entity.getHurtDir();
    }

    @Override
    public void submit(ExocraftRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0, 1.501F, 0);
        poseStack.rotate(Axis.YP.rotationDegrees(180 - state.yRot));
        if (state.hurtTime > 0) {
            poseStack.rotate(Axis.XP.rotationDegrees(Mth.sin(state.hurtTime) * state.hurtTime * 0.4F * state.hurtDir));
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
