package io.github.sweetcornna.universe.microbe;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.block.ResourcePlantBlock;
import io.github.sweetcornna.universe.core.microbe.EngineeredPlant;
import io.github.sweetcornna.universe.core.microbe.Gene;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * 种在地上的改造种苗（ADR-0039）：方块还是普通的资源植物，编辑进去的基因记在区块上（{@link UniverseAttachments#ENGINEERED_PLANTS}），
 * 拆下来时掉回同样的种苗，不会变回野生的。带荧光素酶的方块亮着（{@link ResourcePlantBlock#GLOWING}）。
 */
public final class PlantedSeedlings {
    private PlantedSeedlings() {
    }

    /** 种下以后记下来。 */
    public static void record(ServerLevel level, BlockPos pos, EngineeredPlant plant) {
        LevelChunk chunk = level.getChunkAt(pos);
        Map<Long, EngineeredPlant> plants = chunk.getExistingDataOrNull(UniverseAttachments.ENGINEERED_PLANTS.get());
        if (plants == null) {
            plants = new HashMap<>();
            chunk.setData(UniverseAttachments.ENGINEERED_PLANTS.get(), plants);
        }
        plants.put(pos.asLong(), plant);
        chunk.markUnsaved();
    }

    /** 这个位置上种的改造种苗；没种过、或者记的那株和现在的方块对不上（被命令换掉过）时为空。 */
    public static @Nullable EngineeredPlant at(Level level, BlockPos pos) {
        Map<Long, EngineeredPlant> plants = level.getChunkAt(pos).getExistingDataOrNull(UniverseAttachments.ENGINEERED_PLANTS.get());
        if (plants == null) {
            return null;
        }
        EngineeredPlant plant = plants.get(pos.asLong());
        return plant != null && matches(level.getBlockState(pos), plant) ? plant : null;
    }

    /** 方块没了：拿掉记录，返回原来种的那株（方块换成了别的东西时由调用的地方掉回种苗）。 */
    public static @Nullable EngineeredPlant remove(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        Map<Long, EngineeredPlant> plants = chunk.getExistingDataOrNull(UniverseAttachments.ENGINEERED_PLANTS.get());
        if (plants == null) {
            return null;
        }
        EngineeredPlant plant = plants.remove(pos.asLong());
        if (plant != null) {
            chunk.markUnsaved();
        }
        return plant;
    }

    /** 种下去的样子：带荧光素酶的亮着。 */
    public static BlockState planted(BlockState state, EngineeredPlant plant) {
        return state.setValue(ResourcePlantBlock.GLOWING, plant.genes().contains(Gene.LUCIFERASE));
    }

    private static boolean matches(BlockState state, EngineeredPlant plant) {
        return state.getBlock() instanceof ResourcePlantBlock block && block.plant() == plant.plant();
    }
}
