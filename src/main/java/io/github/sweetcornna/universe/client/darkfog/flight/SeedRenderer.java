package io.github.sweetcornna.universe.client.darkfog.flight;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.darkfog.flight.DarkFogCraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** 黑雾种子的渲染器：翻滚的带刺黑壳和一圈环，接缝和环暗红地一明一暗。 */
public class SeedRenderer extends MobRenderer<DarkFogCraft, SeedRenderState, SeedModel> {
    private static final Identifier TEXTURE = Universe.id("textures/entity/dark_fog_seed.png");
    private static final Identifier GLOW = Universe.id("textures/entity/dark_fog_seed_glow.png");
    private static final int COLOR = 0xFF2A48;

    public SeedRenderer(EntityRendererProvider.Context context) {
        super(context, new SeedModel(), 0);
        this.addLayer(new GlowLayer(this));
    }

    @Override
    public SeedRenderState createRenderState() {
        return new SeedRenderState();
    }

    @Override
    public void extractRenderState(DarkFogCraft entity, SeedRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.phase = (entity.getId() * 37 % 100) / 100F * Mth.TWO_PI;
        state.deathTime = 0;
    }

    @Override
    protected void scale(SeedRenderState state, PoseStack poseStack) {
        poseStack.scale(SeedModel.SCALE, SeedModel.SCALE, SeedModel.SCALE);
    }

    @Override
    protected float getFlipDegrees() {
        return 0;
    }

    @Override
    public Identifier getTextureLocation(SeedRenderState state) {
        return TEXTURE;
    }

    @Override
    protected int getBlockLightLevel(DarkFogCraft entity, BlockPos pos) {
        return Math.max(8, super.getBlockLightLevel(entity, pos));
    }

    private static final class GlowLayer extends RenderLayer<SeedRenderState, SeedModel> {
        GlowLayer(SeedRenderer renderer) {
            super(renderer);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, SeedRenderState state, float yRot, float xRot) {
            if (state.isInvisible) {
                return;
            }
            float pulse = 0.65F + 0.35F * Mth.sin(state.ageInTicks * 0.08F + state.phase);
            int r = Math.min(255, (int) ((COLOR >> 16 & 0xFF) * pulse));
            int g = Math.min(255, (int) ((COLOR >> 8 & 0xFF) * pulse));
            int b = Math.min(255, (int) ((COLOR & 0xFF) * pulse));
            collector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(GLOW), lightCoords, OverlayTexture.NO_OVERLAY,
                    0xFF000000 | r << 16 | g << 8 | b, null, state.outlineColor);
        }
    }
}
