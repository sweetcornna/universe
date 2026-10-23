package io.github.sweetcornna.universe.factory.belt;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.blueprint.BlueprintSettings;
import io.github.sweetcornna.universe.core.factory.Cargo;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.MachinePanel;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 流速监测器（ADR-0022）：串在传送带中间，数最近一分钟流过多少个物品（叠着的按个数算，可以只数一种）。设了下限、
 * 流量低于下限时报警：方块亮红灯，发出红石信号。不要电。蓝图记下下限和只数的物品（ADR-0030）。
 */
public class TrafficMonitorBlockEntity extends BeltPassBlockEntity implements MenuProvider, MachineMenu.ButtonTarget, BlueprintSettings {
    /** 界面数据：最近一分钟的个数、下限、第 0 位报警、第 1 位开机满一分钟。 */
    public static final int COUNT = 0;
    public static final int THRESHOLD = 1;
    public static final int FLAGS = 2;
    public static final int DATA_COUNT = 3;
    /** 界面上的按钮：下限减 100、10、1，加 1、10、100。 */
    public static final int[] STEPS = {-100, -10, -1, 1, 10, 100};
    public static final int MAX_THRESHOLD = 30_000;

    /** 每秒一格，记最近 60 秒。 */
    private final int[] seconds = new int[60];
    private int current;
    private int ticks;
    /** 开机了几秒，不满一分钟不报警。 */
    private int uptime;
    private int threshold;
    private @Nullable Item filter;
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            TrafficMonitorBlockEntity self = TrafficMonitorBlockEntity.this;
            return switch (index) {
                case COUNT -> Math.min(Short.MAX_VALUE, self.perMinute());
                case THRESHOLD -> self.threshold;
                case FLAGS -> (self.alarm() ? 1 : 0) | (self.uptime >= self.seconds.length ? 2 : 0);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public TrafficMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.TRAFFIC_MONITOR.get(), pos, state);
    }

    public @Nullable Item filter() {
        return this.filter;
    }

    public void setFilter(@Nullable Item filter) {
        this.filter = filter == Items.AIR ? null : filter;
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public int threshold() {
        return this.threshold;
    }

    @Override
    public @Nullable CompoundTag blueprintSettings() {
        if (this.threshold == 0 && this.filter == null) {
            return null;
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("Threshold", this.threshold);
        if (this.filter != null) {
            tag.putString("Filter", BuiltInRegistries.ITEM.getKey(this.filter).toString());
        }
        return tag;
    }

    @Override
    public void applyBlueprintSettings(CompoundTag settings) {
        this.threshold = Math.max(0, Math.min(MAX_THRESHOLD, settings.getIntOr("Threshold", 0)));
        Identifier id = Identifier.tryParse(settings.getStringOr("Filter", ""));
        this.setFilter(id == null ? null : BuiltInRegistries.ITEM.getValue(id));
    }

    /** 最近一分钟流过的个数。 */
    public int perMinute() {
        int sum = 0;
        for (int count : this.seconds) {
            sum += count;
        }
        return sum;
    }

    public boolean alarm() {
        return this.threshold > 0 && this.measured() && this.perMinute() < this.threshold;
    }

    /** 开机满一分钟了：之前不报警。 */
    public boolean measured() {
        return this.uptime >= this.seconds.length;
    }

    @Override
    protected void passed(int cargo) {
        if (this.filter == null || BeltNetwork.item(cargo) == this.filter) {
            this.seconds[this.current] += Cargo.count(cargo);
        }
    }

    @Override
    protected void tick() {
        if (++this.ticks < 20) {
            return;
        }
        this.ticks = 0;
        this.current = (this.current + 1) % this.seconds.length;
        this.seconds[this.current] = 0;
        this.uptime = Math.min(this.uptime + 1, this.seconds.length);
        boolean alarm = this.alarm();
        BlockState state = this.getBlockState();
        if (this.level != null && state.getValue(TrafficMonitorBlock.ALARM) != alarm) {
            this.level.setBlock(this.worldPosition, state.setValue(TrafficMonitorBlock.ALARM, alarm), Block.UPDATE_ALL);
        }
        this.setChanged();
    }

    @Override
    public boolean clickButton(ServerPlayer player, int button) {
        if (button < 0 || button >= STEPS.length) {
            return false;
        }
        this.threshold = Math.max(0, Math.min(MAX_THRESHOLD, this.threshold + STEPS[button]));
        this.setChanged();
        return true;
    }

    // ---- 界面

    @Override
    public Component getDisplayName() {
        return this.getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(UniverseMenus.TRAFFIC_MONITOR, containerId, inventory, new MachinePanel(this), this.data, this.worldPosition);
    }

    // ---- 存档和同步

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        int[] seconds = input.getIntArray("Seconds").orElse(new int[0]);
        System.arraycopy(seconds, 0, this.seconds, 0, Math.min(seconds.length, this.seconds.length));
        this.current = Math.floorMod(input.getIntOr("Current", 0), this.seconds.length);
        this.uptime = input.getIntOr("Uptime", 0);
        this.threshold = input.getIntOr("Threshold", 0);
        this.filter = input.read("Filter", BuiltInRegistries.ITEM.byNameCodec()).filter(item -> item != Items.AIR).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putIntArray("Seconds", this.seconds);
        output.putInt("Current", this.current);
        output.putInt("Uptime", this.uptime);
        output.putInt("Threshold", this.threshold);
        Optional.ofNullable(this.filter).ifPresent(item -> output.store("Filter", BuiltInRegistries.ITEM.byNameCodec(), item));
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }
}
