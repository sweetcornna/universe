package io.github.sweetcornna.universe.datagen;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.core.trade.TradeCategory;
import io.github.sweetcornna.universe.entity.CreatureHusbandry;
import io.github.sweetcornna.universe.factory.FluidItems;
import io.github.sweetcornna.universe.fluid.AcidEffects;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItemTags;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.trade.ItemValues;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.ItemTagsProvider;

/** 物品标签：哪些物品算流体（能装进储液罐，ADR-0015）；交易品的大类（ADR-0017）。 */
final class UniverseItemTagsProvider extends ItemTagsProvider {
    UniverseItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Universe.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        this.tag(FluidItems.TAG).add(
                UniverseItems.WATER.key(),
                UniverseItems.CRUDE_OIL.key(),
                UniverseItems.REFINED_OIL.key(),
                UniverseItems.HYDROGEN.key(),
                UniverseItems.DEUTERIUM.key(),
                UniverseItems.SULFURIC_ACID.key());
        // 两种食性的食物（ADR-0017）
        this.tag(CreatureHusbandry.HERBIVORE_FOOD).add(vanilla(Items.WHEAT), vanilla(Items.CARROT), vanilla(Items.APPLE),
                UniverseItems.HERBIVORE_FEED.key());
        this.tag(CreatureHusbandry.CARNIVORE_FOOD).add(vanilla(Items.BEEF), vanilla(Items.PORKCHOP), vanilla(Items.MUTTON),
                vanilla(Items.CHICKEN), vanilla(Items.RABBIT), UniverseItems.ALIEN_MEAT.key(), UniverseItems.CARNIVORE_FEED.key());
        // 异星肉算生肉：卵序列仪拿生肉改性情（ADR-0026 §九）
        this.tag(Tags.Items.FOODS_RAW_MEAT).add(UniverseItems.ALIEN_MEAT.key());
        // 异星的鱼（ADR-0026）：算鱼、生鱼和熟鱼，肉食动物也吃生鱼
        this.tag(ItemTags.FISHES).add(UniverseItems.ALIEN_FISH.key(), UniverseItems.COOKED_ALIEN_FISH.key());
        this.tag(Tags.Items.FOODS_RAW_FISH).add(UniverseItems.ALIEN_FISH.key());
        this.tag(Tags.Items.FOODS_COOKED_FISH).add(UniverseItems.COOKED_ALIEN_FISH.key());
        this.tag(CreatureHusbandry.CARNIVORE_FOOD).add(UniverseItems.ALIEN_FISH.key());
        // 异星原木和原版原木一样能烧成木炭（熔炉和便携精炼机，ADR-0026）
        this.tag(ItemTags.LOGS).add(UniverseItems.PLANET_LOG.key());
        this.tag(ItemTags.LOGS_THAT_BURN).add(UniverseItems.PLANET_LOG.key());
        this.trade();
        this.wrapUpAcid();
        this.wrapUpInterop();
        FishingData.itemTags(this::tag);
        // M19 钓鱼（A）的物品标签（调自己的方法）加在这行上面
        SettlementDatagen.itemTags(this::tag);
        // M19 聚落（B）的物品标签（调自己的方法）加在这行上面
        // M19 化石（C）的物品标签（调自己的方法）加在这行上面
        DeepSpaceDatagen.itemTags(this::tag);
        // M19 深空和重力枪（D）的物品标签（调自己的方法）加在这行上面
        // M19 科技布局和局部危害（E）的物品标签（调自己的方法）加在这行上面
        ChemistryDatagen.itemTags(this::tag);
        // M20 化学（A）的物品标签（调自己的方法）加在这行上面
        // M20 反应釜（B）的物品标签（调自己的方法）加在这行上面
        // M20 微生物和基因（C）的物品标签（调自己的方法）加在这行上面
        // M20 生物反应器（D）的物品标签（调自己的方法）加在这行上面
        // M20 星球改造（E）的物品标签（调自己的方法）加在这行上面
    }

    private static ResourceKey<Item> vanilla(Item item) {
        return BuiltInRegistries.ITEM.getResourceKey(item).orElseThrow();
    }

    /** M17 收尾的物品标签：酸液（子代理 A，ADR-0030）。 */
    private void wrapUpAcid() {
        // 不怕浓硫酸的东西：玻璃和石英（硅酸盐）、金和铂族（王水才咬得动）、塑料和聚合物、陶瓷和宝石。
        // 铁、铜、铝这些活泼金属，木头、布、肉、植物都会被蚀掉
        this.tag(AcidEffects.RESISTANT).add(
                vanilla(Items.GLASS), vanilla(Items.GLASS_PANE), vanilla(Items.GLASS_BOTTLE), vanilla(Items.TINTED_GLASS),
                vanilla(Items.QUARTZ), vanilla(Items.QUARTZ_BLOCK), vanilla(Items.AMETHYST_SHARD),
                vanilla(Items.GOLD_INGOT), vanilla(Items.GOLD_NUGGET), vanilla(Items.GOLD_BLOCK), vanilla(Items.RAW_GOLD),
                vanilla(Items.DIAMOND), vanilla(Items.DIAMOND_BLOCK), vanilla(Items.EMERALD), vanilla(Items.NETHERITE_INGOT),
                vanilla(Items.BRICK), vanilla(Items.OBSIDIAN),
                UniverseItems.PLASTIC.key(), UniverseItems.SYNTHETIC_DIAMOND.key(), UniverseItems.TITANIUM_GLASS.key(),
                UniverseItems.TITANIUM_CRYSTAL.key(), UniverseItems.CASIMIR_CRYSTAL.key(),
                // 装酸的桶和硫酸本身不会被自己蚀掉
                UniverseItems.ACID_BUCKET.key(), UniverseItems.SULFURIC_ACID.key());
        // 酸液桶和水桶一样算桶
        this.tag(Tags.Items.BUCKETS).add(UniverseItems.ACID_BUCKET.key());
    }

    /** M17 收尾的物品标签：互通（子代理 C，ADR-0030）。 */
    private void wrapUpInterop() {
        // 战斗残骸（ADR-0039）：守卫机和海盗的残骸都能做技术模块和多功能工具的武器
        this.tag(UniverseItemTags.COMBAT_SALVAGE).add(UniverseItems.SENTINEL_SCRAP.key(), UniverseItems.PIRATE_SCRAP.key());
        // 通用标签（ADR-0039）：本模组的材料登记进去，别的模组认得出；设备配方的原料也用这些标签
        UniverseItemTags.COMMON.forEach((id, tag) -> this.tag(tag).add(ResourceKey.create(Registries.ITEM, Identifier.parse(id))));
        for (Map.Entry<String, TagKey<Item>> entry : UniverseItemTags.COMMON.entrySet()) {
            String path = entry.getValue().location().getPath();
            int slash = path.indexOf('/');
            if (slash > 0) {
                // 父标签（c:ingots、c:raw_materials、c:dusts）收进子标签
                this.tag(UniverseItemTags.common(path.substring(0, slash))).addTag(entry.getValue());
            }
        }
        // 星球矿石的方块物品：c:ores 和按矿种的 c:ores/<矿>
        UniverseBlocks.ORES.forEach((kind, block) -> {
            TagKey<Item> ore = UniverseItemTags.common("ores/" + kind.commonName());
            ResourceKey<Item> item = ResourceKey.create(Registries.ITEM, block.getId());
            this.tag(ore).add(item);
            this.tag(Tags.BlockItems.ORES.item()).addTag(ore);
        });
        // 镍、铂空间站也收也卖（ADR-0036 §四）
        this.tag(ItemValues.tag(TradeCategory.REFINED)).add(UniverseItems.NICKEL_INGOT.key(), UniverseItems.PLATINUM_INGOT.key());
    }

    /**
     * 交易品的大类（ADR-0017）。只有这里列出的东西会被空间站挑来当特产卖；没列出的照样能卖，大类按价钱估。
     * 一样东西只放进一个大类。
     */
    private void trade() {
        this.tag(ItemValues.tag(TradeCategory.RAW)).add(
                vanilla(Items.RAW_IRON), vanilla(Items.RAW_COPPER), vanilla(Items.RAW_GOLD), vanilla(Items.COAL), vanilla(Items.REDSTONE),
                vanilla(Items.QUARTZ), vanilla(Items.LAPIS_LAZULI), vanilla(Items.SAND), vanilla(Items.CLAY_BALL),
                UniverseItems.ORE_DROPS.get(OreKind.TITANIUM).key(), UniverseItems.ORE_DROPS.get(OreKind.SILICON).key(),
                UniverseItems.ORE_DROPS.get(OreKind.MAGNETIC).key(), UniverseItems.ORE_DROPS.get(OreKind.URANIUM).key(),
                UniverseItems.ORE_DROPS.get(OreKind.SULFUR).key(), UniverseItems.ORE_DROPS.get(OreKind.FIRE_ICE).key(),
                UniverseItems.ORE_DROPS.get(OreKind.SALT).key(), UniverseItems.ORE_DROPS.get(OreKind.OPTICAL_CRYSTAL).key(),
                UniverseItems.CRUDE_OIL.key(), UniverseItems.WATER.key());
        this.tag(ItemValues.tag(TradeCategory.REFINED)).add(
                vanilla(Items.IRON_INGOT), vanilla(Items.COPPER_INGOT), vanilla(Items.GOLD_INGOT), vanilla(Items.GLASS),
                UniverseItems.ORE_SMELTED.get(OreKind.TITANIUM).key(), UniverseItems.ORE_SMELTED.get(OreKind.SILICON).key(),
                UniverseItems.ORE_SMELTED.get(OreKind.MAGNETIC).key(), UniverseItems.ORE_SMELTED.get(OreKind.URANIUM).key(),
                UniverseItems.STEEL.key(), UniverseItems.ENERGETIC_GRAPHITE.key(), UniverseItems.CRYSTAL_SILICON.key(),
                UniverseItems.TITANIUM_ALLOY.key(), UniverseItems.SYNTHETIC_DIAMOND.key(), UniverseItems.PLASTIC.key(),
                UniverseItems.SULFURIC_ACID.key(), UniverseItems.GRAPHENE.key(), UniverseItems.TITANIUM_CRYSTAL.key(),
                UniverseItems.TITANIUM_GLASS.key());
        this.tag(ItemValues.tag(TradeCategory.COMPONENT)).add(
                UniverseItems.GEAR.key(), UniverseItems.MAGNETIC_COIL.key(), UniverseItems.CIRCUIT_BOARD.key(),
                UniverseItems.ELECTRIC_MOTOR.key(), UniverseItems.PRISM.key(), UniverseItems.PLASMA_EXCITER.key(),
                UniverseItems.MICROCRYSTALLINE_COMPONENT.key(), UniverseItems.ELECTROMAGNETIC_TURBINE.key(),
                UniverseItems.SUPER_MAGNETIC_RING.key(), UniverseItems.PARTICLE_CONTAINER.key(), UniverseItems.THRUSTER.key(),
                UniverseItems.REINFORCED_THRUSTER.key(), UniverseItems.LOGISTICS_DRONE.key(), UniverseItems.CARBON_NANOTUBE.key());
        this.tag(ItemValues.tag(TradeCategory.ADVANCED)).add(
                UniverseItems.PROCESSOR.key(), UniverseItems.PARTICLE_BROADBAND.key(), UniverseItems.QUANTUM_CHIP.key(),
                UniverseItems.FRAME_MATERIAL.key(), UniverseItems.DYSON_SPHERE_COMPONENT.key(), UniverseItems.CASIMIR_CRYSTAL.key(),
                UniverseItems.PLANE_FILTER.key(), UniverseItems.GRAVITON_LENS.key(), UniverseItems.PHOTON_COMBINER.key(),
                UniverseItems.SOLAR_SAIL.key(), UniverseItems.SMALL_CARRIER_ROCKET.key(), UniverseItems.LOGISTICS_VESSEL.key(),
                UniverseItems.STRANGE_MATTER.key(), UniverseItems.ANNIHILATION_CONSTRAINT_SPHERE.key());
        this.tag(ItemValues.tag(TradeCategory.ENERGY)).add(
                UniverseItems.HYDROGEN.key(), UniverseItems.DEUTERIUM.key(), UniverseItems.REFINED_OIL.key(), UniverseItems.LAUNCH_FUEL.key(),
                UniverseItems.SPACE_WARPER.key(), UniverseItems.DEUTERON_FUEL_ROD.key(), UniverseItems.CRITICAL_PHOTON.key(),
                UniverseItems.ELECTROMAGNETIC_MATRIX.key(), UniverseItems.ENERGY_MATRIX.key(), UniverseItems.STRUCTURE_MATRIX.key(),
                UniverseItems.INFORMATION_MATRIX.key(), UniverseItems.GRAVITY_MATRIX.key(), UniverseItems.OXYGEN_CAPSULE.key(),
                UniverseItems.HAZARD_CELL.key(), UniverseItems.ANTIMATTER.key(), UniverseItems.ANTIMATTER_FUEL_ROD.key(),
                UniverseItems.COMPOSITE_MATRIX.key(), UniverseItems.UNIVERSE_MATRIX.key());
        this.tag(ItemValues.tag(TradeCategory.BIO)).add(
                UniverseItems.ALIEN_MEAT.key(), UniverseItems.COOKED_ALIEN_MEAT.key(), UniverseItems.ALIEN_HIDE.key(),
                UniverseItems.ALIEN_EGG.key(), UniverseItems.COOKED_ALIEN_EGG.key(), UniverseItems.ALIEN_FLUFF.key(), UniverseItems.BIO_GEL.key(),
                UniverseItems.ORE_DROPS.get(OreKind.ORGANIC_CRYSTAL).key(), vanilla(Items.LEATHER), vanilla(Items.WHEAT), vanilla(Items.STRING),
                vanilla(Items.EGG), vanilla(Items.FEATHER), vanilla(Items.BONE), vanilla(Items.SLIME_BALL), vanilla(Items.HONEYCOMB),
                UniverseItems.ALIEN_FISH.key(), UniverseItems.COOKED_ALIEN_FISH.key(), UniverseItems.LIVING_PEARL.key());
        // 营养处理器做的组合菜、便携精炼机做的防护凝胶（ADR-0026）
        UniverseItems.DISHES.forEach((dish, item) -> {
            if (dish.nutrition() >= 8) {
                this.tag(ItemValues.tag(TradeCategory.BIO)).add(item.key());
            }
        });
        UniverseItems.GELS.values().forEach(item -> this.tag(ItemValues.tag(TradeCategory.ENERGY)).add(item.key()));
        this.tag(ItemValues.tag(TradeCategory.TECH)).add(
                UniverseItems.SHIP_HELM.key(), UniverseItems.SHIP_THRUSTER.key(), UniverseItems.FUEL_TANK.key(), UniverseItems.IMPULSE_ENGINE.key(),
                UniverseItems.WARP_DRIVE.key(), UniverseItems.SHIP_SEAT.key(), UniverseItems.MULTITOOL.key(),
                UniverseItems.PHOTON_CANNON.key(), UniverseItems.MINING_BEAM.key(), UniverseItems.SHIELD_GENERATOR.key(),
                UniverseItems.SHIP_THRUSTER_MK2.key(), UniverseItems.FUEL_TANK_MK2.key(), UniverseItems.IMPULSE_ENGINE_MK2.key(),
                UniverseItems.WARP_DRIVE_MK2.key(), UniverseItems.ALLOY_HULL_PLATING.key(),
                UniverseItems.EXOSUIT_HELMET.key(), UniverseItems.EXOSUIT_CHESTPLATE.key(), UniverseItems.EXOSUIT_LEGGINGS.key(),
                UniverseItems.EXOSUIT_BOOTS.key(), UniverseItems.EMERGENCY_RECEIVER.key())
                .addAll(UniverseItems.MODULES.values().stream().map(item -> item.key()))
                .addAll(UniverseItems.HULL_PLATING.values().stream().map(item -> item.key()));
    }
}
