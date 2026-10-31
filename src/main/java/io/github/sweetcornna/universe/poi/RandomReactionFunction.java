package io.github.sweetcornna.universe.poi;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.chem.reaction.Reaction;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * 战利品函数 {@code universe:random_reaction}（ADR-0037 §五）：给实验记录写上随机一条反应（{@code universe:reaction} 组件），
 * 箱子打开时当场定下。每条反应机会一样。
 */
public class RandomReactionFunction extends LootItemConditionalFunction {
    public static final MapCodec<RandomReactionFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i)
            .apply(i, RandomReactionFunction::new));

    private RandomReactionFunction(Optional<Holder<LootItemCondition>> condition) {
        super(condition);
    }

    public static LootItemConditionalFunction.Builder<?> randomReaction() {
        return simpleBuilder(RandomReactionFunction::new);
    }

    @Override
    public MapCodec<RandomReactionFunction> codec() {
        return MAP_CODEC;
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        Reaction[] reactions = Reaction.values();
        stack.set(UniverseDataComponents.REACTION.get(), reactions[context.getRandom().nextInt(reactions.length)].name());
        return stack;
    }
}
