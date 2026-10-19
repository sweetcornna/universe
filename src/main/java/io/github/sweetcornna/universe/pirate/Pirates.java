package io.github.sweetcornna.universe.pirate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.ship.PirateFlight;
import io.github.sweetcornna.universe.core.ship.PirateRaid;
import io.github.sweetcornna.universe.core.space.StationLayout.Site;
import io.github.sweetcornna.universe.core.trade.Economy;
import io.github.sweetcornna.universe.entity.ship.ShipCargo;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.registry.UniverseStats;
import io.github.sweetcornna.universe.station.Stations;
import io.github.sweetcornna.universe.trade.Credits;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 海盗袭击（ADR-0017，规则见 {@link PirateRaid}）：每个玩家在太空里驾驶飞船、离空间站够远时累计飞行时间，到点掷一次骰子；
 * 掷中了先提示扫描货舱，几秒后海盗战机从身后出现。这里记着每场袭击的海盗，判断袭击怎么结束，发赏金。
 *
 * <p>袭击和计时都不存档：重新登录或者重开服务器，正在打的海盗就散了（战机本身也不存档）。
 */
public final class Pirates {
    /** 多久检查一次谁该掷骰子。 */
    private static final int CHECK_INTERVAL = 20;
    private static final Map<UUID, Raid> RAIDS = new HashMap<>();
    /** 每个玩家累计的有效飞行时间和这次要飞满多久才掷骰子。 */
    private static final Map<UUID, int[]> TIMERS = new HashMap<>();

    /** 一场袭击。 */
    private static final class Raid {
        final UUID player;
        final ResourceKey<Level> dimension;
        final int wealthTier;
        final int count;
        final boolean forced;
        long spawnAt;
        final List<PirateFighter> fighters = new ArrayList<>();
        boolean spawned;
        boolean over;
        int destroyed;

        Raid(UUID player, ResourceKey<Level> dimension, int wealthTier, int count, boolean forced, long spawnAt) {
            this.player = player;
            this.dimension = dimension;
            this.wealthTier = wealthTier;
            this.count = count;
            this.forced = forced;
            this.spawnAt = spawnAt;
        }
    }

    private Pirates() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Post.class, event -> tick(event.getServer()));
        gameBus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            Raid raid = RAIDS.remove(event.getEntity().getUUID());
            if (raid != null) {
                raid.fighters.forEach(PirateFighter::leave);
            }
            TIMERS.remove(event.getEntity().getUUID());
        });
        gameBus.addListener(ServerStoppedEvent.class, event -> {
            RAIDS.clear();
            TIMERS.clear();
        });
    }

    // ---- 查询

    /** 战机还属于一场没结束的袭击（不属于的会撤走）。 */
    static boolean belongs(PirateFighter fighter) {
        UUID target = fighter.targetId();
        Raid raid = target == null ? null : RAIDS.get(target);
        return raid != null && !raid.over && raid.fighters.contains(fighter);
    }

    /** 飞船的脉冲引擎被干扰：驾驶员正被海盗袭击，附近有没撤走的战机。 */
    public static boolean jammed(ShipEntity ship) {
        Entity pilot = ship.pilot();
        Raid raid = pilot == null ? null : RAIDS.get(pilot.getUUID());
        if (raid == null || raid.over || !raid.spawned) {
            return false;
        }
        double range = PirateRaid.JAM_DISTANCE * PirateRaid.JAM_DISTANCE;
        return raid.fighters.stream().anyMatch(f -> !f.isRemoved() && !f.leaving() && f.distanceToSqr(ship) < range);
    }

    /** 调试命令用：{@code {累计刻数, 要飞满的刻数}}，还没开始计时返回空。 */
    static int @Nullable [] timer(ServerPlayer player) {
        return TIMERS.get(player.getUUID());
    }

    static boolean raiding(ServerPlayer player) {
        Raid raid = RAIDS.get(player.getUUID());
        return raid != null && !raid.over;
    }

    // ---- 每刻

    private static void tick(MinecraftServer server) {
        if (!RAIDS.isEmpty()) {
            for (Raid raid : List.copyOf(RAIDS.values())) {
                tickRaid(server, raid);
            }
        }
        if (server.getTickCount() % CHECK_INTERVAL != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (RAIDS.containsKey(player.getUUID())) {
                continue;
            }
            ShipEntity ship = eligibleShip(player);
            if (ship == null || player.isCreative() || player.isSpectator()) {
                continue;
            }
            int[] timer = TIMERS.computeIfAbsent(player.getUUID(), id -> new int[] {0, PirateRaid.nextCheck(player.getRandom().nextDouble())});
            timer[0] += CHECK_INTERVAL;
            if (timer[0] < timer[1]) {
                continue;
            }
            timer[0] = 0;
            timer[1] = PirateRaid.nextCheck(player.getRandom().nextDouble());
            double value = ShipCargo.value(ship, server);
            if (player.getRandom().nextDouble() < PirateRaid.chance(value)) {
                start(player, ship, value, false);
            }
        }
    }

    /** 玩家正在太空里、空间站附近以外驾驶着飞船（没在跃迁、没被击毁），返回那艘船。 */
    private static @Nullable ShipEntity eligibleShip(ServerPlayer player) {
        if (!(player.getVehicle() instanceof ShipEntity ship) || ship.pilot() != player || ship.isWarping()
                || !(player.level() instanceof ServerLevel level) || !SpaceDimensions.isSpace(level.dimension())
                || level.getDifficulty() == Difficulty.PEACEFUL) {
            return null;
        }
        return nearStation(level, ship.position()) ? null : ship;
    }

    private static boolean nearStation(ServerLevel level, Vec3 pos) {
        Optional<Site> site = Stations.site(level.getServer(), level.dimension());
        return site.isPresent() && site.get().distanceTo(pos.x, pos.y, pos.z) < PirateRaid.STATION_SAFE;
    }

    /**
     * 开始一场袭击：先提示扫描货舱。{@code forced}（调试命令）时不管创造模式、离空间站多近。
     *
     * @return 来几架
     */
    static int start(ServerPlayer player, ShipEntity ship, double cargoValue, boolean forced) {
        ServerLevel level = (ServerLevel) player.level();
        int count = PirateRaid.fighters(cargoValue, player.getRandom().nextDouble());
        Raid raid = new Raid(player.getUUID(), level.dimension(), wealthTier(level), count, forced,
                level.getGameTime() + PirateRaid.WARNING_TICKS);
        Raid old = RAIDS.put(player.getUUID(), raid);
        if (old != null) {
            old.fighters.forEach(PirateFighter::leave);
        }
        player.sendSystemMessage(Component.translatable("message.universe.pirate.scanning").withStyle(ChatFormatting.GOLD));
        player.sendOverlayMessage(Component.translatable("message.universe.pirate.scanning").withStyle(ChatFormatting.GOLD));
        level.playSound(null, ship.getX(), ship.getY(), ship.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.0F, 0.6F);
        return count;
    }

    private static int wealthTier(ServerLevel level) {
        OptionalInt system = SpaceDimensions.system(level.dimension());
        if (system.isEmpty()) {
            return 1;
        }
        return Planets.galaxy(level.getServer()).system(system.getAsInt()).map(star -> Economy.of(star).wealth().tier()).orElse(1);
    }

    private static void tickRaid(MinecraftServer server, Raid raid) {
        raid.fighters.removeIf(Entity::isRemoved);
        if (raid.over) {
            if (raid.fighters.isEmpty()) {
                RAIDS.remove(raid.player, raid);
            }
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(raid.player);
        if (player == null || !player.isAlive()) {
            end(raid, null, null);
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        if (level.dimension() != raid.dimension) {
            // 跃迁走了、降落到星球上了
            end(raid, player, raid.spawned ? Component.translatable("message.universe.pirate.escaped") : null);
            return;
        }
        Entity target = player.getRootVehicle();
        if (!raid.forced && nearStation(level, target.position())) {
            end(raid, player, Component.translatable("message.universe.pirate.fled_station"));
            return;
        }
        if (!raid.spawned) {
            if (level.getGameTime() >= raid.spawnAt) {
                spawn(level, player, target, raid);
            }
            return;
        }
        if (raid.fighters.isEmpty()) {
            if (raid.destroyed > 0) {
                player.awardStat(UniverseStats.PIRATE_RAIDS_REPELLED.get());
                player.sendSystemMessage(Component.translatable("message.universe.pirate.repelled", raid.destroyed).withStyle(ChatFormatting.GREEN));
                player.sendOverlayMessage(Component.translatable("message.universe.pirate.repelled", raid.destroyed).withStyle(ChatFormatting.GREEN));
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);
            }
            raid.over = true;
            RAIDS.remove(raid.player, raid);
            return;
        }
        double giveUp = PirateRaid.GIVE_UP_DISTANCE * PirateRaid.GIVE_UP_DISTANCE;
        if (raid.fighters.stream().allMatch(f -> f.distanceToSqr(target) > giveUp)) {
            end(raid, player, Component.translatable("message.universe.pirate.lost"));
        }
    }

    /** 海盗战机从目标身后出现，朝着目标飞过来。 */
    private static void spawn(ServerLevel level, ServerPlayer player, Entity target, Raid raid) {
        raid.spawned = true;
        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 heading = target.getDeltaMovement().horizontalDistanceSqr() > 0.01
                ? target.getDeltaMovement().multiply(1, 0, 1).normalize()
                : Vec3.directionFromRotation(0, target.getYRot());
        Vec3 right = heading.cross(new Vec3(0, 1, 0)).normalize();
        for (int i = 0; i < raid.count; i++) {
            double spread = (i - (raid.count - 1) / 2.0) * 22;
            Vec3 pos = center.subtract(heading.scale(PirateRaid.SPAWN_DISTANCE + level.getRandom().nextDouble() * 15))
                    .add(right.scale(spread)).add(0, (level.getRandom().nextDouble() - 0.3) * 20, 0);
            pos = new Vec3(pos.x, Math.clamp(pos.y, level.getMinY() + 30, level.getMaxY() + 150), pos.z);
            Vec3 velocity = center.subtract(pos).normalize().scale(PirateFlight.SPEED);
            PirateFighter fighter = PirateFighter.create(level, raid.player, i, raid.count, pos, velocity);
            level.addFreshEntity(fighter);
            raid.fighters.add(fighter);
        }
        Component incoming = Component.translatable("message.universe.pirate.incoming", raid.count).withStyle(ChatFormatting.RED);
        player.sendSystemMessage(incoming);
        player.sendOverlayMessage(incoming);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.RAID_HORN, SoundSource.HOSTILE, 0.8F, 1.4F);
    }

    /** 袭击结束（不是打退的）：剩下的海盗撤走。 */
    private static void end(Raid raid, @Nullable ServerPlayer player, @Nullable Component message) {
        raid.over = true;
        raid.fighters.forEach(PirateFighter::leave);
        if (raid.fighters.isEmpty()) {
            RAIDS.remove(raid.player, raid);
        }
        if (message != null && player != null) {
            player.sendSystemMessage(message.copy().withStyle(ChatFormatting.YELLOW));
            player.sendOverlayMessage(message.copy().withStyle(ChatFormatting.YELLOW));
        }
    }

    /** 飞船被击毁：海盗抢了货就走。 */
    public static void onShipWrecked(ShipEntity ship) {
        for (Entity passenger : ship.getPassengers()) {
            Raid raid = RAIDS.get(passenger.getUUID());
            if (raid != null && !raid.over && raid.spawned) {
                end(raid, passenger instanceof ServerPlayer player ? player : null, Component.translatable("message.universe.pirate.looted"));
            }
        }
    }

    /** 调试命令：所有海盗撤走，返回撤走了几架。 */
    static int clear() {
        int n = 0;
        for (Raid raid : RAIDS.values()) {
            n += (int) raid.fighters.stream().filter(f -> !f.isRemoved()).count();
            raid.over = true;
            raid.fighters.forEach(PirateFighter::leave);
        }
        TIMERS.clear();
        return n;
    }

    // ---- 击落

    /** 一架海盗战机被打下来：记战绩、给赏金，海盗残骸装进击落它的人的船里（装不下就飘在原地）。 */
    static void onDestroyed(PirateFighter fighter, ServerLevel level, DamageSource source) {
        UUID target = fighter.targetId();
        Raid raid = target == null ? null : RAIDS.get(target);
        if (raid != null) {
            raid.destroyed++;
            raid.fighters.remove(fighter);
        }
        ServerPlayer killer = killer(source);
        List<ItemStack> loot = new ArrayList<>();
        fighter.getType().getDefaultLootTable().ifPresent(key -> {
            LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.THIS_ENTITY, fighter)
                    .withParameter(LootContextParams.ORIGIN, fighter.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                    .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
                    .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity())
                    .create(LootContextParamSets.ENTITY);
            table.getRandomItems(params, loot::add);
        });
        boolean stowed = false;
        ShipEntity ship = killer != null && killer.getRootVehicle() instanceof ShipEntity s ? s : null;
        for (ItemStack stack : loot) {
            if (ship != null) {
                stowed |= ShipCargo.insert(ship, level.registryAccess(), stack) > 0;
            }
            if (!stack.isEmpty()) {
                ItemEntity item = new ItemEntity(level, fighter.getX(), fighter.getY(), fighter.getZ(), stack);
                item.setNoGravity(true);
                item.setDeltaMovement(fighter.getDeltaMovement().scale(0.1));
                level.addFreshEntity(item);
            }
        }
        if (killer == null) {
            return;
        }
        killer.awardStat(UniverseStats.PIRATES_DESTROYED.get());
        long bounty = PirateRaid.bounty(raid != null ? raid.wealthTier : wealthTier(level));
        Credits.add(killer, bounty);
        killer.sendSystemMessage(Component.translatable(stowed ? "message.universe.pirate.destroyed_stowed" : "message.universe.pirate.destroyed",
                Credits.format(bounty)).withStyle(ChatFormatting.GOLD));
        level.playSound(null, killer.getX(), killer.getY(), killer.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7F, 0.8F);
    }

    /** 打下战机的玩家：直接打的，或者开炮的驾驶员（光子弹的主人）。 */
    private static @Nullable ServerPlayer killer(DamageSource source) {
        Entity entity = source.getEntity();
        if (entity instanceof ServerPlayer player) {
            return player;
        }
        if (entity instanceof ShipEntity ship && ship.pilot() instanceof ServerPlayer pilot) {
            return pilot;
        }
        return null;
    }
}
