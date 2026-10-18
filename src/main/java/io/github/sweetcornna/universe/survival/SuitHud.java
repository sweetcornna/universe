package io.github.sweetcornna.universe.survival;

import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.survival.Environment;
import io.github.sweetcornna.universe.core.survival.LifeSupport;
import io.github.sweetcornna.universe.core.survival.Shield;
import io.github.sweetcornna.universe.core.survival.Suit;
import io.github.sweetcornna.universe.core.tech.Stat;
import io.github.sweetcornna.universe.core.tech.TechBonuses;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * 发给玩家自己的外骨骼读数，HUD 用。数值取到 0.1，变化了才同步。
 *
 * @param laser     采矿激光的热量（0–100）
 * @param flags     见 {@code NEEDS_OXYGEN} 等常量；第 8 位起是正在暴露的危害（按 {@link Hazard} 的序号）
 * @param shield    个人护盾的当前值和容量（没装护盾模块时都是 0）
 * @param jetTime   喷气背包时长的倍数（喷气推进器模块）
 * @param jetThrust 喷气背包推力的倍数
 */
public record SuitHud(float oxygen, float cold, float heat, float toxic, float radiation, float jet, float laser, int flags,
        float shield, float shieldMax, float jetTime, float jetThrust) {
    public static final int NEEDS_OXYGEN = 1;
    public static final int SEALED = 1 << 1;
    public static final int SHELTERED = 1 << 2;
    public static final int STORM = 1 << 3;
    public static final int SUPPLIED = 1 << 4;
    public static final int CHEST = 1 << 5;
    public static final int NEAR_HEAT = 1 << 6;
    public static final int NEAR_COLD = 1 << 7;
    private static final int EXPOSED_SHIFT = 8;
    public static final int OVERHEATED = 1 << 16;

    public static final SuitHud FULL = new SuitHud(LifeSupport.OXYGEN_MAX, LifeSupport.HAZARD_MAX, LifeSupport.HAZARD_MAX,
            LifeSupport.HAZARD_MAX, LifeSupport.HAZARD_MAX, LifeSupport.JET_MAX, 0, 0, 0, 0, 1, 1);
    public static final StreamCodec<RegistryFriendlyByteBuf, SuitHud> STREAM_CODEC = StreamCodec.ofMember(SuitHud::write, SuitHud::read);

    public static SuitHud of(LifeSupport support, Environment env, Suit suit, float laser, boolean overheated, Shield shield,
            TechBonuses bonuses) {
        int flags = 0;
        flags |= env.needsOxygen() ? NEEDS_OXYGEN : 0;
        flags |= suit.sealed() ? SEALED : 0;
        flags |= env.sheltered() ? SHELTERED : 0;
        flags |= env.storm() >= 2 ? STORM : 0;
        flags |= env.supplied() ? SUPPLIED : 0;
        flags |= suit.chest() ? CHEST : 0;
        flags |= env.nearHeat() ? NEAR_HEAT : 0;
        flags |= env.nearCold() ? NEAR_COLD : 0;
        flags |= overheated ? OVERHEATED : 0;
        for (Hazard hazard : Hazard.values()) {
            if (env.exposedTo(hazard)) {
                flags |= 1 << (EXPOSED_SHIFT + hazard.ordinal());
            }
        }
        return new SuitHud(round(support.oxygen()), round(support.protection(Hazard.COLD)), round(support.protection(Hazard.HEAT)),
                round(support.protection(Hazard.TOXIC)), round(support.protection(Hazard.RADIATION)), round(support.jet()), round(laser), flags,
                round(shield.value()), round(shield.capacity()), (float) bonuses.boost(Stat.JET_TIME), (float) bonuses.boost(Stat.JET_THRUST));
    }

    /** 换掉护盾和喷气背包倍数（创造模式也显示护盾）。 */
    public SuitHud withTech(Shield shield, TechBonuses bonuses) {
        return new SuitHud(this.oxygen, this.cold, this.heat, this.toxic, this.radiation, this.jet, this.laser, this.flags,
                round(shield.value()), round(shield.capacity()), (float) bonuses.boost(Stat.JET_TIME), (float) bonuses.boost(Stat.JET_THRUST));
    }

    private static float round(float value) {
        return Math.round(value * 10) / 10F;
    }

    public boolean has(int flag) {
        return (this.flags & flag) != 0;
    }

    public boolean exposed(Hazard hazard) {
        return (this.flags & (1 << (EXPOSED_SHIFT + hazard.ordinal()))) != 0;
    }

    public float protection(Hazard hazard) {
        return switch (hazard) {
            case COLD -> this.cold;
            case HEAT -> this.heat;
            case TOXIC -> this.toxic;
            case RADIATION -> this.radiation;
        };
    }

    /** 这种补给现在用不上（已经满了）。 */
    public boolean full(SuitRefillItem.Kind kind) {
        if (kind == SuitRefillItem.Kind.OXYGEN) {
            return this.oxygen >= LifeSupport.OXYGEN_MAX;
        }
        for (Hazard hazard : Hazard.values()) {
            if (this.protection(hazard) < LifeSupport.HAZARD_MAX) {
                return false;
            }
        }
        return true;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeFloat(this.oxygen);
        buf.writeFloat(this.cold);
        buf.writeFloat(this.heat);
        buf.writeFloat(this.toxic);
        buf.writeFloat(this.radiation);
        buf.writeFloat(this.jet);
        buf.writeFloat(this.laser);
        buf.writeVarInt(this.flags);
        buf.writeFloat(this.shield);
        buf.writeFloat(this.shieldMax);
        buf.writeFloat(this.jetTime);
        buf.writeFloat(this.jetThrust);
    }

    private static SuitHud read(RegistryFriendlyByteBuf buf) {
        return new SuitHud(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }
}
