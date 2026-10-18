package io.github.sweetcornna.universe.client.dyson;

import java.util.List;

import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.dyson.DysonSphere;
import io.github.sweetcornna.universe.core.dyson.SwarmOrbit;
import io.github.sweetcornna.universe.dyson.EmRailEjectorBlockEntity;
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
 * 电磁轨道弹射器：叉架转方位、炮管转仰角，对着选定轨道上帆要去的方向（恒星方向按轨道倾角偏开一些，所以不同
 * 轨道的弹射器指向不同，正午也不会突然掉头）。射出一张帆时炮管后坐一下，帆拖着一道光飞出去。
 */
public class EjectorRenderer implements BlockEntityRenderer<EmRailEjectorBlockEntity, EjectorRenderer.State> {
    public static final StandaloneModelKey<BlockStateModelPart> YOKE = new StandaloneModelKey<>(() -> "universe:em_rail_ejector_yoke");
    public static final StandaloneModelKey<BlockStateModelPart> BARREL = new StandaloneModelKey<>(() -> "universe:em_rail_ejector_barrel");
    public static final StandaloneModelKey<BlockStateModelPart> SAIL = new StandaloneModelKey<>(() -> "universe:solar_sail_flight");
    private static final float PIVOT_Y = 10F / 16;
    /** 炮口离转轴多远（方块）。 */
    private static final float MUZZLE = 1.0F;
    /** 帆飞出去以后画多少刻。 */
    private static final int FLIGHT = 26;
    private static final float MIN_PITCH = 12;
    private static final float MAX_PITCH = 80;

    public EjectorRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void registerModels(ModelEvent.RegisterStandalone event) {
        event.register(YOKE, SimpleUnbakedStandaloneModel.simpleModelWrapper(Universe.id("block/em_rail_ejector_yoke")));
        event.register(BARREL, SimpleUnbakedStandaloneModel.simpleModelWrapper(Universe.id("block/em_rail_ejector_barrel")));
        event.register(SAIL, SimpleUnbakedStandaloneModel.simpleModelWrapper(Universe.id("block/solar_sail_flight")));
    }

    /** 帆飞出去 age 刻后离炮口多远：先快后更快。 */
    static float flightDistance(float age) {
        return 0.8F * age + 0.05F * age * age;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(EmRailEjectorBlockEntity blockEntity, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }
        Vec3 aim = aim(level, blockEntity);
        state.yaw = (float) Math.toDegrees(Math.atan2(aim.x, aim.z));
        double pitch = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, aim.y))));
        state.pitch = (float) Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch));
        state.age = blockEntity.lastShot() == Long.MIN_VALUE ? Float.MAX_VALUE : level.getGameTime() - blockEntity.lastShot() + partialTicks;
        state.lightCoords = LightCoordsUtil.getLightCoords(level, blockEntity.getBlockPos().above());
    }

    /** 炮管该指的方向。 */
    private static Vec3 aim(Level level, EmRailEjectorBlockEntity blockEntity) {
        Vec3 sun = ClientSun.direction(level, blockEntity.getBlockPos());
        if (ClientSun.space(level)) {
            return sun;
        }
        double inclination = 0;
        if (ClientDyson.currentSystem().isPresent()) {
            DysonSphere sphere = ClientDyson.sphere(ClientDyson.currentSystem().getAsInt()).orElse(null);
            SwarmOrbit orbit = sphere == null ? null : sphere.orbit(blockEntity.orbit());
            inclination = orbit == null ? 0 : orbit.inclination();
        }
        // 黄道面的法线是世界的 z 轴：轨道倾角越大，帆去的方向越偏离恒星
        double offset = 0.3 + 0.6 * Math.sin(Math.toRadians(inclination));
        if (Math.abs(offset) < 0.15) {
            offset = Math.copySign(0.15, offset);
        }
        return new Vec3(sun.x, sun.y, sun.z + offset).normalize();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        BlockStateModelPart yoke = Minecraft.getInstance().getModelManager().getStandaloneModel(YOKE);
        BlockStateModelPart barrel = Minecraft.getInstance().getModelManager().getStandaloneModel(BARREL);
        if (yoke == null || barrel == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5F, PIVOT_Y, 0.5F);
        poseStack.rotateDegrees(Axis.YP, state.yaw);
        poseStack.translate(-0.5F, -PIVOT_Y, -0.5F);
        collector.submitBlockModel(poseStack, RenderTypes.cutoutMovingBlock(), List.of(yoke), new int[0], state.lightCoords,
                OverlayTexture.NO_OVERLAY, 0);
        poseStack.translate(0.5F, PIVOT_Y, 0.5F);
        poseStack.rotateDegrees(Axis.XP, -state.pitch);
        // 后坐：射出的一瞬间退回 2 像素，6 刻内复位
        float recoil = state.age < 6 ? 2F / 16 * (1 - state.age / 6) : 0;
        poseStack.translate(0, 0, -recoil);
        poseStack.pushPose();
        poseStack.translate(-0.5F, -PIVOT_Y, -0.5F);
        collector.submitBlockModel(poseStack, RenderTypes.cutoutMovingBlock(), List.of(barrel), new int[0], state.lightCoords,
                OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
        if (state.age < FLIGHT) {
            this.submitSail(state, poseStack, collector);
        }
        poseStack.popPose();
    }

    /** 飞出去的帆和身后的光迹，坐标系已经在转轴上、+Z 沿炮管。 */
    private void submitSail(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        BlockStateModelPart sail = Minecraft.getInstance().getModelManager().getStandaloneModel(SAIL);
        float distance = MUZZLE + flightDistance(state.age);
        float fade = 1 - state.age / FLIGHT;
        // 光迹：两片交叉的细长条，炮口那头细、帆那头粗
        float tail = Math.max(MUZZLE, distance - 6 - state.age * 0.6F);
        int alpha = (int) (200 * fade);
        collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
            Matrix4fc matrix = pose.pose();
            streak(buffer, matrix, tail, distance, 0.015F, 0.07F, alpha, true);
            streak(buffer, matrix, tail, distance, 0.015F, 0.07F, alpha, false);
        });
        if (sail == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0, 0, distance);
        poseStack.rotateDegrees(Axis.ZP, state.age * 40);
        float scale = 0.45F;
        poseStack.scale(scale, scale, scale);
        poseStack.rotateDegrees(Axis.XP, 90);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        collector.submitBlockModel(poseStack, RenderTypes.cutoutMovingBlock(), List.of(sail), new int[0], LightCoordsUtil.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    /** 沿 z 轴从 z0 到 z1 的一条渐宽光带，横在 x 方向（horizontal）或 y 方向。 */
    private static void streak(VertexConsumer buffer, Matrix4fc matrix, float z0, float z1, float w0, float w1, int alpha, boolean horizontal) {
        float ax = horizontal ? w0 : 0;
        float ay = horizontal ? 0 : w0;
        float bx = horizontal ? w1 : 0;
        float by = horizontal ? 0 : w1;
        buffer.addVertex(matrix, -ax, -ay, z0).setColor(255, 214, 110, 0);
        buffer.addVertex(matrix, ax, ay, z0).setColor(255, 214, 110, 0);
        buffer.addVertex(matrix, bx, by, z1).setColor(255, 236, 170, alpha);
        buffer.addVertex(matrix, -bx, -by, z1).setColor(255, 236, 170, alpha);
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(EmRailEjectorBlockEntity blockEntity) {
        // 帆飞出去最远五十多格；平时只有炮管伸出方块
        Level level = blockEntity.getLevel();
        if (level != null && blockEntity.lastShot() != Long.MIN_VALUE && level.getGameTime() - blockEntity.lastShot() < FLIGHT + 2) {
            return new AABB(blockEntity.getBlockPos()).inflate(64);
        }
        return new AABB(blockEntity.getBlockPos()).inflate(1, 0, 1).expandTowards(0, 1, 0);
    }

    public static class State extends BlockEntityRenderState {
        float yaw;
        float pitch = MIN_PITCH;
        float age = Float.MAX_VALUE;
    }
}
