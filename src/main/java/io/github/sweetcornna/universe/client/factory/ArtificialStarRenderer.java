package io.github.sweetcornna.universe.client.factory;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.factory.power.ArtificialStarBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/**
 * 人造恒星上半截里的小恒星：发电时核心转动，外面两层半透明的日冕一胀一缩；不发电时核心暗着、不动，没有日冕。
 */
public class ArtificialStarRenderer implements BlockEntityRenderer<ArtificialStarBlockEntity, ArtificialStarRenderer.State> {
    public static final StandaloneModelKey<BlockStateModelPart> CORE = new StandaloneModelKey<>(() -> "universe:artificial_star_core");
    public static final StandaloneModelKey<BlockStateModelPart> CORONA = new StandaloneModelKey<>(() -> "universe:artificial_star_corona");
    /** 恒星的中心（以下半截的方块原点算）：上半截的 (8, 7, 8)。 */
    private static final float CENTER_Y = 1 + 7 / 16F;

    public ArtificialStarRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void registerModels(ModelEvent.RegisterStandalone event) {
        event.register(CORE, SimpleUnbakedStandaloneModel.simpleModelWrapper(Universe.id("block/artificial_star_core")));
        event.register(CORONA, SimpleUnbakedStandaloneModel.simpleModelWrapper(Universe.id("block/artificial_star_corona")));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ArtificialStarBlockEntity blockEntity, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.working = blockEntity.getBlockState().getValue(MachineBlock.WORKING);
        float time = blockEntity.getLevel() == null ? 0 : (blockEntity.getLevel().getGameTime() % 72000) + partialTicks;
        state.angle = state.working ? time * 1.5F % 360 : 20;
        state.pulse = state.working ? 1 + (float) Math.sin(time / 9) * 0.08F : 1;
        if (state.working) {
            state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        BlockStateModelPart core = Minecraft.getInstance().getModelManager().getStandaloneModel(CORE);
        BlockStateModelPart corona = Minecraft.getInstance().getModelManager().getStandaloneModel(CORONA);
        if (core == null || corona == null) {
            return;
        }
        // 核心是两个错开 45° 的立方体叠在一起，轮廓接近球形
        this.part(poseStack, collector, core, RenderTypes.cutoutMovingBlock(), state.lightCoords, state.angle, 1, 35);
        this.part(poseStack, collector, core, RenderTypes.cutoutMovingBlock(), state.lightCoords, state.angle + 45, 0.97F, -35);
        if (state.working) {
            // 两层很淡的日冕错开角度、反着转
            this.part(poseStack, collector, corona, RenderTypes.translucentMovingBlock(), state.lightCoords, -state.angle * 0.6F, state.pulse, 20);
            this.part(poseStack, collector, corona, RenderTypes.translucentMovingBlock(), state.lightCoords, state.angle * 0.8F + 45,
                    state.pulse * 1.15F, 55);
        }
    }

    /** 画一个绕 (8, 7, 8) 转的部件；模型的原点在方块角上。 */
    private void part(PoseStack poseStack, SubmitNodeCollector collector, BlockStateModelPart part, RenderType type,
            int light, float yaw, float scale, float tilt) {
        poseStack.pushPose();
        poseStack.translate(0.5F, CENTER_Y, 0.5F);
        poseStack.rotateDegrees(Axis.YP, yaw);
        poseStack.rotateDegrees(Axis.XP, tilt);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-0.5F, -7 / 16F, -0.5F);
        collector.submitBlockModel(poseStack, type, List.of(part), new int[0], light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(ArtificialStarBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).expandTowards(0, 1, 0);
    }

    public static class State extends BlockEntityRenderState {
        boolean working;
        float angle;
        float pulse;
    }
}
