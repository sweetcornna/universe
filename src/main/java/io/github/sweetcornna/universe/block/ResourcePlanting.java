package io.github.sweetcornna.universe.block;

import java.util.Optional;

import io.github.sweetcornna.universe.core.ecology.ResourceFlora;
import io.github.sweetcornna.universe.core.ecology.ResourcePlant;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * 拿着资源植物的产物右键地面，把它种下去（ADR-0026）：只能种在这类星球本来就长它的地方，种下去是没长熟的。
 * 水培槽不受这个限制，由水培槽自己处理。
 */
public final class ResourcePlanting {
    private ResourcePlanting() {
    }

    /** 种不下去时返回 {@link InteractionResult#PASS}，让物品照常用（比如氧气右键补给）。 */
    public static InteractionResult plant(UseOnContext context, ResourcePlant plant) {
        if (context.getClickedFace() != Direction.UP || plant.habitat() != ResourcePlant.Habitat.SOIL) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockPos soil = context.getClickedPos();
        BlockPos pos = soil.above();
        if (!level.getBlockState(soil).is(FloraBlocks.PLANT_SOIL) || !level.getBlockState(pos).canBeReplaced()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        Optional<Planet> body = Planets.body((ServerLevel) level);
        boolean grows = body.map(p -> ResourceFlora.grows(plant, p.type(), p.ecology().lifeless())).orElse(false);
        Player player = context.getPlayer();
        if (!grows) {
            if (player != null) {
                player.sendOverlayMessage(Component.translatable("message.universe.resource_plant.wrong_planet"));
            }
            return InteractionResult.FAIL;
        }
        BlockState state = UniverseBlocks.RESOURCE_PLANTS.get(plant).get().defaultBlockState().setValue(ResourcePlantBlock.RIPE, false);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, state));
        level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
        context.getItemInHand().consume(1, player);
        return InteractionResult.SUCCESS;
    }
}
