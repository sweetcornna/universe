package io.github.sweetcornna.universe.client.deepspace;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.sweetcornna.universe.client.BlockMesh;
import io.github.sweetcornna.universe.core.deepspace.Layout;
import io.github.sweetcornna.universe.core.deepspace.Part;
import io.github.sweetcornna.universe.deepspace.DebrisChunk;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 画碎片带的碎片（ADR-0034 §四）：按种子拼出的残骸方块（舱板、桁架、散热板、燃料罐、导管）建成网格，
 * 小块的缩成一半；绕按种子定的轴慢慢翻滚，挨了采矿光束抖一下。
 */
public class DebrisChunkRenderer extends EntityRenderer<DebrisChunk, DebrisChunkRenderer.State> {
    public DebrisChunkRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    /** 一帧的渲染状态。 */
    public static class State extends EntityRenderState {
        @Nullable BlockMesh mesh;
        float radius;
        float scale;
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
    public void extractRenderState(DebrisChunk chunk, State state, float partialTicks) {
        super.extractRenderState(chunk, state, partialTicks);
        Layout shape = chunk.shape();
        state.mesh = mesh(chunk);
        state.radius = (float) chunk.radius();
        state.scale = (float) chunk.size().scale();
        double[] center = shape.center();
        for (int i = 0; i < 3; i++) {
            state.center[i] = (float) center[i];
        }
        double[] spin = chunk.spin();
        state.axis[0] = (float) spin[0];
        state.axis[1] = (float) spin[1];
        state.axis[2] = (float) spin[2];
        state.angle = (float) ((chunk.tickCount + partialTicks) * spin[3] + (chunk.seed() & 0xFFFF) * 0.01);
        if (chunk.shake > 0) {
            float amount = 0.05F * (chunk.shake - partialTicks);
            state.shakeX = (chunk.getRandom().nextFloat() - 0.5F) * amount;
            state.shakeY = (chunk.getRandom().nextFloat() - 0.5F) * amount;
            state.shakeZ = (chunk.getRandom().nextFloat() - 0.5F) * amount;
        } else {
            state.shakeX = 0;
            state.shakeY = 0;
            state.shakeZ = 0;
        }
    }

    private static @Nullable BlockMesh mesh(DebrisChunk chunk) {
        if (chunk.renderCache instanceof BlockMesh mesh) {
            return mesh;
        }
        if (!(chunk.level() instanceof ClientLevel level)) {
            return null;
        }
        List<BlockMesh.Block> blocks = new ArrayList<>();
        for (Layout.Cell cell : chunk.shape().cells()) {
            blocks.add(new BlockMesh.Block(cell.x(), cell.y(), cell.z(), block(cell.part())));
        }
        BlockMesh mesh = BlockMesh.of(blocks, level, BlockPos.containing(chunk.position()), 0, 0, 0);
        chunk.renderCache = mesh;
        return mesh;
    }

    /** 碎片上的一格画成哪种残骸方块。 */
    static BlockState block(Part part) {
        return switch (part) {
            case FRAME -> UniverseBlocks.DERELICT_FRAME.get().defaultBlockState();
            case RADIATOR -> UniverseBlocks.DERELICT_RADIATOR.get().defaultBlockState();
            case TANK -> UniverseBlocks.DERELICT_TANK.get().defaultBlockState();
            case CONDUIT -> UniverseBlocks.DERELICT_CONDUIT.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
            case CARGO -> UniverseBlocks.DERELICT_CARGO.get().defaultBlockState();
            case DECK -> UniverseBlocks.DERELICT_DECK.get().defaultBlockState();
            case ENGINE -> UniverseBlocks.DERELICT_ENGINE.get().defaultBlockState();
            case SOLAR -> UniverseBlocks.OUTPOST_SOLAR.get().defaultBlockState();
            default -> UniverseBlocks.DERELICT_PLATING.get().defaultBlockState();
        };
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        BlockMesh mesh = state.mesh;
        if (mesh != null) {
            int light = state.lightCoords;
            poseStack.pushPose();
            poseStack.translate(state.shakeX, state.radius + state.shakeY, state.shakeZ);
            poseStack.rotate(new Quaternionf().rotateAxis((float) Math.toRadians(state.angle), state.axis[0], state.axis[1], state.axis[2]));
            poseStack.scale(state.scale, state.scale, state.scale);
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
