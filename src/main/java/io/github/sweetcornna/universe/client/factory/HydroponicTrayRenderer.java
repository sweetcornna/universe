package io.github.sweetcornna.universe.client.factory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.sweetcornna.universe.block.HydroponicTrayBlockEntity;
import io.github.sweetcornna.universe.block.ResourcePlantBlock;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
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
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** 水培槽上种着的植物：用资源植物方块的模型，缩小一点立在营养液面上，长到一半以后换成长熟的样子。 */
public class HydroponicTrayRenderer implements BlockEntityRenderer<HydroponicTrayBlockEntity, HydroponicTrayRenderer.State> {
    /** 液面高度（槽高十格）。 */
    private static final float SURFACE = 10 / 16.0F;
    private static final float SCALE = 0.8F;
    private static final Map<BlockState, List<BlockStateModelPart>> PARTS = new HashMap<>();
    private static @Nullable BlockStateModelSet modelSet;

    public HydroponicTrayRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(HydroponicTrayBlockEntity blockEntity, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.plant = blockEntity.plant()
                .map(plant -> UniverseBlocks.RESOURCE_PLANTS.get(plant).get().defaultBlockState().setValue(ResourcePlantBlock.RIPE, blockEntity.ripe()))
                .orElse(null);
        Level level = blockEntity.getLevel();
        state.light = level == null ? LightCoordsUtil.FULL_BRIGHT : LightCoordsUtil.getLightCoords(level, blockEntity.getBlockPos().above());
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.plant == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5F, SURFACE, 0.5F);
        poseStack.scale(SCALE, SCALE, SCALE);
        poseStack.translate(-0.5F, 0, -0.5F);
        collector.submitBlockModel(poseStack, RenderTypes.cutoutMovingBlock(), parts(state.plant), new int[0], state.light,
                OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
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
        @Nullable BlockState plant;
        int light;
    }
}
