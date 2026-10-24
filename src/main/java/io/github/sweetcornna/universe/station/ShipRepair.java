package io.github.sweetcornna.universe.station;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github.sweetcornna.universe.core.space.StationLayout.Site;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.registry.sound.WorldSounds;
import io.github.sweetcornna.universe.trade.Credits;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * 空间站的飞船维修台（ADR-0017）：右键报价，把站里所有飞船的船体修满要多少信用点；5 秒内再点一次付钱修好，
 * 护盾顺带充满。创造模式不要钱。停船再重新组装也能修好船体，这里是不想下船拆船时的便捷办法。
 */
public final class ShipRepair {
    /** 每点船体的修理费（信用点）。 */
    public static final double PRICE_PER_HULL = 2;
    /** 报价后这么多刻内再点一次就付钱。 */
    private static final int CONFIRM_TICKS = 100;

    private record Quote(BlockPos pos, long tick, long cost) {
    }

    private static final Map<UUID, Quote> QUOTES = new HashMap<>();

    private ShipRepair() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerStoppedEvent.class, event -> QUOTES.clear());
    }

    public static void use(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Optional<Site> site = Stations.site(level.getServer(), level.dimension());
        List<ShipEntity> ships = site.map(s -> ships(level, s)).orElse(List.of());
        if (ships.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.universe.repair.no_ship"));
            return;
        }
        double missing = 0;
        boolean shields = false;
        for (ShipEntity ship : ships) {
            missing += ship.missingHull();
            shields |= ship.missingShield() > 0.5;
        }
        if (missing < 0.5 && !shields) {
            player.sendOverlayMessage(Component.translatable("message.universe.repair.intact"));
            return;
        }
        long cost = player.hasInfiniteMaterials() ? 0 : (long) Math.ceil(missing * PRICE_PER_HULL);
        long now = level.getGameTime();
        Quote quote = QUOTES.get(player.getUUID());
        if (cost > 0 && (quote == null || !quote.pos().equals(pos) || now - quote.tick() > CONFIRM_TICKS || quote.cost() != cost)) {
            QUOTES.put(player.getUUID(), new Quote(pos.immutable(), now, cost));
            player.sendOverlayMessage(Component.translatable("message.universe.repair.quote", (int) Math.ceil(missing),
                    Credits.format(cost)).withStyle(ChatFormatting.GOLD));
            return;
        }
        QUOTES.remove(player.getUUID());
        if (!Credits.spend(player, cost)) {
            player.sendOverlayMessage(Component.translatable("message.universe.repair.poor", Credits.format(cost),
                    Credits.format(Credits.get(player))).withStyle(ChatFormatting.RED));
            return;
        }
        for (ShipEntity ship : ships) {
            ship.repair();
        }
        level.playSound(null, pos, WorldSounds.REPAIR.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        player.sendOverlayMessage(Component.translatable("message.universe.repair.done", (int) Math.ceil(missing),
                Credits.format(cost)).withStyle(ChatFormatting.GREEN));
    }

    /** 停在空间站里的飞船。 */
    public static List<ShipEntity> ships(ServerLevel level, Site site) {
        int[] b = site.bounds();
        AABB box = new AABB(b[0], b[1], b[2], b[3] + 1, b[4] + 1, b[5] + 1);
        return level.getEntitiesOfClass(ShipEntity.class, box, ship -> site.inside(ship.getX(), ship.getY() + 0.5, ship.getZ()));
    }
}
