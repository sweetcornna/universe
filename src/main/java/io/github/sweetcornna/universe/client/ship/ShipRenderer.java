package io.github.sweetcornna.universe.client.ship;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.entity.ship.ShipStructure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** 画组装好的飞船：取出（必要时建好）船身网格，按船的朝向转过去提交。 */
public class ShipRenderer extends EntityRenderer<ShipEntity, ShipRenderer.State> {
    public ShipRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    /** 一帧的渲染状态。 */
    public static class State extends EntityRenderState {
        @Nullable ShipMesh mesh;
        float relativeYaw;
        final List<Extracted> blockEntities = new ArrayList<>();
    }

    /** 船上一个方块实体这一帧的渲染状态，和它的角在船上的局部坐标。 */
    private record Extracted(BlockEntityRenderState state, float x, float y, float z) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ShipEntity ship, State state, float partialTicks) {
        super.extractRenderState(ship, state, partialTicks);
        state.mesh = mesh(ship);
        state.relativeYaw = ship.relativeYaw(partialTicks);
        state.blockEntities.clear();
        if (state.mesh != null && !state.mesh.blockEntities().isEmpty()) {
            BlockEntityRenderDispatcher dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
            Vec3 camera = this.entityRenderDispatcher.camera != null ? this.entityRenderDispatcher.camera.position() : ship.position();
            for (ShipMesh.Placed placed : state.mesh.blockEntities()) {
                BlockEntityRenderState extracted = extract(dispatcher, placed.entity(), partialTicks, camera);
                if (extracted != null) {
                    // 方块实体在船上，光照跟船走
                    extracted.lightCoords = state.lightCoords;
                    state.blockEntities.add(new Extracted(extracted, placed.x(), placed.y(), placed.z()));
                }
            }
        }
    }

    private static <E extends BlockEntity, S extends BlockEntityRenderState> @Nullable S extract(
            BlockEntityRenderDispatcher dispatcher, E entity, float partialTicks, Vec3 camera) {
        BlockEntityRenderer<E, S> renderer = dispatcher.getRenderer(entity);
        if (renderer == null) {
            return null;
        }
        S state = renderer.createRenderState();
        renderer.extractRenderState(entity, state, partialTicks, camera, null);
        return state;
    }

    private static @Nullable ShipMesh mesh(ShipEntity ship) {
        if (ship.renderCache instanceof ShipMesh mesh) {
            return mesh;
        }
        ShipStructure structure = ship.structure();
        if (structure == null || !(ship.level() instanceof ClientLevel level)) {
            return null;
        }
        ShipMesh mesh = ShipMesh.build(structure, level, BlockPos.containing(ship.position()));
        ship.renderCache = mesh;
        return mesh;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        ShipMesh mesh = state.mesh;
        if (mesh != null) {
            int light = state.lightCoords;
            poseStack.pushPose();
            // 局部坐标按 ShipShape#rotate 转：偏航增大是从上往下看顺时针，绕 +y 转负角
            poseStack.rotate(Axis.YP.rotationDegrees(-state.relativeYaw));
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
            if (!state.blockEntities.isEmpty()) {
                BlockEntityRenderDispatcher dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
                for (Extracted extracted : state.blockEntities) {
                    poseStack.pushPose();
                    poseStack.translate(extracted.x(), extracted.y(), extracted.z());
                    dispatcher.submit(extracted.state(), poseStack, collector, camera);
                    poseStack.popPose();
                }
            }
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, camera);
    }
}
