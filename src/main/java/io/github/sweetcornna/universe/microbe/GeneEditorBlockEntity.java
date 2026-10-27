package io.github.sweetcornna.universe.microbe;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.ecology.ResourcePlant;
import io.github.sweetcornna.universe.core.microbe.BioTime;
import io.github.sweetcornna.universe.core.microbe.EngineeredPlant;
import io.github.sweetcornna.universe.core.microbe.Gene;
import io.github.sweetcornna.universe.core.microbe.GeneEditing;
import io.github.sweetcornna.universe.core.microbe.Organism;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import io.github.sweetcornna.universe.stats.ProductionLog;
import io.github.sweetcornna.universe.survival.Plantable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 基因编辑台（ADR-0036 §五，照 CRISPR-Cas9）：原料格放要改的东西（一管菌种、资源植物的产物、一株改造种苗），
 * 界面里从基因库选一个基因，耗材格放编辑试剂。现实十二小时（游戏 72 秒）后做出改好的菌种，或者一株改造种苗。
 * 一样东西最多加 {@link Gene#MAX_ADDED} 个外来基因，已经有的加不进去（{@link GeneEditing#check}）。
 */
public class GeneEditorBlockEntity extends BioMachineBlockEntity {
    /** 电穿孔、PCR、培养：3 J/t（60 kW）。 */
    public static final long POWER = 3;
    /** 一次编辑几小时（现实时间）：转化加筛选，现实要一两天，这里压到半天。 */
    public static final double EDIT_HOURS = 12;
    public static final int EDIT_TICKS = (int) Math.round(BioTime.ticks(EDIT_HOURS));
    /** 界面数据：选中的基因（序号，没选为 -1）。 */
    public static final int SELECTED = BIO_DATA;
    public static final int DATA_COUNT = BIO_DATA + 1;

    public enum Status {
        EMPTY,
        /** 盐晶簇这类矿物，没有基因可改。 */
        NOT_EDITABLE,
        NO_GENE,
        NOT_IN_LIBRARY,
        ALREADY_PRESENT,
        FULL,
        NO_KIT,
        OUTPUT_FULL,
        WORKING
    }

    /** 原料格里的东西：一管菌，或者一株植物。 */
    public sealed interface Target permits StrainTarget, PlantTarget {
        GeneEditing.Result canAdd(Gene gene);

        /** 改好的东西（一个）。 */
        ItemStack edited(Gene gene);
    }

    public record StrainTarget(Organism organism) implements Target {
        @Override
        public GeneEditing.Result canAdd(Gene gene) {
            return this.organism.canAdd(gene);
        }

        @Override
        public ItemStack edited(Gene gene) {
            return StrainTubeItem.stack(this.organism.withGene(gene), 1);
        }
    }

    public record PlantTarget(EngineeredPlant plant) implements Target {
        @Override
        public GeneEditing.Result canAdd(Gene gene) {
            return this.plant.canAdd(gene);
        }

        @Override
        public ItemStack edited(Gene gene) {
            return EngineeredSeedlingItem.stack(this.plant.withGene(gene), 1);
        }
    }

    private @Nullable Gene selected;

    public GeneEditorBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.GENE_EDITOR.get(), pos, state);
    }

    /** 这样东西能不能放上编辑台：菌种、改造种苗、资源植物的产物（矿物放得上去，但改不了）。 */
    public static boolean accepts(ItemStack stack) {
        return StrainTubeItem.organism(stack) != null || EngineeredSeedlingItem.plant(stack) != null
                || stack.getItem() instanceof Plantable plantable && plantable.plant().isPresent();
    }

    /** 要改的东西；矿物和放不上的为空。 */
    public static Optional<Target> target(ItemStack stack) {
        Organism organism = StrainTubeItem.organism(stack);
        if (organism != null) {
            return Optional.of(new StrainTarget(organism));
        }
        EngineeredPlant plant = EngineeredSeedlingItem.plant(stack);
        if (plant != null) {
            return Optional.of(new PlantTarget(plant));
        }
        if (stack.getItem() instanceof Plantable plantable) {
            Optional<ResourcePlant> resource = plantable.plant().filter(EngineeredPlant::editable);
            return resource.map(p -> new PlantTarget(new EngineeredPlant(p, List.of())));
        }
        return Optional.empty();
    }

    public @Nullable Gene selected() {
        return this.selected;
    }

    @Override
    protected long power() {
        return POWER;
    }

    @Override
    protected int total() {
        return target(this.getItem(INPUT)).isPresent() ? EDIT_TICKS : 0;
    }

    @Override
    protected int status() {
        return this.check().ordinal();
    }

    private Status check() {
        ItemStack input = this.getItem(INPUT);
        if (input.isEmpty()) {
            return Status.EMPTY;
        }
        Optional<Target> target = target(input);
        if (target.isEmpty()) {
            return Status.NOT_EDITABLE;
        }
        if (this.selected == null) {
            return Status.NO_GENE;
        }
        if (this.level instanceof ServerLevel server && !GeneLibraries.library(server.getServer()).has(this.selected)) {
            return Status.NOT_IN_LIBRARY;
        }
        GeneEditing.Result result = target.get().canAdd(this.selected);
        if (result == GeneEditing.Result.ALREADY_PRESENT) {
            return Status.ALREADY_PRESENT;
        }
        if (result == GeneEditing.Result.FULL) {
            return Status.FULL;
        }
        if (!this.getItem(CONSUMABLE).is(UniverseItems.EDITING_KIT.get())) {
            return Status.NO_KIT;
        }
        if (!this.outputHasRoom(OUTPUT, target.get().edited(this.selected))) {
            return Status.OUTPUT_FULL;
        }
        return Status.WORKING;
    }

    @Override
    protected boolean tickMachine() {
        if (!this.advance(this.check() == Status.WORKING)) {
            return this.moving();
        }
        Optional<Target> target = target(this.getItem(INPUT));
        if (target.isEmpty() || this.selected == null) {
            return false;
        }
        ItemStack out = target.get().edited(this.selected);
        this.consumeInput(INPUT, 1);
        this.consumeInput(CONSUMABLE, 1);
        this.addOutput(OUTPUT, out);
        ProductionLog.produced(this.level, out.getItem(), 1);
        this.play(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.7F, 1.2F);
        this.setChanged();
        return true;
    }

    /** 按钮：基因的序号，选中它；再点一次取消。只能选库里有的。 */
    @Override
    public boolean clickButton(ServerPlayer player, int button) {
        if (button < 0 || button >= Gene.values().length) {
            return false;
        }
        Gene gene = Gene.values()[button];
        if (gene == this.selected) {
            this.selected = null;
        } else if (GeneLibraries.library(player.level().getServer()).has(gene)) {
            this.selected = gene;
        } else {
            return false;
        }
        this.progress = 0;
        this.setChanged();
        return true;
    }

    // ---- 物品栏

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case INPUT -> accepts(stack);
            case CONSUMABLE -> stack.is(UniverseItems.EDITING_KIT.get());
            default -> false;
        };
    }

    // ---- 界面

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        return index == SELECTED ? (this.selected == null ? -1 : this.selected.ordinal()) : super.dataValue(index);
    }

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.GENE_EDITOR;
    }

    // ---- 存档

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.selected = input.getString("Gene").map(Gene::byName).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (this.selected != null) {
            output.putString("Gene", this.selected.name());
        }
    }

    /** 给 GameTest 用。 */
    public void select(@Nullable Gene gene) {
        this.selected = gene;
        this.setChanged();
    }
}
