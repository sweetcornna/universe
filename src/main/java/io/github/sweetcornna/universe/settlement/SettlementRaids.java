package io.github.sweetcornna.universe.settlement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.settlement.BuildingType;
import io.github.sweetcornna.universe.core.settlement.Plot;
import io.github.sweetcornna.universe.core.settlement.Settlement;
import io.github.sweetcornna.universe.core.settlement.SettlementRules;
import io.github.sweetcornna.universe.core.ship.PirateFlight;
import io.github.sweetcornna.universe.core.ship.ShipCombat;
import io.github.sweetcornna.universe.entity.PlasmaBolt;
import io.github.sweetcornna.universe.pirate.PirateFighter;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.sound.SettlementSounds;
import io.github.sweetcornna.universe.trade.Credits;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * 聚落的海盗来袭（ADR-0034 §二）。到了时间：总督在 {@value SettlementRules#RAID_RANGE} 格内就真打——3–6 架海盗战机从远处飞来，
 * 一轮轮俯冲扫射建筑，炮台朝它们开火，2 分钟内全部打下来算守住；总督不在场（或者打到一半走了）就按防御值算输赢。
 * 结果落到数值上（{@link Settlement#finishRaid}）：守住了给信用点，没守住两座建筑受损、储备被抢。
 *
 * <p>战机不存档；服务器重启时打到一半的来袭按没守住算，再排一次也不公平，所以这里按防御值补算。
 */
public final class SettlementRaids {
    /** 战机从多远的地方飞来，多高。 */
    private static final double SPAWN_DISTANCE = 140;
    private static final double SPAWN_HEIGHT = 50;
    /** 炮台的射程、隔多少刻打一发、一发的伤害、弹速。 */
    private static final double TURRET_RANGE = 72;
    private static final int TURRET_INTERVAL = 16;
    private static final float TURRET_DAMAGE = 5.0F;
    private static final double TURRET_BOLT_SPEED = 4.0;
    private static final int TURRET_COLOR = 0x6EDCF5;
    /** 总督走开多久算不在场。 */
    private static final int ABSENT_TICKS = 100;
    /** 隔多久给战机换一个扫射的目标。 */
    private static final int RETARGET = 120;

    private static final Map<String, Raid> RAIDS = new HashMap<>();

    private static final class Raid {
        final String settlement;
        final UUID owner;
        final int total;
        final List<PirateFighter> fighters = new ArrayList<>();
        final ServerBossEvent bar;
        final long start;
        int absent;
        /** 游戏内测试放的：没有总督也一直打。 */
        boolean test;

        Raid(String settlement, UUID owner, int total, long start, Component name) {
            this.settlement = settlement;
            this.owner = owner;
            this.total = total;
            this.start = start;
            this.bar = new ServerBossEvent(UUID.randomUUID(), name, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_6);
        }
    }

    private SettlementRaids() {
    }

    /** 这架战机属于一场还在打的聚落来袭。 */
    public static boolean belongs(PirateFighter fighter) {
        for (Raid raid : RAIDS.values()) {
            if (raid.fighters.contains(fighter)) {
                return true;
            }
        }
        return false;
    }

    /** 战机被打下来了。 */
    public static void onDestroyed(PirateFighter fighter) {
        for (Raid raid : RAIDS.values()) {
            raid.fighters.remove(fighter);
        }
    }

    static boolean active(String settlement) {
        return RAIDS.containsKey(settlement);
    }

    /** 还剩几架；没在打是 0。 */
    static int remaining(String settlement) {
        Raid raid = RAIDS.get(settlement);
        return raid == null ? 0 : raid.fighters.size();
    }

    static void clear() {
        for (Raid raid : RAIDS.values()) {
            raid.bar.removeAllPlayers();
        }
        RAIDS.clear();
    }

    /** 不打了（辞去总督、测试收尾）：战机撤走，不算输赢。 */
    static void cancel(String settlement) {
        Raid raid = RAIDS.remove(settlement);
        if (raid != null) {
            raid.bar.removeAllPlayers();
            raid.fighters.forEach(PirateFighter::leave);
        }
    }

    /** 来袭开始：总督在场就放战机，不在场按防御值算。 */
    static void begin(MinecraftServer server, @Nullable ServerLevel level, SettlementData.Entry entry, long now) {
        Settlement s = entry.settlement();
        ServerPlayer owner = Settlements.owner(server, s);
        Rng rng = Rng.of(Seeds.derive(s.seed(), "raid/" + now));
        if (level == null || owner == null || owner.level() != level || s.distance(owner.getX(), owner.getZ()) > SettlementRules.RAID_RANGE) {
            resolve(server, level, entry, s.resolveRaid(rng), false);
            return;
        }
        int count = s.raidFighters();
        Raid raid = new Raid(s.id(), owner.getUUID(), count, now, Component.translatable("bossbar.universe.settlement_raid", Settlements.displayName(entry)));
        double angle = rng.nextDouble() * Math.PI * 2;
        List<BlockPos> targets = targets(entry);
        for (int slot = 0; slot < count; slot++) {
            double a = angle + (slot - count / 2.0) * 0.25;
            double x = s.x() + Math.cos(a) * SPAWN_DISTANCE;
            double z = s.z() + Math.sin(a) * SPAWN_DISTANCE;
            double y = Math.max(s.y(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z)) + SPAWN_HEIGHT;
            BlockPos target = targets.get(slot % targets.size());
            PirateFighter fighter = PirateFighter.settlementRaider(level, owner.getUUID(), slot, count, new Vec3(x, y, z), Vec3.atCenterOf(target));
            if (level.addFreshEntity(fighter)) {
                raid.fighters.add(fighter);
            }
        }
        if (raid.fighters.isEmpty()) {
            resolve(server, level, entry, s.resolveRaid(rng), false);
            return;
        }
        RAIDS.put(s.id(), raid);
        raid.bar.addPlayer(owner);
        level.playSound(null, s.x(), s.y() + 2, s.z(), SettlementSounds.RAID_ALARM.get(), SoundSource.HOSTILE, 2.0F, 1.0F);
        Settlements.chat(owner, Component.translatable("message.universe.settlement.raid_start", Settlements.displayName(entry), count,
                SettlementRules.RAID_TICKS / 1200).withStyle(ChatFormatting.RED));
    }

    /**
     * 给游戏内测试用：不等总督，从 {@code from} 放 {@code count} 架扫射这个聚落（假玩家不在玩家表里，算不在场）。返回放出来的战机。
     */
    public static List<PirateFighter> testRaid(ServerLevel level, SettlementData.Entry entry, int count, Vec3 from) {
        Settlement s = entry.settlement();
        long now = Settlements.now(level.getServer());
        Raid raid = new Raid(s.id(), s.owner().orElse(UUID.randomUUID()), count, now, Component.literal(s.id()));
        raid.test = true;
        List<BlockPos> targets = targets(entry);
        for (int slot = 0; slot < count; slot++) {
            PirateFighter fighter = PirateFighter.settlementRaider(level, raid.owner, slot, count, from.add(slot * 4, 0, 0),
                    Vec3.atCenterOf(targets.get(slot % targets.size())));
            if (level.addFreshEntity(fighter)) {
                raid.fighters.add(fighter);
            }
        }
        RAIDS.put(s.id(), raid);
        return List.copyOf(raid.fighters);
    }

    /** 战机扫射的地方：开着的建筑，没有就打广场。 */
    private static List<BlockPos> targets(SettlementData.Entry entry) {
        Settlement s = entry.settlement();
        List<BlockPos> targets = new ArrayList<>();
        for (int i = 0; i < s.plotCount(); i++) {
            if (s.plot(i).type() != null && entry.plotY()[i] != Integer.MIN_VALUE) {
                targets.add(SettlementBuilder.center(entry, i));
            }
        }
        if (targets.isEmpty()) {
            targets.add(new BlockPos(s.x(), s.y() + 1, s.z()));
        }
        return targets;
    }

    static void tick(MinecraftServer server, long now) {
        if (RAIDS.isEmpty()) {
            return;
        }
        SettlementData data = SettlementData.get(server);
        for (Raid raid : List.copyOf(RAIDS.values())) {
            SettlementData.Entry entry = data.entry(raid.settlement).orElse(null);
            if (entry == null || !entry.settlement().owned()) {
                cancel(raid.settlement);
                continue;
            }
            Settlement s = entry.settlement();
            ServerLevel level = Settlements.level(server, s);
            raid.fighters.removeIf(fighter -> fighter.isRemoved() || !fighter.isAlive());
            if (level == null) {
                cancel(raid.settlement);
                continue;
            }
            ServerPlayer owner = server.getPlayerList().getPlayer(raid.owner);
            boolean present = owner != null && owner.level() == level && s.distance(owner.getX(), owner.getZ()) <= SettlementRules.RAID_RANGE * 1.5;
            raid.absent = present || raid.test ? 0 : raid.absent + 1;
            raid.bar.setProgress(raid.fighters.size() / (float) raid.total);
            if (raid.fighters.isEmpty()) {
                finish(server, level, entry, raid, true);
                continue;
            }
            if (raid.absent > ABSENT_TICKS) {
                // 总督走了：剩下的按防御值算
                Rng rng = Rng.of(Seeds.derive(s.seed(), "raid/absent/" + now));
                boolean won = SettlementRules.defended(s.defense(), SettlementRules.strength(raid.fighters.size(), s.threat()), rng.nextDouble());
                finish(server, level, entry, raid, won);
                continue;
            }
            if (now - raid.start > SettlementRules.RAID_TICKS) {
                finish(server, level, entry, raid, false);
                continue;
            }
            if ((now - raid.start) % RETARGET == 0) {
                List<BlockPos> targets = targets(entry);
                for (int k = 0; k < raid.fighters.size(); k++) {
                    raid.fighters.get(k).strafe(Vec3.atCenterOf(targets.get((k + (int) ((now - raid.start) / RETARGET)) % targets.size())));
                }
            }
            if ((now - raid.start) % TURRET_INTERVAL == 0) {
                turrets(level, entry, raid, owner);
            }
        }
    }

    /** 每座开着的炮台朝最近的一架开一炮，炮塔跟着转。 */
    private static void turrets(ServerLevel level, SettlementData.Entry entry, Raid raid, @Nullable ServerPlayer owner) {
        Settlement s = entry.settlement();
        for (int i = 0; i < s.plotCount(); i++) {
            Plot plot = s.plot(i);
            if (!plot.active() || plot.type() != BuildingType.TURRET) {
                continue;
            }
            BlockPos at = SettlementBuilder.turret(entry, i);
            BlockState state = level.getBlockState(at);
            if (!state.is(UniverseBlocks.SETTLEMENT_TURRET.get())) {
                continue;
            }
            Vec3 muzzle = Vec3.atCenterOf(at).add(0, 0.2, 0);
            PirateFighter nearest = null;
            double best = TURRET_RANGE * TURRET_RANGE;
            for (PirateFighter fighter : raid.fighters) {
                double d = fighter.getBoundingBox().getCenter().distanceToSqr(muzzle);
                if (d < best) {
                    best = d;
                    nearest = fighter;
                }
            }
            if (nearest == null) {
                continue;
            }
            Vec3 to = nearest.getBoundingBox().getCenter().subtract(muzzle);
            double[] lead = ShipCombat.lead(new double[] {to.x, to.y, to.z},
                    new double[] {nearest.getDeltaMovement().x, nearest.getDeltaMovement().y, nearest.getDeltaMovement().z}, TURRET_BOLT_SPEED);
            Vec3 direction = new Vec3(lead[0], lead[1], lead[2]);
            Direction facing = Direction.getApproximateNearest(direction.x, 0, direction.z);
            if (facing.getAxis().isHorizontal() && state.getValue(HorizontalDirectionalBlock.FACING) != facing) {
                level.setBlock(at, state.setValue(HorizontalDirectionalBlock.FACING, facing), 2);
            }
            float damage = (float) (TURRET_DAMAGE * BuildingType.factor(plot.grade()));
            PlasmaBolt bolt = PlasmaBolt.shoot(level, owner != null ? owner : nearest, muzzle.add(direction.normalize().scale(0.8)), direction, damage,
                    TURRET_COLOR, TURRET_BOLT_SPEED, 2.0F, 40);
            if (owner == null) {
                bolt.setOwner(null);
            }
            level.addFreshEntity(bolt);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, muzzle.x, muzzle.y, muzzle.z, 4, 0.2, 0.2, 0.2, 0.05);
            level.playSound(null, at, SettlementSounds.TURRET_FIRE.get(), SoundSource.BLOCKS, 1.0F, 0.95F + level.getRandom().nextFloat() * 0.1F);
        }
    }

    private static void finish(MinecraftServer server, ServerLevel level, SettlementData.Entry entry, Raid raid, boolean won) {
        RAIDS.remove(raid.settlement);
        raid.bar.removeAllPlayers();
        raid.fighters.forEach(PirateFighter::leave);
        Settlement s = entry.settlement();
        Settlement.RaidOutcome outcome = s.finishRaid(won, raid.total, Rng.of(Seeds.derive(s.seed(), "raid/end/" + raid.start)));
        resolve(server, level, entry, outcome, true);
    }

    /** 结果落下来：给信用点、盖坏两座、告诉总督。 */
    private static void resolve(MinecraftServer server, @Nullable ServerLevel level, SettlementData.Entry entry, Settlement.RaidOutcome outcome,
            boolean fought) {
        Settlement s = entry.settlement();
        ServerPlayer owner = Settlements.owner(server, s);
        if (outcome.won()) {
            if (owner != null && fought) {
                Credits.add(owner, outcome.reward());
            } else {
                s.addTreasury(outcome.reward());
            }
        }
        if (level != null) {
            for (int index : outcome.damaged()) {
                SettlementBuilder.sync(level, entry, index, Settlements.now(server), true);
                BlockPos c = SettlementBuilder.center(entry, index);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, 30, 2.0, 1.5, 2.0, 0.02);
            }
            level.playSound(null, s.x(), s.y() + 2, s.z(), outcome.won() ? SettlementSounds.RAID_WON.get() : SettlementSounds.RAID_LOST.get(),
                    SoundSource.NEUTRAL, 1.5F, 1.0F);
        }
        SettlementData.get(server).changed();
        Component name = Settlements.displayName(entry);
        Component message = outcome.won()
                ? Component.translatable(fought ? "message.universe.settlement.raid_won" : "message.universe.settlement.raid_won_away", name,
                        Credits.format(outcome.reward())).withStyle(ChatFormatting.GREEN)
                : Component.translatable(fought ? "message.universe.settlement.raid_lost" : "message.universe.settlement.raid_lost_away", name,
                        outcome.damaged().size(), (int) Math.round(outcome.stolen())).withStyle(ChatFormatting.RED);
        Settlements.chat(owner, message);
    }
}
