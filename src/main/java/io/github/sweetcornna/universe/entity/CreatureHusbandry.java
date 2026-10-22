package io.github.sweetcornna.universe.entity;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.ecology.CreatureSpecies;
import io.github.sweetcornna.universe.core.ecology.Husbandry;
import io.github.sweetcornna.universe.core.ecology.Husbandry.Diet;
import io.github.sweetcornna.universe.core.ecology.Husbandry.Product;
import io.github.sweetcornna.universe.core.ecology.Temperament;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 喂养异星动物的适配层（ADR-0017）：两种食性各吃哪些东西（物品标签，整合包可以改），三种产物对应的物品和音效。
 * 规则本身在 {@link Husbandry}。
 */
public final class CreatureHusbandry {
    /** 温顺、易受惊的动物吃的：小麦、胡萝卜、苹果、草食饲料。 */
    public static final TagKey<Item> HERBIVORE_FOOD = TagKey.create(Registries.ITEM, Universe.id("herbivore_food"));
    /** 有领地意识的和捕食者吃的：生肉、异星肉、肉食饲料。 */
    public static final TagKey<Item> CARNIVORE_FOOD = TagKey.create(Registries.ITEM, Universe.id("carnivore_food"));

    private CreatureHusbandry() {
    }

    public static TagKey<Item> food(Diet diet) {
        return diet == Diet.HERBIVORE ? HERBIVORE_FOOD : CARNIVORE_FOOD;
    }

    /** 两种食性里随便哪种的食物。 */
    public static boolean isFood(ItemStack stack) {
        return stack.is(HERBIVORE_FOOD) || stack.is(CARNIVORE_FOOD);
    }

    public static boolean eats(CreatureSpecies species, ItemStack stack) {
        return eats(species.temperament(), stack);
    }

    /** 按这一只的性情（卵序列仪能改）看吃不吃。 */
    public static boolean eats(Temperament temperament, ItemStack stack) {
        return stack.is(food(Husbandry.diet(temperament)));
    }

    public static Component dietName(Diet diet) {
        return Component.translatable("discovery.universe.diet." + (diet == Diet.HERBIVORE ? "herbivore" : "carnivore"));
    }

    public static Item productItem(Product product) {
        return switch (product) {
            case EGG -> UniverseItems.ALIEN_EGG.get();
            case FLUFF -> UniverseItems.ALIEN_FLUFF.get();
            case GEL -> UniverseItems.BIO_GEL.get();
        };
    }

    /** 这个物种喂过之后产出的一份。 */
    public static ItemStack product(CreatureSpecies species) {
        return new ItemStack(productItem(Husbandry.product(species)), Husbandry.productCount(species));
    }

    public static SoundEvent productSound(Product product) {
        return switch (product) {
            case EGG -> SoundEvents.CHICKEN_EGG;
            case FLUFF -> SoundEvents.SHEEP_SHEAR;
            case GEL -> SoundEvents.SLIME_SQUISH;
        };
    }
}
