package io.github.sweetcornna.universe.client.ship;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.sweetcornna.universe.entity.ship.ShipStructure;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * 一艘船的网格：组装时用原版的方块渲染器把每个方块的面拼好（船身内部相邻的面剔掉，环境光遮蔽照算），
 * 存下面片和顶点颜色；每帧只换上当前的光照再提交。船身不会变，只在第一次画的时候建一次。
 */
final class ShipMesh {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 一个面片：所在方块的局部坐标（方块的角）和四个顶点的颜色（含染色和环境光遮蔽）。 */
    private record Quad(BakedQuad quad, float x, float y, float z, int c0, int c1, int c2, int c3) {
    }

    /** 要用方块实体渲染器画的方块（箱子、告示牌、旗帜……）和它的角在船上的局部坐标。 */
    record Placed(BlockEntity entity, float x, float y, float z) {
    }

    private final List<Quad> solid = new ArrayList<>();
    private final List<Quad> cutout = new ArrayList<>();
    private final List<Quad> translucent = new ArrayList<>();
    private final List<Placed> blockEntities = new ArrayList<>();
    private final QuadInstance instance = new QuadInstance();

    static ShipMesh build(ShipStructure structure, ClientLevel level, BlockPos tintAt) {
        ShipMesh mesh = new ShipMesh();
        Minecraft minecraft = Minecraft.getInstance();
        boolean cutoutLeaves = minecraft.options.cutoutLeaves().get();
        BlockStateModelSet models = minecraft.getModelManager().getBlockStateModelSet();
        ModelBlockRenderer renderer = new ModelBlockRenderer(minecraft.options.ambientOcclusion().get(), true, minecraft.getBlockColors());
        ShipBlockGetter getter = new ShipBlockGetter(structure, level, tintAt);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (ShipStructure.ShipBlock block : structure.blocks()) {
            BlockState state = block.state();
            if (state.getRenderShape() != RenderShape.MODEL) {
                continue;
            }
            pos.set(block.x(), block.y(), block.z());
            float bx = block.x() - 0.5F;
            float by = block.y();
            float bz = block.z() - 0.5F;
            boolean opaque = ModelBlockRenderer.forceOpaque(cutoutLeaves, state);
            BlockQuadOutput output = (x, y, z, quad, instance) -> {
                ChunkSectionLayer layer = opaque ? ChunkSectionLayer.SOLID : quad.materialInfo().layer();
                Quad stored = new Quad(quad, bx + x, by + y, bz + z,
                        instance.getColor(0), instance.getColor(1), instance.getColor(2), instance.getColor(3));
                switch (layer) {
                    case SOLID -> mesh.solid.add(stored);
                    case CUTOUT -> mesh.cutout.add(stored);
                    case TRANSLUCENT -> mesh.translucent.add(stored);
                }
            };
            renderer.tesselateBlock(output, 0, 0, 0, getter, pos.immutable(), state, models.get(state), state.getSeed(pos));
        }
        BlockEntityRenderDispatcher dispatcher = minecraft.getBlockEntityRenderDispatcher();
        for (ShipStructure.ShipBlock block : structure.blocks()) {
            if (block.blockEntity() == null || !(block.state().getBlock() instanceof EntityBlock entityBlock)) {
                continue;
            }
            BlockEntity entity = entityBlock.newBlockEntity(new BlockPos(block.x(), block.y(), block.z()), block.state());
            if (entity == null) {
                continue;
            }
            // 有些渲染器要从世界里取时间、相邻方块；位置是船上的局部坐标，取到的光照画的时候换成船的
            entity.setLevel(level);
            if (!block.blockEntity().isEmpty()) {
                try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
                    entity.loadWithComponents(TagValueInput.create(reporter, level.registryAccess(), block.blockEntity()));
                }
            }
            if (dispatcher.getRenderer(entity) != null) {
                mesh.blockEntities.add(new Placed(entity, block.x() - 0.5F, block.y(), block.z() - 0.5F));
            }
        }
        return mesh;
    }

    List<Placed> blockEntities() {
        return this.blockEntities;
    }

    boolean hasSolid() {
        return !this.solid.isEmpty();
    }

    boolean hasCutout() {
        return !this.cutout.isEmpty();
    }

    boolean hasTranslucent() {
        return !this.translucent.isEmpty();
    }

    void emitSolid(PoseStack.Pose pose, VertexConsumer buffer, int light) {
        this.emit(this.solid, pose, buffer, light);
    }

    void emitCutout(PoseStack.Pose pose, VertexConsumer buffer, int light) {
        this.emit(this.cutout, pose, buffer, light);
    }

    void emitTranslucent(PoseStack.Pose pose, VertexConsumer buffer, int light) {
        this.emit(this.translucent, pose, buffer, light);
    }

    private void emit(List<Quad> quads, PoseStack.Pose pose, VertexConsumer buffer, int light) {
        PoseStack.Pose local = pose.copy();
        QuadInstance instance = this.instance;
        instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
        for (Quad quad : quads) {
            local.set(pose);
            local.translate(quad.x(), quad.y(), quad.z());
            // 自发光的面（屏幕、喷口）不受环境光影响
            instance.setLightCoords(LightCoordsUtil.lightCoordsWithEmission(light, quad.quad().materialInfo().lightEmission()));
            instance.setColor(0, quad.c0());
            instance.setColor(1, quad.c1());
            instance.setColor(2, quad.c2());
            instance.setColor(3, quad.c3());
            buffer.putBakedQuad(local, quad.quad(), instance);
        }
    }

    /** 只有船身方块的"世界"：剔面和环境光遮蔽看得到船上的邻居，草和树叶按船所在的群系染色。 */
    private static final class ShipBlockGetter implements BlockAndTintGetter {
        private final Long2ObjectOpenHashMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();
        private final ClientLevel level;
        private final BlockPos tintAt;

        ShipBlockGetter(ShipStructure structure, ClientLevel level, BlockPos tintAt) {
            for (ShipStructure.ShipBlock block : structure.blocks()) {
                this.blocks.put(BlockPos.asLong(block.x(), block.y(), block.z()), block.state());
            }
            this.level = level;
            this.tintAt = tintAt;
        }

        @Override
        public CardinalLighting cardinalLighting() {
            return this.level.cardinalLighting();
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return LevelLightEngine.EMPTY;
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver color) {
            return color.getColor(this.level.getBiome(this.tintAt).value(), this.tintAt.getX(), this.tintAt.getZ());
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockState state = this.blocks.get(pos.asLong());
            return state == null ? Blocks.AIR.defaultBlockState() : state;
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return this.getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return 4096;
        }

        @Override
        public int getMinY() {
            return -2048;
        }
    }
}
