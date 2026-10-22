package io.github.sweetcornna.universe.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github.sweetcornna.universe.civ.Civ;
import io.github.sweetcornna.universe.civ.GuildEnvoy;
import io.github.sweetcornna.universe.core.civ.CivState;
import io.github.sweetcornna.universe.core.civ.Guild;
import io.github.sweetcornna.universe.core.civ.GuildMissions;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.civ.Standing;
import io.github.sweetcornna.universe.core.mission.Mission;
import io.github.sweetcornna.universe.core.mission.MissionBoard;
import io.github.sweetcornna.universe.core.mission.MissionKind;
import io.github.sweetcornna.universe.mission.Missions;
import io.github.sweetcornna.universe.network.OpenMissionBoardPayload;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.trade.Credits;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * 公会（ADR-0027 §六）的游戏内测试，由 {@link UniverseGameTests} 登记。测试世界是主世界，算母星系（0 号），
 * 模拟玩家没有连接：用到的路径都不发包。
 */
final class GuildGameTests {
    private GuildGameTests() {
    }

    /** 做完一个守卫机任务：给报酬，佣兵会 +3、母星系主导种族 +2；没做完交不了；同一天同一个任务不能再接。 */
    static void missionRaisesStanding(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        MinecraftServer server = helper.getLevel().getServer();
        Race race = Planets.galaxy(server).system(0).map(Race::dominant).orElseThrow();
        long day = MissionBoard.day(server.overworld().getGameTime());
        Mission mission = new Mission(0, day, MissionBoard.ENVOY_SLOT, MissionKind.SENTINELS, "", 3, 480, Mission.Bonus.NONE, 0);
        CivState state = Civ.state(player);
        int guild = state.standing(Guild.MERCENARIES);
        int standing = state.standing(race);
        Missions.accept(player, mission);
        helper.assertTrue(Missions.state(player).active().isPresent(), "任务没接上");
        // 还没打：交不了，声望不动
        helper.assertTrue(Missions.handIn(player, 0) != null, "没做完也交上了");
        helper.assertTrue(state.standing(Guild.MERCENARIES) == guild, "没做完也加了公会声望");
        Missions.advance(player, 3);
        long credits = Credits.get(player);
        helper.assertTrue(Missions.handIn(player, 0) == null, "做完了交不上");
        helper.assertTrue(Credits.get(player) == credits + 480, "报酬不对：" + credits + " -> " + Credits.get(player));
        helper.assertTrue(state.standing(Guild.MERCENARIES) == guild + Standing.GUILD_MISSION,
                "佣兵会声望没加 3：" + guild + " -> " + state.standing(Guild.MERCENARIES));
        helper.assertTrue(state.standing(race) == standing + Standing.MISSION_RACE,
                "主导种族声望没加 2：" + standing + " -> " + state.standing(race));
        helper.assertTrue(state.standing(Guild.MERCHANTS) == 0 && state.standing(Guild.EXPLORERS) == 0, "别的公会也加了声望");
        // 同一天再接：接不上
        Missions.accept(player, mission);
        helper.assertTrue(Missions.state(player).active().isEmpty(), "同一天同一个任务接了两次");
        helper.assertTrue(Missions.states(player, List.of(mission)).getFirst() == OpenMissionBoardPayload.DONE, "做完的没记成今天做过");
        helper.succeed();
    }

    /**
     * 使者的任务一天里不变，两个、归这个公会、槽位从 10 起、和任务板不重；档高只多给报酬。接了一个就不能再接第二个
     * （和任务板共用一个"手上的任务"）。
     */
    static void envoyOffersFixedForTheDay(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        MinecraftServer server = helper.getLevel().getServer();
        Set<String> board = new HashSet<>();
        Missions.offers(server, 0).forEach(mission -> board.add(mission.id()));
        for (Guild guild : Guild.values()) {
            List<Mission> offers = Missions.envoyOffers(server, 0, guild, Standing.Tier.STRANGER);
            helper.assertTrue(offers.equals(Missions.envoyOffers(server, 0, guild, Standing.Tier.STRANGER)), guild.id() + " 同一天任务变了");
            // 探险会在没有动植物也找不到地标的星系可能一个都没有，另两个总有
            helper.assertTrue(offers.size() == MissionBoard.ENVOY_OFFERS || guild == Guild.EXPLORERS && offers.isEmpty(),
                    guild.id() + " 任务数不对：" + offers.size());
            List<Mission> master = Missions.envoyOffers(server, 0, guild, Standing.Tier.KINDRED);
            helper.assertTrue(master.size() == offers.size(), guild.id() + " 升了档任务数变了");
            for (int i = 0; i < offers.size(); i++) {
                Mission low = offers.get(i);
                Mission high = master.get(i);
                helper.assertTrue(low.slot() == MissionBoard.ENVOY_SLOT + i, "槽位不对：" + low);
                helper.assertTrue(GuildMissions.guild(low.kind()) == guild, guild.id() + " 给了别的公会的任务：" + low.kind());
                helper.assertFalse(board.contains(low.id()), "和任务板的任务重了：" + low.id());
                helper.assertTrue(high.kind() == low.kind() && high.item().equals(low.item()) && high.count() == low.count(),
                        guild.id() + " 升了档任务变了：" + low + " / " + high);
                helper.assertTrue(high.reward() > low.reward(), guild.id() + " 大师的报酬没多：" + low.reward() + " / " + high.reward());
            }
        }
        List<Mission> offers = Missions.envoyOffers(server, 0, Guild.MERCENARIES, Standing.Tier.STRANGER);
        Missions.accept(player, offers.get(0));
        helper.assertTrue(Missions.states(player, offers).equals(List.of(OpenMissionBoardPayload.ACTIVE, OpenMissionBoardPayload.AVAILABLE)),
                "接了以后状态不对：" + Missions.states(player, offers));
        Missions.accept(player, offers.get(1));
        helper.assertTrue(Missions.state(player).active().map(a -> a.mission().equals(offers.get(0))).orElse(false), "手上有任务还能再接");
        Missions.abandon(player);
        helper.succeed();
    }

    /** 礼物：没升档没有；升一档领一份、再领没有；跳两档连着领两份，资深起带模块；除名不给；掉档再升回来不再给。 */
    static void giftOncePerRank(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        Guild guild = Guild.MERCHANTS;
        CivState state = Civ.state(player);
        helper.assertFalse(GuildEnvoy.claimGift(player, guild), "新人也有礼物");
        long credits = Credits.get(player);
        Civ.changeStanding(player, guild, Standing.Tier.ACQUAINTED.min() - state.standing(guild));
        helper.assertTrue(GuildEnvoy.claimGift(player, guild), "升到会员领不到礼物");
        long expected = credits + gift(guild, Standing.Tier.ACQUAINTED).credits();
        helper.assertTrue(Credits.get(player) == expected, "会员的礼物钱不对：" + Credits.get(player) + "，应为 " + expected);
        helper.assertTrue(count(player, new ItemStack(UniverseItems.NAVIGATION_DATA.get())) == gift(guild, Standing.Tier.ACQUAINTED).navigationData(),
                "会员的礼物没给导航数据");
        helper.assertFalse(GuildEnvoy.claimGift(player, guild), "同一档领了两次");
        // 跳到精英：资深、精英各一份，都带模块
        Civ.changeStanding(player, guild, Standing.Tier.FRIEND.min() - state.standing(guild));
        helper.assertTrue(GuildEnvoy.claimGift(player, guild) && GuildEnvoy.claimGift(player, guild), "跳了两档没能领两次");
        helper.assertFalse(GuildEnvoy.claimGift(player, guild), "跳两档领了三次");
        expected += gift(guild, Standing.Tier.REGULAR).credits() + gift(guild, Standing.Tier.FRIEND).credits();
        helper.assertTrue(Credits.get(player) == expected, "资深、精英的礼物钱不对：" + Credits.get(player) + "，应为 " + expected);
        helper.assertTrue(modules(player) == 2, "资深、精英的礼物模块不是两个：" + modules(player));
        // 除名再升回来：不再给
        Civ.changeStanding(player, guild, Standing.MIN - state.standing(guild));
        helper.assertFalse(GuildEnvoy.claimGift(player, guild), "除名了还给礼物");
        Civ.changeStanding(player, guild, Standing.Tier.FRIEND.min() - state.standing(guild));
        helper.assertFalse(GuildEnvoy.claimGift(player, guild), "掉档再升回来又给了礼物");
        // 别的公会自己算
        helper.assertFalse(state.giftClaimable(Guild.EXPLORERS), "别的公会也有礼物");
        helper.succeed();
    }

    private static GuildMissions.Gift gift(Guild guild, Standing.Tier tier) {
        Optional<GuildMissions.Gift> gift = GuildMissions.gift(guild, tier);
        return gift.orElseThrow(() -> new IllegalStateException(guild.id() + " " + tier + " 没有礼物"));
    }

    private static int count(ServerPlayer player, ItemStack wanted) {
        int n = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (ItemStack.isSameItem(stack, wanted)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private static int modules(ServerPlayer player) {
        int n = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.has(UniverseDataComponents.TECH_MODULE.get())) {
                n += stack.getCount();
            }
        }
        return n;
    }
}
