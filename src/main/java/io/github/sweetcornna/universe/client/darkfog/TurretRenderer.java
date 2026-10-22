package io.github.sweetcornna.universe.client.darkfog;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.darkfog.TurretKind;
import io.github.sweetcornna.universe.darkfog.turret.TurretBlockEntity;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/**
 * 炮塔的转台和炮管（ADR-0028 §五）：转台只转方位，炮管再转仰角，转轴在 (8, 11, 8)，炮管沿 +Z 伸出去。
 * 开火时炮管后坐、炮口闪一下；激光炮塔从炮口到目标画一道光束。模型由 {@code tools/m16_turret_models.py} 生成。
 */
public class TurretRenderer implements BlockEntityRenderer<TurretBlockEntity, TurretRenderer.State> {
    private static final Map<TurretKind, StandaloneModelKey<BlockStateModelPart>> HEADS = new EnumMap<>(TurretKind.class);
    private static final Map<TurretKind, StandaloneModelKey<BlockStateModelPart>> BARRELS = new EnumMap<>(TurretKind.class);
    private static final float PIVOT_Y = (float) TurretBlockEntity.PIVOT_Y;
    /** 激光亮几刻。 */
    private static final float BEAM = 4;

    static {
        for (TurretKind kind : TurretKind.values()) {
            HEADS.put(kind, new StandaloneModelKey<>(() -> "universe:" + kind.id() + "_head"));
            BARRELS.put(kind, new StandaloneModelKey<>(() -> "universe:" + kind.id() + "_barrel"));
        }
    }

    public TurretRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void registerModels(ModelEvent.RegisterStandalone event) {
        for (TurretKind kind : TurretKind.values()) {
            event.register(HEADS.get(kind), SimpleUnbakedStandaloneModel.simpleModelWrapper(Universe.id("block/" + kind.id() + "_head")));
            event.register(BARRELS.get(kind), SimpleUnbakedStandaloneModel.simpleModelWrapper(Universe.id("block/" + kind.id() + "_barrel")));
        }
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TurretBlockEntity blockEntity, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        Level level = blockEntity.getLevel();
        state.kind = blockEntity.kind();
        state.yaw = blockEntity.yaw(partialTicks);
        state.pitch = blockEntity.pitch(partialTicks);
        state.beam = null;
        if (level == null) {
            return;
        }
        state.age = blockEntity.lastShot() == Long.MIN_VALUE ? Float.MAX_VALUE : level.getGameTime() - blockEntity.lastShot() + partialTicks;
        state.lightCoords = LightCoordsUtil.getLightCoords(level, blockEntity.getBlockPos().above());
        if (state.kind == TurretKind.LASER && state.age < BEAM && blockEntity.clientTarget() >= 0) {
            Entity target = level.getEntity(blockEntity.clientTarget());
            if (target != null) {
                Vec3 aim = target.getBoundingBox().getCenter();
                state.beam = new Vec3(aim.x, aim.y, aim.z).subtract(blockEntity.pivot());
            }
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockStateModelPart head = minecraft.getModelManager().getStandaloneModel(HEADS.get(state.kind));
        BlockStateModelPart barrel = minecraft.getModelManager().getStandaloneModel(BARRELS.get(state.kind));
        if (head == null || barrel == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5F, PIVOT_Y, 0.5F);
        poseStack.rotateDegrees(Axis.YP, state.yaw);
        poseStack.translate(-0.5F, -PIVOT_Y, -0.5F);
        collector.submitBlockModel(poseStack, RenderTypes.cutoutMovingBlock(), List.of(head), new int[0], state.lightCoords,
                OverlayTexture.NO_OVERLAY, 0);
        poseStack.translate(0.5F, PIVOT_Y, 0.5F);
        poseStack.rotateDegrees(Axis.XP, -state.pitch);
        float recoil = state.age < recoilTicks(state.kind) ? recoilDepth(state.kind) * (1 - state.age / recoilTicks(state.kind)) : 0;
        poseStack.translate(0, 0, -recoil);
        poseStack.pushPose();
        poseStack.translate(-0.5F, -PIVOT_Y, -0.5F);
        collector.submitBlockModel(poseStack, RenderTypes.cutoutMovingBlock(), List.of(barrel), new int[0], state.lightCoords,
                OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
        if (state.kind == TurretKind.GAUSS && state.age < 2) {
            float length = (float) TurretBlockEntity.MUZZLE;
            int alpha = (int) (230 * (1 - state.age / 2));
            collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
                Matrix4fc matrix = pose.pose();
                streak(buffer, matrix, length - 0.05F, length + 0.45F, 0.12F, 0.02F, alpha, true, 255, 220, 140);
                streak(buffer, matrix, length - 0.05F, length + 0.45F, 0.12F, 0.02F, alpha, false, 255, 220, 140);
            });
        }
        poseStack.popPose();
        if (state.beam != null) {
            this.submitBeam(state, poseStack, collector);
        }
    }

    /** 激光：从炮口到目标一道细的青光，开火后几刻里慢慢变淡。 */
    private void submitBeam(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        Vec3 beam = state.beam;
        float length = (float) beam.length();
        if (length < 0.5F) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(beam.x, beam.z));
        float pitch = (float) Math.toDegrees(Math.atan2(beam.y, Math.hypot(beam.x, beam.z)));
        int alpha = (int) (220 * (1 - state.age / BEAM));
        float start = (float) TurretBlockEntity.MUZZLE;
        poseStack.pushPose();
        poseStack.translate(0.5F, PIVOT_Y, 0.5F);
        poseStack.rotateDegrees(Axis.YP, yaw);
        poseStack.rotateDegrees(Axis.XP, -pitch);
        collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
            Matrix4fc matrix = pose.pose();
            streak(buffer, matrix, start, length, 0.06F, 0.06F, alpha, true, 120, 240, 255);
            streak(buffer, matrix, start, length, 0.06F, 0.06F, alpha, false, 120, 240, 255);
            streak(buffer, matrix, start, length, 0.025F, 0.025F, Math.min(255, alpha + 30), true, 235, 255, 255);
        });
        poseStack.popPose();
    }

    private static float recoilTicks(TurretKind kind) {
        return switch (kind) {
            case GAUSS -> 3;
            case LASER -> 1;
            case IMPLOSION -> 10;
            case MISSILE -> 6;
        };
    }

    private static float recoilDepth(TurretKind kind) {
        return switch (kind) {
            case GAUSS -> 1.5F / 16;
            case LASER -> 0;
            case IMPLOSION -> 3F / 16;
            case MISSILE -> 1F / 16;
        };
    }

    /** 沿 z 轴从 z0 到 z1 的一条光带，横在 x 方向（horizontal）或 y 方向。 */
    private static void streak(VertexConsumer buffer, Matrix4fc matrix, float z0, float z1, float w0, float w1, int alpha, boolean horizontal,
            int r, int g, int b) {
        float ax = horizontal ? w0 : 0;
        float ay = horizontal ? 0 : w0;
        float bx = horizontal ? w1 : 0;
        float by = horizontal ? 0 : w1;
        buffer.addVertex(matrix, -ax, -ay, z0).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, ax, ay, z0).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, bx, by, z1).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, -bx, -by, z1).setColor(r, g, b, alpha);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public AABB getRenderBoundingBox(TurretBlockEntity blockEntity) {
        if (blockEntity.kind() == TurretKind.LASER) {
            // 激光要画到目标那里
            return new AABB(blockEntity.getBlockPos()).inflate(blockEntity.kind().range() + 2);
        }
        return new AABB(blockEntity.getBlockPos()).inflate(1, 0, 1).expandTowards(0, 1, 0);
    }

    public static class State extends BlockEntityRenderState {
        TurretKind kind = TurretKind.GAUSS;
        float yaw;
        float pitch;
        float age = Float.MAX_VALUE;
        @Nullable Vec3 beam;
    }
}
