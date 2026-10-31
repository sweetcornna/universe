package io.github.sweetcornna.universe.registry;

import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** 本模组的物品标签，和本模组材料对应的通用标签（ADR-0039）。 */
public final class UniverseItemTags {
    /** 打下来的守卫机、海盗战机的残骸：技术模块和多功能工具的武器都认（ADR-0039）。 */
    public static final TagKey<Item> COMBAT_SALVAGE = TagKey.create(Registries.ITEM, Universe.id("combat_salvage"));

    /**
     * 本模组的材料和对应的通用标签（NeoForge 的 {@code c:} 命名）。本模组的物品登记进这些标签，别的模组认得出；
     * 设备配方的原料换成这些标签，别的模组的同种材料也能放进来。原版的铁、铜、金各模组本来就共用原版物品，不换。
     * 按物品 ID 排好序，数据生成每次写出来的文件一样。
     */
    public static final SortedMap<String, TagKey<Item>> COMMON = Collections.unmodifiableSortedMap(new TreeMap<>(Map.ofEntries(
            Map.entry("universe:raw_titanium", common("raw_materials/titanium")),
            Map.entry("universe:raw_silicon", common("raw_materials/silicon")),
            Map.entry("universe:raw_uranium", common("raw_materials/uranium")),
            Map.entry("universe:raw_magnetite", common("raw_materials/magnetite")),
            Map.entry("universe:titanium_ingot", common("ingots/titanium")),
            Map.entry("universe:uranium_ingot", common("ingots/uranium")),
            Map.entry("universe:steel", common("ingots/steel")),
            Map.entry("universe:nickel_ingot", common("ingots/nickel")),
            Map.entry("universe:platinum_ingot", common("ingots/platinum")),
            Map.entry("universe:silicon", common("silicon")),
            Map.entry("universe:sulfur", common("dusts/sulfur")),
            Map.entry("universe:salt", common("dusts/salt")))));

    private UniverseItemTags() {
    }

    /** 通用标签 {@code c:<path>}。 */
    public static TagKey<Item> common(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", path));
    }
}
