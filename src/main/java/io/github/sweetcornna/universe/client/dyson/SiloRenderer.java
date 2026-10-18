package io.github.sweetcornna.universe.client.dyson;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.dyson.LaunchSiloBlockEntity;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/**
 * 垂直发射井：井里有火箭时，一枚小型运载火箭立在井口，鼻锥露出井壁；发射时它拖着尾焰匀加速升空（高度见
 * {@link LaunchSiloBlockEntity#rise}），之后下一枚从井里升上来就位。
 */
public class SiloRenderer implements BlockEntityRenderer<LaunchSiloBlockEntity, SiloRenderer.State> {
    public static final StandaloneModelKey<BlockStateModelPart> ROCKET = new StandaloneModelKey<>(() -> "universe:small_carrier_rocket_model");
    public static final StandaloneModelKey<BlockStateModelPart> FLAME = new StandaloneModelKey<>(() -> "universe:rocket_flame");
    /** 火箭画多大（模型高 20 像素）。 */
    private static final float SCALE = 1.5F;
    /** 下一枚升上来就位要多少刻。 */
    private static final int RELOAD_TICKS = 40;

    public SiloRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void registerModels(ModelEvent.RegisterStandalone event) {
        event.register(ROCKET, SimpleUnbakedStandaloneModel.simpleModelWrapper(Universe.id("block/small_carrier_rocket_model")));
        event.register(FLAME, SimpleUnbakedStandaloneModel.simpleModelWrapper(Universe.id("block/rocket_flame")));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(LaunchSiloBlockEntity blockEntity, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }
        state.age = blockEntity.lastLaunch() == Long.MIN_VALUE ? Float.MAX_VALUE : level.getGameTime() - blockEntity.lastLaunch() + partialTicks;
        state.loaded = blockEntity.loaded();
        state.lightCoords = LightCoordsUtil.getLightCoords(level, blockEntity.getBlockPos().above(2));
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        BlockStateModelPart rocket = Minecraft.getInstance().getModelManager().getStandaloneModel(ROCKET);
        if (rocket == null) {
            return;
        }
        // 升空的那一枚
        if (state.age >= 0 && state.age < LaunchSiloBlockEntity.ASCENT_TICKS) {
            float base = 1 + (float) LaunchSiloBlockEntity.rise(state.age);
            poseStack.pushPose();
            poseStack.translate(0.5F, base, 0.5F);
            poseStack.rotateDegrees(Axis.YP, state.age * 1.5F);
            poseStack.scale(SCALE, SCALE, SCALE);
            poseStack.translate(-0.5F, 0, -0.5F);
            collector.submitBlockModel(poseStack, RenderTypes.cutoutMovingBlock(), List.of(rocket), new int[0], LightCoordsUtil.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, 0);
            BlockStateModelPart flame = Minecraft.getInstance().getModelManager().getStandaloneModel(FLAME);
            if (flame != null) {
                // 尾焰越飞越长，一闪一闪
                float length = 1.0F + 0.8F * Math.min(1, state.age / 20) + 0.15F * (float) Math.sin(state.age * 2.3);
                poseStack.translate(0.5F, 0, 0.5F);
                poseStack.scale(1, length, 1);
                poseStack.translate(-0.5F, 0, -0.5F);
                collector.submitBlockModel(poseStack, RenderTypes.translucentMovingBlock(), List.of(flame), new int[0], LightCoordsUtil.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, 0);
            }
            poseStack.popPose();
        }
        // 立在井口的下一枚：发射后从井里升上来
        if (state.loaded) {
            float reload = state.age < LaunchSiloBlockEntity.ASCENT_TICKS ? 0
                    : Math.min(1, (state.age - LaunchSiloBlockEntity.ASCENT_TICKS) / RELOAD_TICKS);
            if (reload > 0) {
                float eased = reload * reload * (3 - 2 * reload);
                poseStack.pushPose();
                poseStack.translate(0.5F, 1 - (1 - eased) * 1.6F, 0.5F);
                poseStack.scale(SCALE, SCALE, SCALE);
                poseStack.translate(-0.5F, 0, -0.5F);
                collector.submitBlockModel(poseStack, RenderTypes.cutoutMovingBlock(), List.of(rocket), new int[0], state.lightCoords,
                        OverlayTexture.NO_OVERLAY, 0);
                poseStack.popPose();
            }
        }
    }

    @Override
    public int getViewDistance() {
        return 160;
    }

    @Override
    public AABB getRenderBoundingBox(LaunchSiloBlockEntity blockEntity) {
        Level level = blockEntity.getLevel();
        if (level != null && blockEntity.lastLaunch() != Long.MIN_VALUE
                && level.getGameTime() - blockEntity.lastLaunch() < LaunchSiloBlockEntity.ASCENT_TICKS + 2) {
            return new AABB(blockEntity.getBlockPos()).inflate(1, 0, 1).expandTowards(0, 130, 0);
        }
        return new AABB(blockEntity.getBlockPos()).expandTowards(0, 3, 0);
    }

    public static class State extends BlockEntityRenderState {
        float age = Float.MAX_VALUE;
        boolean loaded;
    }
}
