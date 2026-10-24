package io.github.sweetcornna.universe.exocraft;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.sound.ShipSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 潜艇的物品（ADR-0026）：对着水面用就放下一艘，艇头朝玩家看的方向；在水下用就放在眼前三格。只能放在水里。
 * 物品里存着上次收起来时的燃料和货物，和探索车一样。
 */
public class SubmarineItem extends Item {
    public SubmarineItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        Vec3 at = placement(level, player);
        if (at == null) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.universe.submarine.need_water"));
            }
            return InteractionResult.FAIL;
        }
        Submarine submarine = UniverseEntities.SUBMARINE.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (submarine == null) {
            return InteractionResult.FAIL;
        }
        submarine.snapTo(at.x, at.y, at.z, player.getYRot(), 0);
        if (!level.noCollision(submarine, submarine.getBoundingBox()) || submarine.getBoundingBox().intersects(player.getBoundingBox())) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.universe.exocraft.no_room"));
            }
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            ItemStack stack = player.getItemInHand(hand);
            submarine.loadFrom(stack);
            submarine.setOwner(player.getUUID());
            server.addFreshEntity(submarine);
            server.playSound(null, at.x, at.y, at.z, ShipSounds.VEHICLE_SUMMON.get(), SoundSource.NEUTRAL, 0.9F, 0.85F);
            server.gameEvent(player, GameEvent.ENTITY_PLACE, at);
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }

    /** 放在哪儿（艇底的中心），放不下就是 null。 */
    private static @Nullable Vec3 placement(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        if (player.isEyeInFluid(FluidTags.WATER)) {
            // 水下：眼前三格，前面有墙就放在墙前面
            Vec3 ahead = eye.add(look.scale(3.2));
            BlockHitResult wall = level.clip(new ClipContext(eye, ahead, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            Vec3 centre = wall.getType() == HitResult.Type.MISS ? ahead : wall.getLocation().subtract(look.scale(1.4));
            return water(level, centre) ? centre.subtract(0, 0.75, 0) : null;
        }
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(5)), ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        if (hit.getType() != HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
            return null;
        }
        // 水面：一大半沉在水里
        Vec3 surface = hit.getLocation();
        return new Vec3(surface.x, surface.y - 1.0, surface.z);
    }

    private static boolean water(Level level, Vec3 at) {
        return level.getFluidState(BlockPos.containing(at)).is(FluidTags.WATER);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        int fuel = stack.getOrDefault(UniverseDataComponents.EXOCRAFT_FUEL.get(), 0);
        builder.accept(Component.translatable("tooltip.universe.exocraft.fuel", fuel, (int) Submarine.MAX_FUEL).withStyle(ChatFormatting.GRAY));
    }
}
