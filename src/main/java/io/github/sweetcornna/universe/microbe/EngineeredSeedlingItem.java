package io.github.sweetcornna.universe.microbe;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.block.FloraBlocks;
import io.github.sweetcornna.universe.block.ResourcePlantBlock;
import io.github.sweetcornna.universe.core.ecology.ResourcePlant;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.microbe.EngineeredPlant;
import io.github.sweetcornna.universe.core.microbe.Gene;
import io.github.sweetcornna.universe.core.microbe.PlantTolerance;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.terraform.Terraforming;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * 改造种苗（ADR-0036 §五），带着 {@link UniverseDataComponents#ENGINEERED_PLANT}：编辑过的资源植物。
 * 右键地面种下去，能种的星球按 {@link PlantTolerance} 算（编辑进去的基因扛得住那颗星球的寒冷、高温、辐射、毒、紫外、干旱），
 * 种下去就是一株普通的资源植物；也能种进水培槽，在那里按 {@link EngineeredPlant#trayGrowth} 长、收获时偶尔多给一株种苗。
 */
public class EngineeredSeedlingItem extends Item {
    public EngineeredSeedlingItem(Properties properties) {
        super(properties);
    }

    public static ItemStack stack(EngineeredPlant plant, int count) {
        ItemStack stack = new ItemStack(UniverseItems.ENGINEERED_SEEDLING.get(), count);
        stack.set(UniverseDataComponents.ENGINEERED_PLANT.get(), plant);
        return stack;
    }

    public static @Nullable EngineeredPlant plant(ItemStack stack) {
        return stack.is(UniverseItems.ENGINEERED_SEEDLING.get()) ? stack.get(UniverseDataComponents.ENGINEERED_PLANT.get()) : null;
    }

    @Override
    public Component getName(ItemStack stack) {
        EngineeredPlant plant = plant(stack);
        return plant == null ? super.getName(stack)
                : Component.translatable("item.universe.engineered_seedling.of", Component.translatable("block.universe." + plant.plant().id()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        EngineeredPlant plant = plant(stack);
        if (plant == null) {
            return;
        }
        builder.accept(Component.translatable("tooltip.universe.engineered_seedling.added", plant.added().size(), Gene.MAX_ADDED,
                MicrobeText.genes(plant.added())).withStyle(ChatFormatting.AQUA));
        Set<PlanetType> types = PlantTolerance.allowedTypes(plant.plant(), plant.genes());
        if (!types.isEmpty()) {
            MutableComponent list = Component.empty();
            boolean first = true;
            for (PlanetType type : types) {
                if (!first) {
                    list.append(Component.translatable("microbe.universe.separator"));
                }
                first = false;
                list.append(MicrobeText.name(type.nameZh(), type.nameEn()));
            }
            builder.accept(Component.translatable("tooltip.universe.engineered_seedling.planets", list).withStyle(ChatFormatting.GRAY));
        } else {
            builder.accept(Component.translatable("tooltip.universe.engineered_seedling.tray_only").withStyle(ChatFormatting.GRAY));
        }
        if (plant.added().contains(Gene.CARBON_CONCENTRATING) || plant.selfFertilizing()) {
            builder.accept(Component.translatable("tooltip.universe.engineered_seedling.tray",
                    MicrobeText.bonus(plant.trayGrowth(false) - 1), MicrobeText.bonus(plant.trayYield() - 1)).withStyle(ChatFormatting.GREEN));
        }
    }

    /** 种在星球的地上：上面是空的、下面是能长植物的土（和资源植物的产物一样），星球要扛得住。 */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        EngineeredPlant plant = plant(context.getItemInHand());
        if (plant == null || context.getClickedFace() != Direction.UP || plant.plant().habitat() != ResourcePlant.Habitat.SOIL) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockPos soil = context.getClickedPos();
        BlockPos pos = soil.above();
        if (!level.getBlockState(soil).is(FloraBlocks.PLANT_SOIL) || !level.getBlockState(pos).canBeReplaced()) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        Optional<Planet> body = Terraforming.body(server);
        if (body.isEmpty() || !plant.canPlant(body.get())) {
            if (player != null) {
                player.sendOverlayMessage(unmet(plant, body));
            }
            return InteractionResult.FAIL;
        }
        BlockState state = UniverseBlocks.RESOURCE_PLANTS.get(plant.plant()).get().defaultBlockState().setValue(ResourcePlantBlock.RIPE, false);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, state));
        level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
        context.getItemInHand().consume(1, player);
        return InteractionResult.SUCCESS;
    }

    /** 种不下去时说缺哪几样："还扛不住：寒冷、紫外"。 */
    private static Component unmet(EngineeredPlant plant, Optional<Planet> body) {
        if (body.isEmpty()) {
            return Component.translatable("message.universe.resource_plant.wrong_planet");
        }
        Set<PlantTolerance.Barrier> missing = PlantTolerance.unmet(PlantTolerance.barriers(body.get()), plant.genes());
        List<PlantTolerance.Barrier> list = missing.stream().toList();
        if (list.isEmpty()) {
            return Component.translatable("message.universe.resource_plant.wrong_planet");
        }
        MutableComponent names = Component.empty();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                names.append(Component.translatable("microbe.universe.separator"));
            }
            names.append(MicrobeText.name(list.get(i).nameZh(), list.get(i).nameEn()));
        }
        return Component.translatable("message.universe.engineered_seedling.unmet", names);
    }
}
