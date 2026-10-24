package io.github.sweetcornna.universe.factory.resource;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.network.VeinAlertPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 矿脉枯竭的提醒（ADR-0033 §八）：发给采矿机所在维度里的玩家，聊天栏一行带坐标，HUD 上标出那台采矿机一会儿。
 * 限流在采矿机里（{@link io.github.sweetcornna.universe.core.factory.VeinWarnings}）。
 */
public final class VeinAlerts {
    private VeinAlerts() {
    }

    /** 这个维度里该收到提醒的玩家（观察者也算，他们也在看这片工厂）。 */
    public static List<ServerPlayer> recipients(ServerLevel level) {
        return new ArrayList<>(level.players());
    }

    /** @return 收到提醒的人数 */
    public static int send(ServerLevel level, BlockPos pos, Block machine, Item item, boolean all) {
        Component message = message(pos, machine, item, all);
        List<ServerPlayer> players = recipients(level);
        for (ServerPlayer player : players) {
            player.sendSystemMessage(message);
            // GameTest 的模拟玩家没有协商过频道
            if (player.connection != null && player.connection.hasChannel(VeinAlertPayload.TYPE)) {
                PacketDistributor.sendToPlayer(player, new VeinAlertPayload(pos, machine, item, all));
            }
        }
        return players.size();
    }

    public static Component message(BlockPos pos, Block machine, Item item, boolean all) {
        Component where = Component.translatable("message.universe.vein_alert.where", machine.getName(), pos.getX(), pos.getY(), pos.getZ());
        return (all ? Component.translatable("message.universe.vein_alert.all", where)
                : Component.translatable("message.universe.vein_alert.node", where, new ItemStack(item).getHoverName()))
                .withStyle(ChatFormatting.GOLD);
    }
}
