package io.github.sweetcornna.universe.client.sentinel;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.sentinel.GroundSentinel;
import io.github.sweetcornna.universe.sentinel.QuadrupedSentinel;
import io.github.sweetcornna.universe.sentinel.WalkerSentinel;
import net.minecraft.client.model.EntityModel;
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
 * 地面守卫机（四足机、步行机）的渲染器。面罩、炮口、背后散热口的发光层和普通守卫机一样着色：巡逻时黄色，敌对时红色；
 * 四足机蓄力时发光层越来越亮、最后闪白。
 */
public class GroundSentinelRenderer<T extends GroundSentinel, M extends EntityModel<GroundSentinelRenderState>>
        extends MobRenderer<T, GroundSentinelRenderState, M> {
    private final Identifier texture;
    private final float shadow;

    private GroundSentinelRenderer(EntityRendererProvider.Context context, M model, String name, float shadow) {
        super(context, model, shadow);
        this.texture = Universe.id("textures/entity/" + name + ".png");
        this.shadow = shadow;
        this.addLayer(new GlowLayer<>(this, Universe.id("textures/entity/" + name + "_glow.png")));
    }

    public static GroundSentinelRenderer<QuadrupedSentinel, QuadrupedSentinelModel> quadruped(EntityRendererProvider.Context context) {
        return new GroundSentinelRenderer<>(context, new QuadrupedSentinelModel(), "quadruped_sentinel", 0.8F);
    }

    public static GroundSentinelRenderer<WalkerSentinel, WalkerSentinelModel> walker(EntityRendererProvider.Context context) {
        return new GroundSentinelRenderer<>(context, new WalkerSentinelModel(), "walker_sentinel", 1.3F);
    }

    @Override
    public GroundSentinelRenderState createRenderState() {
        return new GroundSentinelRenderState();
    }

    @Override
    public void extractRenderState(T entity, GroundSentinelRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.hostile = entity.isHostile();
        state.charge = 0;
        state.pouncing = false;
        state.stomp = 0;
        if (entity instanceof QuadrupedSentinel quad) {
            int charge = quad.charge();
            state.charge = charge > 0 ? Mth.clamp(1 - (charge - partialTicks) / QuadrupedSentinel.CHARGE_TICKS, 0, 1) : 0;
            state.pouncing = quad.isPouncing();
        } else if (entity instanceof WalkerSentinel walker) {
            int stomp = walker.stomp();
            state.stomp = stomp > 0 ? Mth.clamp(1 - (stomp - partialTicks) / WalkerSentinel.STOMP_WINDUP, 0, 1) : 0;
        }
    }

    @Override
    public Identifier getTextureLocation(GroundSentinelRenderState state) {
        return this.texture;
    }

    @Override
    protected float getShadowRadius(GroundSentinelRenderState state) {
        return this.shadow;
    }

    @Override
    protected int getBlockLightLevel(T entity, BlockPos pos) {
        return Math.max(7, super.getBlockLightLevel(entity, pos));
    }

    private static final class GlowLayer<M extends EntityModel<GroundSentinelRenderState>> extends RenderLayer<GroundSentinelRenderState, M> {
        private final Identifier glow;

        GlowLayer(GroundSentinelRenderer<?, M> renderer, Identifier glow) {
            super(renderer);
            this.glow = glow;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, GroundSentinelRenderState state, float yRot,
                float xRot) {
            if (state.isInvisible) {
                return;
            }
            int color = state.hostile ? SentinelRenderer.HOSTILE_COLOR : SentinelRenderer.PATROL_COLOR;
            if (state.charge > 0) {
                // 蓄力：越来越亮，最后一小段闪白
                float flash = state.charge > 0.75F && Mth.sin(state.ageInTicks * 2.2F) > 0 ? 1 : state.charge * 0.6F;
                color = mix(color, 0xFFFFFF, flash);
            } else if (state.stomp > 0 && Mth.sin(state.ageInTicks * 1.4F) > 0) {
                color = mix(color, 0xFFE0C0, 0.6F);
            }
            collector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(this.glow), lightCoords,
                    OverlayTexture.NO_OVERLAY, 0xFF000000 | color, null, state.outlineColor);
        }

        private static int mix(int a, int b, float t) {
            int r = (int) Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF);
            int g = (int) Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF);
            int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
            return r << 16 | g << 8 | bl;
        }
    }
}
