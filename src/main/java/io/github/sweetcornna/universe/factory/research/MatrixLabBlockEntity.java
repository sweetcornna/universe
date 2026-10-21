package io.github.sweetcornna.universe.factory.research;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.factory.Proliferation;
import io.github.sweetcornna.universe.core.factory.Throughput;
import io.github.sweetcornna.universe.core.research.TechTree;
import io.github.sweetcornna.universe.core.research.Upgrades;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 矩阵研究站（ADR-0011）：研究全服当前在研究的科技。每种矩阵一格，攒够一点时从每种所需的矩阵里各扣一个、
 * 给全服的进度加一点。几台研究站一起研究就快几倍。
 *
 * <p>喷过增产剂的矩阵（ADR-0022）：选“额外产出”时按等级多出研究点数，选“加速”时研究得更快，都更费电。
 *
 * <p>自我演化研究站也用这个方块实体，研究速度是三倍。研究站可以竖着叠：上面那台每秒从正下方那台拿矩阵，两台的数量
 * 拉平，所以只要给最下面一台送料（ADR-0022）。
 */
public class MatrixLabBlockEntity extends MachineBlockEntity implements MachineMenu.ButtonTarget {
    /** 戴森球计划的 480 kW。 */
    public static final long POWER = 24;
    /** 自我演化研究站：戴森球计划的 1.92 MW，研究速度三倍。 */
    public static final long EVOLUTION_POWER = 96;
    public static final double EVOLUTION_SPEED = 3;
    /** 满电时攒一点的刻数。 */
    public static final int TICKS_PER_POINT = Throughput.LAB_TICKS_PER_POINT;
    /** 自动化（分拣器、漏斗）每格最多塞到这么多，手动放不限。 */
    public static final int AUTOMATION_LIMIT = 10;

    public static final int STATUS = BASE_DATA;
    /** 这一点的进度，千分数。 */
    public static final int PROGRESS = BASE_DATA + 1;
    /** 增产：第 0 位是加速模式，第 1–3 位是矩阵的等级。 */
    public static final int PROLIFERATION = BASE_DATA + 2;
    public static final int DATA_COUNT = BASE_DATA + 3;
    public static final int MODE_BUTTON = 0;

    public static final int IDLE = 0;
    public static final int RUNNING = 1;
    public static final int NO_MATRIX = 2;
    public static final int NO_POWER = 3;

    private double progress;
    private boolean active;
    private double supplied;
    private int status = IDLE;
    private Proliferation.Mode mode = Proliferation.Mode.EXTRA_PRODUCTS;
    private final Proliferation.ExtraProgress extra = new Proliferation.ExtraProgress();
    private int batchLevel;

    public MatrixLabBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.MATRIX_LAB.get(), pos, state, TechTree.MATRICES.size());
    }

    /** 这一格放哪种矩阵。 */
    public static String matrix(int slot) {
        return TechTree.MATRICES.get(slot);
    }

    /** 是不是自我演化研究站。 */
    public boolean evolution() {
        return this.getBlockState().is(UniverseBlocks.SELF_EVOLUTION_LAB.get());
    }

    @Override
    public long demand() {
        return this.active ? Math.round((this.evolution() ? EVOLUTION_POWER : POWER) * Proliferation.powerFactor(this.batchLevel)) : 0;
    }

    public Proliferation.Mode mode() {
        return this.mode;
    }

    @Override
    public boolean clickButton(ServerPlayer player, int button) {
        if (button != MODE_BUTTON) {
            return false;
        }
        this.mode = this.mode.next();
        this.setChanged();
        return true;
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
        if (serverLevel.getGameTime() % 20 == Math.floorMod(this.worldPosition.asLong(), 20)) {
            this.pullFromBelow(serverLevel);
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
        this.batchLevel = Proliferation.MAX_LEVEL;
        for (String id : required) {
            this.batchLevel = Math.min(this.batchLevel, this.inputLevel(TechTree.MATRICES.indexOf(id)));
        }
        if (rate <= 0) {
            this.status = NO_POWER;
            return false;
        }
        this.status = RUNNING;
        this.progress += rate * Upgrades.researchSpeed(Research.level(serverLevel, Upgrades.RESEARCH_SPEED))
                * Proliferation.speedFactor(this.mode, this.batchLevel) * (this.evolution() ? EVOLUTION_SPEED : 1);
        if (this.progress >= TICKS_PER_POINT) {
            this.progress -= TICKS_PER_POINT;
            int level = Proliferation.MAX_LEVEL;
            for (String id : required) {
                level = Math.min(level, this.consumeInput(TechTree.MATRICES.indexOf(id), 1));
            }
            int points = 1 + (this.mode == Proliferation.Mode.EXTRA_PRODUCTS ? this.extra.craft(level) : 0);
            for (int i = 0; i < points; i++) {
                Research.addPoint(serverLevel.getServer());
            }
        }
        this.setChanged();
        return true;
    }

    /** 从正下方的研究站拿矩阵，两台的数量拉平；喷过的点数跟着走。 */
    private void pullFromBelow(ServerLevel level) {
        if (!(level.getBlockEntity(this.worldPosition.below()) instanceof MatrixLabBlockEntity below)) {
            return;
        }
        for (int slot = 0; slot < this.getContainerSize(); slot++) {
            ItemStack own = this.getItem(slot);
            ItemStack theirs = below.getItem(slot);
            int n = Math.min(AUTOMATION_LIMIT - own.getCount(), (theirs.getCount() - own.getCount() + 1) / 2);
            if (n <= 0 || !own.isEmpty() && !ItemStack.isSameItemSameComponents(own, theirs)) {
                continue;
            }
            Item item = theirs.getItem();
            long before = below.pool(slot).points();
            below.consumeInput(slot, n);
            long points = before - below.pool(slot).points();
            this.pool(slot);
            if (own.isEmpty()) {
                this.setItem(slot, new ItemStack(item, n));
            } else {
                own.grow(n);
            }
            this.receiveInput(slot, item, n, points);
            this.setChanged();
            below.setChanged();
        }
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
            case PROLIFERATION -> (this.mode == Proliferation.Mode.SPEEDUP ? 1 : 0) | this.batchLevel << 1;
            default -> super.dataValue(index);
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getDoubleOr("Progress", 0);
        this.mode = input.getBooleanOr("Speedup", false) ? Proliferation.Mode.SPEEDUP : Proliferation.Mode.EXTRA_PRODUCTS;
        this.extra.set(input.getIntOr("ExtraProgress", 0));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("Progress", this.progress);
        output.putBoolean("Speedup", this.mode == Proliferation.Mode.SPEEDUP);
        output.putInt("ExtraProgress", this.extra.milli());
    }
}
