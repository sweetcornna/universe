package io.github.sweetcornna.universe.block.ship;

import io.github.sweetcornna.universe.core.ship.ShipStats;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** 燃料舱里的燃料。 */
public class FuelTankBlockEntity extends BlockEntity {
    private double fuel;

    public FuelTankBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.FUEL_TANK.get(), pos, state);
    }

    public double fuel() {
        return this.fuel;
    }

    public void setFuel(double fuel) {
        this.fuel = Math.max(0, Math.min(this.capacity(), fuel));
        this.setChanged();
    }

    /** 这个燃料舱能装多少（Mk.II 更多）。 */
    public double capacity() {
        return this.getBlockState().getBlock() instanceof FuelTankBlock tank ? tank.capacity() : ShipStats.FUEL_PER_TANK;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.fuel = input.getDoubleOr("Fuel", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("Fuel", this.fuel);
    }
}
