package io.github.sweetcornna.universe.client.fossil;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.fossil.StoneSentryRules;
import io.github.sweetcornna.universe.fossil.StoneSentry;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * 石哨兵的渲染器：石头的底图加一层发光的石缝（{@code _glow}，按状态着色）。没盯上人时是暗的琥珀色，盯上了烧成橙红，
 * 点着以后一明一暗、越来越白。
 */
public class StoneSentryRenderer extends MobRenderer<StoneSentry, StoneSentryRenderState, StoneSentryModel> {
    private static final Identifier TEXTURE = Universe.id("textures/entity/stone_sentry.png");
    private static final Identifier GLOW = Universe.id("textures/entity/stone_sentry_glow.png");
    static final int CALM_COLOR = 0xC88A3A;
    static final int HOSTILE_COLOR = 0xFF6A24;
    static final int FUSE_COLOR = 0xFFF0B0;

    public StoneSentryRenderer(EntityRendererProvider.Context context) {
        super(context, StoneSentryModel.create(), 0.6F);
        this.addLayer(new GlowLayer(this));
    }

    @Override
    public StoneSentryRenderState createRenderState() {
        return new StoneSentryRenderState();
    }

    @Override
    public void extractRenderState(StoneSentry entity, StoneSentryRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.rising = entity.rising();
        state.windup = entity.windup();
        state.fuse = entity.fused() ? Math.min(1, (entity.fuseTicks() + partialTicks) / StoneSentryRules.FUSE_TICKS) : 0;
        state.hostile = entity.angry() || entity.windup() > 0;
        state.bobPhase = (entity.getId() * 53 % 100) / 100F * Mth.TWO_PI;
    }

    @Override
    public Identifier getTextureLocation(StoneSentryRenderState state) {
        return TEXTURE;
    }

    @Override
    protected int getBlockLightLevel(StoneSentry entity, BlockPos pos) {
        return Math.max(4, super.getBlockLightLevel(entity, pos));
    }

    @Override
    protected float getShadowRadius(StoneSentryRenderState state) {
        return 0.6F * (1 - state.rising * 0.5F);
    }

    /** 发光的石缝：琥珀、橙红，点着以后闪白。 */
    static int glowColor(StoneSentryRenderState state) {
        if (state.fuse > 0) {
            float flash = Mth.sin(state.ageInTicks * (0.6F + state.fuse * 1.6F)) * 0.5F + 0.5F;
            return ARGB.srgbLerp(Math.max(flash, state.fuse), 0xFF000000 | HOSTILE_COLOR, 0xFF000000 | FUSE_COLOR);
        }
        int base = state.hostile ? HOSTILE_COLOR : CALM_COLOR;
        float pulse = 0.85F + Mth.sin(state.ageInTicks * 0.12F + state.bobPhase) * 0.15F;
        return ARGB.scaleRGB(0xFF000000 | base, state.rising > 0 ? 1 - state.rising * 0.6F : pulse);
    }

    private static final class GlowLayer extends RenderLayer<StoneSentryRenderState, StoneSentryModel> {
        GlowLayer(StoneSentryRenderer renderer) {
            super(renderer);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, StoneSentryRenderState state, float yRot,
                float xRot) {
            if (state.isInvisible) {
                return;
            }
            collector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(GLOW), lightCoords, OverlayTexture.NO_OVERLAY,
                    glowColor(state), null, state.outlineColor);
        }
    }
}
