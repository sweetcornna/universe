package io.github.sweetcornna.universe.client.asteroid;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.sweetcornna.universe.asteroid.Asteroid;
import io.github.sweetcornna.universe.asteroid.AsteroidMaterials;
import io.github.sweetcornna.universe.client.BlockMesh;
import io.github.sweetcornna.universe.core.space.AsteroidField;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;

/** 画小行星：按种子长出的方块拼成网格（第一次画的时候建），绕自己的轴慢慢转；刚挨了打时抖一下。 */
public class AsteroidRenderer extends EntityRenderer<Asteroid, AsteroidRenderer.State> {
    public AsteroidRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    /** 一帧的渲染状态。 */
    public static class State extends EntityRenderState {
        @Nullable BlockMesh mesh;
        float radius;
        final float[] center = new float[3];
        final float[] axis = new float[3];
        float angle;
        float shakeX;
        float shakeY;
        float shakeZ;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Asteroid asteroid, State state, float partialTicks) {
        super.extractRenderState(asteroid, state, partialTicks);
        AsteroidField.Shape shape = asteroid.shape();
        state.mesh = mesh(asteroid);
        state.radius = (float) shape.radius();
        for (int i = 0; i < 3; i++) {
            state.center[i] = (float) shape.center()[i];
            state.axis[i] = (float) shape.spinAxis()[i];
        }
        // 自转的相位按种子错开，不然同时生成的一片小行星转得整整齐齐
        state.angle = (float) ((asteroid.tickCount + partialTicks) * shape.spinRate() + (asteroid.seed() & 0xFFFF) * 0.01);
        if (asteroid.shake > 0) {
            float amount = 0.06F * (asteroid.shake - partialTicks);
            state.shakeX = (asteroid.getRandom().nextFloat() - 0.5F) * amount;
            state.shakeY = (asteroid.getRandom().nextFloat() - 0.5F) * amount;
            state.shakeZ = (asteroid.getRandom().nextFloat() - 0.5F) * amount;
        } else {
            state.shakeX = 0;
            state.shakeY = 0;
            state.shakeZ = 0;
        }
    }

    private static @Nullable BlockMesh mesh(Asteroid asteroid) {
        if (asteroid.renderCache instanceof BlockMesh mesh) {
            return mesh;
        }
        if (!(asteroid.level() instanceof ClientLevel level)) {
            return null;
        }
        List<BlockMesh.Block> blocks = new ArrayList<>();
        for (AsteroidField.Voxel voxel : asteroid.shape().voxels()) {
            blocks.add(new BlockMesh.Block(voxel.x(), voxel.y(), voxel.z(), AsteroidMaterials.block(voxel.material())));
        }
        BlockMesh mesh = BlockMesh.of(blocks, level, BlockPos.containing(asteroid.position()), 0, 0, 0);
        asteroid.renderCache = mesh;
        return mesh;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        BlockMesh mesh = state.mesh;
        if (mesh != null) {
            int light = state.lightCoords;
            poseStack.pushPose();
            poseStack.translate(state.shakeX, state.radius + state.shakeY, state.shakeZ);
            poseStack.rotate(new Quaternionf().rotateAxis((float) Math.toRadians(state.angle), state.axis[0], state.axis[1], state.axis[2]));
            poseStack.translate(-state.center[0], -state.center[1], -state.center[2]);
            if (mesh.hasSolid()) {
                collector.submitCustomGeometry(poseStack, RenderTypes.solidMovingBlock(), (pose, buffer) -> mesh.emitSolid(pose, buffer, light));
            }
            if (mesh.hasCutout()) {
                collector.submitCustomGeometry(poseStack, RenderTypes.cutoutMovingBlock(), (pose, buffer) -> mesh.emitCutout(pose, buffer, light));
            }
            if (mesh.hasTranslucent()) {
                collector.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(),
                        (pose, buffer) -> mesh.emitTranslucent(pose, buffer, light));
            }
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, camera);
    }
}
