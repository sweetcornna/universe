package io.github.sweetcornna.universe.microbe;

import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.hazard.GeyserKind;
import io.github.sweetcornna.universe.core.microbe.Gene;
import io.github.sweetcornna.universe.core.microbe.SampleSource;
import io.github.sweetcornna.universe.core.terraform.EffectiveEnvironment;
import io.github.sweetcornna.universe.hazard.GeyserCrustBlock;
import io.github.sweetcornna.universe.hazard.GeyserVentBlock;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseFluids;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.terraform.Terraforming;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * 拿空采样管采样（ADR-0036 §五）：方块、液体、生物对应哪种来源，采样处多少度，采到的样本装进管里。
 * 能不能长出菌照星球的微生物组（{@link Microbiomes}）：这里一种菌都没有就不采，提示一句。组织样本不看微生物组。
 */
public final class Sampling {
    /** 星球数据里没有温度时（不是本模组的维度）按这个算。 */
    static final double DEFAULT_K = 288;

    private Sampling() {
    }

    /** 这个方块采得到哪种来源；采不了为空。资源植物另算（植物组织）。两端都能调，服务端再查星球。 */
    public static @Nullable SampleSource source(BlockGetter level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof GeyserCrustBlock crust) {
            return crust.kind() == GeyserKind.COLD ? SampleSource.ICE : SampleSource.HOT_SPRING;
        }
        if (block instanceof GeyserVentBlock vent) {
            return vent.kind() == GeyserKind.COLD ? SampleSource.ICE : SampleSource.HOT_SPRING;
        }
        if (state.is(BlockTags.ICE) || state.is(BlockTags.SNOW) || block == UniverseBlocks.PLANET_ICE.get()
                || block == UniverseBlocks.PLANET_SNOW.get() || block == Blocks.SNOW_BLOCK || block == Blocks.SNOW) {
            return SampleSource.ICE;
        }
        if (block == UniverseBlocks.ORES.get(OreKind.SALT).get()) {
            return SampleSource.SALT;
        }
        if (block == UniverseBlocks.ORES.get(OreKind.IRON).get() || block == UniverseBlocks.ORES.get(OreKind.COPPER).get()
                || block == UniverseBlocks.ORES.get(OreKind.SULFUR).get() || state.is(BlockTags.IRON_ORES) || state.is(BlockTags.COPPER_ORES)
                || state.is(BlockTags.GOLD_ORES)) {
            return SampleSource.ORE;
        }
        // 26.x 的 #dirt 只剩泥土、砂土、缠根泥土，草方块、灰化土、菌丝在 #grass_blocks，苔藓块在 #moss_blocks
        boolean soil = state.is(BlockTags.DIRT) || state.is(BlockTags.GRASS_BLOCKS) || state.is(BlockTags.MOSS_BLOCKS)
                || block == UniverseBlocks.PLANET_SOIL.get() || block == UniverseBlocks.PLANET_TURF.get();
        boolean loose = soil || state.is(BlockTags.SAND) || block == Blocks.GRAVEL || block == Blocks.CLAY || block == UniverseBlocks.PLANET_SAND.get()
                || block == UniverseBlocks.PLANET_REGOLITH.get() || block == UniverseBlocks.PLANET_ASH.get();
        // 水底的泥沙是沉积物
        if (state.is(BlockTags.MUD) || block == Blocks.CLAY || loose && level.getFluidState(pos.above()).is(FluidTags.WATER)) {
            return SampleSource.SEDIMENT;
        }
        if (soil) {
            return SampleSource.SOIL;
        }
        // 荒漠的沙、风化层、火山灰里的菌住在颗粒和石缝里，和岩石内部的是一类
        if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.SAND) || state.is(BlockTags.TERRACOTTA)
                || block == UniverseBlocks.PLANET_ROCK.get() || block == UniverseBlocks.PLANET_COBBLESTONE.get()
                || block == UniverseBlocks.PLANET_STRATA.get() || block == UniverseBlocks.PLANET_REGOLITH.get()
                || block == UniverseBlocks.PLANET_SAND.get() || block == UniverseBlocks.PLANET_ASH.get() || block == Blocks.SANDSTONE
                || block == Blocks.COBBLESTONE) {
            return SampleSource.ROCK;
        }
        return null;
    }

    /** 一格液体采得到什么：水、酸液。 */
    public static @Nullable SampleSource source(FluidState fluid) {
        if (fluid.is(FluidTags.WATER)) {
            return SampleSource.WATER;
        }
        if (fluid.getType().isSame(UniverseFluids.ACID.get())) {
            return SampleSource.ACID;
        }
        return null;
    }

    /** 采样的天体：本模组的星球和主世界（母星）。 */
    public static Optional<Planet> body(ServerLevel level) {
        return Planets.body(level);
    }

    /**
     * 采样处的温度（K）：星球现在的温度（改造过的按改造后的）；热泉的水七八十度，按位置定一个；冰雪最高零下五度，
     * 液态的水最低一度。
     */
    public static double temperature(ServerLevel level, BlockPos pos, SampleSource source) {
        double base = Terraforming.environment(level).map(EffectiveEnvironment::temperature).orElse(DEFAULT_K);
        return switch (source) {
            case HOT_SPRING -> 335 + Math.floorMod(Mth.getSeed(pos), 36);
            case ICE -> Math.min(base, 268);
            case WATER, SEDIMENT -> Math.max(base, 274);
            default -> base;
        };
    }

    /**
     * 在这里采一管：有菌（或者是组织）就把手上的一个空管换成样本。
     *
     * @return 采到了是 SUCCESS；这里采不到给提示，也算用过了（CONSUME），免得接着去放方块
     */
    public static InteractionResult take(ServerLevel level, Player player, InteractionHand hand, Sample sample) {
        if (!sample.tissue() && Microbiomes.candidates(level.getServer(), sample).isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.universe.sample_tube.nothing", MicrobeText.source(sample.source())));
            return InteractionResult.CONSUME;
        }
        ItemStack tube = player.getItemInHand(hand);
        ItemStack filled = SampleItem.stack(sample);
        player.setItemInHand(hand, ItemUtils.createFilledResult(tube, player, filled));
        player.sendOverlayMessage(Component.translatable("message.universe.sample_tube.taken", SampleItem.describe(sample)));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.6F, 1.4F);
        return InteractionResult.SUCCESS;
    }

    /** 方块、液体的样本：天体取这个世界的；不是星球也不是主世界的维度采不了。 */
    public static Optional<Sample> sample(ServerLevel level, BlockPos pos, SampleSource source) {
        return body(level).map(planet -> Sample.of(planet.id(), source, temperature(level, pos, source)));
    }

    /** 组织样本。 */
    public static Optional<Sample> tissue(ServerLevel level, BlockPos pos, SampleSource source, Set<Gene> genes, String nameZh, String nameEn) {
        Optional<BodyId> body = body(level).map(Planet::id);
        return body.map(id -> Sample.tissue(id, source, temperature(level, pos, source), genes, nameZh, nameEn));
    }

    /** 拿垃圾采样（另一只手拿着钓到的杂物）。 */
    public static boolean junk(ItemStack stack) {
        return UniverseItems.JUNK.values().stream().anyMatch(item -> stack.is(item.get()));
    }
}
