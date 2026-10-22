package io.github.sweetcornna.universe.block.ship;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.ship.ShipTech;
import io.github.sweetcornna.universe.core.space.DriveTier;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.tech.DriveItem;
import io.github.sweetcornna.universe.tech.TechModuleItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 驾驶台里装的飞船技术（ADR-0024）：技术模块和镉、翠金、铟驱动，最多 {@link #SLOTS} 格，能用几格看飞船的档次。
 *
 * <p>除了物品本身，每次存盘还按物品写一份摘要 {@link #TECH}。组装时这份数据跟着船走，{@code ShipStructure} 只读摘要算加成，
 * 不用解码物品；同步给客户端的也只有摘要。驾驶台不是容器，货舱的统计和装货都不会算上它。
 */
public class HelmBlockEntity extends BlockEntity {
    public static final int SLOTS = 6;
    /** 摘要在方块实体数据里的键。 */
    public static final String TECH = "Tech";
    private static final Codec<ShipTech.Slot> SLOT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("slot").forGetter(ShipTech.Slot::index),
            UniverseDataComponents.TECH_MODULE_CODEC.optionalFieldOf("module").forGetter(slot -> Optional.ofNullable(slot.module())),
            UniverseDataComponents.enumCodec(DriveTier.class).optionalFieldOf("drive").forGetter(slot -> Optional.ofNullable(slot.drive()))
    ).apply(i, (index, module, drive) -> new ShipTech.Slot(index, module.orElse(null), drive.orElse(null))));
    private static final Codec<List<ShipTech.Slot>> SLOTS_CODEC = SLOT_CODEC.listOf();

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    public HelmBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.SHIP_HELM.get(), pos, state);
    }

    /** 技术槽里的物品，改了要调 {@link #setChanged()}。 */
    public NonNullList<ItemStack> items() {
        return this.items;
    }

    /** 物品的摘要：每个装着模块或驱动的格子一项。 */
    public static List<ShipTech.Slot> summary(List<ItemStack> items) {
        List<ShipTech.Slot> slots = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.getItem() instanceof TechModuleItem module) {
                slots.add(new ShipTech.Slot(i, module.module(stack), null));
            } else if (stack.getItem() instanceof DriveItem drive) {
                slots.add(new ShipTech.Slot(i, null, drive.tier()));
            }
        }
        return slots;
    }

    /** 从方块实体数据里读摘要；旧存档里没有方块实体的驾驶台读出空表。 */
    public static List<ShipTech.Slot> readSummary(@Nullable CompoundTag data) {
        if (data == null) {
            return List.of();
        }
        return data.read(TECH, SLOTS_CODEC).orElse(List.of());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        for (int i = 0; i < SLOTS; i++) {
            this.items.set(i, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(input, this.items);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        output.store(TECH, SLOTS_CODEC, summary(this.items));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.store(TECH, SLOTS_CODEC, summary(this.items));
        return tag;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level != null) {
            Containers.dropContents(this.level, pos, this.items);
        }
    }
}
