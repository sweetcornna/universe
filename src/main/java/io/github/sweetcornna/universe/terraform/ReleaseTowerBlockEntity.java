package io.github.sweetcornna.universe.terraform;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.chem.Gas;
import io.github.sweetcornna.universe.core.chem.Substances;
import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.terraform.Terraform;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import io.github.sweetcornna.universe.stats.ProductionLog;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 大气释放塔（ADR-0036 §六）：放在能改造的星球地表，头顶要看得到天。四个原料格放任意气体物品，通上电每秒往大气里放一个
 * （{@link Terraform#addGas}，一个物品就是一个单位）。放什么气体都行，往能呼吸的星球里放二氧化硫，它也会变得有毒。
 */
public class ReleaseTowerBlockEntity extends MachineBlockEntity {
    public static final int SLOTS = 4;
    /** 用电（焦耳/刻）：界面上是 1.2 kW，照戴森球计划的 1.2 MW 折算。压缩机把气体打到高空。 */
    public static final long POWER = 60;
    /** 满电时放一个要几刻。 */
    public static final int TICKS_PER_ITEM = 20;
    /** 多久重新看一次头顶和所在的星球（刻）。 */
    private static final int CHECK_INTERVAL = 40;

    /** 界面数据：状态（{@link #OK} 等）。 */
    public static final int STATUS = BASE_DATA;
    /** 正在放的气体的序号 + 1，没有为 0。 */
    public static final int GAS = BASE_DATA + 1;
    /** 这座塔一共放了多少单位，两格。 */
    public static final int RELEASED = BASE_DATA + 2;
    public static final int DATA_COUNT = BASE_DATA + 4;

    public static final int OK = 0;
    /** 不在能改造的星球上（母星、太空、气态巨行星）。 */
    public static final int NOT_TERRAFORMABLE = 1;
    /** 头顶有遮挡。 */
    public static final int NO_SKY = 2;
    /** 原料格里没有气体。 */
    public static final int NO_GAS = 3;

    /** 物品到气体，第一次用时建表（物品注册完以后）。 */
    private static @Nullable Map<Item, Gas> gases;

    private @Nullable BodyId target;
    private boolean sky;
    private int checkTimer;
    private double progress;
    private boolean active;
    private double supplied;
    private long released;
    /** 游戏内测试用：不管在哪个维度，往这颗星球里放。 */
    private @Nullable BodyId forced;

    public ReleaseTowerBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.ATMOSPHERE_RELEASE_TOWER.get(), pos, state, SLOTS);
    }

    /** 这个物品是哪种气体；不是气体为空。 */
    public static Optional<Gas> gas(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        Map<Item, Gas> table = gases;
        if (table == null) {
            table = new HashMap<>();
            for (Gas gas : Gas.values()) {
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(Substances.item(gas)));
                if (item != Items.AIR) {
                    table.put(item, gas);
                }
            }
            gases = table;
        }
        return Optional.ofNullable(table.get(stack.getItem()));
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
        if (--this.checkTimer <= 0) {
            this.checkTimer = CHECK_INTERVAL;
            this.check();
        }
        int slot = this.nextSlot();
        this.active = this.target != null && this.sky && slot >= 0;
        if (!this.active || rate <= 0) {
            return false;
        }
        this.progress += rate;
        if (this.progress >= TICKS_PER_ITEM) {
            this.progress -= TICKS_PER_ITEM;
            this.release(slot);
            this.setChanged();
        } else {
            this.markUnsaved();
        }
        return true;
    }

    /** 放一个出去。 */
    private void release(int slot) {
        if (!(this.level instanceof ServerLevel level) || this.target == null) {
            return;
        }
        ItemStack stack = this.getItem(slot);
        Optional<Gas> gas = gas(stack);
        if (gas.isEmpty()) {
            return;
        }
        Item item = stack.getItem();
        this.consumeInput(slot, 1);
        ProductionLog.consumed(level, item, 1);
        Terraforming.addGas(level.getServer(), this.target, gas.get(), 1);
        this.released++;
    }

    /** 下一个要放的气体在哪一格；都空着为 -1。 */
    private int nextSlot() {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (gas(this.getItem(slot)).isPresent()) {
                return slot;
            }
        }
        return -1;
    }

    /** 重新看一次所在的星球和头顶。 */
    private void check() {
        if (!(this.level instanceof ServerLevel level)) {
            return;
        }
        if (this.forced != null) {
            this.target = this.forced;
            this.sky = true;
            return;
        }
        this.target = Planets.planet(level).filter(Terraform::terraformable).map(Planet::id).orElse(null);
        this.sky = level.dimensionType().hasSkyLight() && level.canSeeSky(this.worldPosition.above());
    }

    /** 游戏内测试用：往这颗星球里放，不看所在的维度和头顶。 */
    public void forceTarget(BodyId planet) {
        this.forced = planet;
        this.checkTimer = 0;
    }

    public long released() {
        return this.released;
    }

    private int status() {
        if (this.target == null) {
            return NOT_TERRAFORMABLE;
        }
        if (!this.sky) {
            return NO_SKY;
        }
        return this.nextSlot() < 0 ? NO_GAS : OK;
    }

    @Override
    protected boolean isInput(int slot) {
        return slot >= 0 && slot < SLOTS;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return this.isInput(slot) && gas(stack).isPresent();
    }

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.ATMOSPHERE_RELEASE_TOWER;
    }

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        if (index == STATUS) {
            return this.status();
        }
        if (index == GAS) {
            int slot = this.nextSlot();
            return slot < 0 ? 0 : gas(this.getItem(slot)).map(gas -> gas.ordinal() + 1).orElse(0);
        }
        if (index == RELEASED || index == RELEASED + 1) {
            return index == RELEASED ? MachineMenu.low(this.released) : MachineMenu.high(this.released);
        }
        return super.dataValue(index);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getDoubleOr("Progress", 0);
        this.released = input.getLongOr("Released", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("Progress", this.progress);
        output.putLong("Released", this.released);
    }
}
