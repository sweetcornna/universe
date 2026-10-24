package io.github.sweetcornna.universe.client.beacon;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.beacon.HoloBeaconBlockEntity;
import io.github.sweetcornna.universe.core.beacon.BeaconStyle;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 全息信标（ADR-0033 §六）：投影台上方转着一个图标，再上面一行字，从发光盘往上一道细光柱，都是选的颜色。图标和字自己发光，夜里也看得清。
 */
public class HoloBeaconRenderer implements BlockEntityRenderer<HoloBeaconBlockEntity, HoloBeaconRenderer.State> {
    private static final float EMITTER_Y = 6 / 16F;
    private static final float ICON_Y = 1.25F;
    private static final float ICON_SCALE = 0.55F;
    private static final int BEAM_HEIGHT = 320;
    /** 离得比这远就不写字（和原版的名牌一样）。 */
    private static final double LABEL_DISTANCE = 48;

    private final ItemModelResolver itemModelResolver;

    public HoloBeaconRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(HoloBeaconBlockEntity blockEntity, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        Level level = blockEntity.getLevel();
        long time = level == null ? 0 : level.getGameTime();
        state.animationTime = Math.floorMod(time, 40) + partialTicks;
        state.spin = (Math.floorMod(time, 360) + partialTicks) * 2;
        state.bob = (float) Math.sin((Math.floorMod(time, 1000) + partialTicks) * 0.06) * 0.06F;
        state.color = BeaconStyle.color(blockEntity.color());
        state.icon.clear();
        Item icon = blockEntity.iconItem();
        if (icon != null) {
            this.itemModelResolver.updateForTopItem(state.icon, new ItemStack(icon), ItemDisplayContext.FIXED, level, null,
                    (int) blockEntity.getBlockPos().asLong());
        }
        boolean near = Vec3.atCenterOf(blockEntity.getBlockPos()).distanceTo(cameraPosition) < LABEL_DISTANCE;
        state.label = near ? BeaconClient.name(blockEntity.label(), blockEntity.icon()).copy()
                .withStyle(style -> style.withColor(TextColor.fromRgb(state.color))) : null;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0, EMITTER_Y, 0);
        BeaconRenderer.submitBeaconBeam(poseStack, collector, BeaconRenderer.BEAM_LOCATION, 1.0F, state.animationTime, 0, BEAM_HEIGHT,
                0xFF000000 | state.color, 0.03F, 0.08F);
        poseStack.popPose();
        if (!state.icon.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0.5F, ICON_Y + state.bob, 0.5F);
            poseStack.rotateDegrees(Axis.YP, state.spin);
            poseStack.scale(ICON_SCALE, ICON_SCALE, ICON_SCALE);
            state.icon.submit(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
        if (state.label != null) {
            collector.submitNameTag(poseStack, new Vec3(0.5, state.icon.isEmpty() ? 1.2 : 1.85, 0.5), 0, state.label, true,
                    LightCoordsUtil.FULL_BRIGHT, camera);
        }
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(HoloBeaconBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).expandTowards(0, BEAM_HEIGHT, 0);
    }

    public static class State extends BlockEntityRenderState {
        final ItemStackRenderState icon = new ItemStackRenderState();
        float animationTime;
        float spin;
        float bob;
        int color;
        @Nullable Component label;
    }
}
