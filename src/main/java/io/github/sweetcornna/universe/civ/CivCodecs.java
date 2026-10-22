package io.github.sweetcornna.universe.civ;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.civ.CivState;
import io.github.sweetcornna.universe.core.civ.Guild;
import io.github.sweetcornna.universe.core.civ.Lexicon;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.civ.Standing;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * {@link CivState} 的存档和同步格式。存档里声望和词按种族、公会的 id 存（以后加种族、调顺序都不乱）；
 * 同步只发声望和词，知识石和每天一次的记录只在服务端用。
 */
public final class CivCodecs {
    public static final MapCodec<CivState> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("races", Map.of())
                    .forGetter(state -> byId(Race.values(), Race::id, race -> state.standing(race))),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("guilds", Map.of())
                    .forGetter(state -> byId(Guild.values(), Guild::id, guild -> state.standing(guild))),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("words", Map.of())
                    .forGetter(state -> byId(Race.values(), Race::id, race -> state.words(race))),
            Codec.STRING.listOf().optionalFieldOf("stones", List.of()).forGetter(state -> List.copyOf(state.stones())),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("daily", Map.of()).forGetter(CivState::daily)
    ).apply(i, CivCodecs::restore));

    public static final StreamCodec<ByteBuf, CivState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(16)), state -> boxed(state.races()),
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(16)), state -> boxed(state.guilds()),
            ByteBufCodecs.LONG.apply(ByteBufCodecs.list(16)), state -> boxed(state.wordMasks()),
            (races, guilds, words) -> CivState.restore(
                    races.stream().mapToInt(Integer::intValue).toArray(),
                    guilds.stream().mapToInt(Integer::intValue).toArray(),
                    words.stream().mapToLong(Long::longValue).toArray(),
                    List.of(), Map.of()));

    private CivCodecs() {
    }

    private static CivState restore(Map<String, Integer> races, Map<String, Integer> guilds, Map<String, Long> words,
            List<String> stones, Map<String, Long> daily) {
        int[] raceStanding = new int[Race.values().length];
        long[] wordMasks = new long[Race.values().length];
        for (Race race : Race.values()) {
            raceStanding[race.ordinal()] = races.getOrDefault(race.id(), Standing.START);
            wordMasks[race.ordinal()] = words.getOrDefault(race.id(), Lexicon.INNATE_MASK);
        }
        int[] guildStanding = new int[Guild.values().length];
        for (Guild guild : Guild.values()) {
            guildStanding[guild.ordinal()] = guilds.getOrDefault(guild.id(), Standing.START);
        }
        return CivState.restore(raceStanding, guildStanding, wordMasks, stones, daily);
    }

    private static <E, V> Map<String, V> byId(E[] values, Function<E, String> id, Function<E, V> value) {
        Map<String, V> map = new HashMap<>();
        for (E e : values) {
            map.put(id.apply(e), value.apply(e));
        }
        return map;
    }

    private static List<Integer> boxed(int[] values) {
        return Arrays.stream(values).boxed().toList();
    }

    private static List<Long> boxed(long[] values) {
        return Arrays.stream(values).boxed().toList();
    }
}
