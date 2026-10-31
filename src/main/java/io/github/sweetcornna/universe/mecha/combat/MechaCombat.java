package io.github.sweetcornna.universe.mecha.combat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.darkfog.AmmoKind;
import io.github.sweetcornna.universe.core.darkfog.Support;
import io.github.sweetcornna.universe.core.mecha.combat.MechaWeapon;
import io.github.sweetcornna.universe.core.mecha.combat.ShieldBurst;
import io.github.sweetcornna.universe.core.mecha.combat.WeaponMode;
import io.github.sweetcornna.universe.core.research.Upgrades;
import io.github.sweetcornna.universe.core.survival.Shield;
import io.github.sweetcornna.universe.core.tech.Stat;
import io.github.sweetcornna.universe.darkfog.DarkFogEnemy;
import io.github.sweetcornna.universe.darkfog.defense.ThrownUnit;
import io.github.sweetcornna.universe.darkfog.turret.Turrets;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.mecha.Mecha;
import io.github.sweetcornna.universe.mecha.MechaSettings;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.sound.GearSounds;
import io.github.sweetcornna.universe.registry.sound.HostileSounds;
import io.github.sweetcornna.universe.tech.ExosuitData;
import io.github.sweetcornna.universe.tech.SuitTech;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 机甲的战斗（ADR-0033 §二）：能量护盾用核心的电回充、护盾爆发、胸甲自带的三门武器，和扔干扰胶囊、水瓶。
 *
 * <p>武器每刻看一次：模式不是"关"、没按住潜行、核心有电时，挑一个目标（自动：射程里最近的敌人，正在打自己的优先；
 * 瞄准：准星对着的那个），从背包、货舱里拿这门武器用的炮塔弹药打出去。一个弹药物品打完了才拿下一个，打剩的发数记在这里
 * （不存档，下线时丢了也就半盒）。
 */
public final class MechaCombat {
    private static final Map<UUID, State> STATES = new HashMap<>();
    /** 枪口在右肩：从眼睛往右、往下这么多格。 */
    private static final double SHOULDER_RIGHT = 0.45;
    private static final double SHOULDER_DOWN = 0.35;
    private static final int TRACER = 0xFFD98A;
    /** 三门武器里最远的射程：每刻按它查一次附近的实体。 */
    private static final double MAX_RANGE = java.util.Arrays.stream(MechaWeapon.values()).mapToDouble(MechaWeapon::range).max().orElse(0);
    /** 扔出去的干扰胶囊：这么多格内的黑雾按干扰塔的胶囊减速这么多刻。 */
    public static final double JAM_RADIUS = 6;
    public static final int JAM_TICKS = 160;
    /** 扔出去的水：这么多格内灭火。 */
    public static final double WATER_RADIUS = 3;

    private static final class State {
        final int[] cooldown = new int[MechaWeapon.values().length];
        final @Nullable AmmoKind[] loaded = new AmmoKind[MechaWeapon.values().length];
        final int[] rounds = new int[MechaWeapon.values().length];
        long burstStart = -1;
        long burstReady = Long.MIN_VALUE;
        int attacker = -1;
        long attackedAt = Long.MIN_VALUE;
    }

    private MechaCombat() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                long start = ServerPerf.begin();
                tickWeapons(player);
                ServerPerf.end("player/mecha_weapons", start);
            }
        });
        // 记下谁在打自己：自动模式先打它
        gameBus.addListener(LivingIncomingDamageEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player && event.getSource().getEntity() instanceof LivingEntity attacker
                    && Strikes.hostile(attacker, player)) {
                State state = state(player);
                state.attacker = attacker.getId();
                state.attackedAt = player.level().getGameTime();
            }
        });
        gameBus.addListener(PlayerInteractEvent.RightClickItem.class, MechaCombat::onRightClickItem);
        gameBus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> STATES.remove(event.getEntity().getUUID()));
        gameBus.addListener(ServerStoppedEvent.class, event -> STATES.clear());
    }

    private static State state(Player player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new State());
    }

    // ---- 能量护盾

    /** 升级"能量护盾"加的容量（穿着外骨骼胸甲时才有）。 */
    public static float shieldBonus(Player player) {
        return Mecha.chest(player) ? Upgrades.energyShield(Research.level(player.level(), Upgrades.ENERGY_SHIELD)) : 0;
    }

    /**
     * 护盾的一刻（{@code SuitTech} 每刻调）：模块那部分照旧自己回充；不满时机甲核心有电就再用电回充，每点 200 焦，
     * 每秒最多 4 点，被打以后一样等 5 秒。
     */
    public static void tickShield(ServerPlayer player) {
        Shield shield = player.getData(UniverseAttachments.EXOSUIT).shield();
        shield.tick((float) SuitTech.suit(player).get(Stat.SHIELD), shieldBonus(player));
        if (player.isCreative()) {
            shield.refill();
            return;
        }
        if (!Mecha.chest(player) || !shield.recharging()) {
            return;
        }
        double energy = player.getData(UniverseAttachments.MECHA).energy().energy();
        float points = Shield.corePoints(energy);
        if (points > 0 && Mecha.draw(player, points * Shield.CORE_JOULES_PER_POINT)) {
            shield.recharge(points);
        }
    }

    // ---- 护盾爆发

    /** 客户端按下、松开了护盾爆发键。 */
    public static void handleBurst(CombatPayloads.Burst payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()) {
            return;
        }
        State state = state(player);
        long now = player.level().getGameTime();
        if (payload.pressed()) {
            state.burstStart = now >= state.burstReady ? now : -1;
            return;
        }
        if (state.burstStart < 0) {
            return;
        }
        int charge = (int) Math.min(ShieldBurst.MAX_CHARGE, now - state.burstStart);
        state.burstStart = -1;
        boolean fired = burst(player, charge);
        if (player.connection != null) {
            PacketDistributor.sendToPlayer(player, new CombatPayloads.BurstState(fired, fired ? ShieldBurst.COOLDOWN : 0));
        }
    }

    /**
     * 放一次护盾爆发：用掉当前护盾的一半（按蓄力的比例），半径里的黑雾和怪物挨用掉的 × 2 的伤害，被推开。
     *
     * @param chargeTicks 蓄了几刻
     * @return 放出来了（冷却没好、没穿胸甲、护盾太少、蓄得太短都放不出）
     */
    public static boolean burst(ServerPlayer player, int chargeTicks) {
        State state = state(player);
        ServerLevel level = player.level();
        long now = level.getGameTime();
        if (now < state.burstReady || !Mecha.chest(player)) {
            return false;
        }
        Shield shield = player.getData(UniverseAttachments.EXOSUIT).shield();
        float spent = ShieldBurst.spent(shield.value(), ShieldBurst.charge(chargeTicks));
        if (!ShieldBurst.fires(chargeTicks, spent)) {
            return false;
        }
        shield.spend(spent);
        state.burstReady = now + ShieldBurst.COOLDOWN;
        boolean space = SpaceDimensions.isSpace(level.dimension());
        double radius = ShieldBurst.radius(space);
        float damage = ShieldBurst.damage(spent, space);
        Vec3 center = player.getBoundingBox().getCenter();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius + 2),
                entity -> Strikes.hostile(entity, player))) {
            double distance = Math.sqrt(target.getBoundingBox().distanceToSqr(center));
            if (distance > radius) {
                continue;
            }
            Strikes.hit(level, target, damage, player, player);
            if (target.isAlive()) {
                Strikes.shove(target, center, ShieldBurst.knockback(distance, radius));
            }
        }
        burstEffect(level, center, radius);
        return true;
    }

    /** 一圈往外扩的光：几层粒子环，中间一闪。 */
    private static void burstEffect(ServerLevel level, Vec3 center, double radius) {
        DustParticleOptions blue = new DustParticleOptions(0x7FD8FF, 1.4F);
        int points = (int) Math.min(160, 24 + radius * 8);
        for (int ring = 1; ring <= 3; ring++) {
            double r = radius * ring / 3.0;
            for (int i = 0; i < points; i++) {
                double a = Math.PI * 2 * i / points;
                level.sendParticles(blue, center.x + Math.cos(a) * r, center.y - 0.3 + ring * 0.15, center.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
        }
        level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF9FE8FF), center.x, center.y, center.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 30, radius * 0.3, 0.4, radius * 0.3, 0.6);
        level.playSound(null, center.x, center.y, center.z, GearSounds.SHIELD_BREAK.get(), SoundSource.PLAYERS, 1.2F, 0.6F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.5F, 1.6F);
    }

    /** 护盾爆发还要冷却几刻（GameTest 用）。 */
    public static int burstCooldown(Player player) {
        State state = STATES.get(player.getUUID());
        return state == null ? 0 : (int) Math.max(0, state.burstReady - player.level().getGameTime());
    }

    // ---- 武器

    private static void tickWeapons(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        if (state != null) {
            for (int i = 0; i < state.cooldown.length; i++) {
                if (state.cooldown[i] > 0) {
                    state.cooldown[i]--;
                }
            }
        }
        if (!player.isAlive() || player.isSpectator() || player.isShiftKeyDown() || !Mecha.chest(player) || !Mecha.powered(player)) {
            return;
        }
        MechaSettings settings = player.getData(UniverseAttachments.MECHA_SETTINGS);
        List<LivingEntity> nearby = null;
        for (MechaWeapon weapon : MechaWeapon.values()) {
            WeaponMode mode = settings.mode(weapon);
            if (mode == WeaponMode.OFF || state != null && state.cooldown[weapon.ordinal()] > 0) {
                continue;
            }
            // 没带这种弹药的武器不找目标
            if (!hasAmmo(player, weapon, state)) {
                continue;
            }
            // 附近的实体这一刻只查一次（按最远的射程），每门武器再按自己的射程挑
            if (nearby == null) {
                nearby = player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(MAX_RANGE),
                        entity -> Strikes.hostile(entity, player));
            }
            LivingEntity target = mode == WeaponMode.AUTO ? autoTarget(player, weapon, nearby) : aimedTarget(player, weapon, nearby);
            if (target != null) {
                fire(player, weapon, target);
            }
        }
    }

    /** 开一发（GameTest 也直接调）：拿弹药、扣电、打出去。 */
    public static boolean fire(ServerPlayer player, MechaWeapon weapon, LivingEntity target) {
        State state = state(player);
        AmmoKind ammo = load(player, weapon, state);
        if (ammo == null || !Mecha.draw(player, MechaWeapon.SHOT_JOULES)) {
            return false;
        }
        state.rounds[weapon.ordinal()]--;
        state.cooldown[weapon.ordinal()] = weapon.interval();
        ServerLevel level = player.level();
        Vec3 muzzle = muzzle(player);
        Vec3 aim = Strikes.aimPoint(target);
        float damage = weapon.damage(ammo);
        switch (weapon) {
            case KINETIC -> {
                Strikes.hit(level, target, damage, player, player);
                Strikes.tracer(level, muzzle, aim, TRACER);
                level.playSound(null, muzzle.x, muzzle.y, muzzle.z, HostileSounds.GAUSS.get(), SoundSource.PLAYERS, 0.35F,
                        1.3F + level.getRandom().nextFloat() * 0.15F);
            }
            case MISSILE -> {
                MechaMissile.fire(level, player, muzzle.add(0, 0.4, 0), target, ammo, damage);
                level.playSound(null, muzzle.x, muzzle.y, muzzle.z, HostileSounds.MISSILE.get(), SoundSource.PLAYERS, 0.6F, 1.2F);
            }
            case PLASMA -> {
                double speed = ammo.speed();
                Vec3 lead = aim.add(target.getDeltaMovement().scale(muzzle.distanceTo(aim) / speed));
                PlasmaShot.fire(level, player, null, muzzle, lead, damage, ammo.radius(), speed,
                        ammo == AmmoKind.ANTIMATTER_CAPSULE ? PlasmaShot.ANTIMATTER : PlasmaShot.PLASMA);
                level.playSound(null, muzzle.x, muzzle.y, muzzle.z, HostileSounds.PLASMA.get(), SoundSource.PLAYERS, 0.6F, 1.2F);
            }
        }
        return true;
    }

    private static Vec3 muzzle(Player player) {
        Vec3 eye = player.getEyePosition();
        double yaw = Math.toRadians(player.getYRot());
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        return eye.add(right.scale(SHOULDER_RIGHT)).add(0, -SHOULDER_DOWN, 0);
    }

    /** 自动：射程里最近的敌人，最近 5 秒打过自己的优先；要看得见，等离子炮在星球上要机甲飞着或者打高空的。 */
    private static @Nullable LivingEntity autoTarget(ServerPlayer player, MechaWeapon weapon, List<LivingEntity> nearby) {
        ServerLevel level = player.level();
        boolean space = SpaceDimensions.isSpace(level.dimension());
        Vec3 eye = player.getEyePosition();
        State state = STATES.get(player.getUUID());
        if (state != null && state.attacker >= 0 && level.getGameTime() - state.attackedAt <= MechaWeapon.ATTACKER_MEMORY
                && level.getEntity(state.attacker) instanceof LivingEntity attacker && usable(player, weapon, attacker, space, eye)) {
            return attacker;
        }
        LivingEntity best = null;
        double bestDistance = weapon.range();
        AABB reach = player.getBoundingBox().inflate(weapon.range());
        for (LivingEntity entity : nearby) {
            if (!entity.getBoundingBox().intersects(reach) || !Strikes.hostile(entity, player) || Strikes.building(entity)) {
                continue;
            }
            double distance = Math.sqrt(entity.getBoundingBox().distanceToSqr(eye));
            if (distance < bestDistance && usable(player, weapon, entity, space, eye)) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    /** 瞄准：准星 6° 以内、离准星最近的敌人（黑雾的建筑也算；大个子按碰撞箱的半径放宽）。 */
    private static @Nullable LivingEntity aimedTarget(ServerPlayer player, MechaWeapon weapon, List<LivingEntity> nearby) {
        ServerLevel level = player.level();
        boolean space = SpaceDimensions.isSpace(level.dimension());
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double cone = Math.toRadians(MechaWeapon.AIM_CONE);
        LivingEntity best = null;
        double bestAngle = Double.MAX_VALUE;
        AABB reach = player.getBoundingBox().inflate(weapon.range());
        for (LivingEntity entity : nearby) {
            if (!entity.getBoundingBox().intersects(reach) || !Strikes.hostile(entity, player)) {
                continue;
            }
            Vec3 to = Strikes.aimPoint(entity).subtract(eye);
            double length = to.length();
            if (length < 1e-3) {
                continue;
            }
            double angle = Math.acos(Math.clamp(to.dot(look) / length, -1, 1)) - Math.atan2(entity.getBbWidth() * 0.5, length);
            if (angle <= cone && angle < bestAngle && usable(player, weapon, entity, space, eye)) {
                best = entity;
                bestAngle = angle;
            }
        }
        return best;
    }

    private static boolean usable(ServerPlayer player, MechaWeapon weapon, LivingEntity target, boolean space, Vec3 eye) {
        if (!target.isAlive() || target.level() != player.level()) {
            return false;
        }
        if (Math.sqrt(target.getBoundingBox().distanceToSqr(eye)) > weapon.range()) {
            return false;
        }
        boolean high = target instanceof DarkFogEnemy enemy && (enemy.highAltitude() || target.entityTags().contains(Turrets.TEST_HIGH_ALTITUDE_TAG));
        return weapon.usable(space, high || player.getAbilities().flying) && Strikes.clear(player.level(), eye, Strikes.aimPoint(target), player);
    }

    /** 这门武器有没有弹药：装着的还有发数，或者背包、货舱里有能用的。 */
    private static boolean hasAmmo(ServerPlayer player, MechaWeapon weapon, @Nullable State state) {
        if (state != null && state.rounds[weapon.ordinal()] > 0) {
            return true;
        }
        return findAmmo(player, weapon) != null;
    }

    /** 装着的打完了就从背包（先快捷栏）、货舱里拿一个。 */
    private static @Nullable AmmoKind load(ServerPlayer player, MechaWeapon weapon, State state) {
        int i = weapon.ordinal();
        if (state.rounds[i] > 0 && state.loaded[i] != null) {
            return state.loaded[i];
        }
        ItemStack stack = findAmmo(player, weapon);
        if (stack == null) {
            return null;
        }
        AmmoKind ammo = Turrets.ammoOf(stack).orElseThrow();
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        state.loaded[i] = ammo;
        state.rounds[i] = ammo.rounds();
        return ammo;
    }

    private static @Nullable ItemStack findAmmo(ServerPlayer player, MechaWeapon weapon) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            Optional<AmmoKind> ammo = Turrets.ammoOf(stack);
            if (ammo.isPresent() && weapon.accepts(ammo.get())) {
                return stack;
            }
        }
        ExosuitData exosuit = player.getData(UniverseAttachments.EXOSUIT);
        for (int slot = 0; slot < exosuit.cargoSlots(); slot++) {
            ItemStack stack = exosuit.cargo().get(slot);
            Optional<AmmoKind> ammo = Turrets.ammoOf(stack);
            if (ammo.isPresent() && weapon.accepts(ammo.get())) {
                return stack;
            }
        }
        return null;
    }

    /** 这门武器装着几发、身上还有几个弹药物品（面板显示）：{装着的发数, 背包和货舱里的个数}。 */
    public static int[] ammoCount(Player player, MechaWeapon weapon) {
        State state = STATES.get(player.getUUID());
        int loaded = state == null ? 0 : state.rounds[weapon.ordinal()];
        int items = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            items += count(inventory.getItem(slot), weapon);
        }
        ExosuitData exosuit = player.getData(UniverseAttachments.EXOSUIT);
        for (int slot = 0; slot < exosuit.cargoSlots(); slot++) {
            items += count(exosuit.cargo().get(slot), weapon);
        }
        return new int[] {loaded, items};
    }

    private static int count(ItemStack stack, MechaWeapon weapon) {
        Optional<AmmoKind> ammo = Turrets.ammoOf(stack);
        return ammo.isPresent() && weapon.accepts(ammo.get()) ? stack.getCount() : 0;
    }

    // ---- 投掷

    /**
     * 干扰胶囊右键扔出去（和燃烧单元、爆破单元一样，冷却 1 秒）。水瓶要穿着外骨骼胸甲按住潜行再右键才扔，
     * 不然照原版喝掉、拿去酿药、浇泥巴。
     */
    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        Player player = event.getEntity();
        if (!throwable(stack) || water(stack) && !(player.isShiftKeyDown() && Mecha.chest(player))) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (player.getCooldowns().isOnCooldown(stack)) {
            return;
        }
        Level level = event.getLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 0.6F,
                0.5F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.addFreshEntity(ThrownUnit.throwFrom(serverLevel, player, stack.copyWithCount(1)));
        }
        player.getCooldowns().addCooldown(stack, Support.THROW_COOLDOWN);
        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        stack.consume(1, player);
    }

    /** 能扔的：干扰胶囊和水瓶。 */
    public static boolean throwable(ItemStack stack) {
        return stack.is(UniverseItems.JAMMING_CAPSULE.get()) || water(stack);
    }

    public static boolean water(ItemStack stack) {
        return stack.is(Items.POTION) && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER);
    }
}
