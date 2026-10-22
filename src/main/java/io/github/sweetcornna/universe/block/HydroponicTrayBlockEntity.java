package io.github.sweetcornna.universe.block;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.ecology.ResourcePlant;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.stats.ProductionLog;
import io.github.sweetcornna.universe.survival.Plantable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 水培槽（ADR-0026）：上面种一株资源植物，接电网就一轮一轮地长，熟了产物放进产出格，右键收或者让分拣器拿走。
 * 槽里的水循环使用，不用加水。哪颗星球都能种，洞穴髓球不行。
 */
public class HydroponicTrayBlockEntity extends MachineBlockEntity {
    /** 补光灯和水泵：1 J/t（20 kW）。 */
    public static final long POWER = 1;
    public static final int PLANT = 0;
    public static final int OUTPUT = 1;

    private double progress;
    private boolean growing;
    private double supplied;
    /** 客户端画植物用：长到一半以上画成长熟的样子。 */
    private boolean ripe;

    public HydroponicTrayBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.HYDROPONIC_TRAY.get(), pos, state, 2);
    }

    /** 种着的植物；空槽为空。 */
    public Optional<ResourcePlant> plant() {
        return farmable(this.getItem(PLANT));
    }

    /** 这样东西种在槽里长什么；不能种的为空。 */
    public static Optional<ResourcePlant> farmable(ItemStack stack) {
        return stack.getItem() instanceof Plantable plantable ? plantable.plant().filter(ResourcePlant::farmable) : Optional.empty();
    }

    /** 长了几成，0–1。 */
    public double growth() {
        return this.plant().map(plant -> Math.min(1, this.progress / plant.trayTicks())).orElse(0.0);
    }

    public boolean ripe() {
        return this.ripe;
    }

    @Override
    public long demand() {
        return this.growing ? POWER : 0;
    }

    @Override
    public void settle(double satisfaction, double load, long stored) {
        super.settle(satisfaction, load, stored);
        this.supplied = this.growing ? satisfaction : 0;
    }

    @Override
    protected boolean tickMachine() {
        double rate = this.supplied;
        this.supplied = 0;
        Optional<ResourcePlant> plant = this.plant();
        ItemStack product = plant.map(p -> new ItemStack(UniverseItems.RESOURCES.get(p.product()).get())).orElse(ItemStack.EMPTY);
        // 产出格满了就停下，等人收
        this.growing = plant.isPresent() && this.outputHasRoom(OUTPUT, product);
        boolean shown = !this.getItem(OUTPUT).isEmpty() || this.growth() >= 0.5;
        if (shown != this.ripe) {
            this.ripe = shown;
            this.sync();
        }
        if (!this.growing || rate <= 0 || plant.isEmpty()) {
            return false;
        }
        this.progress += rate;
        if (this.progress >= plant.get().trayTicks()) {
            this.progress = 0;
            int count = plant.get().trayYield(Rng.of(this.level == null ? 0 : this.level.getRandom().nextLong()));
            product.setCount(count);
            ProductionLog.produced(this.level, product.getItem(), count);
            this.addOutput(OUTPUT, product);
            this.sync();
        }
        this.setChanged();
        return true;
    }

    /** 右键收获：把产出格里的东西全拿走。 */
    public ItemStack harvest() {
        ItemStack out = this.removeItemNoUpdate(OUTPUT);
        if (!out.isEmpty()) {
            this.setChanged();
            this.sync();
        }
        return out;
    }

    /** 种下去（一个）。 */
    public boolean sow(ItemStack seed) {
        if (!this.getItem(PLANT).isEmpty() || farmable(seed).isEmpty()) {
            return false;
        }
        this.setItem(PLANT, seed.copyWithCount(1));
        this.progress = 0;
        this.setChanged();
        this.sync();
        return true;
    }

    /** 拔掉：种子还回去，进度清零。 */
    public ItemStack uproot() {
        ItemStack seed = this.removeItemNoUpdate(PLANT);
        this.progress = 0;
        this.setChanged();
        this.sync();
        return seed;
    }

    private void sync() {
        if (this.level != null && !this.level.isClientSide()) {
            BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected boolean isOutput(int slot) {
        return slot == OUTPUT;
    }

    /** 种子只能手种，分拣器只往外拿产物。 */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    @Override
    protected MachineMenu.Type menuType() {
        throw new UnsupportedOperationException("水培槽没有界面");
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getDoubleOr("Progress", 0);
        this.ripe = input.getBooleanOr("Ripe", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("Progress", this.progress);
        output.putBoolean("Ripe", this.ripe);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }
}
