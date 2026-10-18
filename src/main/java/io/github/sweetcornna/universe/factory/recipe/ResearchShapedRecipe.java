package io.github.sweetcornna.universe.factory.recipe;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.registry.UniverseRecipeTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.NormalCraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

/**
 * 要研究解锁的有序合成（ADR-0011）：和原版的有序合成一样，只是科技没完成时不出结果。科技完成时
 * 这些配方会加进所有玩家的配方书。
 */
public final class ResearchShapedRecipe extends NormalCraftingRecipe {
    public static final MapCodec<ResearchShapedRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Recipe.CommonInfo.MAP_CODEC.forGetter(recipe -> recipe.commonInfo),
            CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(recipe -> recipe.bookInfo),
            ShapedRecipePattern.MAP_CODEC.forGetter(ResearchShapedRecipe::pattern),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(ResearchShapedRecipe::result),
            Codec.STRING.fieldOf("research").forGetter(ResearchShapedRecipe::research)
    ).apply(i, ResearchShapedRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchShapedRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC, recipe -> recipe.commonInfo,
            CraftingRecipe.CraftingBookInfo.STREAM_CODEC, recipe -> recipe.bookInfo,
            ShapedRecipePattern.STREAM_CODEC, ResearchShapedRecipe::pattern,
            ItemStackTemplate.STREAM_CODEC, ResearchShapedRecipe::result,
            ByteBufCodecs.STRING_UTF8, ResearchShapedRecipe::research,
            ResearchShapedRecipe::new);
    public static final RecipeSerializer<ResearchShapedRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final ShapedRecipePattern pattern;
    private final ItemStackTemplate result;
    private final String research;

    public ResearchShapedRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ShapedRecipePattern pattern,
            ItemStackTemplate result, String research) {
        super(commonInfo, bookInfo);
        this.pattern = pattern;
        this.result = result;
        this.research = research;
    }

    public ShapedRecipePattern pattern() {
        return this.pattern;
    }

    public ItemStackTemplate result() {
        return this.result;
    }

    public String research() {
        return this.research;
    }

    @Override
    public RecipeSerializer<ResearchShapedRecipe> getSerializer() {
        return UniverseRecipeTypes.RESEARCH_SHAPED.get();
    }

    @Override
    protected PlacementInfo createPlacementInfo() {
        return PlacementInfo.createFromOptionals(this.pattern.ingredients());
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return this.pattern.matches(input) && Research.isUnlocked(level, this.research);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return this.result.create();
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new ShapedCraftingRecipeDisplay(
                this.pattern.width(),
                this.pattern.height(),
                this.pattern.ingredients().stream().map(e -> e.map(Ingredient::display).orElse(SlotDisplay.Empty.INSTANCE)).toList(),
                new SlotDisplay.ItemStackSlotDisplay(this.result),
                new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)));
    }
}
