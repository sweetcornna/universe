package io.github.sweetcornna.universe.client.facility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;

import io.github.sweetcornna.universe.darkfog.facility.FacilityGhostBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/** 残影（ADR-0028 §四）：按原来的方块画成半透明的蓝色，慢慢明暗起伏。两格高的设备上下半截都画。 */
public class FacilityGhostRenderer implements BlockEntityRenderer<FacilityGhostBlockEntity, FacilityGhostRenderer.State> {
    private static final int RGB = 0x5AA8FF;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Map<BlockState, List<BlockStateModelPart>> PARTS = new HashMap<>();
    private static @Nullable BlockStateModelSet modelSet;

    public FacilityGhostRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FacilityGhostBlockEntity blockEntity, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        BlockState original = blockEntity.original();
        state.lower = original.isAir() ? List.of() : parts(original);
        state.upper = blockEntity.tall() ? parts(original.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER)) : List.of();
        float time = (System.currentTimeMillis() % 4000L) / 4000F;
        float alpha = 0.32F + 0.12F * Mth.sin(time * Mth.TWO_PI);
        state.color = (Math.round(alpha * 255) << 24) | RGB;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.lower.isEmpty()) {
            return;
        }
        submitGhost(poseStack, collector, state.lower, state.color);
        if (!state.upper.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0, 1, 0);
            submitGhost(poseStack, collector, state.upper, state.color);
            poseStack.popPose();
        }
    }

    private static void submitGhost(PoseStack poseStack, SubmitNodeCollector collector, List<BlockStateModelPart> parts, int color) {
        collector.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (pose, buffer) -> {
            QuadInstance instance = new QuadInstance();
            instance.setColor(color);
            for (BlockStateModelPart part : parts) {
                for (BakedQuad quad : part.getQuads(null)) {
                    buffer.putBakedQuad(pose, quad, instance);
                }
                for (Direction direction : DIRECTIONS) {
                    for (BakedQuad quad : part.getQuads(direction)) {
                        buffer.putBakedQuad(pose, quad, instance);
                    }
                }
            }
        });
    }

    private static List<BlockStateModelPart> parts(BlockState state) {
        BlockStateModelSet models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        if (models != modelSet) {
            PARTS.clear();
            modelSet = models;
        }
        return PARTS.computeIfAbsent(state, key -> {
            List<BlockStateModelPart> list = new ArrayList<>();
            models.get(key).collectParts(RandomSource.create(42), list);
            return list;
        });
    }

    public static class State extends BlockEntityRenderState {
        List<BlockStateModelPart> lower = List.of();
        List<BlockStateModelPart> upper = List.of();
        int color;
    }
}
