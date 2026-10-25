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
 * 聚落离玩家超过模拟距离时区块加载着也不更新实体，战机停在那里一动不动、打不下来，所以也算不在场；打到一半有战机飞出模拟距离停住，也一样。
 * 只有打下来的才算数：战机不是打下来却没了（被指令清掉之类），剩下的连没了的也按防御值算。结果落到数值上（{@link Settlement#finishRaid}）：守住了给信用点，没守住两座建筑受损、储备被抢。
 *
 * <p>战机不存档；服务器重启时打到一半的来袭按没守住算，再排一次也不公平，所以这里按防御值补算。
 */
public final class SettlementRaids {
    /** 战机从多远的地方飞来，多高。 */
    private static final double SPAWN_DISTANCE = 140;
    private static final double SPAWN_HEIGHT = 50;
    /** 出发的地方不在实体更新的范围里时，往总督那边分几步拉近，最多拉近四分之三（再近就是在头顶上冒出来）。 */
    private static final int PULL_STEPS = 6;
    private static final double MAX_PULL = 0.75;
    /** 炮台的射程、隔多少刻打一发、一发的伤害、弹速。 */
    private static final double TURRET_RANGE = 72;
    private static final int TURRET_INTERVAL = 16;
    private static final float TURRET_DAMAGE = 5.0F;
    private static final double TURRET_BOLT_SPEED = 4.0;
    private static final int TURRET_COLOR = 0x6EDCF5;
    /** 总督走开多久算不在场。 */
    public static final int ABSENT_TICKS = 100;
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
        /** 游戏内测试放的：当总督的假玩家（不在玩家表里，直接按它站在哪算在不在场）。 */
        @Nullable ServerPlayer standIn;

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
        begin(server, level, entry, now, Settlements.owner(server, entry.settlement()));
    }

    /**
     * 总督在 {@value SettlementRules#RAID_RANGE} 格内、聚落中心和每架战机出发的地方都在实体更新的范围里，才放战机真打，返回这一场；
     * 不然按防御值算，返回 null。
     */
    private static @Nullable Raid begin(MinecraftServer server, @Nullable ServerLevel level, SettlementData.Entry entry, long now,
            @Nullable ServerPlayer owner) {
        Settlement s = entry.settlement();
        Rng rng = Rng.of(Seeds.derive(s.seed(), "raid/" + now));
        if (level == null || owner == null || owner.level() != level || s.distance(owner.getX(), owner.getZ()) > SettlementRules.RAID_RANGE
                || !ticking(level, s)) {
            resolve(server, level, entry, s.resolveRaid(rng), false);
            return null;
        }
        int count = s.raidFighters();
        double angle = rng.nextDouble() * Math.PI * 2;
        List<Vec3> from = new ArrayList<>();
        for (int slot = 0; slot < count; slot++) {
            Vec3 at = spawnPoint(level, s, angle + (slot - count / 2.0) * 0.25, owner);
            if (at == null) {
                resolve(server, level, entry, s.resolveRaid(rng), false);
                return null;
            }
            from.add(at);
        }
        Raid raid = new Raid(s.id(), owner.getUUID(), count, now, Component.translatable("bossbar.universe.settlement_raid", Settlements.displayName(entry)));
        List<BlockPos> targets = targets(entry);
        for (int slot = 0; slot < count; slot++) {
            BlockPos target = targets.get(slot % targets.size());
            PirateFighter fighter = PirateFighter.settlementRaider(level, owner.getUUID(), slot, count, from.get(slot), Vec3.atCenterOf(target));
            if (level.addFreshEntity(fighter)) {
                raid.fighters.add(fighter);
            }
        }
        if (raid.fighters.isEmpty()) {
            resolve(server, level, entry, s.resolveRaid(rng), false);
            return null;
        }
        RAIDS.put(s.id(), raid);
        if (owner.connection != null) {
            raid.bar.addPlayer(owner);
        }
        level.playSound(null, s.x(), s.y() + 2, s.z(), SettlementSounds.RAID_ALARM.get(), SoundSource.HOSTILE, 2.0F, 1.0F);
        Settlements.chat(owner, Component.translatable("message.universe.settlement.raid_start", Settlements.displayName(entry), count,
                SettlementRules.RAID_TICKS / 1200).withStyle(ChatFormatting.RED));
        return raid;
    }

    /** 聚落中心在实体更新的范围里（离哪个玩家在模拟距离以内）：战机、炮弹在这里才会动。 */
    private static boolean ticking(ServerLevel level, Settlement s) {
        return level.isPositionEntityTicking(new BlockPos(s.x(), s.y(), s.z()));
    }

    /**
     * 一架战机从哪里飞来：先放在 {@code angle} 方向 {@value #SPAWN_DISTANCE} 格外；那里不在实体更新的范围里（放过去也一动不动），
     * 就一步步往总督那边拉近。拉到最近还不行返回 null。
     */
    private static @Nullable Vec3 spawnPoint(ServerLevel level, Settlement s, double angle, ServerPlayer owner) {
        double farX = s.x() + Math.cos(angle) * SPAWN_DISTANCE;
        double farZ = s.z() + Math.sin(angle) * SPAWN_DISTANCE;
        for (int step = 0; step <= PULL_STEPS; step++) {
            double pull = MAX_PULL * step / PULL_STEPS;
            double x = farX + (owner.getX() - farX) * pull;
            double z = farZ + (owner.getZ() - farZ) * pull;
            BlockPos column = BlockPos.containing(x, s.y(), z);
            if (level.isPositionEntityTicking(column)) {
                double y = Math.max(s.y(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ())) + SPAWN_HEIGHT;
                return new Vec3(x, y, z);
            }
        }
        return null;
    }

    /**
     * 给游戏内测试用：不等总督，从 {@code from} 放 {@code count} 架扫射这个聚落（假玩家不在玩家表里，算不在场）。返回放出来的战机。
     */
    public static List<PirateFighter> testRaid(ServerLevel level, SettlementData.Entry entry, int count, Vec3 from) {
        return testRaid(level, entry, count, from, null);
    }

    /** 同上；{@code owner} 不为空时它当总督，照它站在哪、聚落中心在不在更新算在不在场。 */
    public static List<PirateFighter> testRaid(ServerLevel level, SettlementData.Entry entry, int count, Vec3 from, @Nullable ServerPlayer owner) {
        return testRaid(level, entry, count, from, owner, 0);
    }

    /** 同上；当作已经打了 {@code age} 刻。 */
    public static List<PirateFighter> testRaid(ServerLevel level, SettlementData.Entry entry, int count, Vec3 from, @Nullable ServerPlayer owner,
            long age) {
        Settlement s = entry.settlement();
        long now = Settlements.now(level.getServer());
        Raid raid = new Raid(s.id(), s.owner().orElse(UUID.randomUUID()), count, now - age, Component.literal(s.id()));
        raid.test = owner == null;
        raid.standIn = owner;
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

    /** 给游戏内测试用：到点开始来袭，{@code owner} 当总督。真打返回放出来的战机；按防御值算了返回空表。 */
    public static List<PirateFighter> testBegin(ServerLevel level, SettlementData.Entry entry, ServerPlayer owner) {
        MinecraftServer server = level.getServer();
        Raid raid = begin(server, level, entry, Settlements.now(server), owner);
        if (raid == null) {
            return List.of();
        }
        raid.standIn = owner;
        return List.copyOf(raid.fighters);
    }

    /** 给游戏内测试用：总督已经连着多少刻不在场；没在打返回 -1。 */
    public static int testAbsent(String settlement) {
        Raid raid = RAIDS.get(settlement);
        return raid == null ? -1 : raid.absent;
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
            // 打下来的已经由 onDestroyed 拿走了；还在表里却没了的是别的原因（被指令清掉之类），不算打下来。
            // 区块卸下时不存档的实体不会被移除，只是停着不动，下面按不在场算
            int before = raid.fighters.size();
            raid.fighters.removeIf(fighter -> fighter.isRemoved() || !fighter.isAlive());
            int lost = before - raid.fighters.size();
            if (level == null) {
                cancel(raid.settlement);
                continue;
            }
            ServerPlayer owner = raid.standIn != null ? raid.standIn : server.getPlayerList().getPlayer(raid.owner);
            // 聚落中心不在实体更新的范围里，战机停在半空不动，人在附近也打不下来：和走开一样，过一会儿按防御值算。
            // 战机飞出模拟距离（总督站得偏、模拟距离小）也一样停住，表空不了，所以每架都要在更新
            boolean present = owner != null && owner.level() == level && s.distance(owner.getX(), owner.getZ()) <= SettlementRules.RAID_RANGE * 1.5
                    && ticking(level, s) && raid.fighters.stream().allMatch(fighter -> level.isPositionEntityTicking(fighter.blockPosition()));
            raid.absent = present || raid.test ? 0 : raid.absent + 1;
            raid.bar.setProgress(raid.fighters.size() / (float) raid.total);
            boolean late = now - raid.start > SettlementRules.RAID_TICKS;
            if (lost == 0 && raid.fighters.isEmpty()) {
                // 全是打下来的：守住了，哪怕最后一架正好在总督走开满时间、刚到点的这一刻打下来
                finish(server, level, entry, raid, true);
                continue;
            }
            if (lost > 0 || raid.absent > ABSENT_TICKS || late && raid.absent > 0) {
                // 总督走了、战机停住了或者没了（到点时刚停住也算）：剩下的（连没了的）按防御值算
                Rng rng = Rng.of(Seeds.derive(s.seed(), "raid/absent/" + now));
                boolean won = SettlementRules.defended(s.defense(), SettlementRules.strength(raid.fighters.size() + lost, s.threat()), rng.nextDouble());
                finish(server, level, entry, raid, won);
                continue;
            }
            if (late) {
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
            // 不在实体更新范围里的炮台不开火：炮弹不会动，也不为了看一眼炮台把区块加载起来
            if (!level.isPositionEntityTicking(at)) {
                continue;
            }
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
