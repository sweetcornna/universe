package io.github.sweetcornna.universe.client.darkfog.space;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.darkfog.space.DarkFogLancer;
import io.github.sweetcornna.universe.darkfog.space.DarkFogSpaceUnit;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * 太空单位的渲染器：枪骑、座头鲸各一个模型和贴图。发光层枪骑是暗红的，座头鲸偏紫，打人、轰炸时更亮。
 * 死的时候不像原版的怪那样侧倒，打着转炸掉。
 */
public class SpaceUnitRenderer extends MobRenderer<DarkFogSpaceUnit, SpaceUnitRenderState, SpaceUnitModel> {
    static final int RED = 0xFF2A48;
    static final int VIOLET = 0xC040FF;
    private final Identifier texture;
    private final Identifier glow;
    private final int color;
    private final float modelScale;

    private SpaceUnitRenderer(EntityRendererProvider.Context context, SpaceUnitModel model, String name, float shadow, int color, float modelScale) {
        super(context, model, shadow);
        this.texture = Universe.id("textures/entity/" + name + ".png");
        this.glow = Universe.id("textures/entity/" + name + "_glow.png");
        this.color = color;
        this.modelScale = modelScale;
        this.addLayer(new GlowLayer(this));
    }

    public static SpaceUnitRenderer lancer(EntityRendererProvider.Context context) {
        return new SpaceUnitRenderer(context, SpaceUnitModel.lancer(), "dark_fog_lancer", 0, RED, 1);
    }

    /** 座头鲸按一半的尺寸建模，这里放大一倍。 */
    public static SpaceUnitRenderer humpback(EntityRendererProvider.Context context) {
        return new SpaceUnitRenderer(context, SpaceUnitModel.humpback(), "dark_fog_humpback", 0, VIOLET, 2);
    }

    @Override
    public SpaceUnitRenderState createRenderState() {
        return new SpaceUnitRenderState();
    }

    @Override
    public void extractRenderState(DarkFogSpaceUnit entity, SpaceUnitRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.kind = entity.kind();
        state.phase = (entity.getId() * 37 % 100) / 100F * Mth.TWO_PI;
        state.angry = entity.angry() || entity instanceof DarkFogLancer lancer && lancer.attackingFacility();
        state.roll = entity.roll(partialTicks);
    }

    @Override
    protected void setupRotations(SpaceUnitRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
        poseStack.rotateDegrees(Axis.YP, 180.0F - bodyRot);
        if (state.deathTime > 0) {
            // 绕着机身中间打转
            float h = state.boundingBoxHeight / 2 / entityScale;
            poseStack.translate(0, h, 0);
            poseStack.rotateDegrees(Axis.ZP, state.deathTime * 25);
            poseStack.translate(0, -h, 0);
        }
    }

    @Override
    protected void scale(SpaceUnitRenderState state, PoseStack poseStack) {
        if (this.modelScale != 1) {
            poseStack.scale(this.modelScale, this.modelScale, this.modelScale);
        }
    }

    @Override
    public Identifier getTextureLocation(SpaceUnitRenderState state) {
        return this.texture;
    }

    @Override
    protected int getBlockLightLevel(DarkFogSpaceUnit entity, BlockPos pos) {
        return Math.max(6, super.getBlockLightLevel(entity, pos));
    }

    private static final class GlowLayer extends RenderLayer<SpaceUnitRenderState, SpaceUnitModel> {
        private final SpaceUnitRenderer renderer;

        GlowLayer(SpaceUnitRenderer renderer) {
            super(renderer);
            this.renderer = renderer;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, SpaceUnitRenderState state, float yRot, float xRot) {
            if (state.isInvisible) {
                return;
            }
            float pulse = state.angry ? 0.85F + 0.15F * Mth.sin(state.ageInTicks * 0.9F + state.phase)
                    : 0.65F + 0.1F * Mth.sin(state.ageInTicks * 0.15F + state.phase);
            collector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(this.renderer.glow),
                    lightCoords, OverlayTexture.NO_OVERLAY, 0xFF000000 | scale(this.renderer.color, pulse), null, state.outlineColor);
        }
    }

    static int scale(int rgb, float f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * f));
        int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((rgb & 0xFF) * f));
        return (r << 16) | (g << 8) | b;
    }
}
