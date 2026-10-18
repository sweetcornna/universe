package io.github.sweetcornna.universe.factory.resource;

import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 原油萃取站（ADR-0011）：两格高，下半截要压在原油涌泉上。产量由涌泉决定，越抽越少，降到一个下限为止。
 */
public class OilExtractorBlockEntity extends MachineBlockEntity {
    /** 戴森球计划的 840 kW。 */
    public static final long POWER = 42;
    public static final int OUTPUT = 0;

    /** 涌泉现在的产量（个/秒 × 100），没压在涌泉上时是 0。 */
    public static final int RATE = BASE_DATA;
    public static final int DATA_COUNT = BASE_DATA + 1;

    private double seepRate;
    private int checkTimer;
    private double progress;
    private boolean active;
    private double supplied;

    public OilExtractorBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.OIL_EXTRACTOR.get(), pos, state, 1);
    }

    @Override
    public long demand() {
        return this.active ? POWER : 0;
    }

    @Override
    public void settle(double satisfaction, double load, long stored) {
        super.settle(satisfaction, load, stored);
        this.supplied = this.active ? satisfaction : 0;
    }

    @Override
    protected boolean tickMachine() {
        double rate = this.supplied;
        this.supplied = 0;
        ServerLevel level = (ServerLevel) this.level;
        BlockPos seep = this.worldPosition.below();
        if (--this.checkTimer <= 0) {
            this.checkTimer = 40;
            this.seepRate = level.getBlockState(seep).is(UniverseBlocks.CRUDE_OIL_SEEP) ? VeinNodes.seepRate(level, seep) : 0;
        }
        this.pushOutput(OUTPUT, this.getBlockState().getValue(MachineBlock.FACING), 4);
        ItemStack oil = new ItemStack(UniverseItems.CRUDE_OIL.get());
        this.active = this.seepRate > 0 && this.outputHasRoom(OUTPUT, oil);
        if (!this.active || rate <= 0) {
            return false;
        }
        this.progress += this.seepRate / 20 * rate;
        if (this.progress >= 1) {
            this.progress -= 1;
            VeinNodes.extractSeep(level, seep);
            this.addOutput(OUTPUT, oil);
        }
        this.setChanged();
        return true;
    }

    @Override
    protected boolean isOutput(int slot) {
        return slot == OUTPUT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.OIL_EXTRACTOR;
    }

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        return index == RATE ? (int) Math.round(this.seepRate * 100) : super.dataValue(index);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getDoubleOr("Progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("Progress", this.progress);
    }
}
