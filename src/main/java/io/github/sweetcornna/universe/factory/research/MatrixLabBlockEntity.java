package io.github.sweetcornna.universe.factory.research;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.research.TechTree;
import io.github.sweetcornna.universe.core.research.Upgrades;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 矩阵研究站（ADR-0011）：研究全服当前在研究的科技。每种矩阵一格，攒够一点时从每种所需的矩阵里各扣一个、
 * 给全服的进度加一点。几台研究站一起研究就快几倍。
 */
public class MatrixLabBlockEntity extends MachineBlockEntity {
    /** 戴森球计划的 480 kW。 */
    public static final long POWER = 24;
    /** 满电时攒一点的刻数。 */
    public static final int TICKS_PER_POINT = 100;
    /** 自动化（分拣器、漏斗）每格最多塞到这么多，手动放不限。 */
    public static final int AUTOMATION_LIMIT = 10;

    public static final int STATUS = BASE_DATA;
    /** 这一点的进度，千分数。 */
    public static final int PROGRESS = BASE_DATA + 1;
    public static final int DATA_COUNT = BASE_DATA + 2;

    public static final int IDLE = 0;
    public static final int RUNNING = 1;
    public static final int NO_MATRIX = 2;
    public static final int NO_POWER = 3;

    private double progress;
    private boolean active;
    private double supplied;
    private int status = IDLE;

    public MatrixLabBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.MATRIX_LAB.get(), pos, state, TechTree.MATRICES.size());
    }

    /** 这一格放哪种矩阵。 */
    public static String matrix(int slot) {
        return TechTree.MATRICES.get(slot);
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
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }
        List<String> required = Research.state(serverLevel).currentMatrices(Research.TREE);
        if (required.isEmpty()) {
            this.active = false;
            this.status = IDLE;
            return false;
        }
        if (!this.hasAll(required)) {
            this.active = false;
            this.status = NO_MATRIX;
            return false;
        }
        this.active = true;
        if (rate <= 0) {
            this.status = NO_POWER;
            return false;
        }
        this.status = RUNNING;
        this.progress += rate * Upgrades.researchSpeed(Research.level(serverLevel, Upgrades.RESEARCH_SPEED));
        if (this.progress >= TICKS_PER_POINT) {
            this.progress -= TICKS_PER_POINT;
            for (String id : required) {
                this.getItem(TechTree.MATRICES.indexOf(id)).shrink(1);
            }
            Research.addPoint(serverLevel.getServer());
        }
        this.setChanged();
        return true;
    }

    private boolean hasAll(List<String> required) {
        for (String id : required) {
            int slot = TechTree.MATRICES.indexOf(id);
            if (slot < 0 || this.getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected boolean isInput(int slot) {
        return true;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot < TechTree.MATRICES.size() && stack.is(Research.item(matrix(slot)));
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return super.canPlaceItemThroughFace(slot, stack, side) && this.getItem(slot).getCount() < AUTOMATION_LIMIT;
    }

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.MATRIX_LAB;
    }

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        return switch (index) {
            case STATUS -> this.status;
            case PROGRESS -> (int) (this.progress * 1000 / TICKS_PER_POINT);
            default -> super.dataValue(index);
        };
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
