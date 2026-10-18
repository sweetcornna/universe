package io.github.sweetcornna.universe.poi;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.tech.ModuleLoot;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * 战利品函数 {@code universe:random_module}（ADR-0014）：把物品换成一个随机的技术模块，品级按这颗星球所在恒星的稀有程度抽，
 * {@code bonus} 为 1 时再往上提一档（坠毁飞船）。数值当场定下，不用等进背包再校准。
 */
public class RandomModuleFunction extends LootItemConditionalFunction {
    public static final MapCodec<RandomModuleFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i)
            .and(Codec.INT.optionalFieldOf("bonus", 0).forGetter(f -> f.bonus))
            .apply(i, RandomModuleFunction::new));

    private final int bonus;

    private RandomModuleFunction(Optional<Holder<LootItemCondition>> condition, int bonus) {
        super(condition);
        this.bonus = bonus;
    }

    public static LootItemConditionalFunction.Builder<?> randomModule(int bonus) {
        return simpleBuilder(condition -> new RandomModuleFunction(condition, bonus));
    }

    @Override
    public MapCodec<RandomModuleFunction> codec() {
        return MAP_CODEC;
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        Rng rng = Rng.of(context.getRandom().nextLong());
        TechModule module = ModuleLoot.roll(rng, List.of(ModuleType.values()), Pois.rarity(context.getLevel()), this.bonus);
        ItemStack result = new ItemStack(UniverseItems.MODULES.get(module.type()).get());
        result.set(UniverseDataComponents.TECH_MODULE.get(), module);
        return result;
    }
}
