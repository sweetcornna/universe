package io.github.sweetcornna.universe.datagen;

import java.util.concurrent.CompletableFuture;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.core.trade.TradeCategory;
import io.github.sweetcornna.universe.entity.CreatureHusbandry;
import io.github.sweetcornna.universe.factory.FluidItems;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.trade.ItemValues;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
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
        // 异星的鱼（ADR-0026）：算鱼、生鱼和熟鱼，肉食动物也吃生鱼
        this.tag(ItemTags.FISHES).add(UniverseItems.ALIEN_FISH.key(), UniverseItems.COOKED_ALIEN_FISH.key());
        this.tag(Tags.Items.FOODS_RAW_FISH).add(UniverseItems.ALIEN_FISH.key());
        this.tag(Tags.Items.FOODS_COOKED_FISH).add(UniverseItems.COOKED_ALIEN_FISH.key());
        this.tag(CreatureHusbandry.CARNIVORE_FOOD).add(UniverseItems.ALIEN_FISH.key());
        // 异星原木和原版原木一样能烧成木炭（熔炉和便携精炼机，ADR-0026）
        this.tag(ItemTags.LOGS).add(UniverseItems.PLANET_LOG.key());
        this.tag(ItemTags.LOGS_THAT_BURN).add(UniverseItems.PLANET_LOG.key());
        this.trade();
    }

    private static ResourceKey<Item> vanilla(Item item) {
        return BuiltInRegistries.ITEM.getResourceKey(item).orElseThrow();
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
