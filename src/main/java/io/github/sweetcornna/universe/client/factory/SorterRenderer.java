package io.github.sweetcornna.universe.client.factory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.factory.SorterTier;
import io.github.sweetcornna.universe.factory.belt.SorterBlock;
import io.github.sweetcornna.universe.factory.belt.SorterBlockEntity;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/**
 * 分拣器的手臂（ADR-0011）：停着时伸在背后那格上方，抓到东西后转半圈到前面放下，再转回来。抓着的东西挂在爪子
 * 下面，过滤物品画成立柱顶上的一个小图标。
 */
public class SorterRenderer implements BlockEntityRenderer<SorterBlockEntity, SorterRenderer.State> {
    /** 三档的手臂，条纹颜色不同。 */
    private static final Map<SorterTier, StandaloneModelKey<BlockStateModelPart>> ARMS = new EnumMap<>(SorterTier.class);

    static {
        for (SorterTier tier : SorterTier.values()) {
            String name = "sorter_arm" + tierSuffix(tier);
            ARMS.put(tier, new StandaloneModelKey<>(() -> "universe:" + name));
        }
    }
    /** 爪子下面挂东西的位置（手臂朝前时，以方块中心算；爪子在模型里的 z 是 -4，离中心 12 像素）。 */
    private static final float GRIP_Y = 5.5F / 16;
    private static final float GRIP_Z = -12 / 16F;
    private static final float HELD_SCALE = 0.3F;
    private static final float FILTER_Y = 13.05F / 16;
    private static final float FILTER_SCALE = 0.22F;

    private final ItemModelResolver itemModelResolver;

    public SorterRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    public static void registerModels(ModelEvent.RegisterStandalone event) {
        ARMS.forEach((tier, key) -> event.register(key, SimpleUnbakedStandaloneModel.simpleModelWrapper(
                Universe.id("block/sorter_arm" + tierSuffix(tier)))));
    }

    /** 模型、贴图名的后缀：Mk.I 没有后缀，之后是 {@code _mk2}、{@code _mk3}。 */
    public static String tierSuffix(SorterTier tier) {
        return tier == SorterTier.MK1 ? "" : "_" + tier.name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SorterBlockEntity blockEntity, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.facing = blockEntity.getBlockState().getValue(SorterBlock.FACING);
        state.tier = blockEntity.tier();
        state.angle = armAngle(blockEntity.displayProgress(partialTicks), state.tier.halfCycle());
        int seed = (int) blockEntity.getBlockPos().asLong();
        state.held.clear();
        if (!blockEntity.held().isEmpty()) {
            this.itemModelResolver.updateForTopItem(state.held, blockEntity.held(), ItemDisplayContext.FIXED, blockEntity.getLevel(), null, seed);
        }
        state.filter.clear();
        Item filter = blockEntity.filter();
        if (filter != null) {
            this.itemModelResolver.updateForTopItem(state.filter, new ItemStack(filter), ItemDisplayContext.FIXED, blockEntity.getLevel(), null, seed + 1);
        }
    }

    /** 手臂相对前方转过的角度：停着时 180°（朝后），放下东西的那一刻 0°（朝前），来回走同一边，两头放缓。 */
    static float armAngle(double progress, double half) {
        if (progress < 0) {
            return 180;
        }
        double t = progress < half ? 1 - progress / half : (progress - half) / half;
        t = Math.clamp(t, 0, 1);
        return (float) (180 * t * t * (3 - 2 * t));
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0, 0.5F);
        poseStack.rotateDegrees(Axis.YP, -state.facing.toYRot() + 180);
        if (!state.filter.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0, FILTER_Y, 0);
            poseStack.rotateDegrees(Axis.XP, 90);
            poseStack.scale(FILTER_SCALE, FILTER_SCALE, FILTER_SCALE);
            state.filter.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
        poseStack.rotateDegrees(Axis.YP, state.angle);
        BlockStateModelPart arm = Minecraft.getInstance().getModelManager().getStandaloneModel(ARMS.get(state.tier));
        if (arm != null) {
            poseStack.pushPose();
            // 模型的原点在方块角上，转轴在 (8, y, 8)
            poseStack.translate(-0.5F, 0, -0.5F);
            collector.submitBlockModel(poseStack, RenderTypes.cutoutMovingBlock(), List.of(arm), new int[0], state.lightCoords,
                    OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
        if (!state.held.isEmpty()) {
            poseStack.translate(0, GRIP_Y, GRIP_Z);
            poseStack.scale(HELD_SCALE, HELD_SCALE, HELD_SCALE);
            state.held.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        }
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(SorterBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(0.5, 0, 0.5);
    }

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        SorterTier tier = SorterTier.MK1;
        float angle = 180;
        final ItemStackRenderState held = new ItemStackRenderState();
        final ItemStackRenderState filter = new ItemStackRenderState();
    }
}
