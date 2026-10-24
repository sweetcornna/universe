package io.github.sweetcornna.universe.client.fossil;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.core.fossil.Bone;
import io.github.sweetcornna.universe.core.fossil.SkeletonPose;
import io.github.sweetcornna.universe.fossil.FossilPedestalBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

/**
 * 化石底座上的那块骨头（ADR-0034 §三）：放在柱顶上，按自己的大小缩进 0.8×0.6×0.8 格以内，斜着摆，每个底座的角度不一样。
 */
public class FossilPedestalRenderer implements BlockEntityRenderer<FossilPedestalBlockEntity, FossilPedestalRenderer.State> {
    /** 柱顶的高度（像素）。 */
    private static final float TOP = 12;
    private static final float SIZE = 0.8F;
    private static final float HEIGHT = 0.6F;
    private static final Map<Bone, SkeletonPose> POSES = new HashMap<>();

    public static class State extends BlockEntityRenderState {
        @Nullable SkeletonPose pose;
        float yaw;
    }

    public FossilPedestalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FossilPedestalBlockEntity pedestal, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(pedestal, state, partialTicks, cameraPosition, breakProgress);
        Bone bone = pedestal.bone();
        state.pose = bone == null ? null : POSES.computeIfAbsent(bone, SkeletonPose::single);
        long hash = pedestal.getBlockPos().asLong() * 0x9E3779B97F4A7C15L;
        state.yaw = 20 + (hash >>> 58) * 2.5F;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.pose == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5F, TOP / 16F, 0.5F);
        poseStack.rotateDegrees(Axis.YP, state.yaw);
        BoneModels.submitFitted(state.pose, SIZE, HEIGHT, SIZE, 1.2F, poseStack, collector, state.lightCoords);
        poseStack.popPose();
    }
}
