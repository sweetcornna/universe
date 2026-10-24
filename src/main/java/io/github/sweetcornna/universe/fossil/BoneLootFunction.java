package io.github.sweetcornna.universe.fossil;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.fossil.Bone;
import io.github.sweetcornna.universe.core.fossil.FossilFinds;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * 战利品函数 {@code universe:fossil_bone}（ADR-0034 §三）：把物品换成一块骨头，类群按这颗星球的类型抽（{@link FossilFinds}）。
 * {@code sentry} 为 true 时按石哨兵的掉落抽，偏向稀有的类群。不在生成的星球上（比如主世界）就什么也不给。
 */
public class BoneLootFunction extends LootItemConditionalFunction {
    public static final MapCodec<BoneLootFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i)
            .and(Codec.BOOL.optionalFieldOf("sentry", false).forGetter(f -> f.sentry))
            .apply(i, BoneLootFunction::new));

    private final boolean sentry;

    private BoneLootFunction(Optional<Holder<LootItemCondition>> condition, boolean sentry) {
        super(condition);
        this.sentry = sentry;
    }

    /** 化石土、发掘场的箱子里挖到的。 */
    public static LootItemConditionalFunction.Builder<?> dig() {
        return simpleBuilder(condition -> new BoneLootFunction(condition, false));
    }

    /** 石哨兵掉的。 */
    public static LootItemConditionalFunction.Builder<?> sentry() {
        return simpleBuilder(condition -> new BoneLootFunction(condition, true));
    }

    @Override
    public MapCodec<BoneLootFunction> codec() {
        return MAP_CODEC;
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        Optional<Planet> planet = Planets.planet(context.getLevel());
        if (planet.isEmpty()) {
            return ItemStack.EMPTY;
        }
        RandomSource random = context.getRandom();
        Bone bone = this.sentry ? FossilFinds.sentry(planet.get().type(), random.nextDouble(), random.nextDouble())
                : FossilFinds.dig(planet.get().type(), random.nextDouble(), random.nextDouble());
        return new ItemStack(UniverseItems.BONES.get(bone).get());
    }
}
