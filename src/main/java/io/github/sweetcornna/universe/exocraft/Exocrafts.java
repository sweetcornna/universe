package io.github.sweetcornna.universe.exocraft;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.block.ship.ShipFuel;
import io.github.sweetcornna.universe.network.ExocraftControlPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 探索车、潜艇的服务端事件（ADR-0017、ADR-0026）：武器开关的包、坐在车上拿燃料右键加油、坐在车上用多功能工具扫描。 */
public final class Exocrafts {
    private Exocrafts() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(PlayerInteractEvent.RightClickItem.class, Exocrafts::onRightClickItem);
    }

    /** 这个玩家正开着的探索车或潜艇，没在开就是 null。 */
    public static @Nullable UniverseVehicle driving(Player player) {
        return player.getVehicle() instanceof UniverseVehicle vehicle && vehicle.getControllingPassenger() == player ? vehicle : null;
    }

    public static void handleControl(ExocraftControlPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            UniverseVehicle vehicle = driving(player);
            if (vehicle != null) {
                vehicle.setControls(payload.firing(), payload.thirdPerson());
            }
        }
    }

    /** 多功能工具在车上右键：车载扫描（潜艇是声呐）。由 {@code MultitoolItem#use} 调用，返回 null 表示没在开车。 */
    public static @Nullable InteractionResult multitoolUse(Player player) {
        UniverseVehicle vehicle = driving(player);
        if (vehicle == null) {
            return null;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            vehicle.scan(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    /** 坐在车上拿着燃料右键：直接加进油箱，不用下车。 */
    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        UniverseVehicle vehicle = driving(event.getEntity());
        if (vehicle == null || ShipFuel.value(event.getItemStack()) <= 0) {
            return;
        }
        if (!event.getLevel().isClientSide()) {
            vehicle.refuel(event.getEntity(), event.getHand());
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
