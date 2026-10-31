package io.github.sweetcornna.universe.microbe;

import java.util.Optional;
import java.util.Set;

import io.github.sweetcornna.universe.block.ResourcePlantBlock;
import io.github.sweetcornna.universe.core.ecology.CreatureSpecies;
import io.github.sweetcornna.universe.core.ecology.ResourcePlant;
import io.github.sweetcornna.universe.core.fishing.FishSpecies;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.microbe.Gene;
import io.github.sweetcornna.universe.core.microbe.SampleSource;
import io.github.sweetcornna.universe.core.microbe.TissueGenes;
import io.github.sweetcornna.universe.entity.AlienCreature;
import io.github.sweetcornna.universe.entity.AquaticCreature;
import io.github.sweetcornna.universe.fishing.FishItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 空采样管（ADR-0036 §五）：
 *
 * <ul>
 *   <li>右键方块：土壤、冰雪、盐矿、矿石、岩石，水底的泥沙是沉积物，间歇泉的结壳是热泉；</li>
 *   <li>对着水、酸液右键：水、酸液；</li>
 *   <li>右键资源植物、动物：植物组织、生物组织；</li>
 *   <li>另一只手拿着钓到的杂物或鱼再右键：垃圾（上面有跟着人带过来的菌）、鱼的组织。</li>
 * </ul>
 */
public class SampleTubeItem extends Item {
    public SampleTubeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (state.getBlock() instanceof ResourcePlantBlock plantBlock) {
            if (!(level instanceof ServerLevel server)) {
                return InteractionResult.SUCCESS;
            }
            ResourcePlant plant = plantBlock.plant();
            return Sampling.tissue(server, pos, SampleSource.PLANT_TISSUE, TissueGenes.plant(plant), plant.nameZh(), plant.nameEn())
                    .map(sample -> Sampling.take(server, player, context.getHand(), sample)).orElse(InteractionResult.PASS);
        }
        SampleSource source = Sampling.source(level, pos, state);
        if (source == null) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        return Sampling.sample(server, pos, source).map(sample -> Sampling.take(server, player, context.getHand(), sample))
                .orElse(InteractionResult.PASS);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        boolean junk = Sampling.junk(other);
        boolean fish = other.getItem() instanceof FishItem;
        SampleSource fluid = null;
        BlockPos pos = player.blockPosition();
        if (!junk && !fish) {
            BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
            if (hit.getType() != HitResult.Type.BLOCK) {
                return InteractionResult.PASS;
            }
            pos = hit.getBlockPos();
            fluid = Sampling.source(level.getFluidState(pos));
            if (fluid == null) {
                return InteractionResult.PASS;
            }
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        Optional<Sample> sample;
        if (junk) {
            sample = Sampling.sample(server, pos, SampleSource.JUNK);
        } else if (fish) {
            FishSpecies species = ((FishItem) other.getItem()).species();
            sample = Sampling.tissue(server, pos, SampleSource.CREATURE_TISSUE, TissueGenes.fish(species.habitat()), species.nameZh(),
                    species.nameEn());
        } else {
            sample = Sampling.sample(server, pos, fluid);
        }
        return sample.map(s -> Sampling.take(server, player, hand, s)).orElse(InteractionResult.PASS);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        CreatureSpecies species;
        if (target instanceof AlienCreature creature) {
            species = creature.species();
        } else if (target instanceof AquaticCreature creature) {
            species = creature.species();
        } else {
            return InteractionResult.PASS;
        }
        if (!(player.level() instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        PlanetType type = Sampling.body(server).map(Planet::type).orElse(PlanetType.HABITABLE);
        Set<Gene> genes = TissueGenes.creature(type, species);
        return Sampling.tissue(server, target.blockPosition(), SampleSource.CREATURE_TISSUE, genes, species.name().zh(), species.name().en())
                .map(sample -> Sampling.take(server, player, hand, sample)).orElse(InteractionResult.PASS);
    }
}
