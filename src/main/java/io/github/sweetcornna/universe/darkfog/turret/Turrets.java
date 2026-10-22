package io.github.sweetcornna.universe.darkfog.turret;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.darkfog.AmmoKind;
import io.github.sweetcornna.universe.darkfog.DarkFogEnemy;
import io.github.sweetcornna.universe.entity.PlasmaBolt;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

/**
 * 炮塔共用的东西（ADR-0028 §五）：认弹药、只伤黑雾的伤害和范围伤害、物品能力（分拣器、传送带、漏斗往弹药格里送）。
 */
public final class Turrets {
    private Turrets() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(RegisterCapabilitiesEvent.class, Turrets::registerCapabilities);
    }

    private static List<Supplier<? extends BlockEntityType<TurretBlockEntity>>> types() {
        return List.of(UniverseBlockEntities.GAUSS_TURRET, UniverseBlockEntities.LASER_TURRET, UniverseBlockEntities.IMPLOSION_CANNON,
                UniverseBlockEntities.MISSILE_TURRET, UniverseBlockEntities.PLASMA_TURRET, UniverseBlockEntities.SR_PLASMA_TURRET);
    }

    /** 游戏内测试用：带这个标签的黑雾按高空目标算（没有真的从太空下来的枪骑也能测炮塔挑目标）。 */
    public static final String TEST_HIGH_ALTITUDE_TAG = "universe.test_high_altitude";

    /** 这个黑雾是不是高空目标。 */
    static boolean highAltitude(Entity entity) {
        return entity instanceof DarkFogEnemy enemy && (enemy.highAltitude() || entity.entityTags().contains(TEST_HIGH_ALTITUDE_TAG));
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (Supplier<? extends BlockEntityType<TurretBlockEntity>> type : types()) {
            event.registerBlockEntity(Capabilities.Item.BLOCK, type.get(), (turret, side) -> new WorldlyContainerWrapper(turret, side));
        }
    }

    /** 这个物品是哪种弹药。 */
    public static Optional<AmmoKind> ammoOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        if (stack.getItem() instanceof AmmoItem ammo) {
            return Optional.of(ammo.ammo());
        }
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key.getNamespace().equals(Universe.MOD_ID) ? AmmoKind.byId(key.getPath()) : Optional.empty();
    }

    /** 炮塔的伤害：没有攻击者，算等离子伤害（不带原版的击退）。 */
    static DamageSource source(ServerLevel level, @Nullable Entity direct) {
        return level.damageSources().source(PlasmaBolt.DAMAGE, direct, null);
    }

    /**
     * 打一下黑雾：不管上次挨打过了多久都算（炮塔打得比原版的受伤冷却快）。
     *
     * @return 伤害生效了
     */
    static boolean damage(ServerLevel level, LivingEntity target, float damage) {
        if (!(target instanceof DarkFogEnemy) || !target.isAlive()) {
            return false;
        }
        PlasmaBolt.clearHurtCooldown(target);
        return target.hurtServer(level, source(level, null), damage);
    }

    /**
     * 范围伤害：只伤黑雾，离爆心越远伤害越低；推开（引力导弹是吸过来）。
     *
     * @return 炸死了几个
     */
    static int splash(ServerLevel level, Vec3 center, AmmoKind ammo, @Nullable Entity direct) {
        return splash(level, center, ammo.damage(), ammo.radius(), ammo.push(), direct);
    }

    /** 同上，按爆心伤害、半径、推力算（近程等离子炮塔不用弹药）。 */
    static int splash(ServerLevel level, Vec3 center, float centerDamage, double radius, double push, @Nullable Entity direct) {
        int killed = 0;
        for (Mob mob : TurretBlockEntity.enemies(level, new AABB(center, center).inflate(radius + 2))) {
            double distance = Math.sqrt(mob.getBoundingBox().distanceToSqr(center));
            float damage = AmmoKind.splash(centerDamage, radius, distance);
            if (damage <= 0) {
                continue;
            }
            PlasmaBolt.clearHurtCooldown(mob);
            if (!mob.hurtServer(level, source(level, direct), damage)) {
                continue;
            }
            if (!mob.isAlive()) {
                killed++;
                continue;
            }
            double resistance = mob.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            Vec3 away = mob.getBoundingBox().getCenter().subtract(center);
            if (resistance < 1 && away.lengthSqr() > 1e-6 && push != 0) {
                double strength = push * (1 - distance / radius) * (1 - resistance);
                Vec3 shove = away.normalize().scale(strength);
                mob.push(new Vec3(shove.x, push > 0 ? Math.abs(strength) * 0.3 : shove.y, shove.z));
            }
        }
        return killed;
    }

    /** 炮弹、导弹打死了东西：记在开火的炮塔上（炮塔还在的话）。 */
    static void creditKills(ServerLevel level, @Nullable BlockPos turret, int kills) {
        if (turret != null && kills > 0 && level.isLoaded(turret) && level.getBlockEntity(turret) instanceof TurretBlockEntity blockEntity) {
            for (int i = 0; i < kills; i++) {
                blockEntity.creditKill();
            }
        }
    }
}
