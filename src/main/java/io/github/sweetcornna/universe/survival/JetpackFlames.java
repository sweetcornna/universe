package io.github.sweetcornna.universe.survival;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.github.sweetcornna.universe.network.ShipCargoPayloads;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 喷气背包的火焰给附近的人看（ADR-0030 §六）：推力在客户端加，客户端报告开、关时（{@link PlayerSurvival#handleJetpack}）
 * 转发给看得见这个玩家的人；后来才看见的人在开始跟踪时补发一次。没穿外骨骼胸甲的报告不转发。
 */
public final class JetpackFlames {
    private static final Set<UUID> FLAMING = ConcurrentHashMap.newKeySet();

    private JetpackFlames() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(PlayerEvent.StartTracking.class, event -> {
            if (event.getTarget() instanceof ServerPlayer target && FLAMING.contains(target.getUUID())
                    && event.getEntity() instanceof ServerPlayer viewer) {
                PacketDistributor.sendToPlayer(viewer, new ShipCargoPayloads.Jetpack(target.getId(), true));
            }
        });
        gameBus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> FLAMING.remove(event.getEntity().getUUID()));
    }

    /** 客户端报告推进开、关。 */
    static void report(ServerPlayer player, boolean thrusting) {
        boolean on = thrusting && Exosuit.of(player).chest();
        boolean changed = on ? FLAMING.add(player.getUUID()) : FLAMING.remove(player.getUUID());
        if (changed) {
            PacketDistributor.sendToPlayersTrackingEntity(player, new ShipCargoPayloads.Jetpack(player.getId(), on));
        }
    }
}
