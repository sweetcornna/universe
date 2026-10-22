package io.github.sweetcornna.universe.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** 技术商的货单和买东西的数据包（ADR-0027 §六）。 */
public final class TechShopPayloads {
    public static final int MAX_ROWS = 6;

    private TechShopPayloads() {
    }

    /**
     * 一样货。
     *
     * @param price 打完折的价钱（扣的就是这个数）
     * @param sold  今天已经买过了
     */
    public record Row(ItemStack stack, long price, boolean sold) {
    }

    /**
     * 服务端 → 客户端：技术商今天的货。对话里按"看货"时打开，买完再发一份刷新开着的界面。
     *
     * @param open    true 时打开界面；false 只刷新开着的、同一个 NPC 的界面
     * @param entity  NPC 的实体 id
     * @param race    {@code Race} 的序号
     * @param tier    玩家在这个种族的声望档（{@code Standing.Tier} 的序号）
     * @param credits 玩家的信用点
     * @param notice  上一次买的结果
     */
    public record State(boolean open, int entity, String nameZh, String nameEn, int race, int tier, long credits, List<Row> rows,
            Optional<Component> notice) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(Universe.id("tech_shop_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM_CODEC = StreamCodec.of(State::write, State::read);

        private static void write(RegistryFriendlyByteBuf buf, State state) {
            buf.writeBoolean(state.open);
            buf.writeVarInt(state.entity);
            buf.writeUtf(state.nameZh, 64);
            buf.writeUtf(state.nameEn, 64);
            buf.writeVarInt(state.race);
            buf.writeVarInt(state.tier);
            buf.writeVarLong(state.credits);
            int rows = Math.min(MAX_ROWS, state.rows.size());
            buf.writeVarInt(rows);
            for (int i = 0; i < rows; i++) {
                Row row = state.rows.get(i);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, row.stack);
                buf.writeVarLong(row.price);
                buf.writeBoolean(row.sold);
            }
            ComponentSerialization.OPTIONAL_STREAM_CODEC.encode(buf, state.notice);
        }

        private static State read(RegistryFriendlyByteBuf buf) {
            boolean open = buf.readBoolean();
            int entity = buf.readVarInt();
            String nameZh = buf.readUtf(64);
            String nameEn = buf.readUtf(64);
            int race = buf.readVarInt();
            int tier = buf.readVarInt();
            long credits = buf.readVarLong();
            int count = Math.min(MAX_ROWS, buf.readVarInt());
            List<Row> rows = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                rows.add(new Row(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarLong(), buf.readBoolean()));
            }
            Optional<Component> notice = ComponentSerialization.OPTIONAL_STREAM_CODEC.decode(buf);
            return new State(open, entity, nameZh, nameEn, race, tier, credits, rows, notice);
        }

        @Override
        public Type<State> type() {
            return TYPE;
        }
    }

    /**
     * 客户端 → 服务端：买第几样。
     *
     * @param price 界面上看到的价钱；和服务端现算的对不上（档变了、换了一天）就不买，刷新一下货单
     */
    public record Buy(int entity, int index, long price) implements CustomPacketPayload {
        public static final Type<Buy> TYPE = new Type<>(Universe.id("tech_shop_buy"));
        public static final StreamCodec<ByteBuf, Buy> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Buy::entity,
                ByteBufCodecs.VAR_INT, Buy::index,
                ByteBufCodecs.VAR_LONG, Buy::price,
                Buy::new);

        @Override
        public Type<Buy> type() {
            return TYPE;
        }
    }
}
