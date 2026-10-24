package io.github.sweetcornna.universe.network;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：统计面板里选中的一种物品（ADR-0033 §四）：曲线、参考产量、上下游配方、和别的星球之间的进出口。
 * 26.x 的配方不全发给客户端，上下游在服务端查好了发过去。
 *
 * @param bucketTicks 曲线一格多少刻
 * @param produced    曲线：从旧到新每格每分钟产出多少
 * @param consumed    曲线：每格每分钟消耗多少
 * @param referenceProduced 在用做它的配方、正在运转的设备满速时每分钟一共做多少
 * @param referenceConsumed 在用它当原料、正在运转的设备满速时每分钟一共用多少
 * @param producers   做它的、在运转的设备有几台
 * @param consumers   用它的、在运转的设备有几台
 * @param upstream    做出它的配方
 * @param downstream  用掉它的配方
 * @param trades      和别处之间每分钟运进、运出多少
 */
public record StatsDetailPayload(String scope, int range, String item, int bucketTicks, float[] produced, float[] consumed,
        float referenceProduced, float referenceConsumed, int producers, int consumers, List<Recipe> upstream, List<Recipe> downstream,
        List<Trade> trades) implements CustomPacketPayload {
    public static final Type<StatsDetailPayload> TYPE = new Type<>(Universe.id("stats_detail"));
    public static final StreamCodec<FriendlyByteBuf, StatsDetailPayload> STREAM_CODEC = StreamCodec.of(StatsDetailPayload::write,
            StatsDetailPayload::read);
    /** 上游、下游各最多发几个配方。 */
    public static final int MAX_RECIPES = 32;
    public static final int MAX_TRADES = 32;
    private static final int MAX_POINTS = 60;

    public record Stack(String item, int count) {
    }

    /** 一个配方：设备的物品 ID（工作台是 {@code minecraft:crafting_table}）、原料、产物、标准时间（刻，工作台是 0）。 */
    public record Recipe(String id, String machine, List<Stack> inputs, List<Stack> outputs, int time) {
    }

    public record Trade(String partner, float imported, float exported) {
    }

    private static void write(FriendlyByteBuf buf, StatsDetailPayload payload) {
        buf.writeUtf(payload.scope, 256);
        buf.writeVarInt(payload.range);
        buf.writeUtf(payload.item, 256);
        buf.writeVarInt(payload.bucketTicks);
        writeFloats(buf, payload.produced);
        writeFloats(buf, payload.consumed);
        buf.writeFloat(payload.referenceProduced);
        buf.writeFloat(payload.referenceConsumed);
        buf.writeVarInt(payload.producers);
        buf.writeVarInt(payload.consumers);
        writeRecipes(buf, payload.upstream);
        writeRecipes(buf, payload.downstream);
        int n = Math.min(MAX_TRADES, payload.trades.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Trade trade = payload.trades.get(i);
            buf.writeUtf(trade.partner, 256);
            buf.writeFloat(trade.imported);
            buf.writeFloat(trade.exported);
        }
    }

    private static StatsDetailPayload read(FriendlyByteBuf buf) {
        String scope = buf.readUtf(256);
        int range = buf.readVarInt();
        String item = buf.readUtf(256);
        int bucketTicks = buf.readVarInt();
        float[] produced = readFloats(buf);
        float[] consumed = readFloats(buf);
        float referenceProduced = buf.readFloat();
        float referenceConsumed = buf.readFloat();
        int producers = buf.readVarInt();
        int consumers = buf.readVarInt();
        List<Recipe> upstream = readRecipes(buf);
        List<Recipe> downstream = readRecipes(buf);
        int n = Math.min(MAX_TRADES, buf.readVarInt());
        List<Trade> trades = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            trades.add(new Trade(buf.readUtf(256), buf.readFloat(), buf.readFloat()));
        }
        return new StatsDetailPayload(scope, range, item, bucketTicks, produced, consumed, referenceProduced, referenceConsumed, producers,
                consumers, upstream, downstream, trades);
    }

    static void writeFloats(FriendlyByteBuf buf, float[] values) {
        int n = Math.min(MAX_POINTS, values.length);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeFloat(values[i]);
        }
    }

    static float[] readFloats(FriendlyByteBuf buf) {
        int n = Math.min(MAX_POINTS, buf.readVarInt());
        float[] values = new float[n];
        for (int i = 0; i < n; i++) {
            values[i] = buf.readFloat();
        }
        return values;
    }

    private static void writeRecipes(FriendlyByteBuf buf, List<Recipe> recipes) {
        int n = Math.min(MAX_RECIPES, recipes.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Recipe recipe = recipes.get(i);
            buf.writeUtf(recipe.id, 256);
            buf.writeUtf(recipe.machine, 256);
            writeStacks(buf, recipe.inputs);
            writeStacks(buf, recipe.outputs);
            buf.writeVarInt(recipe.time);
        }
    }

    private static List<Recipe> readRecipes(FriendlyByteBuf buf) {
        int n = Math.min(MAX_RECIPES, buf.readVarInt());
        List<Recipe> recipes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            recipes.add(new Recipe(buf.readUtf(256), buf.readUtf(256), readStacks(buf), readStacks(buf), buf.readVarInt()));
        }
        return recipes;
    }

    private static void writeStacks(FriendlyByteBuf buf, List<Stack> stacks) {
        int n = Math.min(9, stacks.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeUtf(stacks.get(i).item, 256);
            buf.writeVarInt(stacks.get(i).count);
        }
    }

    private static List<Stack> readStacks(FriendlyByteBuf buf) {
        int n = Math.min(9, buf.readVarInt());
        List<Stack> stacks = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            stacks.add(new Stack(buf.readUtf(256), buf.readVarInt()));
        }
        return stacks;
    }

    @Override
    public Type<StatsDetailPayload> type() {
        return TYPE;
    }
}
