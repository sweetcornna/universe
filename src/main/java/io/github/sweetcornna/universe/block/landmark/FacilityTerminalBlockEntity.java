package io.github.sweetcornna.universe.block.landmark;

import io.github.sweetcornna.universe.poi.OutpostInteractions;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 制造设施终端的方块实体（ADR-0026）：记着门外的守卫放过没有。每两秒看一眼附近有没有人，有人走近就放一次，以后不再放。
 */
public class FacilityTerminalBlockEntity extends BlockEntity {
    private static final int CHECK_INTERVAL = 40;
    private boolean guarded;

    public FacilityTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.FACILITY_TERMINAL.get(), pos, state);
    }

    void serverTick(ServerLevel level) {
        if (this.guarded || (level.getGameTime() + this.getBlockPos().asLong()) % CHECK_INTERVAL != 0) {
            return;
        }
        if (OutpostInteractions.postGuards(level, this.getBlockPos(), this.getBlockState())) {
            this.guarded = true;
            this.setChanged();
        }
    }

    public boolean guarded() {
        return this.guarded;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("Guarded", this.guarded);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.guarded = input.getBooleanOr("Guarded", false);
    }
}
