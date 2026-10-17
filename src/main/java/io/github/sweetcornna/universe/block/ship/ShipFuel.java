package io.github.sweetcornna.universe.block.ship;

import java.util.Map;

import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;

/**
 * 能加进燃料舱的东西和它们的燃料值。一个燃料舱装 2000，飞一趟卫星来回大约要 500。
 *
 * <p>26.x 的熔炉燃料要带战利品上下文才能算出燃烧时间，这里不跟它走，单独列一张短表：发射燃料最划算，煤也能凑合。
 */
public final class ShipFuel {
    private static Map<Item, Double> values;

    private ShipFuel() {
    }

    /** 一个物品的燃料值；不是燃料返回 0。 */
    public static double value(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (values == null) {
            values = Map.of(
                    UniverseItems.LAUNCH_FUEL.get(), 1000.0,
                    Items.COAL, 200.0,
                    Items.CHARCOAL, 200.0,
                    Items.COAL_BLOCK, 1800.0,
                    Items.BLAZE_ROD, 400.0,
                    Items.LAVA_BUCKET, 1000.0);
        }
        return values.getOrDefault(stack.getItem(), 0.0);
    }

    /** 用掉手上的一个燃料，岩浆桶还回空桶。 */
    public static void consume(ItemStack stack, Player player, InteractionHand hand) {
        if (stack.is(Items.LAVA_BUCKET)) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
        } else {
            stack.consume(1, player);
        }
    }
}
