package io.github.sweetcornna.universe.client.factory;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.factory.research.MatrixLabBlockEntity;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/**
 * 矩阵研究站玻璃罩里的能量核心：研究时转起来、上下浮动、发光，停着时暗着不动。
 */
public class MatrixLabRenderer implements BlockEntityRenderer<MatrixLabBlockEntity, MatrixLabRenderer.State> {
    public static final StandaloneModelKey<BlockStateModelPart> CORE = new StandaloneModelKey<>(() -> "universe:matrix_lab_core");
    /** 自我演化研究站的核心：紫绿两色（ADR-0022）。 */
    public static final StandaloneModelKey<BlockStateModelPart> EVOLUTION_CORE = new StandaloneModelKey<>(() -> "universe:self_evolution_lab_core");
    /** 核心的中心（以方块原点算）。 */
    private static final float CENTER_Y = 10 / 16F;

    public MatrixLabRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void registerModels(ModelEvent.RegisterStandalone event) {
        event.register(CORE, SimpleUnbakedStandaloneModel.simpleModelWrapper(Universe.id("block/matrix_lab_core")));
        event.register(EVOLUTION_CORE, SimpleUnbakedStandaloneModel.simpleModelWrapper(Universe.id("block/self_evolution_lab_core")));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MatrixLabBlockEntity blockEntity, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.working = blockEntity.getBlockState().getValue(MachineBlock.WORKING);
        state.evolution = blockEntity.evolution();
        float time = blockEntity.getLevel() == null ? 0 : (blockEntity.getLevel().getGameTime() % 72000) + partialTicks;
        // 每个研究站的相位错开一点，一排研究站不会整齐划一
        float phase = (blockEntity.getBlockPos().hashCode() & 0xFF) * 1.4F;
        state.angle = state.working ? (time * 3 + phase) % 360 : 45;
        state.bob = state.working ? (float) Math.sin((time + phase) / 12) * 0.6F / 16 : 0;
        if (state.working) {
            state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        BlockStateModelPart core = Minecraft.getInstance().getModelManager().getStandaloneModel(state.evolution ? EVOLUTION_CORE : CORE);
        if (core == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5F, CENTER_Y + state.bob, 0.5F);
        poseStack.rotateDegrees(Axis.YP, state.angle);
        if (state.working) {
            poseStack.rotateDegrees(Axis.XP, 35);
            poseStack.rotateDegrees(Axis.ZP, 35);
        }
        // 模型的原点在方块角上，核心的中心在 (8, 10, 8)
        poseStack.translate(-0.5F, -CENTER_Y, -0.5F);
        collector.submitBlockModel(poseStack, RenderTypes.cutoutMovingBlock(), List.of(core), new int[0], state.lightCoords,
                OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    public static class State extends BlockEntityRenderState {
        boolean working;
        boolean evolution;
        float angle;
        float bob;
    }
}
