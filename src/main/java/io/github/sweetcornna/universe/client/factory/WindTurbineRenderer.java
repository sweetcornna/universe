package io.github.sweetcornna.universe.client.factory;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.factory.power.WindTurbineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/**
 * 风力涡轮机的风轮：机舱前面的轮毂加三片叶片，转速跟着风力走。方块模型的元素只能转 22.5° 的倍数，所以叶片是一片的
 * 模型，画三次各转 120°。
 */
public class WindTurbineRenderer implements BlockEntityRenderer<WindTurbineBlockEntity, WindTurbineRenderer.State> {
    public static final StandaloneModelKey<BlockStateModelPart> HUB = new StandaloneModelKey<>(() -> "universe:wind_turbine_hub");
    public static final StandaloneModelKey<BlockStateModelPart> BLADE = new StandaloneModelKey<>(() -> "universe:wind_turbine_blade");
    /** 风力系数为 1 时每刻转多少度（每秒半圈多一点）。 */
    private static final float DEGREES_PER_TICK = 10;
    /** 轮毂中心：上半截里、机舱前端（朝北时），以下半截的方块原点算。 */
    private static final float HUB_Y = 1 + 12 / 16F;
    private static final float HUB_Z = -6 / 16F;

    public WindTurbineRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void registerModels(ModelEvent.RegisterStandalone event) {
        event.register(HUB, SimpleUnbakedStandaloneModel.simpleModelWrapper(Universe.id("block/wind_turbine_hub")));
        event.register(BLADE, SimpleUnbakedStandaloneModel.simpleModelWrapper(Universe.id("block/wind_turbine_blade")));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(WindTurbineBlockEntity blockEntity, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.facing = blockEntity.getBlockState().getValue(MachineBlock.FACING);
        state.angle = blockEntity.rotorAngle(partialTicks, DEGREES_PER_TICK);
        if (blockEntity.getLevel() != null) {
            state.lightCoords = LightCoordsUtil.getLightCoords(blockEntity.getLevel(), blockEntity.getBlockPos().above());
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockStateModelPart hub = minecraft.getModelManager().getStandaloneModel(HUB);
        BlockStateModelPart blade = minecraft.getModelManager().getStandaloneModel(BLADE);
        if (hub == null || blade == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5F, 0, 0.5F);
        poseStack.rotateDegrees(Axis.YP, -state.facing.toYRot() + 180);
        poseStack.translate(0, HUB_Y, HUB_Z);
        poseStack.rotateDegrees(Axis.ZP, state.angle);
        for (int i = 0; i < 3; i++) {
            poseStack.pushPose();
            poseStack.rotateDegrees(Axis.ZP, i * 120);
            // 模型的原点在方块角上，轮毂中心在 (8, 8, 8)
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            collector.submitBlockModel(poseStack, RenderTypes.cutoutMovingBlock(), List.of(blade), new int[0], state.lightCoords,
                    OverlayTexture.NO_OVERLAY, 0);
            if (i == 0) {
                collector.submitBlockModel(poseStack, RenderTypes.cutoutMovingBlock(), List.of(hub), new int[0], state.lightCoords,
                        OverlayTexture.NO_OVERLAY, 0);
            }
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(WindTurbineBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(1.5, 0, 1.5).expandTowards(0, 3, 0);
    }

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        float angle;
    }
}
