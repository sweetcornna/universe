package io.github.sweetcornna.universe.client.sentinel;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.sentinel.Sentinel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * 守卫机的渲染器：普通和重型两种模型，提交前按状态换。面罩、推进器的发光层按状态着色：巡逻时黄色，
 * 扫描玩家时黄色闪烁，敌对时红色。
 */
public class SentinelRenderer extends MobRenderer<Sentinel, SentinelRenderState, SentinelModel> {
    private static final Identifier DRONE = Universe.id("textures/entity/sentinel.png");
    private static final Identifier DRONE_GLOW = Universe.id("textures/entity/sentinel_glow.png");
    private static final Identifier HEAVY = Universe.id("textures/entity/heavy_sentinel.png");
    private static final Identifier HEAVY_GLOW = Universe.id("textures/entity/heavy_sentinel_glow.png");
    public static final int PATROL_COLOR = 0xFFD34A;
    public static final int HOSTILE_COLOR = 0xFF3B30;
    private final SentinelModel drone;
    private final SentinelModel heavy;

    public SentinelRenderer(EntityRendererProvider.Context context) {
        super(context, SentinelModel.drone(), 0.4F);
        this.drone = this.model;
        this.heavy = SentinelModel.heavy();
        this.addLayer(new GlowLayer(this));
    }

    @Override
    public SentinelRenderState createRenderState() {
        return new SentinelRenderState();
    }

    @Override
    public void extractRenderState(Sentinel entity, SentinelRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.heavy = entity.isHeavy();
        state.hostile = entity.isHostile();
        state.scanning = entity.isScanning();
        state.bobPhase = (entity.getId() * 37 % 100) / 100F * Mth.TWO_PI;
    }

    @Override
    public void submit(SentinelRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        this.model = state.heavy ? this.heavy : this.drone;
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    public Identifier getTextureLocation(SentinelRenderState state) {
        return state.heavy ? HEAVY : DRONE;
    }

    @Override
    protected float getShadowRadius(SentinelRenderState state) {
        return state.heavy ? 0.8F : 0.4F;
    }

    @Override
    protected int getBlockLightLevel(Sentinel entity, BlockPos pos) {
        return Math.max(7, super.getBlockLightLevel(entity, pos));
    }

    private static final class GlowLayer extends RenderLayer<SentinelRenderState, SentinelModel> {
        GlowLayer(SentinelRenderer renderer) {
            super(renderer);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, SentinelRenderState state, float yRot, float xRot) {
            if (state.isInvisible) {
                return;
            }
            int color = state.hostile ? HOSTILE_COLOR : PATROL_COLOR;
            if (state.scanning && !state.hostile && Mth.sin(state.ageInTicks * 0.8F) > 0) {
                color = 0xFFF2A8;
            }
            collector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(state.heavy ? HEAVY_GLOW : DRONE_GLOW),
                    lightCoords, OverlayTexture.NO_OVERLAY, 0xFF000000 | color, null, state.outlineColor);
        }
    }
}
