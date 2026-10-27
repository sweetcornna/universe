package io.github.sweetcornna.universe.microbe;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 培养箱、测序仪、基因编辑台共用的部分（ADR-0036 §五）：一个原料格、一个耗材格、一个产物格；干活时要电，
 * 供电不足按满足率放慢；记着最后一个打开界面的玩家（测序仪测出新菌时发现日志记在他名下）。
 */
public abstract class BioMachineBlockEntity extends MachineBlockEntity implements MachineMenu.ButtonTarget {
    public static final int INPUT = 0;
    public static final int CONSUMABLE = 1;
    public static final int OUTPUT = 2;
    /** 界面数据：进度（刻）、要多少刻、状态。子类从 {@link #BIO_DATA} 往后排。 */
    public static final int PROGRESS = BASE_DATA;
    public static final int TOTAL = BASE_DATA + 1;
    public static final int STATUS = BASE_DATA + 2;
    public static final int BIO_DATA = BASE_DATA + 3;

    protected double progress;
    /** 下一刻要不要电（电网在设备之前结算，所以这一刻定下一刻的）。 */
    private boolean running;
    private double supplied;
    private @Nullable UUID user;

    protected BioMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 3);
    }

    /** 干活时的功率（焦耳/刻）。 */
    protected abstract long power();

    /** 这一样东西要做多少刻；没有活为 0。 */
    protected abstract int total();

    /** 界面上的状态（各自的枚举序号）。 */
    protected abstract int status();

    @Override
    public long demand() {
        return this.running ? this.power() : 0;
    }

    @Override
    public void settle(double satisfaction, double load, long stored) {
        super.settle(satisfaction, load, stored);
        this.supplied = this.running ? satisfaction : 0;
    }

    /**
     * 按这一刻到手的电推进一点。
     *
     * @param ready 有活可干：没活时进度清零、不要电
     * @return 推进到头了
     */
    protected boolean advance(boolean ready) {
        double rate = this.supplied;
        this.supplied = 0;
        this.running = ready;
        if (!ready) {
            if (this.progress != 0) {
                this.progress = 0;
                this.setChanged();
            }
            return false;
        }
        this.progress += rate;
        this.setChanged();
        if (this.progress >= this.total()) {
            this.progress = 0;
            return true;
        }
        return false;
    }

    /** 这一刻有没有在走（亮灯）。 */
    protected boolean moving() {
        return this.running && this.progress > 0;
    }

    protected void play(SoundEvent sound, float volume, float pitch) {
        if (this.level != null) {
            this.level.playSound(null, this.worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
        }
    }

    /** 最后一个打开界面、还在线的玩家。 */
    protected Optional<ServerPlayer> user() {
        if (this.user == null || !(this.level instanceof ServerLevel server)) {
            return Optional.empty();
        }
        return Optional.ofNullable(server.getServer().getPlayerList().getPlayer(this.user));
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        this.user = inventory.player.getUUID();
        this.setChanged();
        return super.createMenu(containerId, inventory);
    }

    @Override
    public boolean clickButton(ServerPlayer player, int button) {
        return false;
    }

    // ---- 物品栏

    @Override
    protected boolean isInput(int slot) {
        return slot == INPUT || slot == CONSUMABLE;
    }

    @Override
    protected boolean isOutput(int slot) {
        return slot == OUTPUT;
    }

    // ---- 界面

    @Override
    protected int dataCount() {
        return BIO_DATA;
    }

    @Override
    protected int dataValue(int index) {
        return switch (index) {
            case PROGRESS -> (int) Math.min(Short.MAX_VALUE, this.progress);
            case TOTAL -> Math.min(Short.MAX_VALUE, this.total());
            case STATUS -> this.status();
            default -> super.dataValue(index);
        };
    }

    // ---- 存档

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getDoubleOr("Progress", 0);
        this.user = input.read("User", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("Progress", this.progress);
        if (this.user != null) {
            output.store("User", UUIDUtil.CODEC, this.user);
        }
    }
}
