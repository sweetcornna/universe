package io.github.sweetcornna.universe.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * 服务端 → 客户端：交易终端的界面内容（ADR-0017）。打开终端和每次交易以后发一份，价钱全按服务端算好。
 *
 * @param open    true 时客户端打开交易界面；false 只刷新已经开着的界面
 * @param system  恒星系序号
 * @param economy 经济类型的序号（{@code Economy.Type}）
 * @param wealth  财富的序号（{@code Economy.Wealth}）
 * @param credits 玩家现在的信用点
 * @param sell    背包里能卖的东西
 * @param buy     空间站的货（最后几行是技术模块）
 * @param notice  上一次交易的结果，界面底部显示
 */
public record TradeStatePayload(boolean open, int system, int economy, int wealth, long credits, List<Row> sell, List<Row> buy,
        Optional<Component> notice) implements CustomPacketPayload {
    public static final Type<TradeStatePayload> TYPE = new Type<>(Universe.id("trade_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TradeStatePayload> STREAM_CODEC = StreamCodec.of(TradeStatePayload::write,
            TradeStatePayload::read);
    /** 一页最多多少行。 */
    public static final int MAX_ROWS = 96;

    /**
     * 一行货物。
     *
     * @param stack    显示用的物品（数量 1；模块带着数值）
     * @param key      交易时回传给服务端的键
     * @param count    卖出页是背包里有几件，购买页是存货
     * @param capacity 购买页的存货上限；卖出页为 0
     * @param price    现在一件的单价
     * @param one      交易一件一共多少信用点；做不了是负数
     * @param group    交易一组（Shift）一共多少
     * @param all      全部卖掉（Ctrl）一共多少；购买页不用
     * @param category 大类的序号（{@code TradeCategory}）
     */
    public record Row(ItemStack stack, String key, int count, int capacity, double price, double one, double group, double all, int category) {
    }

    private static void write(RegistryFriendlyByteBuf buf, TradeStatePayload payload) {
        buf.writeBoolean(payload.open);
        buf.writeVarInt(payload.system);
        buf.writeVarInt(payload.economy);
        buf.writeVarInt(payload.wealth);
        buf.writeVarLong(payload.credits);
        writeRows(buf, payload.sell);
        writeRows(buf, payload.buy);
        ComponentSerialization.OPTIONAL_STREAM_CODEC.encode(buf, payload.notice);
    }

    private static void writeRows(RegistryFriendlyByteBuf buf, List<Row> rows) {
        int n = Math.min(MAX_ROWS, rows.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Row row = rows.get(i);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, row.stack);
            buf.writeUtf(row.key, 256);
            buf.writeVarInt(row.count);
            buf.writeVarInt(row.capacity);
            buf.writeDouble(row.price);
            buf.writeDouble(row.one);
            buf.writeDouble(row.group);
            buf.writeDouble(row.all);
            buf.writeVarInt(row.category);
        }
    }

    private static TradeStatePayload read(RegistryFriendlyByteBuf buf) {
        boolean open = buf.readBoolean();
        int system = buf.readVarInt();
        int economy = buf.readVarInt();
        int wealth = buf.readVarInt();
        long credits = buf.readVarLong();
        List<Row> sell = readRows(buf);
        List<Row> buy = readRows(buf);
        Optional<Component> notice = ComponentSerialization.OPTIONAL_STREAM_CODEC.decode(buf);
        return new TradeStatePayload(open, system, economy, wealth, credits, sell, buy, notice);
    }

    private static List<Row> readRows(RegistryFriendlyByteBuf buf) {
        int n = Math.min(MAX_ROWS, buf.readVarInt());
        List<Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rows.add(new Row(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readUtf(256), buf.readVarInt(), buf.readVarInt(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt()));
        }
        return rows;
    }

    @Override
    public Type<TradeStatePayload> type() {
        return TYPE;
    }
}
