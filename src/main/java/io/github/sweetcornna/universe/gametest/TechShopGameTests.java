package io.github.sweetcornna.universe.gametest;

import java.util.List;
import java.util.Objects;

import io.github.sweetcornna.universe.civ.Civ;
import io.github.sweetcornna.universe.civ.TechShop;
import io.github.sweetcornna.universe.core.civ.NpcRole;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.civ.Standing;
import io.github.sweetcornna.universe.core.civ.TechMerchant;
import io.github.sweetcornna.universe.core.procgen.NameGenerator;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.entity.civ.AlienNpc;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.trade.Credits;
import io.github.sweetcornna.universe.trade.Trading;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** 技术商的货（ADR-0027 §六）的游戏内测试，由 {@link UniverseGameTests} 登记。测试世界是主世界，算母星系（商旅族的行情）。 */
final class TechShopGameTests {
    private TechShopGameTests() {
    }

    /**
     * 同一天同一档货不变；买了扣钱、给模块，同一样第二次买不了；价钱对不上不买；钱不够买不了；敌视不卖；
     * 至交的货种类不变、价钱是空间站的价打 85 折。
     */
    static void merchantSellsOncePerDay(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        MinecraftServer server = helper.getLevel().getServer();
        Race race = Race.SCHOLAR;
        AlienNpc npc = spawn(helper, race, 424242L, "test:shop");
        List<TechShop.Offer> offers = TechShop.offers(player, npc);
        // 学者族另卖一张实验记录（ADR-0037 §五）
        helper.assertTrue(offers.size() == TechMerchant.OFFERS + 1, "货不是 3 样模块加一张实验记录：" + offers.size());
        helper.assertTrue(offers.getLast().stack().is(UniverseItems.LAB_NOTES.get())
                && offers.getLast().stack().has(UniverseDataComponents.REACTION.get()), "学者族最后一样不是写着反应的实验记录");
        helper.assertTrue(same(offers, TechShop.offers(player, npc), true), "同一天同一档货变了");

        // 买第一样
        Credits.add(player, 1_000_000);
        long before = Credits.get(player);
        TechShop.Offer first = offers.get(0);
        TechShop.buy(player, npc, 0, first.price());
        helper.assertTrue(Credits.get(player) == before - first.price(), "扣的钱不对：" + before + " -> " + Credits.get(player) + "，价 " + first.price());
        helper.assertTrue(count(player, first.stack()) == 1, "没给模块");
        helper.assertTrue(TechShop.sold(player, npc, 0), "买过的没记下");
        // 同一样再买：不扣钱
        TechShop.buy(player, npc, 0, first.price());
        helper.assertTrue(Credits.get(player) == before - first.price() && count(player, first.stack()) == 1, "同一样一天买了两次");
        // 价钱对不上：不买
        long credits = Credits.get(player);
        TechShop.Offer second = offers.get(1);
        TechShop.buy(player, npc, 1, second.price() + 1);
        helper.assertTrue(Credits.get(player) == credits && count(player, second.stack()) == 0, "价钱对不上也买了");
        // 钱不够：不买
        Credits.add(player, -Credits.get(player) + second.price() - 1);
        TechShop.buy(player, npc, 1, second.price());
        helper.assertTrue(Credits.get(player) == second.price() - 1 && count(player, second.stack()) == 0, "钱不够也买了");
        helper.assertFalse(TechShop.sold(player, npc, 1), "没买成也记成买过了");
        // 敌视：不卖
        Credits.add(player, 1_000_000);
        credits = Credits.get(player);
        Civ.changeStanding(player, race, Standing.MIN - Civ.state(player).standing(race));
        TechShop.buy(player, npc, 1, TechShop.offers(player, npc).get(1).price());
        helper.assertTrue(Credits.get(player) == credits && count(player, second.stack()) == 0, "敌视也卖了");

        // 至交：种类不变，价钱是空间站的价打 85 折
        Civ.changeStanding(player, race, Standing.MAX - Civ.state(player).standing(race));
        List<TechShop.Offer> kindred = TechShop.offers(player, npc);
        helper.assertTrue(same(offers, kindred, false), "升了档货的种类变了");
        for (TechShop.Offer offer : kindred.subList(0, TechMerchant.OFFERS)) {
            double station = Trading.modulePrice(server, 0, offer.stack()).orElseThrow();
            long expected = (long) Math.ceil(station * Standing.priceFactor(Standing.Tier.KINDRED) - 1e-9);
            helper.assertTrue(offer.price() == expected && offer.price() < station, "至交的价钱不对：" + offer.price() + "，空间站 " + station);
        }
        npc.discard();
        helper.succeed();
    }

    /** 两份货单一样：{@code exact} 时连品级、数值、价钱都一样，否则只看种类。 */
    private static boolean same(List<TechShop.Offer> a, List<TechShop.Offer> b, boolean exact) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).stack().has(UniverseDataComponents.REACTION.get())) {
                // 实验记录：写的反应一样
                if (!Objects.equals(a.get(i).stack().get(UniverseDataComponents.REACTION.get()), b.get(i).stack().get(UniverseDataComponents.REACTION.get()))
                        || exact && a.get(i).price() != b.get(i).price()) {
                    return false;
                }
                continue;
            }
            TechModule x = a.get(i).stack().get(UniverseDataComponents.TECH_MODULE.get());
            TechModule y = b.get(i).stack().get(UniverseDataComponents.TECH_MODULE.get());
            if (x == null || y == null || x.type() != y.type()) {
                return false;
            }
            if (exact && (!Objects.equals(x, y) || a.get(i).price() != b.get(i).price())) {
                return false;
            }
        }
        return true;
    }

    /** 背包里和这个模块一模一样（种类、品级、数值）的有几个。 */
    private static int count(ServerPlayer player, ItemStack wanted) {
        int n = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (ItemStack.isSameItemSameComponents(stack, wanted)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private static AlienNpc spawn(GameTestHelper helper, Race race, long seed, String key) {
        AlienNpc npc = UniverseEntities.ALIEN_NPC.get().create(helper.getLevel(), EntitySpawnReason.EVENT);
        if (npc == null) {
            throw new IllegalStateException("放不出 NPC");
        }
        npc.setup(race, NpcRole.TECH_MERCHANT, seed, key, new NameGenerator.Name("Tester", "测试"), helper.absoluteVec(new Vec3(3.5, 1, 3.5)), 0);
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
