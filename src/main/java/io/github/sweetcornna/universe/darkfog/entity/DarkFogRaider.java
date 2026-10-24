package io.github.sweetcornna.universe.darkfog.entity;

import io.github.sweetcornna.universe.core.darkfog.Tactics;
import io.github.sweetcornna.universe.core.darkfog.UnitKind;
import io.github.sweetcornna.universe.darkfog.Facilities;
import io.github.sweetcornna.universe.entity.PlasmaBolt;
import io.github.sweetcornna.universe.registry.sound.HostileSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** 突袭者：贴地悬浮，慢，贴上去放近身电弧，参加进攻。 */
public class DarkFogRaider extends DarkFogUnit {
    /** 电弧亮多少刻。 */
    private static final int ARC_TICKS = 6;

    public DarkFogRaider(EntityType<? extends DarkFogRaider> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, UnitKind.RAIDER.baseHealth())
                .add(Attributes.ATTACK_DAMAGE, UnitKind.RAIDER.baseDamage())
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FLYING_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    @Override
    public UnitKind kind() {
        return UnitKind.RAIDER;
    }

    @Override
    public boolean grounded() {
        return true;
    }

    @Override
    protected Vec3 engagePosition(ServerLevel level, Vec3 aim) {
        // 停在目标跟前一格半，和目标的中间差不多高
        Vec3 offset = this.position().subtract(aim).multiply(1, 0, 1);
        Vec3 back = offset.lengthSqr() < 1e-4 ? Vec3.ZERO : offset.normalize().scale(1.6);
        return new Vec3(aim.x + back.x, aim.y - 0.4, aim.z + back.z);
    }

    @Override
    protected void strike(ServerLevel level, LivingEntity enemy, float damage) {
        PlasmaBolt.clearHurtCooldown(enemy);
        enemy.hurtServer(level, DarkFogDamage.source(level, this, this), damage);
        this.showArc(enemy, null, ARC_TICKS);
        this.zap(level, enemy.position());
    }

    @Override
    protected void strikeBlock(ServerLevel level, BlockPos pos, float damage) {
        Facilities.damage(level, pos, (float) (damage * Tactics.FACILITY_DAMAGE), this);
        this.showArc(null, pos, ARC_TICKS);
        this.zap(level, Vec3.atCenterOf(pos));
    }

    private void zap(ServerLevel level, Vec3 at) {
        level.playSound(null, at.x, at.y, at.z, HostileSounds.RAIDER_ZAP.get(), SoundSource.HOSTILE, 0.7F, 0.95F + this.random.nextFloat() * 0.1F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.2);
    }
}
