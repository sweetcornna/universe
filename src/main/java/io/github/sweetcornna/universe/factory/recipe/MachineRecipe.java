package io.github.sweetcornna.universe.factory.recipe;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.registry.UniverseRecipeTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * 设备的配方（ADR-0011）：几种带数量的原料、一到两种产物、标准时间（刻，速度 1 的设备做一次要多久），
 * 以及可选的解锁科技。设备上由玩家选定配方，第 i 个原料格只放第 i 种原料，所以不需要按输入去找配方。
 *
 * <p>几种类型共用这个类：冶炼（电弧熔炉、位面熔炉）、制造（制造台）、精炼（原油精炼厂）、化工（化工厂，ADR-0015）。
 */
public final class MachineRecipe implements Recipe<MachineRecipe.Input> {
    /** 原料、产物最多几种，和设备的格子数一致。 */
    public static final int MAX_INGREDIENTS = 4;
    public static final int MAX_RESULTS = 2;

    private final Kind kind;
    private final List<SizedIngredient> ingredients;
    private final List<ItemStackTemplate> results;
    private final int time;
    private final String research;

    public MachineRecipe(Kind kind, List<SizedIngredient> ingredients, List<ItemStackTemplate> results, int time, String research) {
        this.kind = kind;
        this.ingredients = List.copyOf(ingredients);
        this.results = List.copyOf(results);
        this.time = time;
        this.research = research;
    }

    public static RecipeSerializer<MachineRecipe> serializer(Kind kind) {
        MapCodec<MachineRecipe> codec = RecordCodecBuilder.mapCodec(i -> i.group(
                SizedIngredient.NESTED_CODEC.listOf(1, MAX_INGREDIENTS).fieldOf("ingredients").forGetter(MachineRecipe::ingredients),
                ItemStackTemplate.CODEC.listOf(1, MAX_RESULTS).fieldOf("results").forGetter(MachineRecipe::results),
                ExtraCodecs.POSITIVE_INT.fieldOf("time").forGetter(MachineRecipe::time),
                Codec.STRING.optionalFieldOf("research", "").forGetter(MachineRecipe::research)
        ).apply(i, (ingredients, results, time, research) -> new MachineRecipe(kind, ingredients, results, time, research)));
        StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> streamCodec = StreamCodec.composite(
                SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_INGREDIENTS)), MachineRecipe::ingredients,
                ItemStackTemplate.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_RESULTS)), MachineRecipe::results,
                ByteBufCodecs.VAR_INT, MachineRecipe::time,
                ByteBufCodecs.STRING_UTF8, MachineRecipe::research,
                (ingredients, results, time, research) -> new MachineRecipe(kind, ingredients, results, time, research));
        return new RecipeSerializer<>(codec, streamCodec);
    }

    public Kind kind() {
        return this.kind;
    }

    public List<SizedIngredient> ingredients() {
        return this.ingredients;
    }

    public List<ItemStackTemplate> results() {
        return this.results;
    }

    /** 标准时间（刻）。 */
    public int time() {
        return this.time;
    }

    /** 解锁的科技，空字符串表示一开始就能用。 */
    public String research() {
        return this.research;
    }

    /** 每种原料在输入里都够数（不管放在哪一格）。设备自己按格子检查，这个只给通用的查找用。 */
    @Override
    public boolean matches(Input input, Level level) {
        for (SizedIngredient ingredient : this.ingredients) {
            boolean found = false;
            for (ItemStack stack : input.items()) {
                if (ingredient.test(stack)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(Input input) {
        return this.results.getFirst().create();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeSerializer<MachineRecipe> getSerializer() {
        return switch (this.kind) {
            case SMELTING -> UniverseRecipeTypes.SMELTING_SERIALIZER.get();
            case ASSEMBLING -> UniverseRecipeTypes.ASSEMBLING_SERIALIZER.get();
            case REFINING -> UniverseRecipeTypes.REFINING_SERIALIZER.get();
            case CHEMICAL -> UniverseRecipeTypes.CHEMICAL_SERIALIZER.get();
            case PARTICLE -> UniverseRecipeTypes.PARTICLE_SERIALIZER.get();
            case NUTRIENT -> UniverseRecipeTypes.NUTRIENT_SERIALIZER.get();
            case PORTABLE_REFINING -> UniverseRecipeTypes.PORTABLE_REFINING_SERIALIZER.get();
        };
    }

    @Override
    public RecipeType<MachineRecipe> getType() {
        return this.kind.type();
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return UniverseRecipeTypes.MACHINE_CATEGORY.get();
    }

    /** 配方的种类，对应一类设备。 */
    public enum Kind {
        SMELTING,
        ASSEMBLING,
        REFINING,
        CHEMICAL,
        /** 微型粒子对撞机（ADR-0016）。 */
        PARTICLE,
        /** 营养处理器（ADR-0026）。 */
        NUTRIENT,
        /** 便携精炼机（ADR-0026）。 */
        PORTABLE_REFINING;

        public RecipeType<MachineRecipe> type() {
            return switch (this) {
                case SMELTING -> UniverseRecipeTypes.SMELTING.get();
                case ASSEMBLING -> UniverseRecipeTypes.ASSEMBLING.get();
                case REFINING -> UniverseRecipeTypes.REFINING.get();
                case CHEMICAL -> UniverseRecipeTypes.CHEMICAL.get();
                case PARTICLE -> UniverseRecipeTypes.PARTICLE.get();
                case NUTRIENT -> UniverseRecipeTypes.NUTRIENT.get();
                case PORTABLE_REFINING -> UniverseRecipeTypes.PORTABLE_REFINING.get();
            };
        }
    }

    /** 设备的原料格。 */
    public record Input(List<ItemStack> items) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return this.items.get(index);
        }

        @Override
        public int size() {
            return this.items.size();
        }
    }
}
