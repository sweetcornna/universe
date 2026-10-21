package io.github.sweetcornna.universe.factory.belt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import io.github.sweetcornna.universe.core.factory.Cargo;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 一格传送带的方块实体（ADR-0011）：不 tick，只负责登记、存档和对外的物品能力。加载时连同存档里的物品一起登记到
 * 所在世界的 {@link BeltNetwork}（两端都登记，客户端的物品来自区块数据里的更新标签）；卸载时把这格的物品取回来
 * 留着存档，拆掉时掉在地上。
 */
public class BeltBlockEntity extends BlockEntity {
    private static final Codec<List<Item>> ITEMS_CODEC = BuiltInRegistries.ITEM.byNameCodec().listOf();

    /** 没登记时（加载前、卸载后）这格上的物品。 */
    private BeltNetwork.@Nullable BlockItems held;
    private boolean registered;
    private final BeltInsertHandler insertHandler = new BeltInsertHandler(this);

    public BeltBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.CONVEYOR_BELT.get(), pos, state);
    }

    public BeltInsertHandler insertHandler() {
        return this.insertHandler;
    }

    // ---- 登记

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null) {
            BeltNetwork.of(this.level).add(this.worldPosition, this.getBlockState(), this.held);
            this.held = null;
            this.registered = true;
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        this.leave();
    }

    @Override
    public void setRemoved() {
        this.leave();
        super.setRemoved();
    }

    private void leave() {
        if (!this.registered || this.level == null) {
            return;
        }
        this.registered = false;
        BeltNetwork network = BeltNetwork.existing(this.level);
        if (network != null) {
            this.held = network.remove(this.worldPosition);
        }
    }

    /** 拆掉时这格上的物品掉在地上。 */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        this.leave();
        BeltNetwork.BlockItems items = this.held;
        this.held = null;
        if (items != null) {
            for (int cargo : items.items()) {
                for (ItemStack stack : BeltNetwork.stacks(cargo)) {
                    Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, stack);
                }
            }
        }
    }

    /** 朝向或坡度变了（潜行右键换坡度）时重新登记。 */
    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        if (this.registered && this.level != null) {
            BeltNetwork.of(this.level).update(this.worldPosition, state);
        }
    }

    // ---- 存档

    private BeltNetwork.@Nullable BlockItems currentItems() {
        if (this.registered && this.level != null) {
            BeltNetwork network = BeltNetwork.existing(this.level);
            return network == null ? null : network.itemsAt(this.worldPosition);
        }
        return this.held;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        int[] positions = input.getIntArray("BeltPositions").orElse(new int[0]);
        List<Item> items = input.read("BeltItems", ITEMS_CODEC).orElse(List.of());
        // 叠了几个、增产点数（ADR-0022）；旧档没有，都是一个、0 点
        int[] stacks = input.getIntArray("BeltStacks").orElse(new int[0]);
        int n = Math.min(positions.length, items.size());
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) {
            ids[i] = Cargo.withItem(i < stacks.length ? stacks[i] : 0, BeltNetwork.id(items.get(i)));
        }
        this.held = n == 0 ? null : new BeltNetwork.BlockItems(Arrays.copyOf(positions, n), ids);
        if (this.registered && this.level != null) {
            // 客户端收到更新标签：重新登记
            BeltNetwork network = BeltNetwork.of(this.level);
            network.remove(this.worldPosition);
            network.add(this.worldPosition, this.getBlockState(), this.held);
            this.held = null;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        BeltNetwork.BlockItems items = this.currentItems();
        if (items == null || items.isEmpty()) {
            return;
        }
        int[] cargo = items.items();
        List<Item> list = new ArrayList<>(cargo.length);
        int[] stacks = new int[cargo.length];
        boolean stacked = false;
        for (int i = 0; i < cargo.length; i++) {
            list.add(BeltNetwork.item(cargo[i]));
            stacks[i] = Cargo.withItem(cargo[i], 0);
            stacked |= stacks[i] != 0;
        }
        output.putIntArray("BeltPositions", items.positions());
        output.store("BeltItems", ITEMS_CODEC, list);
        if (stacked) {
            output.putIntArray("BeltStacks", stacks);
        }
    }

    /** 区块数据里带上这格的物品，客户端一加载就能画出来。 */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }
}
