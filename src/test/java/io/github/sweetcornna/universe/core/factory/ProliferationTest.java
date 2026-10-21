package io.github.sweetcornna.universe.core.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProliferationTest {
    @Test
    void tiersMatchTheOriginal() {
        // 戴森球计划：额外产出 12.5%、20%、25%，加速 25%、50%、100%，耗电多 30%、70%、150%
        int[] levels = {Proliferation.Tier.MK1.points(), Proliferation.Tier.MK2.points(), Proliferation.Tier.MK3.points()};
        double[] extra = {0.125, 0.2, 0.25};
        double[] speed = {0.25, 0.5, 1.0};
        double[] power = {0.3, 0.7, 1.5};
        for (int i = 0; i < 3; i++) {
            assertEquals(extra[i], Proliferation.extraProducts(levels[i]), 1e-9);
            assertEquals(speed[i], Proliferation.speedup(levels[i]), 1e-9);
            assertEquals(power[i], Proliferation.extraPower(levels[i]), 1e-9);
        }
        assertEquals(12, Proliferation.Tier.MK1.sprays());
        assertEquals(24, Proliferation.Tier.MK2.sprays());
        assertEquals(60, Proliferation.Tier.MK3.sprays());
        assertEquals(0, Proliferation.extraProducts(0));
        assertEquals(1, Proliferation.speedFactor(Proliferation.Mode.EXTRA_PRODUCTS, 4));
        assertEquals(2, Proliferation.speedFactor(Proliferation.Mode.SPEEDUP, 4));
    }

    @Test
    void sprayingTopsUpWithoutStacking() {
        assertEquals(1, Proliferation.sprayed(0, Proliferation.Tier.MK1));
        assertEquals(4, Proliferation.sprayed(1, Proliferation.Tier.MK3));
        // 喷过 Mk.III 的再过 Mk.I 的喷涂机，还是 4 点
        assertEquals(4, Proliferation.sprayed(4, Proliferation.Tier.MK1));
    }

    @Test
    void poolAveragesWhatIsTaken() {
        Proliferation.Pool pool = new Proliferation.Pool();
        pool.add(2, 4);
        pool.add(2, 0);
        // 一半喷过 Mk.III、一半没喷：拿两个平均 2 点
        assertEquals(2, pool.take(2));
        assertEquals(2, pool.count());
        assertEquals(4, pool.points());
        // 不够就不拿
        assertEquals(-1, pool.take(3));
        assertEquals(2, pool.count());
        assertEquals(2, pool.take(2));
        assertEquals(0, pool.points());
    }

    @Test
    void poolRoundsDownAndKeepsTheRemainder() {
        Proliferation.Pool pool = new Proliferation.Pool();
        pool.add(3, 1);
        pool.add(1, 0);
        // 4 个物品 3 点：拿一个分到 0 点（3/4 取整），零头留着
        assertEquals(0, pool.take(1));
        assertEquals(3, pool.points());
        assertEquals(1, pool.take(3));
    }

    @Test
    void reconcileFollowsTheSlot() {
        Proliferation.Pool pool = new Proliferation.Pool();
        pool.add(10, 4);
        // 玩家拿走一半：点数跟着走一半
        pool.reconcile(5);
        assertEquals(5, pool.count());
        assertEquals(20, pool.points());
        // 漏斗放进 5 个没喷过的
        pool.reconcile(10);
        assertEquals(10, pool.count());
        assertEquals(20, pool.points());
        assertEquals(2, pool.take(10));
        // 分不匀的货物整格放进来
        pool.addTotal(3, 5);
        assertEquals(5, pool.points());
        pool.addTotal(1, 9);
        assertEquals(9, pool.points(), "一个物品最多 4 点");
    }

    @Test
    void recipeUsesTheLowestIngredient() {
        assertEquals(1, Proliferation.recipeLevel(4, 1, 2));
        assertEquals(0, Proliferation.recipeLevel());
        assertEquals(4, Proliferation.recipeLevel(4));
    }

    @Test
    void extraProductsAccumulate() {
        Proliferation.ExtraProgress progress = new Proliferation.ExtraProgress();
        int extra = 0;
        // Mk.III 喷满：每做 4 次多出 1 份
        for (int i = 0; i < 40; i++) {
            extra += progress.craft(4);
        }
        assertEquals(10, extra);
        // Mk.I：每做 8 次多 1 份
        extra = 0;
        progress.set(0);
        for (int i = 0; i < 80; i++) {
            extra += progress.craft(1);
        }
        assertEquals(10, extra);
    }
}
