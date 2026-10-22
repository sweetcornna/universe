package io.github.sweetcornna.universe.gametest;

import io.github.sweetcornna.universe.core.darkfog.DarkFogSettings;
import io.github.sweetcornna.universe.core.factory.Veins;
import io.github.sweetcornna.universe.core.sentinel.SentinelKind;
import io.github.sweetcornna.universe.factory.resource.VeinNodes;
import io.github.sweetcornna.universe.registry.UniverseGameRules;
import io.github.sweetcornna.universe.rules.UniverseRules;
import io.github.sweetcornna.universe.sentinel.Sentinel;
import io.github.sweetcornna.universe.sentinel.Sentinels;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.phys.Vec3;

/**
 * 建世界的选项（ADR-0028 §七）的游戏内测试，由 {@link UniverseGameTests} 登记。游戏规则对整个服务器生效：
 * 每个测试在同一刻里改完、查完、改回去，不影响同时跑的别的测试。
 */
final class WorldOptionsGameTests {
    private WorldOptionsGameTests() {
    }

    /** 守卫机强度 0 时放不出守卫机；200% 时血量翻倍。 */
    static void sentinelStrengthScales(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Vec3 pos = helper.absoluteVec(new Vec3(2.5, 3.0, 2.5));
        GameRule<Integer> rule = UniverseGameRules.SENTINEL_STRENGTH.get();
        try {
            set(server, rule, 0);
            helper.assertTrue(Sentinels.spawnAt(level, pos, SentinelKind.DRONE, null) == null, "强度 0 还放出了守卫机");
            set(server, rule, 200);
            Sentinel sentinel = Sentinels.spawnAt(level, pos, SentinelKind.DRONE, null);
            helper.assertTrue(sentinel != null, "强度 200% 放不出守卫机");
            helper.assertTrue(sentinel.getMaxHealth() == 40 && sentinel.getHealth() == 40,
                    "强度 200% 的无人机血量不是 40：" + sentinel.getHealth() + "/" + sentinel.getMaxHealth());
            sentinel.discard();
        } finally {
            set(server, rule, 100);
        }
        helper.succeed();
    }

    /** 资源倍率：300% 时节点的储量是三倍；0 时无限，开采不记数、节点不会采完。 */
    static void resourceMultiplierScalesVeins(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.IRON_ORE);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        VeinNodes.Node node = VeinNodes.node(level.getBlockState(pos));
        helper.assertTrue(node != null, "铁矿不是矿脉节点");
        GameRule<Integer> rule = UniverseGameRules.RESOURCE_MULTIPLIER.get();
        try {
            int base = VeinNodes.initialReserve(level, pos, node);
            set(server, rule, 300);
            int tripled = VeinNodes.initialReserve(level, pos, node);
            helper.assertTrue(Math.abs(tripled - base * 3) <= 2, "300% 的储量不是三倍：" + base + " -> " + tripled);
            set(server, rule, 0);
            helper.assertTrue(VeinNodes.initialReserve(level, pos, node) == Veins.INFINITE, "倍率 0 时储量不是无限");
            for (int i = 0; i < 50; i++) {
                helper.assertTrue(!VeinNodes.extract(level, pos, node), "无限的节点采完了");
            }
            set(server, rule, 100);
            helper.assertTrue(VeinNodes.remaining(level, pos, node) == base, "无限时开采也记了数：" + VeinNodes.remaining(level, pos, node));
        } finally {
            set(server, rule, 100);
        }
        helper.succeed();
    }

    /** 黑雾的规则读成 DarkFogSettings；恒星系数量改了规则也不变。 */
    static void rulesReadIntoSettings(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int systems = UniverseRules.starSystems(server);
        try {
            set(server, UniverseGameRules.DARK_FOG_AGGRESSIVENESS.get(), 4);
            set(server, UniverseGameRules.DARK_FOG_OCCUPATION.get(), 250);
            set(server, UniverseGameRules.STAR_SYSTEMS.get(), 128);
            DarkFogSettings settings = UniverseRules.darkFog(server);
            helper.assertTrue(settings.aggressiveness() == 4, "攻击性没读到：" + settings.aggressiveness());
            helper.assertTrue(settings.occupation() == 2.5, "初始占领没读到：" + settings.occupation());
            helper.assertTrue(settings.enabled(), "黑雾默认是关的");
            helper.assertTrue(UniverseRules.starSystems(server) == systems, "恒星系数量跟着规则变了");
        } finally {
            set(server, UniverseGameRules.DARK_FOG_AGGRESSIVENESS.get(), 2);
            set(server, UniverseGameRules.DARK_FOG_OCCUPATION.get(), 100);
            set(server, UniverseGameRules.STAR_SYSTEMS.get(), 64);
        }
        helper.assertTrue(UniverseRules.darkFog(server).equals(DarkFogSettings.DEFAULT), "改回去以后不是默认值");
        helper.succeed();
    }

    private static void set(MinecraftServer server, GameRule<Integer> rule, int value) {
        server.getGameRules().set(rule, value, server);
    }
}
