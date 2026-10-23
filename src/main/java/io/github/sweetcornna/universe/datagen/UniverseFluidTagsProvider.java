package io.github.sweetcornna.universe.datagen;

import java.util.concurrent.CompletableFuture;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.factory.fluid.FluidBridge;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.FluidTagsProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidIds;

/**
 * 流体标签（ADR-0030 §三）：本模组的流体物品对外算哪些流体（{@link FluidBridge}）。每种物品一个
 * {@code universe:interop/<物品>}，默认放社区约定的 {@code c:} 标签，整合包可以用数据包改：
 *
 * <ul>
 * <li>水：原版的水（{@code minecraft:water}、{@code minecraft:flowing_water}）
 * <li>硫酸：本模组的酸液 {@code universe:acid}（可选，没注册时跳过）和 {@code c:sulfuric_acid}（通用机械用这个名字）
 * <li>原油：{@code c:crude_oil}（气动工艺）和 {@code c:oil}（一些多加载器模组）
 * <li>精炼油：{@code c:refined_oil}；氢：{@code c:hydrogen}（通用机械）；重氢：{@code c:deuterium}（通用机械叫重水，不是一回事）
 * </ul>
 *
 * <p>我们自己的酸液也放进 {@code c:sulfuric_acid}，别的模组照这个名字就能认出来。
 */
final class UniverseFluidTagsProvider extends FluidTagsProvider {
    UniverseFluidTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Universe.MOD_ID);
    }

    private static TagKey<Fluid> common(String name) {
        return TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("c", name));
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        ResourceKey<Fluid> acid = ResourceKey.create(Registries.FLUID, Universe.id("acid"));
        TagKey<Fluid> sulfuric = common("sulfuric_acid");
        this.tag(sulfuric).addOptional(acid);

        // 只认原版的水：别的模组为了能游泳把自己的流体塞进 minecraft:water 的不少，那些不算水
        this.tag(FluidBridge.tag("water")).add(FluidIds.WATER, FluidIds.FLOWING_WATER);
        this.tag(FluidBridge.tag("sulfuric_acid")).addOptional(acid).addOptionalTag(sulfuric);
        this.tag(FluidBridge.tag("crude_oil")).addOptionalTag(common("crude_oil")).addOptionalTag(common("oil"));
        this.tag(FluidBridge.tag("refined_oil")).addOptionalTag(common("refined_oil"));
        this.tag(FluidBridge.tag("hydrogen")).addOptionalTag(common("hydrogen"));
        this.tag(FluidBridge.tag("deuterium")).addOptionalTag(common("deuterium"));
    }
}
