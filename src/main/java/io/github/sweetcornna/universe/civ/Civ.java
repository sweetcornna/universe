package io.github.sweetcornna.universe.civ;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import io.github.sweetcornna.universe.core.civ.AlienTongue;
import io.github.sweetcornna.universe.core.civ.CivState;
import io.github.sweetcornna.universe.core.civ.Guild;
import io.github.sweetcornna.universe.core.civ.Lexicon;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.civ.Standing;
import io.github.sweetcornna.universe.dyson.Dyson;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 外星文明的服务端入口（ADR-0027）：玩家的声望和词、恒星系的主导种族、语言。所有改动在这里做，做完同步给本人。
 */
public final class Civ {
    private static final long DAY = 24000L;
    /** 算好的语言，换了世界（星系种子）就清掉。只在服务端线程用。 */
    private static final Map<Race, AlienTongue> TONGUES = new EnumMap<>(Race.class);
    private static long tonguesSeed;

    private Civ() {
    }

    /** 玩家的状态（附件，死亡后保留）。改了要调 {@link #sync}；用下面的方法改会自己同步。 */
    public static CivState state(ServerPlayer player) {
        return player.getData(UniverseAttachments.CIVILIZATION);
    }

    /** 这个维度所在恒星系的主导种族；主世界算母星系。不在任何恒星系里时为空。 */
    public static Optional<Race> dominant(ServerLevel level) {
        OptionalInt system = Dyson.systemOf(level.dimension());
        if (system.isEmpty()) {
            return Optional.empty();
        }
        return Planets.galaxy(level.getServer()).system(system.getAsInt()).map(Race::dominant);
    }

    /** 这个世界里一个种族的语言。 */
    public static AlienTongue tongue(MinecraftServer server, Race race) {
        long seed = Planets.galaxySeed(server);
        if (seed != tonguesSeed) {
            TONGUES.clear();
            tonguesSeed = seed;
        }
        return TONGUES.computeIfAbsent(race, r -> AlienTongue.of(seed, r));
    }

    /** 加减种族声望；档变了提示一句。 */
    public static void changeStanding(ServerPlayer player, Race race, int delta) {
        CivState state = state(player);
        if (state.addStanding(race, delta)) {
            tell(player, Component.translatable("message.universe.civ.tier", raceName(race), tierName(state.tier(race))).withColor(race.color()));
        }
        sync(player);
    }

    /** 加减公会声望；档变了提示一句。 */
    public static void changeStanding(ServerPlayer player, Guild guild, int delta) {
        CivState state = state(player);
        if (state.addStanding(guild, delta)) {
            tell(player, Component.translatable("message.universe.civ.tier", guildName(guild), guildTierName(state.tier(guild)))
                    .withStyle(ChatFormatting.AQUA));
        }
        sync(player);
    }

    /**
     * 在 {@code among}（概念掩码，-1 是整张词表）里教玩家一个还不认识的词，提示"学会了一个词"；返回概念序号，都认识了返回 -1。
     */
    public static int teachWord(ServerPlayer player, Race race, long among, long seed) {
        int concept = state(player).learnOne(race, among, seed);
        if (concept >= 0) {
            tell(player, Component.translatable("message.universe.civ.word",
                    Component.literal(tongue(player.level().getServer(), race).word(concept)).withColor(race.color()),
                    wordMeaning(concept)));
            sync(player);
        }
        return concept;
    }

    /** 今天和这个 NPC 做过这件事没有（{@code key} 是"动作:NPC"）。 */
    public static boolean usedToday(ServerPlayer player, String key) {
        return state(player).usedToday(key, today(player.level().getServer()));
    }

    public static void markUsed(ServerPlayer player, String key) {
        state(player).markUsed(key, today(player.level().getServer()));
    }

    /** 把状态同步给本人。 */
    public static void sync(ServerPlayer player) {
        player.syncData(UniverseAttachments.CIVILIZATION);
    }

    /** 没有连接的假玩家（GameTest 的模拟玩家）不发。 */
    private static void tell(ServerPlayer player, Component message) {
        if (player.connection != null) {
            player.sendSystemMessage(message);
        }
    }

    /** 第几天：按主世界的游戏时间，睡觉跳夜不算。 */
    static long today(MinecraftServer server) {
        return server.overworld().getGameTime() / DAY;
    }

    // ---- 语言键，界面也用

    public static Component raceName(Race race) {
        return Component.translatable("civ.universe.race." + race.id());
    }

    public static Component guildName(Guild guild) {
        return Component.translatable("civ.universe.guild." + guild.id());
    }

    /** 种族声望的档名。 */
    public static Component tierName(Standing.Tier tier) {
        return Component.translatable("civ.universe.standing." + tier.name().toLowerCase(Locale.ROOT));
    }

    /** 公会声望的档名（分界和种族一样，叫法不同）。 */
    public static Component guildTierName(Standing.Tier tier) {
        return Component.translatable("civ.universe.guild_standing." + tier.name().toLowerCase(Locale.ROOT));
    }

    public static Component wordMeaning(int concept) {
        return Component.translatable("civ.universe.word." + Lexicon.id(concept));
    }
}
