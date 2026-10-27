package io.github.sweetcornna.universe.chem.reactor;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.chem.reaction.Reaction;
import io.github.sweetcornna.universe.core.chem.reaction.YieldGrid;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 全服跑过的工况（ADR-0036 §四），存在 {@code data/universe/reactor_knowledge.dat}：每条反应在产量图的哪些格子里做成过一批。
 * 一台反应釜试出来的，别的反应釜的界面上也看得到。按反应的名字存格子的编号（{@link YieldGrid}），读到不认识的反应就跳过。
 *
 * <p>界面数据按 16 位传，打开界面的玩家从菜单数据里拿到选中那条反应的格子（{@link #word}），格子变了下一刻就同步过去。
 */
public final class ReactorKnowledge extends SavedData {
    /** 一段多少个格子。 */
    public static final int WORD_BITS = 16;
    /** 产量图分几段传。 */
    public static final int WORDS = (YieldGrid.CELLS + WORD_BITS - 1) / WORD_BITS;

    private static final Codec<ReactorKnowledge> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT.listOf()).optionalFieldOf("measured", Map.of()).forGetter(ReactorKnowledge::encode)
    ).apply(i, ReactorKnowledge::new));

    public static final SavedDataType<ReactorKnowledge> TYPE = new SavedDataType<>(Universe.id("reactor_knowledge"), ReactorKnowledge::new, CODEC);

    private final Map<Reaction, BitSet> measured = new EnumMap<>(Reaction.class);

    private ReactorKnowledge() {
    }

    private ReactorKnowledge(Map<String, List<Integer>> saved) {
        saved.forEach((name, cells) -> {
            Reaction reaction = Reaction.byName(name);
            if (reaction == null) {
                return;
            }
            BitSet bits = this.bits(reaction);
            for (int cell : cells) {
                if (cell >= 0 && cell < YieldGrid.CELLS) {
                    bits.set(cell);
                }
            }
        });
    }

    public static ReactorKnowledge get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** 在这一格做成了一批。 */
    public void mark(Reaction reaction, int cell) {
        BitSet bits = this.bits(reaction);
        if (!bits.get(cell)) {
            bits.set(cell);
            this.setDirty();
        }
    }

    public boolean measured(Reaction reaction, int cell) {
        BitSet bits = this.measured.get(reaction);
        return bits != null && bits.get(cell);
    }

    /** 第 {@code word} 段的 16 个格子，第 b 位是编号 word × 16 + b 的格子。 */
    public int word(Reaction reaction, int word) {
        BitSet bits = this.measured.get(reaction);
        if (bits == null) {
            return 0;
        }
        int value = 0;
        for (int b = 0; b < WORD_BITS; b++) {
            if (bits.get(word * WORD_BITS + b)) {
                value |= 1 << b;
            }
        }
        return value;
    }

    private BitSet bits(Reaction reaction) {
        return this.measured.computeIfAbsent(reaction, r -> new BitSet(YieldGrid.CELLS));
    }

    private Map<String, List<Integer>> encode() {
        Map<String, List<Integer>> out = new LinkedHashMap<>();
        this.measured.forEach((reaction, bits) -> {
            List<Integer> cells = new ArrayList<>();
            bits.stream().forEach(cells::add);
            if (!cells.isEmpty()) {
                out.put(reaction.name(), cells);
            }
        });
        return out;
    }
}
