package io.github.sweetcornna.universe.datagen;

import java.util.concurrent.CompletableFuture;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.survival.SurvivalDamage;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.DamageTypeTagsProvider;
import net.minecraft.tags.DamageTypeTags;

/** 伤害类型标签：缺氧和环境危害无视护甲、不击退（和原版的溺水、冰冻一样）。 */
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
    }
}
