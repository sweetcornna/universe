package io.github.sweetcornna.universe.block;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.ecology.CreatureTraits;
import io.github.sweetcornna.universe.core.ecology.GeneEdit;
import io.github.sweetcornna.universe.entity.CreatureEggs;
import io.github.sweetcornna.universe.entity.CreatureEggs.Egg;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.power.LevelPower;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import io.github.sweetcornna.universe.registry.sound.MachineSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 卵序列仪（ADR-0026 §九）：一个卵格、一个材料格、一个产物格。放进卵和材料，5 秒改好一个卵，放进产物格；
 * 改不动的（体型到头了、已经是那种颜色或性情）不动手，也不收材料。不接电网。
 */
public class EggSequencerBlockEntity extends MachineBlockEntity {
    public static final int EGG = 0;
    public static final int MATERIAL = 1;
    public static final int OUTPUT = 2;
    /** 界面数据：改了几刻。 */
    public static final int PROGRESS = BASE_DATA;
    public static final int DATA_COUNT = BASE_DATA + 1;

    private int progress;

    public EggSequencerBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.EGG_SEQUENCER.get(), pos, state, 3);
    }

    /** 卵格和材料格里的东西改出来是什么性状；改不了的为空。客户端的界面也按它显示。 */
    public static Optional<CreatureTraits> result(ItemStack egg, ItemStack material) {
        Egg data = CreatureEggs.egg(egg);
        return data == null ? Optional.empty() : CreatureEggs.edit(material).flatMap(edit -> edit.apply(data.traits()));
    }

    /** 改好的卵（一个）；改不了的为空。 */
    public static ItemStack product(ItemStack egg, ItemStack material) {
        Egg data = CreatureEggs.egg(egg);
        return data == null ? ItemStack.EMPTY
                : result(egg, material).map(traits -> CreatureEggs.stack(data.withTraits(traits))).orElse(ItemStack.EMPTY);
    }

    @Override
    protected boolean tickMachine() {
        ItemStack product = product(this.getItem(EGG), this.getItem(MATERIAL));
        if (product.isEmpty() || !this.outputHasRoom(OUTPUT, product)) {
            if (this.progress != 0) {
                this.progress = 0;
                this.setChanged();
            }
            return false;
        }
        this.progress++;
        if (this.progress >= GeneEdit.TICKS) {
            this.progress = 0;
            this.consumeInput(EGG, 1);
            this.consumeInput(MATERIAL, 1);
            this.addOutput(OUTPUT, product);
            if (this.level != null) {
                this.level.playSound(null, this.worldPosition, MachineSounds.SEQUENCER_DONE.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            this.setChanged();
        } else {
            this.markUnsaved();
        }
        return true;
    }

    // ---- 不接电网

    @Override
    protected void joinGrid(LevelPower power) {
    }

    @Override
    protected void leaveGrid(LevelPower power) {
    }

    @Override
    public long demand() {
        return 0;
    }

    // ---- 物品栏

    @Override
    protected boolean isInput(int slot) {
        return slot == EGG || slot == MATERIAL;
    }

    @Override
    protected boolean isOutput(int slot) {
        return slot == OUTPUT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case EGG -> CreatureEggs.egg(stack) != null;
            case MATERIAL -> CreatureEggs.edit(stack).isPresent();
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
        return index == PROGRESS ? this.progress : super.dataValue(index);
    }

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.EGG_SEQUENCER;
    }

    // ---- 存档

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getIntOr("Progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Progress", this.progress);
    }

    /** 给 GameTest 用。 */
    public @Nullable Egg output() {
        return CreatureEggs.egg(this.getItem(OUTPUT));
    }
}
