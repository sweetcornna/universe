package io.github.sweetcornna.universe.client.factory;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.core.factory.BeltLine;
import io.github.sweetcornna.universe.core.factory.Cargo;
import io.github.sweetcornna.universe.factory.belt.BeltBlock;
import io.github.sweetcornna.universe.factory.belt.BeltBlockEntity;
import io.github.sweetcornna.universe.factory.belt.BeltCurve;
import io.github.sweetcornna.universe.factory.belt.BeltNetwork;
import io.github.sweetcornna.universe.factory.belt.BeltSlope;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 传送带上的物品（ADR-0011）：每格画自己那一段上的物品，平躺在带面上。位置在两刻之间按速度插值，不越过前面的物品
 * 和线路尽头；弯道走四分之一圆弧，坡道斜着走。叠着的货物（ADR-0022）一层层摞起来画。
 */
public class BeltRenderer implements BlockEntityRenderer<BeltBlockEntity, BeltRenderer.State> {
    /** 带面的高度（平的传送带）。 */
    private static final float SURFACE = 3 / 16F;
    private static final float ITEM_SCALE = 0.3F;
    /** 叠着的货物每层高多少（格）。 */
    private static final float LAYER = 0.045F;

    private final ItemModelResolver itemModelResolver;
    private final Int2ObjectMap<ItemStack> stacks = new Int2ObjectOpenHashMap<>();

    public BeltRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(BeltBlockEntity blockEntity, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.items.clear();
        Level level = blockEntity.getLevel();
        BeltNetwork network = level == null ? null : BeltNetwork.existing(level);
        BeltNetwork.Segment segment = network == null ? null : network.segment(blockEntity.getBlockPos());
        BeltLine line = segment == null ? null : segment.line();
        if (line == null) {
            return;
        }
        BlockState blockState = blockEntity.getBlockState();
        state.facing = blockState.getValue(BeltBlock.FACING);
        BeltSlope slope = blockState.getValue(BeltBlock.SLOPE);
        BeltCurve curve = blockState.getValue(BeltBlock.CURVE);
        int from = segment.from();
        int to = from + BeltNetwork.UNITS;
        float step = line.speed() * partialTicks;
        float limit = line.length();
        int seed = (int) blockEntity.getBlockPos().asLong();
        for (int i = 0; i < line.count(); i++) {
            int p = line.position(i);
            float shown = Math.min(p + step, limit);
            limit = shown - BeltLine.SPACING;
            if (shown < from) {
                break;
            }
            if (shown > to || shown == to && !segment.last()) {
                continue;
            }
            float t = (shown - from) / BeltNetwork.UNITS;
            Item entry = place(slope, curve, t);
            entry.render = new ItemStackRenderState();
            int cargo = line.item(i);
            ItemStack stack = this.stacks.computeIfAbsent(cargo, BeltNetwork::display);
            entry.layers = Cargo.count(cargo);
            this.itemModelResolver.updateForTopItem(entry.render, stack, ItemDisplayContext.FIXED, level, null, seed + i);
            state.items.add(entry);
        }
    }

    /** 朝北时这一段上 {@code t}（0 是后沿，1 是前沿）处的位置和朝向。 */
    private static Item place(BeltSlope slope, BeltCurve curve, float t) {
        Item item = new Item();
        switch (slope) {
            case UP -> {
                item.x = 0.5F;
                item.y = SURFACE + t;
                item.z = 1 - t;
                item.pitch = 45;
            }
            case DOWN -> {
                item.x = 0.5F;
                item.y = SURFACE + 1 - t;
                item.z = 1 - t;
                item.pitch = -45;
            }
            case FLAT -> {
                item.y = SURFACE;
                double angle = t * Math.PI / 2;
                switch (curve) {
                    case LEFT -> {
                        // 从西边进来，绕西北角拐向北
                        item.x = (float) (0.5 * Math.sin(angle));
                        item.z = (float) (0.5 * Math.cos(angle));
                        item.yaw = -t * 90;
                    }
                    case RIGHT -> {
                        item.x = (float) (1 - 0.5 * Math.sin(angle));
                        item.z = (float) (0.5 * Math.cos(angle));
                        item.yaw = t * 90;
                    }
                    case NONE -> {
                        item.x = 0.5F;
                        item.z = 1 - t;
                    }
                }
            }
        }
        return item;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        for (Item item : state.items) {
            if (item.render == null || item.render.isEmpty()) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(0.5F, 0, 0.5F);
            poseStack.rotateDegrees(Axis.YP, -state.facing.toYRot() + 180);
            poseStack.translate(item.x - 0.5F, item.y + 0.01F, item.z - 0.5F);
            poseStack.rotateDegrees(Axis.YP, item.yaw);
            for (int layer = 0; layer < item.layers; layer++) {
                poseStack.pushPose();
                // 一层层往上摞，稍微错开一点角度，看得出是几个
                poseStack.translate(0, layer * LAYER, 0);
                poseStack.rotateDegrees(Axis.YP, layer * 17);
                // 转 90° 平躺；坡道再顺着坡面立起来（上坡前高后低）
                poseStack.rotateDegrees(Axis.XP, 90 + item.pitch);
                poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
                item.render.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
                poseStack.popPose();
            }
            poseStack.popPose();
        }
    }

    @Override
    public AABB getRenderBoundingBox(BeltBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).expandTowards(0, 1, 0);
    }

    /** 一个要画的物品。 */
    static final class Item {
        float x;
        float y;
        float z;
        float yaw;
        float pitch;
        int layers = 1;
        @Nullable ItemStackRenderState render;
    }

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        final List<Item> items = new ArrayList<>();
    }
}
