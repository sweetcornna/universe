package io.github.sweetcornna.universe.client.factory;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.power.Solar;
import io.github.sweetcornna.universe.factory.power.SolarPanelBlockEntity;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/**
 * 太阳能板上的板子：绕南北向的轴转，白天追着太阳从东转到西，夜里放平（{@link Solar#trackingAngle}）。
 * 一片区的板子角度都一样，整齐地一起转。太空里没有昼夜，板子平放。
 */
public class SolarPanelRenderer implements BlockEntityRenderer<SolarPanelBlockEntity, SolarPanelRenderer.State> {
    public static final StandaloneModelKey<BlockStateModelPart> ARRAY = new StandaloneModelKey<>(() -> "universe:solar_panel_array");
    /** 转轴的高度（扭力管的中心）。 */
    private static final float PIVOT_Y = 10.5F / 16;

    public SolarPanelRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void registerModels(ModelEvent.RegisterStandalone event) {
        event.register(ARRAY, SimpleUnbakedStandaloneModel.simpleModelWrapper(Universe.id("block/solar_panel_array")));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SolarPanelBlockEntity blockEntity, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }
        boolean space = SpaceDimensions.system(level.dimension()).isPresent();
        state.tilt = space ? 0 : (float) Solar.trackingAngle(level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, blockEntity.getBlockPos()));
        state.lightCoords = LightCoordsUtil.getLightCoords(level, blockEntity.getBlockPos().above());
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        BlockStateModelPart array = Minecraft.getInstance().getModelManager().getStandaloneModel(ARRAY);
        if (array == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5F, PIVOT_Y, 0.5F);
        poseStack.rotateDegrees(Axis.ZP, state.tilt);
        poseStack.translate(-0.5F, -PIVOT_Y, -0.5F);
        collector.submitBlockModel(poseStack, RenderTypes.cutoutMovingBlock(), List.of(array), new int[0], state.lightCoords,
                OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    public static class State extends BlockEntityRenderState {
        float tilt;
    }
}
