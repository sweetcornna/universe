package io.github.sweetcornna.universe.datagen;

import java.util.concurrent.CompletableFuture;

import io.github.sweetcornna.universe.entity.PlasmaBolt;
import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.survival.SurvivalDamage;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.DamageTypeTagsProvider;
import net.minecraft.tags.DamageTypeTags;

/**
 * 伤害类型标签：缺氧和环境危害无视护甲、不击退（和原版的溺水、冰冻一样）；等离子弹算弹射物（弹射物保护有效），
 * 也不用原版的击退：原版击退会把站在地上的目标往上抛，低重力星球上一抛就飞出去老远，弹体自己推一下（{@link PlasmaBolt}）。
 */
final class UniverseDamageTypeTagsProvider extends DamageTypeTagsProvider {
    UniverseDamageTypeTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Universe.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        for (var key : java.util.List.of(SurvivalDamage.VACUUM, SurvivalDamage.COLD, SurvivalDamage.HEAT,
                SurvivalDamage.TOXIC, SurvivalDamage.RADIATION)) {
            this.tag(DamageTypeTags.BYPASSES_ARMOR).add(key);
            this.tag(DamageTypeTags.NO_KNOCKBACK).add(key);
        }
        this.tag(DamageTypeTags.IS_PROJECTILE).add(PlasmaBolt.DAMAGE);
        this.tag(DamageTypeTags.NO_KNOCKBACK).add(PlasmaBolt.DAMAGE);
        this.tag(DamageTypeTags.NO_KNOCKBACK).add(SurvivalDamage.SHOCK);
        // 黑雾的电弧、激光、炮弹（ADR-0028）：和等离子弹一样不往上抛
        this.tag(DamageTypeTags.NO_KNOCKBACK).add(io.github.sweetcornna.universe.darkfog.entity.DarkFogDamage.KEY);
    }
}
