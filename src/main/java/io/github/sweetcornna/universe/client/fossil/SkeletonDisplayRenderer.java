package io.github.sweetcornna.universe.client.fossil;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.core.fossil.Skeleton;
import io.github.sweetcornna.universe.core.fossil.SkeletonPose;
import io.github.sweetcornna.universe.fossil.FossilNames;
import io.github.sweetcornna.universe.fossil.SkeletonDisplayBlock;
import io.github.sweetcornna.universe.fossil.SkeletonDisplayBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 骨架展台上的骨架（ADR-0034 §三）：五块骨头按 {@link SkeletonPose} 拼起来，侧对着展台的正面（铭牌那面），头朝看的人的左手边，
 * 像博物馆里那样从侧面看。骨架按完整的大小缩放，放一块、放五块大小不变；大的骨架会伸出展台。五格都放上了，
 * 骨架上方写出它的名字（{@value #NAME_RANGE} 格以内看得见）。
 */
public class SkeletonDisplayRenderer implements BlockEntityRenderer<SkeletonDisplayBlockEntity, SkeletonDisplayRenderer.State> {
    /** 骨架占的范围（格）：横着最长 1.9，竖着 1.7，前后 1.1。 */
    static final float LENGTH = 1.9F;
    static final float HEIGHT = 1.7F;
    static final float WIDTH = 1.1F;
    static final float MAX_SCALE = 1.5F;
    private static final double NAME_RANGE = 12;

    public static class State extends BlockEntityRenderState {
        @Nullable SkeletonPose pose;
        Direction facing = Direction.NORTH;
        @Nullable Component name;
    }

    public SkeletonDisplayRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SkeletonDisplayBlockEntity display, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(display, state, partialTicks, cameraPosition, breakProgress);
        Skeleton skeleton = display.skeleton();
        state.pose = BoneModels.pose(skeleton);
        state.facing = display.getBlockState().hasProperty(SkeletonDisplayBlock.FACING)
                ? display.getBlockState().getValue(SkeletonDisplayBlock.FACING) : Direction.NORTH;
        boolean near = cameraPosition.distanceToSqr(Vec3.atCenterOf(display.getBlockPos())) < NAME_RANGE * NAME_RANGE;
        state.name = skeleton.complete() && near ? FossilNames.skeleton(skeleton) : null;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        SkeletonPose pose = state.pose;
        if (pose == null) {
            return;
        }
        float k = pose.fit(WIDTH * 16, HEIGHT * 16, LENGTH * 16, MAX_SCALE);
        poseStack.pushPose();
        poseStack.translate(0.5F, SkeletonDisplayBlock.TOP / 16F, 0.5F);
        // 头（-z）转到看的人的左手边：朝南放的展台，头朝西
        poseStack.rotateDegrees(Axis.YP, 90 - state.facing.toYRot());
        BoneModels.submitFitted(pose, WIDTH, HEIGHT, LENGTH, MAX_SCALE, poseStack, collector, state.lightCoords);
        poseStack.popPose();
        if (state.name != null) {
            float top = SkeletonDisplayBlock.TOP / 16F + pose.bounds().height() * k / 16F;
            collector.submitNameTag(poseStack, new Vec3(0.5, top - 0.25, 0.5), 0, state.name, false, state.lightCoords, camera);
        }
    }

    @Override
    public AABB getRenderBoundingBox(SkeletonDisplayBlockEntity display) {
        // 大的骨架伸出展台
        return new AABB(display.getBlockPos()).inflate(1, 0, 1).expandTowards(0, 2, 0);
    }
}
