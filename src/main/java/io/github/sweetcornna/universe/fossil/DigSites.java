package io.github.sweetcornna.universe.fossil;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.fossil.StoneSentryRules;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.sound.FossilSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * 发掘场里吵醒石哨兵（ADR-0034 §三）。挖开一块化石土，水平 {@value StoneSentryRules#HEARING} 格、上下
 * {@value StoneSentryRules#HEARING_HEIGHT} 格以内睡着的石哨兵都躁动一级；挖到第 {@value StoneSentryRules#WAKE_DIGS} 块就醒过来，
 * 冲着挖的人去。和平难度下只躁动、不醒。
 */
public final class DigSites {
    private DigSites() {
    }

    /** 挖开了 {@code dug} 的化石土。返回吵到了几个石哨兵。 */
    public static int disturb(ServerLevel level, BlockPos dug, @Nullable Player player) {
        List<BlockPos> sentries = find(level, dug);
        for (BlockPos pos : sentries) {
            stir(level, pos, level.getBlockState(pos), player);
        }
        return sentries.size();
    }

    /** 一个石哨兵躁动一级；够了就醒。 */
    public static void stir(ServerLevel level, BlockPos pos, BlockState state, @Nullable Player player) {
        if (!state.is(UniverseBlocks.DORMANT_STONE_SENTRY.get())) {
            return;
        }
        int unrest = StoneSentryRules.disturb(state.getValue(DormantSentryBlock.UNREST));
        if (StoneSentryRules.awake(unrest) && !Fossils.peaceful(level)) {
            wake(level, pos, state, player);
            return;
        }
        int capped = Math.min(unrest, StoneSentryRules.WAKE_DIGS - 1);
        if (capped != state.getValue(DormantSentryBlock.UNREST)) {
            level.setBlock(pos, state.setValue(DormantSentryBlock.UNREST, capped), Block.UPDATE_ALL);
        }
        level.playSound(null, pos, FossilSounds.UNREST.get(), SoundSource.HOSTILE, 0.6F + capped * 0.2F, 0.9F + capped * 0.1F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                4 + capped * 4, 0.3, 0.2, 0.3, 0.05);
    }

    /** 石哨兵醒过来：石头换成浮起来的 {@link StoneSentry}，冲着 {@code player} 去。 */
    public static @Nullable StoneSentry wake(ServerLevel level, BlockPos pos, BlockState state, @Nullable Player player) {
        if (level.getBlockState(pos).is(UniverseBlocks.DORMANT_STONE_SENTRY.get())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        StoneSentry sentry = UniverseEntities.STONE_SENTRY.get().create(level, EntitySpawnReason.EVENT);
        if (sentry == null) {
            return null;
        }
        sentry.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
        sentry.awaken(pos, player);
        if (!level.addFreshEntity(sentry)) {
            return null;
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                30, 0.4, 0.4, 0.4, 0.1);
        level.playSound(null, pos, FossilSounds.SENTRY_WAKE.get(), SoundSource.HOSTILE, 1.2F, 1.0F);
        return sentry;
    }

    /** 听得到 {@code center} 的、睡着的石哨兵。先按区块段的调色板跳过没有石哨兵的段。 */
    static List<BlockPos> find(ServerLevel level, BlockPos center) {
        List<BlockPos> found = new ArrayList<>();
        Block block = UniverseBlocks.DORMANT_STONE_SENTRY.get();
        int r = StoneSentryRules.HEARING;
        int h = StoneSentryRules.HEARING_HEIGHT;
        int minY = Math.max(level.getMinY(), center.getY() - h);
        int maxY = Math.min(level.getMaxY(), center.getY() + h);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int cx = SectionPos.blockToSectionCoord(center.getX() - r); cx <= SectionPos.blockToSectionCoord(center.getX() + r); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(center.getZ() - r); cz <= SectionPos.blockToSectionCoord(center.getZ() + r); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (int sy = SectionPos.blockToSectionCoord(minY); sy <= SectionPos.blockToSectionCoord(maxY); sy++) {
                    int index = chunk.getSectionIndexFromSectionY(sy);
                    if (index < 0 || index >= chunk.getSectionsCount()) {
                        continue;
                    }
                    LevelChunkSection section = chunk.getSection(index);
                    if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(block))) {
                        continue;
                    }
                    for (int y = Math.max(minY, sy << 4); y <= Math.min(maxY, (sy << 4) + 15); y++) {
                        for (int x = Math.max(center.getX() - r, cx << 4); x <= Math.min(center.getX() + r, (cx << 4) + 15); x++) {
                            for (int z = Math.max(center.getZ() - r, cz << 4); z <= Math.min(center.getZ() + r, (cz << 4) + 15); z++) {
                                if (section.getBlockState(x & 15, y & 15, z & 15).is(block)) {
                                    found.add(cursor.set(x, y, z).immutable());
                                }
                            }
                        }
                    }
                }
            }
        }
        return found;
    }
}
