package io.github.sweetcornna.universe.client.tech;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** 技术模块图标边框的颜色：按品级（ADR-0014）。还没随机过的按 C 级。 */
public record ModuleGradeTint() implements ItemTintSource {
    public static final ModuleGradeTint INSTANCE = new ModuleGradeTint();
    public static final MapCodec<ModuleGradeTint> MAP_CODEC = MapCodec.unit(INSTANCE);

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        TechModule module = stack.get(UniverseDataComponents.TECH_MODULE.get());
        return ARGB.opaque((module == null ? Grade.C : module.grade()).color());
    }

    @Override
    public MapCodec<ModuleGradeTint> type() {
        return MAP_CODEC;
    }
}
