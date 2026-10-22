package io.github.sweetcornna.universe.fleet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.core.fleet.Fleet;
import io.github.sweetcornna.universe.core.fleet.FreighterRules;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.procgen.NameGenerator.Name;
import io.github.sweetcornna.universe.core.space.Warp;
import io.github.sweetcornna.universe.discovery.Discoveries;
import io.github.sweetcornna.universe.network.FleetPayloads;
import io.github.sweetcornna.universe.network.WarpPayload;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.world.freighter.FreighterDimensions;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 货船舰桥上的控制台（ADR-0024）：列出货船航程内的恒星系，选一个就带着整艘货船跃迁过去，用
 * {@link FreighterRules#WARPERS} 个空间翘曲器，不看驱动档次。充能时船舱里的人看到和飞船跃迁一样的画面；
 * 到了以后外壳从旧的系拆掉、在新的系的锚点上放好，船舱里的东西都在。只有货船的主人能开。
 */
public final class FreighterConsole {
    private static final double REACH = 8;

    private record Session(ResourceKey<Level> dimension, BlockPos pos, int freighter) {
    }

    private static final class Spool {
        private final UUID owner;
        private final int target;
        private int ticks = Warp.SPOOL_TICKS;

        private Spool(UUID owner, int target) {
            this.owner = owner;
            this.target = target;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** 正在充能的货船：编号 → 去哪。 */
    private static final Map<Integer, Spool> SPOOLS = new HashMap<>();

    private FreighterConsole() {
    }

    static void clear() {
        SESSIONS.clear();
        SPOOLS.clear();
    }

    public static void open(ServerPlayer player, ServerLevel level, BlockPos pos) {
        OptionalInt id = FreighterDimensions.id(level.dimension());
        if (id.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.universe.trade.no_signal"));
            return;
        }
        Optional<Map.Entry<UUID, FleetData.Entry>> owner = Freighters.byId(level.getServer(), id.getAsInt());
        if (owner.isEmpty() || !owner.get().getKey().equals(player.getUUID())) {
            player.sendOverlayMessage(Component.translatable("message.universe.freighter.not_yours"));
            return;
        }
        Session session = new Session(level.dimension(), pos.immutable(), id.getAsInt());
        SESSIONS.put(player.getUUID(), session);
        send(player, session, true, null);
    }

    private static @Nullable Session session(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !player.isAlive() || player.level().dimension() != session.dimension()
                || player.distanceToSqr(Vec3.atCenterOf(session.pos())) > REACH * REACH
                || !player.level().getBlockState(session.pos()).is(UniverseBlocks.FREIGHTER_CONSOLE.get())) {
            SESSIONS.remove(player.getUUID());
            return null;
        }
        return session;
    }

    private static Optional<Fleet.Freighter> freighter(MinecraftServer server, Session session) {
        return Freighters.byId(server, session.freighter()).flatMap(e -> e.getValue().fleet().freighter());
    }

    /** 航程内的恒星系，近的在前。 */
    private static List<FleetPayloads.Target> targets(MinecraftServer server, int here, double range) {
        Galaxy galaxy = Planets.galaxy(server);
        StarSystem from = galaxy.system(here).orElse(null);
        if (from == null) {
            return List.of();
        }
        List<FleetPayloads.Target> targets = new ArrayList<>();
        for (StarSystem to : galaxy.systems()) {
            double distance = from.distanceTo(to);
            if (to.index() == here || !Warp.inReach(from, to, range)) {
                continue;
            }
            Name name = Discoveries.resolve(server, DiscoveryKey.system(to.index()), to.name());
            targets.add(new FleetPayloads.Target(to.index(), name.zh(), name.en(), distance,
                    Discoveries.log(server).contains(DiscoveryKey.system(to.index()))));
        }
        targets.sort(Comparator.comparingDouble(FleetPayloads.Target::distance));
        return targets.subList(0, Math.min(FleetPayloads.MAX_ROWS, targets.size()));
    }

    private static void send(ServerPlayer player, Session session, boolean open, @Nullable Component notice) {
        MinecraftServer server = player.level().getServer();
        Optional<Fleet.Freighter> freighter = freighter(server, session);
        OptionalInt here = Freighters.system(server, session.freighter());
        if (freighter.isEmpty() || here.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.universe.freighter.not_yours"));
            return;
        }
        StarSystem system = Planets.galaxy(server).system(here.getAsInt()).orElseThrow();
        Name name = Discoveries.resolve(server, DiscoveryKey.system(system.index()), system.name());
        double range = freighter.get().range();
        PacketDistributor.sendToPlayer(player, new FleetPayloads.Console(open, system.index(), name.zh(), name.en(), freighter.get().grade().ordinal(),
                range, count(player.getInventory()), SPOOLS.containsKey(session.freighter()), targets(server, here.getAsInt(), range),
                Optional.ofNullable(notice)));
    }

    public static void handleWarp(FleetPayloads.Warp payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Session session = session(player);
        if (session != null) {
            send(player, session, false, warp(player, session, payload.system()));
        }
    }

    private static Component warp(ServerPlayer player, Session session, int target) {
        MinecraftServer server = player.level().getServer();
        if (SPOOLS.containsKey(session.freighter())) {
            return Component.translatable("message.universe.warp.busy");
        }
        Optional<Fleet.Freighter> freighter = freighter(server, session);
        OptionalInt here = Freighters.system(server, session.freighter());
        Galaxy galaxy = Planets.galaxy(server);
        Optional<StarSystem> from = here.isPresent() ? galaxy.system(here.getAsInt()) : Optional.empty();
        Optional<StarSystem> to = galaxy.system(target);
        if (freighter.isEmpty() || from.isEmpty() || to.isEmpty() || from.get().index() == to.get().index()) {
            return Component.translatable("message.universe.warp.bad_target");
        }
        if (!Warp.inReach(from.get(), to.get(), freighter.get().range())) {
            return Component.translatable("message.universe.warp.out_of_range",
                    String.format(java.util.Locale.ROOT, "%.1f", from.get().distanceTo(to.get())), (int) freighter.get().range());
        }
        if (!player.hasInfiniteMaterials()) {
            if (count(player.getInventory()) < FreighterRules.WARPERS) {
                return Component.translatable("message.universe.freighter.no_warpers", FreighterRules.WARPERS);
            }
            consume(player.getInventory(), FreighterRules.WARPERS);
        }
        SPOOLS.put(session.freighter(), new Spool(player.getUUID(), target));
        ServerLevel interior = player.level();
        interior.playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 0.8F, 0.9F);
        for (ServerPlayer aboard : interior.players()) {
            PacketDistributor.sendToPlayer(aboard, new WarpPayload(WarpPayload.Stage.SPOOL, Warp.SPOOL_TICKS, target));
        }
        Name name = Discoveries.resolve(server, DiscoveryKey.system(to.get().index()), to.get().name());
        return Component.translatable("message.universe.freighter.spooling", name.zh(), name.en());
    }

    /** 每刻末尾：充能满了的货船挪过去。 */
    static void tick(MinecraftServer server) {
        if (SPOOLS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Integer, Spool>> iterator = SPOOLS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Spool> entry = iterator.next();
            Spool spool = entry.getValue();
            if (--spool.ticks > 0) {
                continue;
            }
            iterator.remove();
            Freighters.moveTo(server, spool.owner, spool.target, null);
            ServerLevel interior = server.getLevel(FreighterDimensions.key(entry.getKey()));
            if (interior == null) {
                continue;
            }
            StarSystem system = Planets.galaxy(server).system(spool.target).orElseThrow();
            Name name = Discoveries.resolve(server, DiscoveryKey.system(system.index()), system.name());
            for (ServerPlayer aboard : interior.players()) {
                PacketDistributor.sendToPlayer(aboard, new WarpPayload(WarpPayload.Stage.ARRIVE, Warp.ARRIVAL_TICKS, spool.target));
                aboard.sendOverlayMessage(Component.translatable("message.universe.freighter.arrived", name.zh(), name.en()));
                Discoveries.discover(aboard, DiscoveryKey.system(system.index()));
                interior.playSound(null, aboard.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.BLOCKS, 1.0F, 0.6F);
                Session session = SESSIONS.get(aboard.getUUID());
                if (session != null && session.freighter() == entry.getKey()) {
                    send(aboard, session, false, null);
                }
            }
        }
    }

    private static int count(Inventory inventory) {
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(UniverseItems.SPACE_WARPER.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void consume(Inventory inventory, int amount) {
        for (int i = 0; i < inventory.getContainerSize() && amount > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(UniverseItems.SPACE_WARPER.get())) {
                int taken = Math.min(amount, stack.getCount());
                stack.shrink(taken);
                amount -= taken;
            }
        }
        inventory.setChanged();
    }
}
