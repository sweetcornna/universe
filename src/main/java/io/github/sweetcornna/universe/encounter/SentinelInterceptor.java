package io.github.sweetcornna.universe.encounter;

import java.util.UUID;

import io.github.sweetcornna.universe.core.sentinel.SentinelStrength;
import io.github.sweetcornna.universe.core.ship.PirateFlight;
import io.github.sweetcornna.universe.core.space.Encounters;
import io.github.sweetcornna.universe.pirate.PirateFighter;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.rules.UniverseRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 太空守卫机的拦截机（ADR-0024）：星球上三星通缉还飞上太空、或者朝遇险的货船开火，就从身后追上来。飞法和海盗战机一样，
 * 船体、护盾更厚，飞得快三成；打下来掉守卫机残骸，不给赏金。由 {@link SpaceEncounters} 生成，不存档。
 *
 * <p>建世界时定的守卫机强度（ADR-0028 §七）乘在架数、船体（挨的伤害除以强度）和炮火上。
 */
public class SentinelInterceptor extends PirateFighter {
    /** 守卫机的炮火：橙红色。 */
    public static final int BOLT_COLOR = 0xFF7A2A;

    public SentinelInterceptor(EntityType<? extends SentinelInterceptor> type, Level level) {
        super(type, level, Encounters.INTERCEPTOR_HULL, Encounters.INTERCEPTOR_SHIELD);
    }

    /** 服务端：追 {@code targetId} 的第 {@code slot} 架（一共 {@code count} 架）。 */
    static SentinelInterceptor create(ServerLevel level, UUID targetId, int slot, int count, Vec3 pos, Vec3 velocity) {
        SentinelInterceptor interceptor = new SentinelInterceptor(UniverseEntities.SENTINEL_INTERCEPTOR.get(), level);
        interceptor.launch(targetId, new PirateFlight(level.getRandom().nextLong(), slot, count, Encounters.INTERCEPTOR_SPEED), pos, velocity);
        return interceptor;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return super.hurtServer(level, source, (float) (damage / SentinelStrength.scale(1, UniverseRules.sentinelStrength(level.getServer()))));
    }

    @Override
    protected float boltDamage(ServerLevel level) {
        return (float) SentinelStrength.scale(super.boltDamage(level), UniverseRules.sentinelStrength(level.getServer()));
    }

    @Override
    protected void destroyed(ServerLevel level, DamageSource source) {
        SpaceEncounters.onInterceptorDestroyed(this, level, source);
    }

    @Override
    protected boolean belongs() {
        return SpaceEncounters.belongs(this);
    }

    @Override
    protected int boltColor() {
        return BOLT_COLOR;
    }
}
