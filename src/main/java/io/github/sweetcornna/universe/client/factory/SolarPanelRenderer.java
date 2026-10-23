package io.github.sweetcornna.universe.client.factory;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.power.Solar;
import io.github.sweetcornna.universe.core.power.StarFacing;
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
 * 一片区的板子角度都一样，整齐地一起转。太空里没有昼夜，板子先转到恒星的方位、再倾斜到正对恒星（{@link StarFacing}，
 * ADR-0030），离恒星远的一片板子朝向差不多，近的看得出各朝各的。
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
        if (SpaceDimensions.system(level.dimension()).isPresent()) {
            // 恒星在太空维度的原点
            double[] facing = StarFacing.facing(blockEntity.getBlockPos().getX() + 0.5, blockEntity.getBlockPos().getY() + PIVOT_Y,
                    blockEntity.getBlockPos().getZ() + 0.5);
            state.yaw = (float) facing[0];
            state.tilt = (float) facing[1];
        } else {
            state.yaw = 0;
            state.tilt = (float) Solar.trackingAngle(level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, blockEntity.getBlockPos()));
        }
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
        if (state.yaw != 0) {
            poseStack.rotateDegrees(Axis.YP, state.yaw);
        }
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
        float yaw;
        float tilt;
    }
}
