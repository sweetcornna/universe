package io.github.sweetcornna.universe.client.factory;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.client.RenderFrames;
import io.github.sweetcornna.universe.client.SharedItemModels;
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
    /** 这一帧各条线路上每个物品画在哪，同一帧里各段传送带共用（ADR-0038），换一帧（{@link RenderFrames}）或者换了插值就清掉。 */
    private final Map<BeltLine, float[]> shown = new IdentityHashMap<>();
    private long shownFrame = -1;
    private float shownPartial = Float.NaN;
    /** 同一种货物这一帧共用一份模型。 */
    private final SharedItemModels models = new SharedItemModels();

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
        boolean last = segment.last();
        long frame = RenderFrames.current();
        if (frame != this.shownFrame || Float.compare(partialTicks, this.shownPartial) != 0) {
            this.shownFrame = frame;
            this.shownPartial = partialTicks;
            this.shown.clear();
        }
        float[] shown = this.shown.computeIfAbsent(line, key -> shown(key, partialTicks));
        int seed = (int) blockEntity.getBlockPos().asLong();
        // 物品由近到远排，画的位置逐个变小：跳过还没到这一段的，画到离开这一段为止
        for (int i = skipAhead(shown, to, last); i < shown.length && shown[i] >= from; i++) {
            float t = (shown[i] - from) / BeltNetwork.UNITS;
            Item entry = place(slope, curve, t);
            int cargo = line.item(i);
            entry.layers = Cargo.count(cargo);
            // 画出来的样子只看物品和增产等级，叠了几个、点数零头不同的共用一份
            int look = Cargo.uniform(Cargo.item(cargo), 1, Cargo.level(cargo));
            ItemStack stack = this.stacks.computeIfAbsent(look, BeltNetwork::display);
            entry.render = this.models.get(look, stack, this.itemModelResolver, level, seed + i);
            state.items.add(entry);
        }
    }

    /**
     * 线路上每个物品这一帧画在离起点多远：位置按速度往前插，不越过线路尽头，和前一个至少隔 {@link BeltLine#SPACING}。
     * 结果逐个变小。
     */
    private static float[] shown(BeltLine line, float partialTicks) {
        float[] shown = new float[line.count()];
        float step = line.speed() * partialTicks;
        float limit = line.length();
        for (int i = 0; i < shown.length; i++) {
            shown[i] = Math.min(line.position(i) + step, limit);
            limit = shown[i] - BeltLine.SPACING;
        }
        return shown;
    }

    /** 第一个不在这一段前面的物品：画在 {@code to} 以前，正好在 {@code to} 上的只有线路的最后一段画。 */
    private static int skipAhead(float[] shown, int to, boolean last) {
        int lo = 0;
        int hi = shown.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (shown[mid] > to || shown[mid] == to && !last) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
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
