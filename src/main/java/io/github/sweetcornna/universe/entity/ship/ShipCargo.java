package io.github.sweetcornna.universe.entity.ship;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.trade.ItemValues;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 飞船的货舱：船上所有的箱子、木桶这类容器（ADR-0017）。海盗按货值决定要不要打劫，打下的海盗残骸、打碎的小行星的产出直接装进来。
 * 船飞着的时候方块存在实体里，所以这里先把方块实体读出来、改完再写回去。
 */
public final class ShipCargo {
    private ShipCargo() {
    }

    /** 货舱里所有东西按空间站的基准价一共值多少信用点（卖不掉的不算）。 */
    public static double value(ShipEntity ship, MinecraftServer server) {
        ShipStructure structure = ship.structure();
        if (structure == null) {
            return 0;
        }
        RegistryAccess registries = server.registryAccess();
        double total = 0;
        for (ShipStructure.ShipBlock block : structure.blocks()) {
            if (block.blockEntity() == null) {
                continue;
            }
            BlockEntity entity = BlockEntity.loadStatic(new BlockPos(block.x(), block.y(), block.z()), block.state(), block.blockEntity(), registries);
            if (entity instanceof Container container) {
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    ItemStack stack = container.getItem(slot);
                    if (!stack.isEmpty()) {
                        total += ItemValues.unitValue(server, stack) * stack.getCount();
                    }
                }
            }
        }
        return total;
    }

    /**
     * 往货舱里装东西：先叠到同样的东西上，再放进空格。{@code stack} 会被减掉装进去的数量，返回装进去了几件。
     */
    public static int insert(ShipEntity ship, RegistryAccess registries, ItemStack stack) {
        ShipStructure structure = ship.structure();
        if (structure == null || stack.isEmpty()) {
            return 0;
        }
        int before = stack.getCount();
        List<ShipStructure.ShipBlock> blocks = new ArrayList<>(structure.blocks());
        boolean changed = false;
        // 两遍：先叠，再放空格
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int i = 0; i < blocks.size() && !stack.isEmpty(); i++) {
                ShipStructure.ShipBlock block = blocks.get(i);
                if (block.blockEntity() == null) {
                    continue;
                }
                BlockEntity entity = BlockEntity.loadStatic(new BlockPos(block.x(), block.y(), block.z()), block.state(), block.blockEntity(), registries);
                if (!(entity instanceof Container container)) {
                    continue;
                }
                boolean touched = false;
                for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
                    if (!container.canPlaceItem(slot, stack)) {
                        continue;
                    }
                    ItemStack there = container.getItem(slot);
                    int limit = Math.min(container.getMaxStackSize(), stack.getMaxStackSize());
                    if (pass == 0 && !there.isEmpty() && ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < limit) {
                        int move = Math.min(stack.getCount(), limit - there.getCount());
                        there.grow(move);
                        container.setItem(slot, there);
                        stack.shrink(move);
                        touched = true;
                    } else if (pass == 1 && there.isEmpty()) {
                        int move = Math.min(stack.getCount(), limit);
                        container.setItem(slot, stack.split(move));
                        touched = true;
                    }
                }
                if (touched) {
                    blocks.set(i, new ShipStructure.ShipBlock(block.x(), block.y(), block.z(), block.state(), entity.saveWithFullMetadata(registries)));
                    changed = true;
                }
            }
        }
        if (changed) {
            ship.replaceStructure(new ShipStructure(blocks));
        }
        return before - stack.getCount();
    }
}
