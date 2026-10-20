package io.github.sweetcornna.universe.teleport;

import io.github.sweetcornna.universe.core.teleport.TeleportRules;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.power.LevelPower;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 传送器（ADR-0017）：接电网充电，充满 {@link TeleportRules#CHARGE} 焦能传送一次。空间站里的传送器不接电网，随时能用。
 * 没有物品栏，右键打开的是选目的地的界面（{@link Teleporters#open}），不是设备界面。
 */
public class TeleporterBlockEntity extends MachineBlockEntity {
    private long energy;

    public TeleporterBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.TELEPORTER.get(), pos, state, 0);
    }

    /** 空间站里的那种：不用电。 */
    public boolean free() {
        return this.getBlockState().is(UniverseBlocks.STATION_TELEPORTER.get());
    }

    public long energy() {
        return this.free() ? TeleportRules.CHARGE : this.energy;
    }

    public boolean charged() {
        return this.energy() >= TeleportRules.CHARGE;
    }

    /** 传送了一次，电放光。 */
    void discharge() {
        if (!this.free() && this.energy != 0) {
            this.energy = 0;
            this.setChanged();
        }
    }

    @Override
    protected void joinGrid(LevelPower power) {
        if (!this.free()) {
            super.joinGrid(power);
        }
    }

    @Override
    public long demand() {
        return this.free() ? 0 : TeleportRules.demand(this.energy);
    }

    @Override
    public void settle(double satisfaction, double load, long stored) {
        long before = this.energy;
        this.energy = TeleportRules.charge(this.energy, satisfaction);
        super.settle(satisfaction, load, stored);
        if (this.energy != before) {
            this.setChanged();
        }
    }

    @Override
    protected boolean tickMachine() {
        // 充满了亮灯
        return this.charged();
    }

    @Override
    protected MachineMenu.Type menuType() {
        throw new UnsupportedOperationException("传送器用自己的界面");
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.energy = Math.max(0, Math.min(TeleportRules.CHARGE, input.getLongOr("Energy", 0)));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("Energy", this.energy);
    }
}
