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
 * 守卫机的拦截机（ADR-0024）用同一副模型，换一套贴图和发光的颜色。
 */
public class PirateRenderer extends EntityRenderer<PirateFighter, PirateRenderState> {
    private static final int LEAVING_COLOR = 0xFFFFC07A;
    private final PirateModel model = PirateModel.create();
    private final Identifier texture;
    private final Identifier glow;
    private final int glowColor;

    public PirateRenderer(EntityRendererProvider.Context context) {
        this(context, "pirate_fighter", 0xFFFF5A3A);
    }

    /** @param name 贴图名，发光层是后面加上 _glow 的那张 */
    public PirateRenderer(EntityRendererProvider.Context context, String name, int glowColor) {
        super(context);
        this.shadowRadius = 0;
        this.texture = Universe.id("textures/entity/" + name + ".png");
        this.glow = Universe.id("textures/entity/" + name + "_glow.png");
        this.glowColor = glowColor;
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
        collector.submitModel(this.model, state, poseStack, RenderTypes.entityCutout(this.texture), state.lightCoords, OverlayTexture.NO_OVERLAY,
                state.outlineColor);
        collector.order(1).submitModel(this.model, state, poseStack, RenderTypes.eyes(this.glow), state.lightCoords, OverlayTexture.NO_OVERLAY,
                state.leaving ? LEAVING_COLOR : this.glowColor, null, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
