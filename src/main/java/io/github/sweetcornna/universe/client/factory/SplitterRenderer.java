package io.github.sweetcornna.universe.client.factory;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.factory.belt.SplitterBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/** 分流器（ADR-0022）：优先出口那一边亮一个黄色的箭头，过滤物品画成中间转盘上的小图标。 */
public class SplitterRenderer implements BlockEntityRenderer<SplitterBlockEntity, SplitterRenderer.State> {
    private static final StandaloneModelKey<BlockStateModelPart> ARROW = new StandaloneModelKey<>(() -> "universe:splitter_priority");
    private static final float FILTER_Y = 8.05F / 16;
    private static final float FILTER_SCALE = 0.3F;

    private final ItemModelResolver itemModelResolver;

    public SplitterRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    public static void registerModels(ModelEvent.RegisterStandalone event) {
        event.register(ARROW, SimpleUnbakedStandaloneModel.simpleModelWrapper(Universe.id("block/splitter_priority")));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SplitterBlockEntity blockEntity, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.priority = blockEntity.priority();
        state.filter.clear();
        Item filter = blockEntity.filter();
        if (filter != null) {
            this.itemModelResolver.updateForTopItem(state.filter, new ItemStack(filter), ItemDisplayContext.FIXED, blockEntity.getLevel(), null,
                    (int) blockEntity.getBlockPos().asLong());
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.priority != null) {
            BlockStateModelPart arrow = Minecraft.getInstance().getModelManager().getStandaloneModel(ARROW);
            if (arrow != null) {
                poseStack.pushPose();
                // 模型里的箭头在北边，转到优先出口那一边
                poseStack.translate(0.5F, 0, 0.5F);
                poseStack.rotateDegrees(Axis.YP, -state.priority.toYRot() + 180);
                poseStack.translate(-0.5F, 0, -0.5F);
                collector.submitBlockModel(poseStack, RenderTypes.cutoutMovingBlock(), List.of(arrow), new int[0], state.lightCoords,
                        OverlayTexture.NO_OVERLAY, 0);
                poseStack.popPose();
            }
        }
        if (!state.filter.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0.5F, FILTER_Y, 0.5F);
            poseStack.rotateDegrees(Axis.XP, 90);
            poseStack.scale(FILTER_SCALE, FILTER_SCALE, FILTER_SCALE);
            state.filter.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    public static class State extends BlockEntityRenderState {
        @Nullable Direction priority;
        final ItemStackRenderState filter = new ItemStackRenderState();
    }
}
