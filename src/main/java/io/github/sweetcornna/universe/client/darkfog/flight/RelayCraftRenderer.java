package io.github.sweetcornna.universe.client.darkfog.flight;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.darkfog.DarkFogBuildingModel;
import io.github.sweetcornna.universe.client.darkfog.DarkFogBuildingRenderState;
import io.github.sweetcornna.universe.core.darkfog.BuildingKind;
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

/**
 * 飞着的中继站（ADR-0028 §八）：和基地上空的那座一个样子（模型、贴图都借地面基地的），一直亮着，臂转得快一些。
 */
public class RelayCraftRenderer extends MobRenderer<DarkFogCraft, DarkFogBuildingRenderState, DarkFogBuildingModel> {
    private static final Identifier TEXTURE = Universe.id("textures/entity/dark_fog_relay.png");
    private static final Identifier GLOW = Universe.id("textures/entity/dark_fog_relay_glow.png");
    private static final int COLOR = 0xC040FF;

    public RelayCraftRenderer(EntityRendererProvider.Context context) {
        super(context, DarkFogBuildingModel.relay(), 0);
        this.addLayer(new GlowLayer(this));
    }

    @Override
    public DarkFogBuildingRenderState createRenderState() {
        return new DarkFogBuildingRenderState();
    }

    @Override
    public void extractRenderState(DarkFogCraft entity, DarkFogBuildingRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.kind = BuildingKind.RELAY;
        state.powered = true;
        state.phase = (entity.getId() * 37 % 100) / 100F * Mth.TWO_PI;
        state.deathTime = 0;
        // 臂转得比落地以后快
        state.ageInTicks *= 3;
    }

    @Override
    protected void scale(DarkFogBuildingRenderState state, PoseStack poseStack) {
        float scale = this.model.scale();
        poseStack.scale(scale, scale, scale);
    }

    @Override
    protected float getFlipDegrees() {
        return 0;
    }

    @Override
    public Identifier getTextureLocation(DarkFogBuildingRenderState state) {
        return TEXTURE;
    }

    @Override
    protected int getBlockLightLevel(DarkFogCraft entity, BlockPos pos) {
        return Math.max(8, super.getBlockLightLevel(entity, pos));
    }

    private static final class GlowLayer extends RenderLayer<DarkFogBuildingRenderState, DarkFogBuildingModel> {
        GlowLayer(RelayCraftRenderer renderer) {
            super(renderer);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, DarkFogBuildingRenderState state, float yRot,
                float xRot) {
            if (state.isInvisible) {
                return;
            }
            float pulse = 0.8F + 0.2F * Mth.sin(state.ageInTicks * 0.05F + state.phase);
            int r = Math.min(255, (int) ((COLOR >> 16 & 0xFF) * pulse));
            int g = Math.min(255, (int) ((COLOR >> 8 & 0xFF) * pulse));
            int b = Math.min(255, (int) ((COLOR & 0xFF) * pulse));
            collector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(GLOW), lightCoords, OverlayTexture.NO_OVERLAY,
                    0xFF000000 | r << 16 | g << 8 | b, null, state.outlineColor);
        }
    }
}
