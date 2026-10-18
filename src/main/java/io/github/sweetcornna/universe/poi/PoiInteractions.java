package io.github.sweetcornna.universe.poi;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.block.poi.DropPodBlock;
import io.github.sweetcornna.universe.core.research.Tech;
import io.github.sweetcornna.universe.core.tech.SuitSlots;
import io.github.sweetcornna.universe.network.PoiTextPayload;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.tech.ExosuitData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/** 兴趣点里能互动的方块：修掉落舱、读数据终端、碰石碑（ADR-0014）。 */
public final class PoiInteractions {
    /** 先驱者线索的段数（语言文件里 poi.universe.lore.1…）。 */
    public static final int LORE_COUNT = 12;
    /** 第一次碰石碑得到的勘探数据。 */
    static final int MONOLITH_SURVEY_DATA = 3;

    private PoiInteractions() {
    }

    /** 用材料修掉落舱：材料按已经修过的个数分三档，修好后外骨骼多一个技术槽、九格货舱。 */
    public static void repairDropPod(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getValue(DropPodBlock.REPAIRED)) {
            player.sendOverlayMessage(Component.translatable("message.universe.drop_pod.repaired_already"));
            return;
        }
        ExosuitData data = player.getData(UniverseAttachments.EXOSUIT);
        if (!SuitSlots.canGrow(data.pods())) {
            player.sendOverlayMessage(Component.translatable("message.universe.drop_pod.maxed"));
            return;
        }
        List<Tech.ItemCost> cost = SuitSlots.podCost(data.pods());
        Inventory inventory = player.getInventory();
        if (!player.isCreative()) {
            List<Tech.ItemCost> missing = new ArrayList<>();
            for (Tech.ItemCost c : cost) {
                if (count(inventory, item(c)) < c.count()) {
                    missing.add(c);
                }
            }
            if (!missing.isEmpty()) {
                player.sendOverlayMessage(Component.translatable("message.universe.drop_pod.needs", list(cost)));
                level.playSound(null, pos, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.8F, 0.7F);
                return;
            }
            for (Tech.ItemCost c : cost) {
                take(inventory, item(c), c.count());
            }
        }
        data.addPod();
        data.changed();
        level.setBlock(pos, state.setValue(DropPodBlock.REPAIRED, true), 3);
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6F, 1.3F);
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.9F, 1.2F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 16, 0.5, 0.4, 0.5, 0);
        player.sendSystemMessage(Component.translatable("message.universe.drop_pod.upgraded",
                data.techSlots(), SuitSlots.TECH_MAX, data.cargoSlots(), SuitSlots.CARGO_MAX));
    }

    /** 读数据终端：打开日志；每个玩家第一次读一台终端给一份导航数据。 */
    public static void readTerminal(ServerPlayer player, ServerLevel level, BlockPos pos) {
        long key = Pois.claimKey(level, pos);
        boolean first = player.getData(UniverseAttachments.POI_PROGRESS).claim(key);
        if (first) {
            give(player, new ItemStack(UniverseItems.NAVIGATION_DATA.get()));
        }
        level.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.6F, 1.8F);
        PacketDistributor.sendToPlayer(player, new PoiTextPayload(PoiTextPayload.Kind.TERMINAL, key, 0, first));
    }

    /** 碰石碑：第一次碰给勘探数据和下一段先驱者的线索，以后再碰重新显示同一段。 */
    public static void touchMonolith(ServerPlayer player, ServerLevel level, BlockPos pos) {
        long key = Pois.claimKey(level, pos);
        PoiProgress progress = player.getData(UniverseAttachments.POI_PROGRESS);
        boolean first = progress.claim(key);
        int lore = progress.monolithLore(key, LORE_COUNT);
        if (first) {
            give(player, new ItemStack(UniverseItems.SURVEY_DATA.get(), MONOLITH_SURVEY_DATA));
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 0.6F);
            level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 60, 0.6, 0.8, 0.6, 0.6);
        } else {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 0.6F);
        }
        PacketDistributor.sendToPlayer(player, new PoiTextPayload(PoiTextPayload.Kind.MONOLITH, key, lore, first));
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
    }

    private static Item item(Tech.ItemCost cost) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(cost.item()));
    }

    private static int count(Inventory inventory, Item item) {
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void take(Inventory inventory, Item item, int count) {
        for (int i = 0; i < inventory.getContainerSize() && count > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                int used = Math.min(count, stack.getCount());
                stack.shrink(used);
                count -= used;
            }
        }
    }

    /** "2 个铁锭、1 个电路板" 这样的材料清单。 */
    static MutableComponent list(List<Tech.ItemCost> cost) {
        MutableComponent text = Component.empty();
        for (int i = 0; i < cost.size(); i++) {
            if (i > 0) {
                text.append(Component.translatable("message.universe.list_separator"));
            }
            Tech.ItemCost c = cost.get(i);
            text.append(Component.translatable("message.universe.item_count", c.count(), Component.translatable(item(c).getDescriptionId())));
        }
        return text;
    }
}
