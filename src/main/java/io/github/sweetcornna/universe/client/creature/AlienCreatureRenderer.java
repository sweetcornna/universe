package io.github.sweetcornna.universe.client.creature;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.sweetcornna.universe.core.ecology.CreatureAnatomy;
import io.github.sweetcornna.universe.core.ecology.Fauna;
import io.github.sweetcornna.universe.entity.AlienCreature;
import io.github.sweetcornna.universe.entity.AlienFlyer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * 异星动物的渲染器。每个物种的模型不同，所以在提交之前把 {@link #model} 换成这个物种的；
 * 模型在真正绘制时才按状态摆姿势，同一物种的多只动物共用一个模型也没问题。
 */
public class AlienCreatureRenderer extends MobRenderer<AlienCreature, AlienCreatureRenderState, AlienCreatureModel> {
    public AlienCreatureRenderer(EntityRendererProvider.Context context) {
        // 先放一个占位的模型，提交时再换成各自物种的
        super(context, AlienCreatureModel.create(Fauna.FALLBACK.get(0), CreatureAnatomy.of(Fauna.FALLBACK.get(0))), 0.5F);
        this.addLayer(new GlowLayer(this));
    }

    @Override
    public AlienCreatureRenderState createRenderState() {
        return new AlienCreatureRenderState();
    }

    @Override
    public void extractRenderState(AlienCreature entity, AlienCreatureRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.species = entity.species();
        state.flying = entity instanceof AlienFlyer flyer && flyer.isFlying();
        state.attackAnim = entity.getSwingAnimation(partialTicks);
        state.tint = entity.traits().tint();
    }

    /** 个体的色差、卵序列仪染的颜色乘在贴图上；发光层不染。 */
    @Override
    protected int getModelTint(AlienCreatureRenderState state) {
        return 0xFF000000 | state.tint;
    }

    @Override
    public void submit(AlienCreatureRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.species != null) {
            this.model = CreatureAssets.get(state.species).model();
        }
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    public Identifier getTextureLocation(AlienCreatureRenderState state) {
        return CreatureAssets.get(state.species != null ? state.species : Fauna.FALLBACK.get(0)).skin();
    }

    @Override
    protected float getShadowRadius(AlienCreatureRenderState state) {
        if (state.species == null) {
            return super.getShadowRadius(state);
        }
        return CreatureAssets.get(state.species).hitWidth() * 0.55F * state.scale * state.ageScale;
    }

    /** 眼睛、荧光花纹：不受光照影响的发光层（水生动物也用）。 */
    static final class GlowLayer extends RenderLayer<AlienCreatureRenderState, AlienCreatureModel> {
        GlowLayer(RenderLayerParent<AlienCreatureRenderState, AlienCreatureModel> renderer) {
            super(renderer);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AlienCreatureRenderState state,
                float yRot, float xRot) {
            if (state.species == null || state.isInvisible) {
                return;
            }
            Identifier glow = CreatureAssets.get(state.species).glow();
            if (glow != null) {
                collector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(glow), lightCoords,
                        OverlayTexture.NO_OVERLAY, state.outlineColor);
            }
        }
    }
}
