package io.github.sweetcornna.universe.exocraft;

import java.util.function.Consumer;
import java.util.function.Supplier;

import io.github.sweetcornna.universe.advancement.MilestoneTrigger;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * 陆上载具的物品（ADR-0017、ADR-0026）：探索车、悬浮车、重型车、机甲。对着地面用就放下一辆，车头朝玩家看的方向，放下的人是主人；
 * 物品里存着上次收起来时的燃料和货物。
 */
public class ExocraftItem extends Item {
    private final Supplier<? extends EntityType<? extends UniverseVehicle>> type;

    public ExocraftItem(Supplier<? extends EntityType<? extends UniverseVehicle>> type, Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (SpaceDimensions.isSpace(level.dimension())) {
            if (player != null && !level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.universe.exocraft.no_space"));
            }
            return InteractionResult.FAIL;
        }
        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockPos pos = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty() ? clicked : clicked.relative(face);
        UniverseVehicle exocraft = this.type.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (exocraft == null) {
            return InteractionResult.FAIL;
        }
        Vec3 at = Vec3.atBottomCenterOf(pos);
        float yaw = player == null ? 0 : player.getYRot();
        exocraft.snapTo(at.x, at.y, at.z, yaw, 0);
        if (!level.noCollision(exocraft, exocraft.getBoundingBox())) {
            if (player != null && !level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.universe.exocraft.no_room"));
            }
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            ItemStack stack = context.getItemInHand();
            exocraft.loadFrom(stack);
            if (player != null) {
                exocraft.setOwner(player.getUUID());
            }
            server.addFreshEntity(exocraft);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 0.5F, 1.3F);
            server.gameEvent(player, GameEvent.ENTITY_PLACE, at);
            stack.consume(1, player);
            if (player instanceof ServerPlayer serverPlayer) {
                MilestoneTrigger.reach(serverPlayer, MilestoneTrigger.EXOCRAFT);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        int fuel = stack.getOrDefault(UniverseDataComponents.EXOCRAFT_FUEL.get(), 0);
        builder.accept(Component.translatable("tooltip.universe.exocraft.fuel", fuel, (int) Exocraft.MAX_FUEL).withStyle(ChatFormatting.GRAY));
    }
}
