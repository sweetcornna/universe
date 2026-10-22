package io.github.sweetcornna.universe.core.civ;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.github.sweetcornna.universe.core.procgen.Rng;

/**
 * 一个玩家和外星文明的关系（ADR-0027）：三个种族、三个公会的声望，每个种族认识哪些词，碰过的知识石，
 * 今天和哪些 NPC 做过什么（对话、送礼、请教，每个 NPC 每天各一次），每个公会的礼物领到了哪一档。
 */
public final class CivState {
    private final int[] races = new int[Race.values().length];
    private final int[] guilds = new int[Guild.values().length];
    private final long[] words = new long[Race.values().length];
    private final Set<String> stones = new HashSet<>();
    /** 键是"动作:NPC"，值是做的那一天。 */
    private final Map<String, Long> daily = new HashMap<>();
    /** 每个公会的礼物领到了哪一档（{@link Standing.Tier} 的序号）；新人以下没有礼物。 */
    private final int[] guildGifts = new int[Guild.values().length];

    public CivState() {
        Arrays.fill(this.races, Standing.START);
        Arrays.fill(this.guilds, Standing.START);
        Arrays.fill(this.words, Lexicon.INNATE_MASK);
        Arrays.fill(this.guildGifts, Standing.Tier.STRANGER.ordinal());
    }

    /** 读档用，带上公会礼物领到的档；老存档没有这一项，传空数组。 */
    public static CivState restore(int[] races, int[] guilds, long[] words, Collection<String> stones, Map<String, Long> daily,
            int[] guildGifts) {
        CivState state = restore(races, guilds, words, stones, daily);
        for (int i = 0; i < Math.min(guildGifts.length, state.guildGifts.length); i++) {
            state.guildGifts[i] = Math.clamp(guildGifts[i], Standing.Tier.STRANGER.ordinal(), Standing.Tier.KINDRED.ordinal());
        }
        return state;
    }

    /** 读档用。数组长度对不上时（以后加了种族、公会）多出来的按初值。 */
    public static CivState restore(int[] races, int[] guilds, long[] words, Collection<String> stones, Map<String, Long> daily) {
        CivState state = new CivState();
        for (int i = 0; i < Math.min(races.length, state.races.length); i++) {
            state.races[i] = Standing.clamp(races[i]);
        }
        for (int i = 0; i < Math.min(guilds.length, state.guilds.length); i++) {
            state.guilds[i] = Standing.clamp(guilds[i]);
        }
        for (int i = 0; i < Math.min(words.length, state.words.length); i++) {
            state.words[i] = words[i] | Lexicon.INNATE_MASK;
        }
        state.stones.addAll(stones);
        state.daily.putAll(daily);
        return state;
    }

    // ---- 声望

    public int standing(Race race) {
        return this.races[race.ordinal()];
    }

    public Standing.Tier tier(Race race) {
        return Standing.tier(this.standing(race));
    }

    /** 加减声望，夹在范围里；返回档有没有变。 */
    public boolean addStanding(Race race, int delta) {
        Standing.Tier before = this.tier(race);
        this.races[race.ordinal()] = Standing.clamp(this.races[race.ordinal()] + delta);
        return this.tier(race) != before;
    }

    public int standing(Guild guild) {
        return this.guilds[guild.ordinal()];
    }

    public Standing.Tier tier(Guild guild) {
        return Standing.tier(this.standing(guild));
    }

    public boolean addStanding(Guild guild, int delta) {
        Standing.Tier before = this.tier(guild);
        this.guilds[guild.ordinal()] = Standing.clamp(this.guilds[guild.ordinal()] + delta);
        return this.tier(guild) != before;
    }

    // ---- 公会的礼物

    /** 这个公会还有没领的礼物：档比领到的高。 */
    public boolean giftClaimable(Guild guild) {
        return this.tier(guild).ordinal() > this.guildGifts[guild.ordinal()];
    }

    /** 领一档的礼物（比上次领的高一档），返回领的是哪一档；没有可领的返回空。跳了几档就能连着领几次。 */
    public Optional<Standing.Tier> claimGift(Guild guild) {
        if (!this.giftClaimable(guild)) {
            return Optional.empty();
        }
        int claimed = ++this.guildGifts[guild.ordinal()];
        return Optional.of(Standing.Tier.values()[claimed]);
    }

    public int[] guildGifts() {
        return this.guildGifts.clone();
    }

    // ---- 词

    /** 认识的词，按概念序号的掩码。 */
    public long words(Race race) {
        return this.words[race.ordinal()];
    }

    public boolean knows(Race race, int concept) {
        return (this.words[race.ordinal()] & 1L << concept) != 0;
    }

    /** 学会一个词；原来就认识时返回 false。 */
    public boolean learn(Race race, int concept) {
        boolean known = this.knows(race, concept);
        this.words[race.ordinal()] |= 1L << concept;
        return !known;
    }

    public int knownCount(Race race) {
        return Long.bitCount(this.words[race.ordinal()]);
    }

    /**
     * 在 {@code among}（概念的掩码，-1 是整张词表）里按种子挑一个还不认识的学会，返回它的序号；都认识了返回 -1。
     */
    public int learnOne(Race race, long among, long seed) {
        long unknown = among & ~this.words[race.ordinal()];
        if (unknown == 0) {
            return -1;
        }
        int pick = Rng.of(seed).nextInt(Long.bitCount(unknown));
        for (int concept = 0; concept < Lexicon.SIZE; concept++) {
            if ((unknown & 1L << concept) != 0 && pick-- == 0) {
                this.learn(race, concept);
                return concept;
            }
        }
        return -1;
    }

    // ---- 知识石

    /** 碰一块知识石；第一次碰返回 true。键由适配层定（星球加坐标）。 */
    public boolean touchStone(String key) {
        return this.stones.add(key);
    }

    // ---- 每天一次

    public boolean usedToday(String key, long day) {
        Long used = this.daily.get(key);
        return used != null && used == day;
    }

    /** 记下今天做过；顺手清掉以前的。 */
    public void markUsed(String key, long day) {
        this.daily.values().removeIf(d -> d < day);
        this.daily.put(key, day);
    }

    /** 忘掉今天做过的事（调试命令用）。 */
    public void clearDaily() {
        this.daily.clear();
    }

    // ---- 存档用

    public int[] races() {
        return this.races.clone();
    }

    public int[] guilds() {
        return this.guilds.clone();
    }

    public long[] wordMasks() {
        return this.words.clone();
    }

    public Set<String> stones() {
        return Set.copyOf(this.stones);
    }

    public Map<String, Long> daily() {
        return Map.copyOf(this.daily);
    }
}
