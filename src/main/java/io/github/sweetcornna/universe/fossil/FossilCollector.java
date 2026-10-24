package io.github.sweetcornna.universe.fossil;

import io.github.sweetcornna.universe.civ.Civ;
import io.github.sweetcornna.universe.core.civ.Standing;
import io.github.sweetcornna.universe.core.fossil.Bone;
import io.github.sweetcornna.universe.core.fossil.CollectorTrade;
import io.github.sweetcornna.universe.core.fossil.Skeleton;
import io.github.sweetcornna.universe.entity.civ.AlienNpc;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.sound.FossilSounds;
import io.github.sweetcornna.universe.trade.Credits;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 化石收藏家（ADR-0034 §三）：空间站大厅里、发掘场的帐篷门口各有一个。右键直接打开 {@link CollectorMenu}，声望太差时不理人。
 */
public final class FossilCollector {
    private FossilCollector() {
    }

    /** 打开收藏家的界面。 */
    public static void open(ServerPlayer player, AlienNpc npc) {
        if (!Standing.canTalk(Civ.state(player).tier(npc.race()))) {
            player.sendOverlayMessage(Component.translatable("message.universe.npc.refuses", npc.label()).withStyle(ChatFormatting.RED));
            return;
        }
        npc.getLookControl().setLookAt(player);
        npc.gesture();
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new CollectorMenu(id, inventory, npc), npc.label()),
                buf -> buf.writeVarInt(npc.getId()));
    }

    /** 收展品：按 {@link CollectorTrade#exhibitPrice} 给信用点。 */
    static void sell(Player player, CollectorMenu menu) {
        if (player.level().isClientSide()) {
            return;
        }
        Skeleton skeleton = menu.exhibit();
        long price = CollectorTrade.exhibitPrice(skeleton);
        if (skeleton.isEmpty() || price <= 0) {
            player.sendOverlayMessage(Component.translatable("message.universe.collector.no_exhibit").withStyle(ChatFormatting.YELLOW));
            return;
        }
        menu.consumeExhibit();
        Credits.add(player, price);
        player.sendOverlayMessage(Component.translatable("message.universe.collector.sold", FossilNames.skeleton(skeleton),
                Credits.format(price)).withStyle(ChatFormatting.GREEN));
        player.level().playSound(null, player.blockPosition(), FossilSounds.SELL.get(), SoundSource.NEUTRAL, 0.9F, 1.0F);
    }

    /** 换骨头：交三块，换一块选好的。 */
    static void swap(Player player, CollectorMenu menu) {
        if (player.level().isClientSide()) {
            return;
        }
        Bone wanted = menu.wanted();
        switch (menu.swapCheck()) {
            case NEED_THREE -> player.sendOverlayMessage(Component.translatable("message.universe.collector.need_three",
                    CollectorTrade.SWAP_COST).withStyle(ChatFormatting.YELLOW));
            case TOO_RARE -> player.sendOverlayMessage(Component.translatable("message.universe.collector.too_rare",
                    FossilNames.rarity(wanted.family().rarity())).withStyle(ChatFormatting.YELLOW));
            case OK -> {
                menu.consumeOffered();
                Fossils.give(player, new ItemStack(UniverseItems.BONES.get(wanted).get()));
                player.sendOverlayMessage(Component.translatable("message.universe.collector.swapped",
                        FossilNames.family(wanted.family()), FossilNames.part(wanted)).withStyle(ChatFormatting.GREEN));
                player.level().playSound(null, player.blockPosition(), FossilSounds.SWAP.get(), SoundSource.NEUTRAL, 0.9F, 1.0F);
            }
        }
    }
}
