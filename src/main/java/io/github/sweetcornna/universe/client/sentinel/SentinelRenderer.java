package io.github.sweetcornna.universe.client.sentinel;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.sentinel.SentinelKind;
import io.github.sweetcornna.universe.sentinel.Sentinel;
import io.github.sweetcornna.universe.sentinel.SummonerSentinel;
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
 * 守卫机的渲染器：普通、重型、维修、召唤四种飞的守卫机各一个模型，提交前按种类换。面罩、推进器的发光层按状态着色：
 * 巡逻时黄色，扫描玩家时黄色闪烁，敌对时红色。维修无人机的修理头常亮绿光，召唤无人机的环常亮紫光（聚光时闪）。
 * 地面单位有自己的渲染器；没登记之前按重型守卫机画。
 */
public class SentinelRenderer extends MobRenderer<Sentinel, SentinelRenderState, SentinelModel> {
    public static final int PATROL_COLOR = 0xFFD34A;
    public static final int HOSTILE_COLOR = 0xFF3B30;
    private final Map<SentinelKind, Look> looks = new EnumMap<>(SentinelKind.class);

    /** 一种守卫机的模型、贴图、发光层和阴影。 */
    private record Look(SentinelModel model, Identifier texture, Identifier glow, @Nullable Identifier accent, float shadow) {
    }

    public SentinelRenderer(EntityRendererProvider.Context context) {
        super(context, SentinelModel.drone(), 0.4F);
        Look drone = new Look(this.model, texture("sentinel"), texture("sentinel_glow"), null, 0.4F);
        Look heavy = new Look(SentinelModel.heavy(), texture("heavy_sentinel"), texture("heavy_sentinel_glow"), null, 0.8F);
        this.looks.put(SentinelKind.DRONE, drone);
        this.looks.put(SentinelKind.HEAVY, heavy);
        this.looks.put(SentinelKind.REPAIR, new Look(SentinelModel.repair(), texture("repair_sentinel"), texture("repair_sentinel_glow"),
                texture("repair_sentinel_accent"), 0.35F));
        this.looks.put(SentinelKind.SUMMONER, new Look(SentinelModel.summoner(), texture("summoner_sentinel"),
                texture("summoner_sentinel_glow"), texture("summoner_sentinel_accent"), 0.6F));
        this.looks.put(SentinelKind.QUADRUPED, heavy);
        this.looks.put(SentinelKind.WALKER, heavy);
        this.addLayer(new GlowLayer(this));
    }

    private static Identifier texture(String name) {
        return Universe.id("textures/entity/" + name + ".png");
    }

    private Look look(SentinelRenderState state) {
        return this.looks.get(state.kind);
    }

    @Override
    public SentinelRenderState createRenderState() {
        return new SentinelRenderState();
    }

    @Override
    public void extractRenderState(Sentinel entity, SentinelRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.kind = entity.kind();
        state.heavy = entity.isHeavy();
        state.hostile = entity.isHostile();
        state.scanning = entity.isScanning();
        state.charging = entity instanceof SummonerSentinel summoner && summoner.charging();
        state.bobPhase = (entity.getId() * 37 % 100) / 100F * Mth.TWO_PI;
    }

    @Override
    public void submit(SentinelRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        this.model = this.look(state).model();
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    public Identifier getTextureLocation(SentinelRenderState state) {
        return this.look(state).texture();
    }

    @Override
    protected float getShadowRadius(SentinelRenderState state) {
        return this.look(state).shadow();
    }

    @Override
    protected int getBlockLightLevel(Sentinel entity, BlockPos pos) {
        return Math.max(7, super.getBlockLightLevel(entity, pos));
    }

    private static final class GlowLayer extends RenderLayer<SentinelRenderState, SentinelModel> {
        private final SentinelRenderer renderer;

        GlowLayer(SentinelRenderer renderer) {
            super(renderer);
            this.renderer = renderer;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, SentinelRenderState state, float yRot, float xRot) {
            if (state.isInvisible) {
                return;
            }
            Look look = this.renderer.look(state);
            int color = state.hostile ? HOSTILE_COLOR : PATROL_COLOR;
            if (state.scanning && !state.hostile && Mth.sin(state.ageInTicks * 0.8F) > 0) {
                color = 0xFFF2A8;
            }
            collector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(look.glow()),
                    lightCoords, OverlayTexture.NO_OVERLAY, 0xFF000000 | color, null, state.outlineColor);
            if (look.accent() != null) {
                // 修理头、召唤环自带颜色，不按状态着色；聚光时一明一暗
                int accent = state.charging && Mth.sin(state.ageInTicks * 1.2F) < 0 ? 0xFF9A9A9A : 0xFFFFFFFF;
                collector.order(2).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(look.accent()),
                        lightCoords, OverlayTexture.NO_OVERLAY, accent, null, state.outlineColor);
            }
        }
    }
}
