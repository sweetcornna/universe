package io.github.sweetcornna.universe.factory.belt;

import java.util.List;

import io.github.sweetcornna.universe.core.factory.BeltLine;
import io.github.sweetcornna.universe.core.factory.Cargo;
import io.github.sweetcornna.universe.core.research.Upgrades;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 自动集装机（ADR-0022）：串在传送带中间，把同一种物品叠成一格再放出去，叠几个看"集装物流"研究到第几级。凑不齐时
 * 最多等 1 秒；来了别的物品就先把手上这格放出去。
 */
public class PilerBlockEntity extends BeltPassBlockEntity {
    /** 戴森球计划的 160 kW。 */
    public static final long POWER = 8;
    /** 凑不齐时最多等几刻。 */
    public static final int MAX_WAIT = 20;

    /** 正在叠的那一格；没有是 {@link BeltLine#EMPTY}。 */
    private int pending = BeltLine.EMPTY;
    private int waited;

    public PilerBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.AUTOMATIC_PILER.get(), pos, state);
    }

    /** 一直接电（不按有没有货算），原因见喷涂机的 {@code wantsPower}。 */
    @Override
    protected long power() {
        return POWER;
    }

    /** 现在能叠几个。 */
    public int limit() {
        return this.level instanceof ServerLevel serverLevel
                ? Upgrades.beltStack(Research.level(serverLevel, Upgrades.BELT_STACKING)) : Upgrades.beltStack(0);
    }

    @Override
    public boolean acceptFromBelt(int cargo) {
        int limit = this.limit();
        if (!this.lastPowered || limit <= 1) {
            // 没电或者还不能叠：原样放过去
            return super.acceptFromBelt(cargo);
        }
        if (this.pending == BeltLine.EMPTY) {
            if (Cargo.count(cargo) >= limit) {
                return super.acceptFromBelt(cargo);
            }
            this.pending = cargo;
            this.waited = 0;
            this.setChanged();
            return true;
        }
        if (Cargo.item(cargo) != Cargo.item(this.pending)) {
            // 来了别的：先把手上这格放出去
            if (this.output.size() >= BUFFER) {
                return false;
            }
            this.output.enqueue(this.pending);
            this.pending = cargo;
            this.waited = 0;
            this.setChanged();
            return true;
        }
        int room = limit - Cargo.count(this.pending);
        if (Cargo.count(cargo) <= room) {
            this.pending = Cargo.merge(this.pending, cargo);
        } else {
            // 叠满了一格，多出来的另起一格
            if (this.output.size() >= BUFFER) {
                return false;
            }
            int[] parts = Cargo.split(cargo, room);
            this.output.enqueue(Cargo.merge(this.pending, parts[0]));
            this.pending = parts[1];
            this.waited = 0;
        }
        if (this.pending != BeltLine.EMPTY && Cargo.count(this.pending) >= limit && this.output.size() < BUFFER) {
            this.output.enqueue(this.pending);
            this.pending = BeltLine.EMPTY;
        }
        this.setChanged();
        return true;
    }

    @Override
    protected void tick() {
        if (this.pending != BeltLine.EMPTY && ++this.waited >= MAX_WAIT && this.output.size() < BUFFER) {
            this.output.enqueue(this.pending);
            this.pending = BeltLine.EMPTY;
            this.setChanged();
        }
    }

    @Override
    protected List<ItemStack> contents() {
        List<ItemStack> out = super.contents();
        if (this.pending != BeltLine.EMPTY) {
            out.addAll(BeltNetwork.stacks(this.pending));
        }
        return out;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        int[] pending = loadCargo(input, "Pending");
        this.pending = pending.length > 0 ? pending[0] : BeltLine.EMPTY;
        this.waited = input.getIntOr("Waited", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        saveCargo(output, "Pending", this.pending == BeltLine.EMPTY ? new int[0] : new int[] {this.pending});
        output.putInt("Waited", this.waited);
    }
}
