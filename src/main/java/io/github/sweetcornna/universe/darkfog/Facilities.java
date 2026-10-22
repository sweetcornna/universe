package io.github.sweetcornna.universe.darkfog;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.darkfog.FacilityHealth;
import io.github.sweetcornna.universe.darkfog.facility.FacilityRepairs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * 玩家设施的耐久（ADR-0028 §四）：只有黑雾会打设施。耐久只记掉过血的方块（{@link FacilityData}）；
 * 打掉了拆方块、东西掉地上、原地留残影（{@link FacilityRepairs#destroy}）。两格高的设备按下半截算。
 */
public final class Facilities {
    /** 黑雾会打的设施。炮塔、信号塔、干扰塔、战场分析基地由各自的数据生成加进来。 */
    public static final TagKey<Block> TAG = TagKey.create(Registries.BLOCK, Universe.id("dark_fog_targets"));

    private Facilities() {
    }

    /** 这个方块算不算设施（模组的工厂方块：配方设备、发电、电网、传送带、分拣器、物流站、炮塔、战场分析基地）。 */
    public static boolean isFacility(BlockState state) {
        return state.is(TAG);
    }

    /** 设施的基准位置：两格高的设备取下半截（方块实体在那里）。 */
    public static BlockPos origin(BlockState state, BlockPos pos) {
        return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
                ? pos.below() : pos;
    }

    /** 耐久上限。 */
    public static float maxHealth(BlockState state) {
        return FacilityHealth.max(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
    }

    static String key(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    /** 现在的耐久；没掉过血就是上限，不是设施是 0。 */
    public static float health(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!isFacility(state)) {
            return 0;
        }
        BlockPos origin = origin(state, pos);
        BlockState base = level.getBlockState(origin);
        float max = maxHealth(base);
        return FacilityData.get(level).health(origin.asLong(), key(base)).orElse(max);
    }

    /**
     * 黑雾打设施：扣耐久，打掉了拆方块、留残影。
     *
     * @return 这一下打没打掉
     */
    public static boolean damage(ServerLevel level, BlockPos pos, float amount, @Nullable Entity attacker) {
        if (amount <= 0 || !level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!isFacility(state)) {
            return false;
        }
        BlockPos origin = origin(state, pos);
        BlockState base = level.getBlockState(origin);
        if (!isFacility(base)) {
            return false;
        }
        FacilityData data = FacilityData.get(level);
        String key = key(base);
        float max = maxHealth(base);
        float health = FacilityHealth.afterDamage(data.health(origin.asLong(), key).orElse(max), amount);
        if (health <= 0) {
            data.clearHealth(origin.asLong());
            FacilityRepairs.destroy(level, origin, base);
            return true;
        }
        data.setHealth(origin.asLong(), key, health);
        level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 2, 0.25, 0.2, 0.25, 0.01);
        FacilityRepairs.healthChanged(level, origin, FacilityHealth.ratio(health, max));
        return false;
    }

    /**
     * 修一个设施。
     *
     * @return 修了多少（已经满了或不是设施时是 0）
     */
    public static float repair(ServerLevel level, BlockPos origin, float amount) {
        BlockState base = level.getBlockState(origin);
        if (!isFacility(base) || amount <= 0) {
            return 0;
        }
        FacilityData data = FacilityData.get(level);
        String key = key(base);
        Optional<Float> current = data.health(origin.asLong(), key);
        if (current.isEmpty()) {
            data.clearHealth(origin.asLong());
            return 0;
        }
        float max = maxHealth(base);
        float health = FacilityHealth.afterRepair(current.get(), max, amount);
        if (FacilityHealth.full(health, max)) {
            data.clearHealth(origin.asLong());
            FacilityRepairs.healthChanged(level, origin, 1);
        } else {
            data.setHealth(origin.asLong(), key, health);
            FacilityRepairs.healthChanged(level, origin, FacilityHealth.ratio(health, max));
        }
        return health - current.get();
    }

    /** {@code center} 附近 {@code radius} 格内最近的设施，只看已加载的区块。按区块里的方块实体找（设施都有方块实体）。 */
    public static Optional<BlockPos> nearest(ServerLevel level, Vec3 center, double radius) {
        int minX = Mth.floor(center.x - radius) >> 4;
        int maxX = Mth.floor(center.x + radius) >> 4;
        int minZ = Mth.floor(center.z - radius) >> 4;
        int maxZ = Mth.floor(center.z + radius) >> 4;
        double best = radius * radius;
        BlockPos found = null;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity.isRemoved() || !isFacility(blockEntity.getBlockState())) {
                        continue;
                    }
                    BlockPos pos = blockEntity.getBlockPos();
                    double distance = center.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (distance <= best) {
                        best = distance;
                        found = pos;
                    }
                }
            }
        }
        return Optional.ofNullable(found);
    }
}
