package io.github.sweetcornna.universe.client.factory;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.factory.belt.StorageTankBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 储液罐里的液柱：按流体的颜色给一张灰度贴图着色，高度按满了多少。叠起来的罐子之间液柱连成一根（上下没有顶盖和底板）。
 */
public class StorageTankRenderer implements BlockEntityRenderer<StorageTankBlockEntity, StorageTankRenderer.State> {
    private static final RenderType RENDER_TYPE = RenderTypes.entityTranslucent(Universe.id("textures/entity/storage_tank_fluid.png"));
    /** 液柱在四根立柱里面。 */
    private static final float INSET = 2 / 16F;
    private static final float PLATE = 1 / 16F;

    public StorageTankRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(StorageTankBlockEntity blockEntity, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        Item fluid = blockEntity.fluid();
        state.fill = fluid == null ? 0 : blockEntity.fillRatio();
        state.color = fluid == null ? 0 : color(fluid);
        Level level = blockEntity.getLevel();
        state.tankBelow = level != null && level.getBlockEntity(blockEntity.getBlockPos().below()) instanceof StorageTankBlockEntity;
        state.tankAbove = level != null && level.getBlockEntity(blockEntity.getBlockPos().above()) instanceof StorageTankBlockEntity above
                && above.amount() > 0;
    }

    /** 流体的颜色（不透明度另算）。 */
    static int color(Item fluid) {
        return switch (BuiltInRegistries.ITEM.getKey(fluid).getPath()) {
            case "water" -> 0x3F76E4;
            case "crude_oil" -> 0x2E241E;
            case "refined_oil" -> 0xD58C2C;
            case "hydrogen" -> 0xA9E88C;
            case "deuterium" -> 0x6FD8EA;
            case "sulfuric_acid" -> 0xE3D23E;
            default -> 0x9AA0A6;
        };
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.fill <= 0) {
            return;
        }
        float bottom = state.tankBelow ? 0 : PLATE;
        float limit = state.fill >= 1 && state.tankAbove ? 1 : 1 - PLATE;
        float top = bottom + (limit - bottom) * Math.min(1, state.fill);
        int argb = 0xD8000000 | state.color;
        int light = state.lightCoords;
        boolean drawBottom = !state.tankBelow;
        boolean drawTop = top < 1;
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) ->
                column(pose, buffer, bottom, top, argb, light, drawBottom, drawTop));
    }

    private static void column(PoseStack.Pose pose, VertexConsumer buffer, float y0, float y1, int color, int light,
            boolean drawBottom, boolean drawTop) {
        float a = INSET;
        float b = 1 - INSET;
        // 侧面的贴图按真实高度取，不拉伸
        float v0 = 1 - y1;
        float v1 = 1 - y0;
        // 北、南、西、东
        quad(buffer, pose, light, color, 0, 0, -1, b, y0, a, a, y0, a, a, y1, a, b, y1, a, a, b, v0, v1);
        quad(buffer, pose, light, color, 0, 0, 1, a, y0, b, b, y0, b, b, y1, b, a, y1, b, a, b, v0, v1);
        quad(buffer, pose, light, color, -1, 0, 0, a, y0, a, a, y0, b, a, y1, b, a, y1, a, a, b, v0, v1);
        quad(buffer, pose, light, color, 1, 0, 0, b, y0, b, b, y0, a, b, y1, a, b, y1, b, a, b, v0, v1);
        if (drawTop) {
            quad(buffer, pose, light, color, 0, 1, 0, a, y1, a, a, y1, b, b, y1, b, b, y1, a, a, b, a, b);
        }
        if (drawBottom) {
            quad(buffer, pose, light, color, 0, -1, 0, a, y0, b, a, y0, a, b, y0, a, b, y0, b, a, b, a, b);
        }
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, int light, int color, float nx, float ny, float nz,
            float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
            float u0, float u1, float v0, float v1) {
        vertex(buffer, pose, x0, y0, z0, u0, v1, color, light, nx, ny, nz);
        vertex(buffer, pose, x1, y1, z1, u1, v1, color, light, nx, ny, nz);
        vertex(buffer, pose, x2, y2, z2, u1, v0, color, light, nx, ny, nz);
        vertex(buffer, pose, x3, y3, z3, u0, v0, color, light, nx, ny, nz);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int color, int light,
            float nx, float ny, float nz) {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    public static class State extends BlockEntityRenderState {
        float fill;
        int color;
        boolean tankBelow;
        boolean tankAbove;
    }
}
