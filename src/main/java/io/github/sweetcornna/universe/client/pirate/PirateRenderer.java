package io.github.sweetcornna.universe.client.pirate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.pirate.PirateFighter;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * 海盗战机的渲染器：机头朝着飞行方向（偏航、俯仰），转弯时侧倾；引擎、座舱、炮口叠一层发光（撤走时引擎更亮）。
 */
public class PirateRenderer extends EntityRenderer<PirateFighter, PirateRenderState> {
    private static final Identifier TEXTURE = Universe.id("textures/entity/pirate_fighter.png");
    private static final Identifier GLOW = Universe.id("textures/entity/pirate_fighter_glow.png");
    private static final int GLOW_COLOR = 0xFFFF5A3A;
    private static final int LEAVING_COLOR = 0xFFFFC07A;
    private final PirateModel model = PirateModel.create();

    public PirateRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0;
    }

    @Override
    public PirateRenderState createRenderState() {
        return new PirateRenderState();
    }

    @Override
    public void extractRenderState(PirateFighter entity, PirateRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yRot = entity.getYRot(partialTicks);
        state.xRot = entity.getXRot(partialTicks);
        state.bank = Mth.lerp(partialTicks, entity.bankO, entity.bank);
        state.leaving = entity.leaving();
        state.phase = (entity.getId() * 37 % 100) / 100F * Mth.TWO_PI;
    }

    @Override
    protected int getBlockLightLevel(PirateFighter entity, BlockPos pos) {
        // 太空里没有方块光，机身别黑成一团
        return Math.max(9, super.getBlockLightLevel(entity, pos));
    }

    @Override
    public void submit(PirateRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0, state.boundingBoxHeight / 2, 0);
        poseStack.rotate(Axis.YP.rotationDegrees(180 - state.yRot));
        poseStack.rotate(Axis.XP.rotationDegrees(-state.xRot));
        poseStack.rotate(Axis.ZP.rotationDegrees(state.bank));
        poseStack.scale(-1, -1, 1);
        collector.submitModel(this.model, state, poseStack, RenderTypes.entityCutout(TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY,
                state.outlineColor);
        collector.order(1).submitModel(this.model, state, poseStack, RenderTypes.eyes(GLOW), state.lightCoords, OverlayTexture.NO_OVERLAY,
                state.leaving ? LEAVING_COLOR : GLOW_COLOR, null, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
