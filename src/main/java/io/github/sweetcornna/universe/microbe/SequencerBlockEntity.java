package io.github.sweetcornna.universe.microbe;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.core.microbe.BioTime;
import io.github.sweetcornna.universe.core.microbe.Gene;
import io.github.sweetcornna.universe.core.microbe.GeneLibrary;
import io.github.sweetcornna.universe.core.microbe.MicrobeStrain;
import io.github.sweetcornna.universe.core.microbe.Organism;
import io.github.sweetcornna.universe.core.microbe.TissueGenes;
import io.github.sweetcornna.universe.discovery.Discoveries;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.fishing.FishItem;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 测序仪（ADR-0036 §五，照纳米孔测序仪，2016 年上过国际空间站）：原料格放一管菌种、一份组织样本或一条鱼，
 * 耗材格放测序芯片，现实六小时（游戏 36 秒）测完，测出的基因记进全服的基因库，测过的东西原样放进产物格。
 * 第一次测出一种外星菌，发现日志加一条"微生物"，记在最后打开界面的玩家名下，他拿勘探数据。
 */
public class SequencerBlockEntity extends BioMachineBlockEntity {
    /** 测序和比对：2 J/t（40 kW）。 */
    public static final long POWER = 2;
    /** 一次测几小时（现实时间）。纳米孔测序仪几小时就有够用的读长。 */
    public static final double SEQUENCE_HOURS = 6;
    public static final int SEQUENCE_TICKS = (int) Math.round(BioTime.ticks(SEQUENCE_HOURS));
    /** 界面数据：上一次测出的新基因（按位）、上一次的结果。 */
    public static final int LAST_GENES = BIO_DATA;
    public static final int LAST = BIO_DATA + 1;
    public static final int DATA_COUNT = BIO_DATA + 2;

    public enum Status {
        EMPTY,
        NO_CHIP,
        OUTPUT_FULL,
        WORKING
    }

    /** 上一次测的结果。 */
    public enum Last {
        NONE,
        /** 没有新东西。 */
        KNOWN,
        /** 有新基因。 */
        NEW_GENES,
        /** 认出一种新的菌（可能也有新基因）。 */
        NEW_STRAIN
    }

    private int lastGenes;
    private Last last = Last.NONE;

    public SequencerBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.SEQUENCER.get(), pos, state);
    }

    /** 一样东西测得出的基因；测不了的为空。 */
    public static Optional<Set<Gene>> genes(ItemStack stack) {
        Organism organism = StrainTubeItem.organism(stack);
        if (organism != null) {
            return Optional.of(organism.genes());
        }
        Sample sample = SampleItem.sample(stack);
        if (sample != null && sample.tissue()) {
            return Optional.of(sample.genes());
        }
        if (stack.getItem() instanceof FishItem fish) {
            return Optional.of(TissueGenes.fish(fish.species().habitat()));
        }
        return Optional.empty();
    }

    @Override
    protected long power() {
        return POWER;
    }

    @Override
    protected int total() {
        return genes(this.getItem(INPUT)).isPresent() ? SEQUENCE_TICKS : 0;
    }

    @Override
    protected int status() {
        return this.check().ordinal();
    }

    private Status check() {
        ItemStack input = this.getItem(INPUT);
        if (genes(input).isEmpty()) {
            return Status.EMPTY;
        }
        if (!this.getItem(CONSUMABLE).is(UniverseItems.SEQUENCING_CHIP.get())) {
            return Status.NO_CHIP;
        }
        if (!this.outputHasRoom(OUTPUT, input.copyWithCount(1))) {
            return Status.OUTPUT_FULL;
        }
        return Status.WORKING;
    }

    @Override
    protected boolean tickMachine() {
        if (!this.advance(this.check() == Status.WORKING)) {
            return this.moving();
        }
        ItemStack input = this.getItem(INPUT).copyWithCount(1);
        Optional<Set<Gene>> genes = genes(input);
        if (genes.isEmpty() || !(this.level instanceof ServerLevel server)) {
            return false;
        }
        Organism organism = StrainTubeItem.organism(input);
        MicrobeStrain strain = organism == null ? null : organism.strain();
        GeneLibrary.SequenceResult result = GeneLibraries.sequence(server.getServer(), strain == null ? null : strain.key(), genes.get());
        this.lastGenes = mask(result.newGenes());
        this.last = result.newStrain() ? Last.NEW_STRAIN : result.newGenes().isEmpty() ? Last.KNOWN : Last.NEW_GENES;
        this.consumeInput(INPUT, 1);
        this.consumeInput(CONSUMABLE, 1);
        this.addOutput(OUTPUT, input);
        this.play(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.7F, 1.6F);
        this.report(strain, result);
        this.setChanged();
        return true;
    }

    /** 告诉最后打开界面的玩家测出了什么；外星菌第一次测出来记进发现日志。 */
    private void report(@Nullable MicrobeStrain strain, GeneLibrary.SequenceResult result) {
        this.user().ifPresent(player -> {
            if (strain != null && result.newStrain() && !strain.earthly()) {
                DiscoveryKey.microbe(strain.key()).ifPresent(key -> Discoveries.discover(player, key));
            }
            if (!result.newGenes().isEmpty()) {
                player.sendSystemMessage(Component.translatable("message.universe.sequencer.new_genes", MicrobeText.genes(result.newGenes()))
                        .withStyle(ChatFormatting.AQUA));
            }
        });
    }

    static int mask(Set<Gene> genes) {
        int mask = 0;
        for (Gene gene : genes) {
            mask |= 1 << gene.ordinal();
        }
        return mask;
    }

    /** 界面上把按位的基因还原回来。 */
    public static Set<Gene> unmask(int mask) {
        EnumSet<Gene> set = EnumSet.noneOf(Gene.class);
        for (Gene gene : Gene.values()) {
            if ((mask & 1 << gene.ordinal()) != 0) {
                set.add(gene);
            }
        }
        return set;
    }

    // ---- 物品栏

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case INPUT -> genes(stack).isPresent();
            case CONSUMABLE -> stack.is(UniverseItems.SEQUENCING_CHIP.get());
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
        return switch (index) {
            case LAST_GENES -> this.lastGenes;
            case LAST -> this.last.ordinal();
            default -> super.dataValue(index);
        };
    }

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.SEQUENCER;
    }

    // ---- 存档

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.lastGenes = input.getIntOr("LastGenes", 0);
        int last = input.getIntOr("Last", 0);
        this.last = last >= 0 && last < Last.values().length ? Last.values()[last] : Last.NONE;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("LastGenes", this.lastGenes);
        output.putInt("Last", this.last.ordinal());
    }
}
