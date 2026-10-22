package io.github.sweetcornna.universe.client.creature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.core.ecology.BodyPlan;
import io.github.sweetcornna.universe.core.ecology.CreatureAnatomy;
import io.github.sweetcornna.universe.core.ecology.CreatureSpecies;
import io.github.sweetcornna.universe.core.ecology.Fauna;
import io.github.sweetcornna.universe.entity.AquaticCreature;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;

/**
 * 水生动物的渲染器（ADR-0026）：和地上的动物一样按物种换模型和皮肤，另外顺着游的方向俯仰；鱼游的时候身子左右晃，
 * 离了水躺倒在地上（和原版的鳕鱼一样）。
 */
public class AquaticCreatureRenderer extends MobRenderer<AquaticCreature, AlienCreatureRenderState, AlienCreatureModel> {
    public AquaticCreatureRenderer(EntityRendererProvider.Context context) {
        super(context, AlienCreatureModel.create(fallback(), CreatureAnatomy.of(fallback())), 0.3F);
        this.addLayer(new AlienCreatureRenderer.GlowLayer(this));
    }

    private static CreatureSpecies fallback() {
        return Fauna.FALLBACK.get(Fauna.MARINE_INDEX);
    }

    @Override
    public AlienCreatureRenderState createRenderState() {
        return new AlienCreatureRenderState();
    }

    @Override
    public void extractRenderState(AquaticCreature entity, AlienCreatureRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.species = entity.species();
        state.attackAnim = entity.getSwingAnimation(partialTicks);
        state.swimPitch = entity.swimPitch(partialTicks);
    }

    @Override
    public void submit(AlienCreatureRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.species != null) {
            this.model = CreatureAssets.get(state.species).model();
        }
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    protected void setupRotations(AlienCreatureRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
        super.setupRotations(state, poseStack, bodyRot, entityScale);
        BodyPlan plan = state.species == null ? BodyPlan.FISH : state.species.plan();
        if (plan == BodyPlan.FISH) {
            poseStack.rotateDegrees(Axis.YP, 4.3F * (float) Math.sin(0.6F * state.ageInTicks));
        }
        if (!state.isInWater && plan != BodyPlan.JELLY) {
            poseStack.translate(0.1F, 0.1F, -0.1F);
            poseStack.rotateDegrees(Axis.ZP, 90.0F);
        }
    }

    @Override
    public Identifier getTextureLocation(AlienCreatureRenderState state) {
        return CreatureAssets.get(state.species != null ? state.species : fallback()).skin();
    }

    @Override
    protected float getShadowRadius(AlienCreatureRenderState state) {
        if (state.species == null) {
            return super.getShadowRadius(state);
        }
        return CreatureAssets.get(state.species).hitWidth() * 0.4F * state.scale;
    }
}
