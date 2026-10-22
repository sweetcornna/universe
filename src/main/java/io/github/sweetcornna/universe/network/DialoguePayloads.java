package io.github.sweetcornna.universe.network;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 和外星 NPC 对话的数据包（ADR-0027 §四）。 */
public final class DialoguePayloads {
    /** 今天已经回答过这个 NPC 的题。 */
    public static final int ANSWERED = 1;
    /** 今天已经给这个 NPC 送过礼。 */
    public static final int GIFTED = 2;
    /** 今天已经请教过这个 NPC。 */
    public static final int ASKED = 4;
    /** 声望够请教词了。 */
    public static final int CAN_ASK = 8;
    public static final int MAX_WORDS = 8;
    public static final int MAX_NOTES = 8;

    private DialoguePayloads() {
    }

    /**
     * 服务端 → 客户端：对话界面的内容。右键 NPC 时打开，每次选完、送完、问完再发一份刷新开着的界面。
     *
     * @param open      true 时打开界面；false 只刷新开着的、同一个 NPC 的界面
     * @param entity    NPC 的实体 id
     * @param race      {@code Race} 的序号
     * @param role      {@code NpcRole} 的序号
     * @param tier      玩家在这个种族的声望档（{@code Standing.Tier} 的序号）
     * @param standing  声望的数
     * @param encounter 今天这道题的 id
     * @param words     外星话里依次出现的概念
     * @param alien     这些概念的外星写法
     * @param known     玩家认识这个种族的哪些词（掩码）
     * @param options   三个选项依次对上什么（{@code Value} 的序号）
     * @param flags     {@link #ANSWERED} 这些位
     * @param notes     上一次动作的结果：NPC 的回话、声望、学会的词、奖励
     */
    public record State(boolean open, int entity, String nameZh, String nameEn, int race, int role, int tier, int standing,
            String encounter, List<Integer> words, List<String> alien, long known, List<Integer> options, int flags,
            List<Component> notes) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(Universe.id("dialogue_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM_CODEC = StreamCodec.of(State::write, State::read);

        public boolean has(int flag) {
            return (this.flags & flag) != 0;
        }

        private static void write(RegistryFriendlyByteBuf buf, State state) {
            buf.writeBoolean(state.open);
            buf.writeVarInt(state.entity);
            buf.writeUtf(state.nameZh, 64);
            buf.writeUtf(state.nameEn, 64);
            buf.writeVarInt(state.race);
            buf.writeVarInt(state.role);
            buf.writeVarInt(state.tier);
            buf.writeVarInt(state.standing);
            buf.writeUtf(state.encounter, 64);
            int words = Math.min(MAX_WORDS, Math.min(state.words.size(), state.alien.size()));
            buf.writeVarInt(words);
            for (int i = 0; i < words; i++) {
                buf.writeVarInt(state.words.get(i));
                buf.writeUtf(state.alien.get(i), 32);
            }
            buf.writeLong(state.known);
            buf.writeVarInt(state.options.size());
            for (int option : state.options) {
                buf.writeVarInt(option);
            }
            buf.writeVarInt(state.flags);
            int notes = Math.min(MAX_NOTES, state.notes.size());
            buf.writeVarInt(notes);
            for (int i = 0; i < notes; i++) {
                ComponentSerialization.STREAM_CODEC.encode(buf, state.notes.get(i));
            }
        }

        private static State read(RegistryFriendlyByteBuf buf) {
            boolean open = buf.readBoolean();
            int entity = buf.readVarInt();
            String nameZh = buf.readUtf(64);
            String nameEn = buf.readUtf(64);
            int race = buf.readVarInt();
            int role = buf.readVarInt();
            int tier = buf.readVarInt();
            int standing = buf.readVarInt();
            String encounter = buf.readUtf(64);
            int count = Math.min(MAX_WORDS, buf.readVarInt());
            List<Integer> words = new ArrayList<>();
            List<String> alien = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                words.add(buf.readVarInt());
                alien.add(buf.readUtf(32));
            }
            long known = buf.readLong();
            int optionCount = Math.min(3, buf.readVarInt());
            List<Integer> options = new ArrayList<>();
            for (int i = 0; i < optionCount; i++) {
                options.add(buf.readVarInt());
            }
            int flags = buf.readVarInt();
            int noteCount = Math.min(MAX_NOTES, buf.readVarInt());
            List<Component> notes = new ArrayList<>();
            for (int i = 0; i < noteCount; i++) {
                notes.add(ComponentSerialization.STREAM_CODEC.decode(buf));
            }
            return new State(open, entity, nameZh, nameEn, race, role, tier, standing, encounter, words, alien, known, options, flags, notes);
        }

        @Override
        public Type<State> type() {
            return TYPE;
        }
    }

    /**
     * 客户端 → 服务端：对话里按了一个按钮。
     *
     * @param action {@link Action#ANSWER}、{@link Action#GIFT}、{@link Action#ASK}
     * @param option 选第几项（只有回答用）
     */
    public record Action(int entity, int action, int option) implements CustomPacketPayload {
        public static final int ANSWER = 0;
        public static final int GIFT = 1;
        public static final int ASK = 2;
        public static final Type<Action> TYPE = new Type<>(Universe.id("dialogue_action"));
        public static final StreamCodec<ByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Action::entity,
                ByteBufCodecs.VAR_INT, Action::action,
                ByteBufCodecs.VAR_INT, Action::option,
                Action::new);

        @Override
        public Type<Action> type() {
            return TYPE;
        }
    }
}
