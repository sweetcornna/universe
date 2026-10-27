package io.github.sweetcornna.universe.client.chem;

import java.util.List;
import java.util.Optional;

import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.core.chem.Substance;
import io.github.sweetcornna.universe.core.chem.Substances;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 化学在客户端的部分（ADR-0036 §一）：物品说明里名字下面一行化学式，原版的物品也算；分析目镜分析矿石时也写出来。
 */
public final class ChemistryClient {
    private ChemistryClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        gameBus.addListener(ItemTooltipEvent.class, ChemistryClient::onTooltip);
    }

    private static void onTooltip(ItemTooltipEvent event) {
        List<Component> tooltip = event.getToolTip();
        if (tooltip.isEmpty()) {
            return;
        }
        line(event.getItemStack()).ifPresent(line -> tooltip.add(1, line.copy().withStyle(ChatFormatting.GRAY)));
    }

    /** 物品说明里的那一行；不在表里的物品为空。 */
    public static Optional<Component> line(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return Substances.of(id.toString()).map(substance -> describe(substance, stack.getHoverName().getString()));
    }

    /** 矿石方块是什么矿物（分析目镜用）；不认识为空。 */
    public static Optional<Component> mineral(BlockState state) {
        return mineralSubstance(state.getBlock()).map(substance -> describe(substance, ""));
    }

    /** "钛铁矿 FeTiO₃"；物质名和物品名一样时只写化学式。 */
    private static Component describe(Substance substance, String itemName) {
        String name = GalaxyText.pick(substance.nameZh(), substance.nameEn());
        return Component.literal(name.equals(itemName) ? substance.formula() : name + " " + substance.formula());
    }

    private static Optional<Substance> mineralSubstance(Block block) {
        for (var entry : UniverseBlocks.ORES.entrySet()) {
            if (entry.getValue().get() == block) {
                OreKind kind = entry.getKey();
                return Substances.of(kind.vanillaDrop() ? kind.drop() : "universe:" + kind.drop());
            }
        }
        // 原版矿石：掉的粗矿、煤、钻石这些
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        String path = id.getPath().replace("deepslate_", "").replace("nether_", "");
        if (!id.getNamespace().equals("minecraft") || !path.endsWith("_ore")) {
            return Optional.empty();
        }
        String mineral = path.substring(0, path.length() - "_ore".length());
        return Substances.of("minecraft:raw_" + mineral).or(() -> Substances.of("minecraft:" + mineral));
    }
}
