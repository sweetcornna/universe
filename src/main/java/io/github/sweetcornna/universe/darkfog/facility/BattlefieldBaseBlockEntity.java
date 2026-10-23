package io.github.sweetcornna.universe.darkfog.facility;

import java.util.List;

import io.github.sweetcornna.universe.blueprint.BlueprintSettings;
import io.github.sweetcornna.universe.core.darkfog.FacilityHealth;
import io.github.sweetcornna.universe.core.darkfog.PickupFilter;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 战场分析基地（ADR-0028 §五）：接电网，30 格内自动修掉血的设施、用储物格里的方块按残影重建，45 格内自动捡黑雾的掉落
 * 和散落的物品。捡什么可以在界面上换（全部、只捡黑雾掉落、只捡储物格里已有的、不捡）。储物格分拣器、漏斗都能进出。
 * 蓝图记下捡什么（ADR-0030）。
 */
public class BattlefieldBaseBlockEntity extends MachineBlockEntity implements MachineMenu.ButtonTarget, BlueprintSettings {
    public static final int SLOTS = 27;
    public static final double REPAIR_RANGE = 30;
    public static final double PICKUP_RANGE = 45;
    /** 待机、干活时的功率（焦耳/刻）：60 W、1.2 kW。 */
    private static final long IDLE_POWER = 3;
    private static final long WORK_POWER = 60;
    /** 多少刻干一轮活。 */
    private static final int INTERVAL = 10;
    /** 一轮最多捡几堆。 */
    private static final int PICKUPS = 8;
    /** 界面数据：捡东西的模式、范围内待修的设施数、残影数。 */
    public static final int FILTER = BASE_DATA;
    public static final int DAMAGED = BASE_DATA + 1;
    public static final int GHOSTS = BASE_DATA + 2;
    public static final int DATA_COUNT = BASE_DATA + 3;
    /** 界面按钮：换捡东西的模式。 */
    public static final int BUTTON_FILTER = 0;

    private PickupFilter filter = PickupFilter.ALL;
    private boolean busy;
    private int damaged;
    private int ghosts;
    private int timer;
    private int round;

    public BattlefieldBaseBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.BATTLEFIELD_ANALYSIS_BASE.get(), pos, state, SLOTS);
        this.timer = Math.floorMod(pos.hashCode(), INTERVAL);
    }

    public PickupFilter filter() {
        return this.filter;
    }

    @Override
    public long demand() {
        return this.busy ? WORK_POWER : IDLE_POWER;
    }

    @Override
    protected boolean tickMachine() {
        if (!(this.level instanceof ServerLevel level) || ++this.timer < INTERVAL) {
            return this.busy;
        }
        this.timer = 0;
        boolean worked = false;
        if (this.satisfaction > 0.05 && this.connected()) {
            FacilityRepairs.Work work = FacilityRepairs.repairAround(level, this.worldPosition, REPAIR_RANGE,
                    (float) (FacilityHealth.BASE_REPAIR * this.satisfaction), FacilityHealth.BASE_REPAIR_TARGETS);
            this.damaged = work.damaged();
            worked = work.repaired() > 0;
            // 重建一秒一个
            if ((++this.round & 1) == 0) {
                int left = FacilityRepairs.rebuildFrom(level, this.worldPosition, REPAIR_RANGE, this);
                if (left < 0) {
                    worked = true;
                    this.ghosts = -1 - left;
                } else {
                    this.ghosts = left;
                }
            }
            worked |= this.pickUp(level);
        }
        this.busy = worked;
        return worked;
    }

    /** 捡范围内地上的东西，按模式过滤。 */
    private boolean pickUp(ServerLevel level) {
        if (this.filter == PickupFilter.OFF) {
            return false;
        }
        AABB box = new AABB(this.worldPosition).inflate(PICKUP_RANGE);
        double range = PICKUP_RANGE * PICKUP_RANGE;
        Vec3 center = Vec3.atCenterOf(this.worldPosition);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box,
                entity -> entity.isAlive() && !entity.hasPickUpDelay() && entity.distanceToSqr(center) <= range);
        boolean picked = false;
        int count = 0;
        for (ItemEntity entity : items) {
            if (count >= PICKUPS) {
                break;
            }
            ItemStack stack = entity.getItem();
            if (!this.filter.accepts(FacilityRepairs.darkFogLoot(entity), this.stores(stack.getItem()))) {
                continue;
            }
            int before = stack.getCount();
            ItemStack rest = this.insert(stack.copy());
            if (rest.getCount() == before) {
                continue;
            }
            count++;
            picked = true;
            level.sendParticles(ParticleTypes.PORTAL, entity.getX(), entity.getY() + 0.3, entity.getZ(), 6, 0.2, 0.2, 0.2, 0.3);
            if (rest.isEmpty()) {
                entity.discard();
            } else {
                entity.setItem(rest);
            }
        }
        return picked;
    }

    private boolean stores(Item item) {
        for (int i = 0; i < SLOTS; i++) {
            if (this.getItem(i).is(item)) {
                return true;
            }
        }
        return false;
    }

    /** 放进储物格：先叠到同样的东西上，再放空格。返回放不下的。 */
    private ItemStack insert(ItemStack stack) {
        for (int i = 0; i < SLOTS && !stack.isEmpty(); i++) {
            ItemStack slot = this.getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < slot.getMaxStackSize()) {
                int moved = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(moved);
                stack.shrink(moved);
            }
        }
        for (int i = 0; i < SLOTS && !stack.isEmpty(); i++) {
            if (this.getItem(i).isEmpty()) {
                this.setItem(i, stack.split(stack.getMaxStackSize()));
            }
        }
        this.setChanged();
        return stack;
    }

    @Override
    protected boolean isInput(int slot) {
        return true;
    }

    @Override
    protected boolean isOutput(int slot) {
        return true;
    }

    @Override
    public CompoundTag blueprintSettings() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Pickup", this.filter.ordinal());
        return tag;
    }

    @Override
    public void applyBlueprintSettings(CompoundTag settings) {
        this.filter = PickupFilter.byIndex(settings.getIntOr("Pickup", 0));
        this.setChanged();
    }

    @Override
    public boolean clickButton(ServerPlayer player, int button) {
        if (button != BUTTON_FILTER) {
            return false;
        }
        this.filter = this.filter.next();
        this.setChanged();
        return true;
    }

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        return switch (index) {
            case FILTER -> this.filter.ordinal();
            case DAMAGED -> this.damaged;
            case GHOSTS -> this.ghosts;
            default -> super.dataValue(index);
        };
    }

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.BATTLEFIELD_ANALYSIS_BASE;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.filter = PickupFilter.byIndex(input.getIntOr("Pickup", 0));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Pickup", this.filter.ordinal());
    }
}
