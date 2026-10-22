package io.github.sweetcornna.universe.gametest;

import java.util.List;

import io.github.sweetcornna.universe.civ.Civ;
import io.github.sweetcornna.universe.civ.Dialogues;
import io.github.sweetcornna.universe.core.civ.CivState;
import io.github.sweetcornna.universe.core.civ.Encounter;
import io.github.sweetcornna.universe.core.civ.Gifts;
import io.github.sweetcornna.universe.core.civ.Lexicon;
import io.github.sweetcornna.universe.core.civ.NpcRole;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.civ.Standing;
import io.github.sweetcornna.universe.core.procgen.NameGenerator;
import io.github.sweetcornna.universe.entity.civ.AlienNpc;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** 外星 NPC 和对话的游戏内测试（ADR-0027 §三、§四），由 {@link UniverseGameTests} 登记。 */
final class NpcGameTests {
    private NpcGameTests() {
    }

    /**
     * 选对：声望 +3、从那段话里学一个词、拿到奖励；同一天再答没用。另一个 NPC 选错掉 1 点。用学者族：奖励都是物品
     * （模拟玩家没有连接，加信用点时同步附件会出错）。
     */
    static void rightAnswerTeachesWord(GameTestHelper helper) {
        floor(helper);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        AlienNpc npc = spawn(helper, Race.SCHOLAR, 12345L, "test:right");
        AlienNpc other = spawn(helper, Race.SCHOLAR, 67890L, "test:wrong");
        long day = Dialogues.day(level.getServer());
        Encounter encounter = Dialogues.encounter(npc, day);
        int right = option(encounter, Race.SCHOLAR, true);
        CivState state = Civ.state(player);
        int standing = state.standing(Race.SCHOLAR);
        int words = state.knownCount(Race.SCHOLAR);
        List<?> notes = Dialogues.answer(player, npc, right);
        helper.assertTrue(state.standing(Race.SCHOLAR) == standing + Standing.RIGHT_ANSWER,
                "选对了声望没加 3：" + standing + " -> " + state.standing(Race.SCHOLAR));
        helper.assertTrue(state.knownCount(Race.SCHOLAR) == words + 1, "选对了没学会词：" + words + " -> " + state.knownCount(Race.SCHOLAR));
        helper.assertTrue((state.words(Race.SCHOLAR) & encounter.wordMask() & ~Lexicon.INNATE_MASK) != 0,
                "学会的词不是那段话里的");
        helper.assertFalse(player.getInventory().isEmpty(), "选对了没给奖励");
        helper.assertTrue(notes.size() >= 3, "结果没写全：" + notes);
        // 同一天再答一次：什么也不变
        int after = state.standing(Race.SCHOLAR);
        Dialogues.answer(player, npc, right);
        helper.assertTrue(state.standing(Race.SCHOLAR) == after && state.knownCount(Race.SCHOLAR) == words + 1, "同一天答了两次");
        // 另一个 NPC 选错
        int wrong = option(Dialogues.encounter(other, day), Race.SCHOLAR, false);
        Dialogues.answer(player, other, wrong);
        helper.assertTrue(state.standing(Race.SCHOLAR) == after + Standing.WRONG_ANSWER, "选错了声望没减 1");
        npc.discard();
        other.discard();
        helper.succeed();
    }

    /**
     * 送礼：学者族喜欢数据，送一个导航数据声望 +2、回教一个词；同一个 NPC 一天只收一次。空手送不出去，不算次数；
     * 不合心意的（守卫机残骸）照收，声望不变。声望不到"认识"不能请教，到了以后每天一个。
     */
    static void giftOnceADay(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        AlienNpc npc = spawn(helper, Race.SCHOLAR, 2468L, "test:gift");
        AlienNpc other = spawn(helper, Race.SCHOLAR, 1357L, "test:plain");
        CivState state = Civ.state(player);
        player.getInventory().setSelectedSlot(0);
        player.getInventory().setItem(0, new ItemStack(UniverseItems.NAVIGATION_DATA.get(), 3));
        helper.assertTrue(Dialogues.giftKind(player.getMainHandItem()) == Gifts.Kind.DATA, "导航数据不算数据");
        int standing = state.standing(Race.SCHOLAR);
        int words = state.knownCount(Race.SCHOLAR);
        Dialogues.gift(player, npc);
        helper.assertTrue(player.getMainHandItem().getCount() == 2, "礼物没送出去一个：" + player.getMainHandItem());
        helper.assertTrue(state.standing(Race.SCHOLAR) == standing + Standing.LIKED_GIFT, "合心意的礼物声望没加 2");
        helper.assertTrue(state.knownCount(Race.SCHOLAR) == words + 1, "合心意的礼物没回教一个词");
        Dialogues.gift(player, npc);
        helper.assertTrue(player.getMainHandItem().getCount() == 2, "同一个 NPC 一天收了两次礼");
        helper.assertTrue(state.standing(Race.SCHOLAR) == standing + Standing.LIKED_GIFT, "第二次送礼也加了声望");
        // 空手：送不出去，也不算今天送过
        player.getInventory().setItem(0, ItemStack.EMPTY);
        Dialogues.gift(player, other);
        player.getInventory().setItem(0, new ItemStack(UniverseItems.SENTINEL_SCRAP.get(), 2));
        Dialogues.gift(player, other);
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "空手送礼以后再送就送不出去了");
        helper.assertTrue(state.standing(Race.SCHOLAR) == standing + Standing.LIKED_GIFT, "不合心意的礼物也加了声望");
        // 请教：先不够熟，加到"认识"以后每天一个
        int known = state.knownCount(Race.SCHOLAR);
        if (Standing.canAskWord(state.tier(Race.SCHOLAR))) {
            helper.fail("没到认识就能请教了：" + state.standing(Race.SCHOLAR));
        }
        Dialogues.ask(player, npc);
        helper.assertTrue(state.knownCount(Race.SCHOLAR) == known, "不够熟也教了词");
        Civ.changeStanding(player, Race.SCHOLAR, Standing.Tier.ACQUAINTED.min() - state.standing(Race.SCHOLAR));
        Dialogues.ask(player, npc);
        helper.assertTrue(state.knownCount(Race.SCHOLAR) == known + 1, "认识以后请教没学到词");
        Dialogues.ask(player, npc);
        helper.assertTrue(state.knownCount(Race.SCHOLAR) == known + 1, "同一个 NPC 一天教了两个词");
        npc.discard();
        other.discard();
        helper.succeed();
    }

    /** 打 NPC：打不死，掉 5 点声望；两秒内连着打只算一次。打一下还是陌生，再掉就敌视了。 */
    static void hitCostsStanding(GameTestHelper helper) {
        floor(helper);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        AlienNpc npc = spawn(helper, Race.WARDEN, 97531L, "test:hit");
        CivState state = Civ.state(player);
        int standing = state.standing(Race.WARDEN);
        float health = npc.getHealth();
        helper.assertFalse(npc.hurtServer(level, level.damageSources().playerAttack(player), 50), "NPC 挨打掉了血");
        helper.assertTrue(npc.isAlive() && npc.getHealth() == health, "NPC 被打伤了");
        helper.assertTrue(state.standing(Race.WARDEN) == standing + Standing.ATTACK, "打了 NPC 声望没掉 5");
        npc.hurtServer(level, level.damageSources().playerAttack(player), 50);
        helper.assertTrue(state.standing(Race.WARDEN) == standing + Standing.ATTACK, "连着打掉了两次声望");
        helper.assertTrue(Standing.tier(state.standing(Race.WARDEN)) == Standing.Tier.STRANGER, "打一下就敌视了");
        Civ.changeStanding(player, Race.WARDEN, Standing.WRONG_ANSWER);
        helper.assertTrue(Standing.tier(state.standing(Race.WARDEN)) == Standing.Tier.HOSTILE, "掉到 -6 还不是敌视");
        npc.discard();
        helper.succeed();
    }

    /** 选第一个对（或错）的选项。 */
    private static int option(Encounter encounter, Race race, boolean right) {
        for (int i = 0; i < encounter.options().size(); i++) {
            if (encounter.correct(race, i) == right) {
                return i;
            }
        }
        throw new IllegalStateException("题目没有" + (right ? "对" : "错") + "的选项：" + encounter);
    }

    private static AlienNpc spawn(GameTestHelper helper, Race race, long seed, String key) {
        AlienNpc npc = UniverseEntities.ALIEN_NPC.get().create(helper.getLevel(), EntitySpawnReason.EVENT);
        if (npc == null) {
            throw new IllegalStateException("放不出 NPC");
        }
        npc.setup(race, NpcRole.VISITOR, seed, key, new NameGenerator.Name("Tester", "测试"), helper.absoluteVec(new Vec3(3.5, 1, 3.5)), 0);
        npc.setNoAi(true);
        helper.getLevel().addFreshEntity(npc);
        return npc;
    }

    /** 测试场地的地板在测试世界里放不出来，自己铺一块。 */
    private static void floor(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(1, 0, 1, 5, 0, 5)) {
            helper.setBlock(pos, Blocks.STONE);
        }
    }
}
