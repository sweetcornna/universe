package io.github.sweetcornna.universe.client.fossil;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.fossil.SentryRock;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** 石哨兵扔的石块：一块 6×5×6 的石头加一块小的，翻着跟头飞；贴图借石哨兵的（(0, 41) 那一块）。 */
public class SentryRockRenderer extends EntityRenderer<SentryRock, SentryRockRenderer.State> {
    private static final Identifier TEXTURE = Universe.id("textures/entity/stone_sentry.png");
    private final ModelPart rock;

    public static class State extends EntityRenderState {
        float spin;
    }

    public SentryRockRenderer(EntityRendererProvider.Context context) {
        super(context);
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("rock", CubeListBuilder.create()
                        .texOffs(0, 41).addBox(-3, -2.5F, -3, 6, 5, 6)
                        .texOffs(20, 30).addBox(-1, -3.5F, -2, 3, 3, 3),
                PartPose.ZERO);
        this.rock = LayerDefinition.create(mesh, 64, 64).bakeRoot();
        this.shadowRadius = 0.2F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SentryRock entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.spin = (entity.tickCount + partialTicks) * 23 + entity.getId() * 37;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0, 0.2F, 0);
        poseStack.rotateDegrees(Axis.YP, state.spin * 0.6F);
        poseStack.rotateDegrees(Axis.XP, state.spin);
        collector.submitModelPart(this.rock, poseStack, RenderTypes.entityCutout(TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY, null);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
