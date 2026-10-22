package io.github.sweetcornna.universe.core.civ;

import java.util.List;
import java.util.Optional;

import io.github.sweetcornna.universe.core.mission.MissionKind;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.ModuleType;

/**
 * 公会和任务（ADR-0027 §六）：每种任务归哪个公会，空间站的使者是哪个公会的，报酬随公会的档涨多少，每升一档领的礼物。
 *
 * <p>任务的公会只看种类，不另外存：送货、采矿归商会，清剿、守卫机归佣兵会，测绘、地标归探险会。
 */
public final class GuildMissions {
    private static final List<MissionKind> MERCHANTS = List.of(MissionKind.DELIVERY, MissionKind.MINING);
    private static final List<MissionKind> MERCENARIES = List.of(MissionKind.BOUNTY, MissionKind.SENTINELS);
    private static final List<MissionKind> EXPLORERS = List.of(MissionKind.SURVEY, MissionKind.LANDMARK);

    private GuildMissions() {
    }

    public static Guild guild(MissionKind kind) {
        return switch (kind) {
            case DELIVERY, MINING -> Guild.MERCHANTS;
            case BOUNTY, SENTINELS -> Guild.MERCENARIES;
            case SURVEY, LANDMARK -> Guild.EXPLORERS;
        };
    }

    /** 这个公会管的任务种类，使者从里面出。 */
    public static List<MissionKind> kinds(Guild guild) {
        return switch (guild) {
            case MERCHANTS -> MERCHANTS;
            case MERCENARIES -> MERCENARIES;
            case EXPLORERS -> EXPLORERS;
        };
    }

    /** 使者是哪个公会的：按 NPC 的种子定（空间站的使者不换人，公会也不换）。 */
    public static Guild envoyGuild(long npcSeed) {
        return Rng.of(Seeds.derive(npcSeed, "guild")).pick(Guild.values());
    }

    /** 除名的人使者不给任务、不给礼物。 */
    public static boolean serves(Standing.Tier tier) {
        return tier != Standing.Tier.HOSTILE;
    }

    /** 使者给的任务报酬的倍数：新人 1，会员 1.15，资深 1.3，精英 1.5，大师 1.75。 */
    public static double rewardFactor(Standing.Tier tier) {
        return switch (tier) {
            case HOSTILE, STRANGER -> 1.0;
            case ACQUAINTED -> 1.15;
            case REGULAR -> 1.3;
            case FRIEND -> 1.5;
            case KINDRED -> 1.75;
        };
    }

    /**
     * 升到一档领的礼物：信用点、导航数据，资深以后加一个这个公会擅长的模块（资深 B、精英 A、大师 S）。
     * 商会的信用点多一半，探险会多给两份导航数据。
     *
     * @param modules 抽模块的范围；空的就不给模块
     * @param grade   模块的品级
     */
    public record Gift(long credits, int navigationData, List<ModuleType> modules, Grade grade) {
    }

    /** 升到这一档的礼物；新人、除名没有。 */
    public static Optional<Gift> gift(Guild guild, Standing.Tier tier) {
        long credits;
        int navigation;
        Grade grade;
        switch (tier) {
            case ACQUAINTED -> {
                credits = 800;
                navigation = 2;
                grade = null;
            }
            case REGULAR -> {
                credits = 1500;
                navigation = 2;
                grade = Grade.B;
            }
            case FRIEND -> {
                credits = 3000;
                navigation = 3;
                grade = Grade.A;
            }
            case KINDRED -> {
                credits = 6000;
                navigation = 4;
                grade = Grade.S;
            }
            default -> {
                return Optional.empty();
            }
        }
        if (guild == Guild.MERCHANTS) {
            credits = credits * 3 / 2;
        }
        if (guild == Guild.EXPLORERS) {
            navigation += 2;
        }
        List<ModuleType> modules = grade == null ? List.of() : giftModules(guild);
        return Optional.of(new Gift(credits, navigation, modules, grade == null ? Grade.C : grade));
    }

    /** 礼物里的模块：商会给飞船的，佣兵会给武器和护盾，探险会给扫描和外骨骼。 */
    static List<ModuleType> giftModules(Guild guild) {
        return switch (guild) {
            case MERCHANTS -> List.of(ModuleType.THRUST_EFFICIENCY, ModuleType.PULSE_TUNING, ModuleType.HYPERDRIVE_BOOSTER);
            case MERCENARIES -> List.of(ModuleType.WEAPON_CALIBRATOR, ModuleType.SHIELD, ModuleType.FIRE_CONTROL, ModuleType.DEFLECTOR);
            case EXPLORERS -> List.of(ModuleType.SCANNER_ARRAY, ModuleType.ANALYZER, ModuleType.JET_BOOSTER, ModuleType.LIFE_SUPPORT);
        };
    }
}
