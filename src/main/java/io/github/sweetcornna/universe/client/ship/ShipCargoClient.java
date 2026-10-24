package io.github.sweetcornna.universe.client.ship;

import java.util.HashSet;
import java.util.Set;

import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.network.ShipCargoPayloads;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * 飞船货舱和别人的喷气背包火焰的客户端部分（ADR-0030 §六）。
 *
 * <p>货舱：坐在船上按货舱键发一个包，界面由服务端开。火焰：服务端说谁点着了（{@link ShipCargoPayloads.Jetpack}），
 * 这边每刻给这些玩家画和自己一样的两股小火焰；自己的火焰在 {@code SuitClient} 里画，这里跳过自己。
 */
public final class ShipCargoClient {
    /** 正在喷气的别人（实体 id）。 */
    private static final Set<Integer> FLAMING = new HashSet<>();

    private ShipCargoClient() {
    }

    /** 服务端说这个玩家（实体 id）正在喷气（{@code GearAudio} 给别人的喷气背包放声音用）。 */
    public static boolean flaming(int entity) {
        return FLAMING.contains(entity);
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterMenuScreensEvent.class,
                event -> event.register(UniverseMenus.SHIP_CARGO.get(), ShipCargoScreen::new));
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(ShipCargoPayloads.Jetpack.TYPE,
                (payload, context) -> context.enqueueWork(() -> {
                    if (payload.on()) {
                        FLAMING.add(payload.entity());
                    } else {
                        FLAMING.remove(payload.entity());
                    }
                })));
        gameBus.addListener(ClientTickEvent.Post.class, event -> tick());
    }

    private static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            FLAMING.clear();
            return;
        }
        while (UniverseKeys.SHIP_CARGO.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.getVehicle() instanceof ShipEntity) {
                ClientPacketDistributor.sendToServer(ShipCargoPayloads.Open.INSTANCE);
            }
        }
        if (FLAMING.isEmpty() || minecraft.isPaused()) {
            return;
        }
        FLAMING.removeIf(id -> {
            Entity entity = minecraft.level.getEntity(id);
            if (!(entity instanceof Player player) || !player.isAlive()) {
                // 走远了、下线了：等服务端再说一次（看得见时会补发）
                return true;
            }
            if (player != minecraft.player) {
                flames(player);
            }
            return false;
        });
    }

    /** 背后两股小火焰，和自己的一样（{@code SuitClient#push}）。 */
    private static void flames(Player player) {
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        double backX = Mth.sin(yaw) * 0.3;
        double backZ = -Mth.cos(yaw) * 0.3;
        double sideX = Mth.cos(yaw) * 0.18;
        double sideZ = Mth.sin(yaw) * 0.18;
        for (int side = -1; side <= 1; side += 2) {
            double x = player.getX() + backX + sideX * side;
            double z = player.getZ() + backZ + sideZ * side;
            player.level().addParticle(ParticleTypes.SMALL_FLAME, x, player.getY() + 0.7, z, 0, -0.15, 0);
            if (player.tickCount % 2 == 0) {
                player.level().addParticle(ParticleTypes.CLOUD, x, player.getY() + 0.4, z, 0, -0.05, 0);
            }
        }
    }
}
