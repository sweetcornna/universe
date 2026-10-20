package io.github.sweetcornna.universe.datagen;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.advancement.MilestoneTrigger;
import io.github.sweetcornna.universe.core.story.Milestone;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.advancements.triggers.ItemUsedOnLocationTrigger;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.advancements.AdvancementSubProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

/**
 * "寰宇"进度树（ADR-0018）：沿成长线排，分工业、探索、太空、戴森球四支。拿到物品、放下方块用原版触发器，
 * 其余用 {@code universe:milestone}。标题和说明的语言键是 {@code advancements.universe.<名字>.title/description}。
 */
final class UniverseAdvancements extends AdvancementSubProvider {
    /** 语言文件要写的进度，datagen 里对一遍。 */
    static final String[] NAMES = {"root", "repair_multitool", "first_power", "tesla", "mining", "smelting", "assembling", "sorting",
            "blue_matrix", "red_matrix", "yellow_matrix", "purple_matrix", "green_matrix", "logistics", "interstellar", "blueprint",
            "species", "survey", "monolith", "tame", "exocraft", "teleport", "ship", "space", "warp", "trade", "mission", "pirates",
            "ejector", "silo", "first_shell", "ray", "antimatter", "universe_matrix", "artificial_star", "mission_complete"};

    private final HolderGetter<Block> blocks;

    UniverseAdvancements(BootstrapContext<Advancement> output) {
        super(output);
        this.blocks = output.lookup(Registries.BLOCK);
    }

    @Override
    public void generate() {
        AdvancementHolder root = Advancement.Builder.advancement()
                .rootDisplay(UniverseItems.DAMAGED_MULTITOOL.get(), title("root"), description("root"),
                        Universe.id("gui/advancements/backgrounds/universe"), AdvancementType.TASK, false, false, false)
                .addCriterion("damaged_multitool", InventoryChangeTrigger.TriggerInstance.hasItems(UniverseItems.DAMAGED_MULTITOOL.get()))
                .addCriterion("multitool", InventoryChangeTrigger.TriggerInstance.hasItems(UniverseItems.MULTITOOL.get()))
                .requirements(AdvancementRequirements.Strategy.OR)
                .save(this.output, name("root"));

        // ---- 工业
        AdvancementHolder multitool = this.task(root, "repair_multitool", UniverseItems.MULTITOOL.get(), AdvancementType.TASK,
                "multitool", InventoryChangeTrigger.TriggerInstance.hasItems(UniverseItems.MULTITOOL.get()));
        AdvancementHolder power = Advancement.Builder.advancement().parent(multitool)
                .display(UniverseBlocks.WIND_TURBINE.get().asItem(), title("first_power"), description("first_power"), AdvancementType.TASK,
                        true, true, false)
                .addCriterion("wind_turbine", this.placed(UniverseBlocks.WIND_TURBINE.get()))
                .addCriterion("thermal_plant", this.placed(UniverseBlocks.THERMAL_PLANT.get()))
                .addCriterion("solar_panel", this.placed(UniverseBlocks.SOLAR_PANEL.get()))
                .requirements(AdvancementRequirements.Strategy.OR)
                .save(this.output, name("first_power"));
        this.placedTask(power, "tesla", UniverseBlocks.TESLA_TOWER.get());
        AdvancementHolder mining = this.placedTask(power, "mining", UniverseBlocks.MINING_MACHINE.get());
        AdvancementHolder smelting = this.placedTask(mining, "smelting", UniverseBlocks.ARC_SMELTER.get());
        AdvancementHolder assembling = this.placedTask(smelting, "assembling", UniverseBlocks.ASSEMBLER.get());
        AdvancementHolder sorting = this.placedTask(assembling, "sorting", UniverseBlocks.SORTER.get());
        AdvancementHolder logistics = this.placedTask(sorting, "logistics", UniverseBlocks.PLANETARY_LOGISTICS_STATION.get());
        this.placedTask(logistics, "interstellar", UniverseBlocks.INTERSTELLAR_LOGISTICS_STATION.get());
        this.task(assembling, "blueprint", UniverseItems.BLUEPRINT.get(), AdvancementType.TASK, "saved",
                MilestoneTrigger.TriggerInstance.reached(MilestoneTrigger.BLUEPRINT));
        AdvancementHolder blue = this.itemTask(assembling, "blue_matrix", UniverseItems.ELECTROMAGNETIC_MATRIX.get(), AdvancementType.TASK);
        AdvancementHolder red = this.itemTask(blue, "red_matrix", UniverseItems.ENERGY_MATRIX.get(), AdvancementType.TASK);
        AdvancementHolder yellow = this.itemTask(red, "yellow_matrix", UniverseItems.STRUCTURE_MATRIX.get(), AdvancementType.TASK);
        AdvancementHolder purple = this.itemTask(yellow, "purple_matrix", UniverseItems.INFORMATION_MATRIX.get(), AdvancementType.TASK);
        AdvancementHolder green = this.itemTask(purple, "green_matrix", UniverseItems.GRAVITY_MATRIX.get(), AdvancementType.GOAL);

        // ---- 探索
        AdvancementHolder species = this.milestone(root, "species", UniverseItems.SURVEY_DATA.get(), AdvancementType.TASK, MilestoneTrigger.SPECIES);
        this.milestone(species, "survey", UniverseItems.SURVEY_DATA.get(), AdvancementType.GOAL, MilestoneTrigger.SURVEY);
        this.milestone(species, "monolith", UniverseBlocks.MONOLITH.get().asItem(), AdvancementType.TASK, MilestoneTrigger.MONOLITH);
        this.milestone(species, "tame", UniverseItems.HERBIVORE_FEED.get(), AdvancementType.TASK, MilestoneTrigger.TAME);
        AdvancementHolder exocraft = this.milestone(species, "exocraft", UniverseItems.EXOCRAFT.get(), AdvancementType.TASK,
                MilestoneTrigger.EXOCRAFT);
        this.milestone(exocraft, "teleport", UniverseBlocks.TELEPORTER.get().asItem(), AdvancementType.TASK, MilestoneTrigger.TELEPORT);

        // ---- 太空
        AdvancementHolder ship = this.milestone(multitool, "ship", UniverseBlocks.SHIP_HELM.get().asItem(), AdvancementType.TASK,
                MilestoneTrigger.SHIP);
        AdvancementHolder space = this.milestone(ship, "space", UniverseItems.EXOSUIT_HELMET.get(), AdvancementType.TASK, MilestoneTrigger.SPACE);
        this.milestone(space, "warp", UniverseItems.SPACE_WARPER.get(), AdvancementType.GOAL, MilestoneTrigger.WARP);
        AdvancementHolder trade = this.milestone(space, "trade", UniverseItems.CIRCUIT_BOARD.get(), AdvancementType.TASK, MilestoneTrigger.TRADE);
        this.milestone(trade, "mission", Items.WRITABLE_BOOK, AdvancementType.TASK, MilestoneTrigger.MISSION);
        this.milestone(trade, "pirates", UniverseBlocks.PHOTON_CANNON.get().asItem(), AdvancementType.GOAL, MilestoneTrigger.PIRATES);

        // ---- 戴森球
        AdvancementHolder ejector = this.placedTask(red, "ejector", UniverseBlocks.EM_RAIL_EJECTOR.get());
        AdvancementHolder silo = this.placedTask(ejector, "silo", UniverseBlocks.VERTICAL_LAUNCHING_SILO.get());
        AdvancementHolder shell = this.milestone(silo, "first_shell", UniverseItems.DYSON_SPHERE_COMPONENT.get(), AdvancementType.GOAL,
                MilestoneTrigger.story(Milestone.FIRST_SHELL));
        this.placedTask(shell, "ray", UniverseBlocks.RAY_RECEIVER.get());
        AdvancementHolder antimatter = this.itemTask(green, "antimatter", UniverseItems.ANTIMATTER.get(), AdvancementType.TASK);
        this.placedTask(antimatter, "artificial_star", UniverseBlocks.ARTIFICIAL_STAR.get());
        AdvancementHolder white = this.itemTask(antimatter, "universe_matrix", UniverseItems.UNIVERSE_MATRIX.get(), AdvancementType.GOAL);
        this.milestone(white, "mission_complete", UniverseItems.COMPOSITE_MATRIX.get(), AdvancementType.CHALLENGE,
                MilestoneTrigger.story(Milestone.MISSION_COMPLETE));
    }

    private AdvancementHolder task(AdvancementHolder parent, String name, Item icon, AdvancementType type, String criterion, Criterion<?> trigger) {
        return Advancement.Builder.advancement().parent(parent)
                .display(icon, title(name), description(name), type, true, true, false)
                .addCriterion(criterion, trigger)
                .save(this.output, name(name));
    }

    private AdvancementHolder itemTask(AdvancementHolder parent, String name, Item item, AdvancementType type) {
        return this.task(parent, name, item, type, "has_item", InventoryChangeTrigger.TriggerInstance.hasItems(item));
    }

    private AdvancementHolder placedTask(AdvancementHolder parent, String name, Block block) {
        return this.task(parent, name, block.asItem(), AdvancementType.TASK, "placed", this.placed(block));
    }

    private AdvancementHolder milestone(AdvancementHolder parent, String name, ItemLike icon, AdvancementType type, String milestone) {
        return this.task(parent, name, icon.asItem(), type, "milestone", MilestoneTrigger.TriggerInstance.reached(milestone));
    }

    private Criterion<ItemUsedOnLocationTrigger.TriggerInstance> placed(Block block) {
        return ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(this.blocks, block);
    }

    private static String name(String name) {
        return Universe.MOD_ID + ":" + name;
    }

    private static Component title(String name) {
        return Component.translatable("advancements.universe." + name + ".title");
    }

    private static Component description(String name) {
        return Component.translatable("advancements.universe." + name + ".description");
    }
}
