package io.github.sweetcornna.universe.gametest;

import java.util.List;

import com.mojang.serialization.DynamicOps;

import io.github.sweetcornna.universe.civ.NpcSpawner;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.fishing.Habitat;
import io.github.sweetcornna.universe.core.poi.Landmark;
import io.github.sweetcornna.universe.core.poi.LandmarkType;
import io.github.sweetcornna.universe.core.settlement.BuildingType;
import io.github.sweetcornna.universe.core.settlement.Settlement;
import io.github.sweetcornna.universe.fishing.Fishing;
import io.github.sweetcornna.universe.pirate.PirateFighter;
import io.github.sweetcornna.universe.settlement.SettlementData;
import io.github.sweetcornna.universe.settlement.SettlementRaids;
import io.github.sweetcornna.universe.settlement.Settlements;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 1.0 发布前查出来的聚落问题的游戏内测试：总督在附近但聚落中心不更新实体时来袭按防御值算、战机出发的地方往总督那边拉进更新范围、
 * 鱼塘的水记下来（区块没加载时不为了读水去加载）、接管整平时箱子连东西掉出来、没盖发掘场的地方不放收藏家。由 {@link UniverseGameTests} 登记。
 *
 * <p>"中心不更新实体"用一个离场地很远的区块：只靠东边隔一格的强制加载区块带着，加载到能读写方块，但和离玩家超过模拟距离的区块一样不更新实体。
 * 假玩家不在玩家表里，来袭要拿它当总督时走 {@link SettlementRaids} 给测试的入口。
 */
final class SettlementFixGameTests {
    /** 场地中间的地面。 */
    private static final BlockPos CENTER = new BlockPos(3, 0, 3);
    /** 地基都在 ±30 格以内，强制加载这么大一片。 */
    private static final int SITE_RADIUS = 40;

    private SettlementFixGameTests() {
    }

    /** {@code center} 周围 {@code radius} 格内的区块强制加载（或者放掉）。 */
    private static void chunks(ServerLevel level, BlockPos center, boolean forced, int radius) {
        for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
            for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
                level.setChunkForced(cx, cz, forced);
            }
        }
    }

    /** 离场地 {@code (dx, dz)} 的一处地面，那个区块加载着、不更新实体。用完调 {@link #release}。 */
    private static BlockPos idleSite(GameTestHelper helper, int dx, int dz) {
        ServerLevel level = helper.getLevel();
        BlockPos far = helper.absolutePos(CENTER).offset(dx, 0, dz);
        level.setChunkForced((far.getX() >> 4) + 2, far.getZ() >> 4, true);
        level.getChunk(far.getX() >> 4, far.getZ() >> 4);
        BlockPos site = new BlockPos(far.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, far.getX(), far.getZ()) - 1, far.getZ());
        helper.assertTrue(level.isLoaded(site), "中心的区块没加载");
        helper.assertFalse(level.isPositionEntityTicking(site), "中心的区块在更新实体");
        return site;
    }

    private static void release(ServerLevel level, BlockPos site) {
        level.setChunkForced((site.getX() >> 4) + 2, site.getZ() >> 4, false);
    }

    /** 在 {@code center} 立一个聚落（不整平）交给站在中心的假玩家；{@code strong} 时六座 S 档炮台、一座瞭望塔，幸福拉满、没有威胁。 */
    private static Settlement claimed(GameTestHelper helper, ServerPlayer player, BlockPos center, int salt, boolean strong) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Settlement s = Settlements.testSite(level, center, center.asLong() * 31 + salt, Race.WARDEN);
        Settlements.register(server, s);
        s.claim(player.getUUID(), "test", server.overworld().getGameTime());
        player.setPos(center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5);
        if (strong) {
            for (int i = 0; i < 6; i++) {
                built(s, i, BuildingType.TURRET, 3);
            }
            built(s, 6, BuildingType.WATCHTOWER, 3);
            s.debugSet(100, 0, 0);
        }
        return s;
    }

    private static void built(Settlement s, int index, BuildingType type, int grade) {
        s.plot(index).restore(type, BuildingType.STAGES, grade, false, 0, 0, true, false, java.util.Map.of());
    }

    private static SettlementData.Entry entry(GameTestHelper helper, Settlement s) {
        return SettlementData.get(helper.getLevel().getServer()).entry(s.id()).orElseThrow();
    }

    // ---- 海盗

    /**
     * 打到一半，总督就站在聚落中心，可中心不更新实体：战机停着不动，以前要等满 2 分钟按没守住算；现在和总督走开一样，
     * 过一会儿按防御值算，防御高的守住了。
     */
    static void raidIdleCentreRollsDefense(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = idleSite(helper, 3000, 0);
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        Settlement s = claimed(helper, player, site, 11, true);
        List<PirateFighter> fighters = SettlementRaids.testRaid(level, entry(helper, s), 2, Vec3.atCenterOf(site).add(0, 30, 0), player);
        helper.assertValueEqual(fighters.size(), 2, "放出来的战机");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(s.raidsWon() + s.raidsLost() > 0, "来袭还没算"))
                .thenExecute(() -> {
                    helper.assertValueEqual(s.raidsWon(), 1, "防御高的守住次数");
                    helper.assertValueEqual(s.raidsLost(), 0, "防御高的失守次数");
                    fighters.forEach(Entity::discard);
                    Settlements.forget(level.getServer(), s.id());
                    release(level, site);
                })
                .thenSucceed();
    }

    /**
     * 到点了，总督在聚落东边 100 格，他周围在更新实体、聚落中心不在：战机出发的地方拉得进更新范围，可飞到聚落就停住了，
     * 所以不放战机，直接按防御值算。
     */
    static void raidBeginSkipsIdleCentre(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos stand = helper.absolutePos(CENTER).offset(-3000, 0, 0);
        int reach = 64;
        chunks(level, stand, true, reach);
        BlockPos site = stand.offset(-100, 0, 0).atY(level.getHeight(Heightmap.Types.MOTION_BLOCKING, stand.getX(), stand.getZ()) - 1);
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        Settlement s = claimed(helper, player, site, 12, true);
        player.setPos(stand.getX() + 0.5, site.getY() + 1, stand.getZ() + 0.5);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ticking(level, stand, reach), "强制加载的区块还没开始更新实体"))
                .thenExecute(() -> {
                    helper.assertFalse(level.isPositionEntityTicking(site), "聚落中心在更新实体");
                    List<PirateFighter> fighters = SettlementRaids.testBegin(level, entry(helper, s), player);
                    helper.assertTrue(fighters.isEmpty(), "聚落中心不更新实体也放了战机");
                    helper.assertValueEqual(s.raidsWon(), 1, "按防御值算的守住次数");
                    Settlements.forget(level.getServer(), s.id());
                    chunks(level, stand, false, reach);
                })
                .thenSucceed();
    }

    /** 只有聚落周围一小片在更新实体：140 格外的出发点不行，往总督那边拉近，每架都从更新范围里飞来。 */
    static void raidSpawnPulledTowardOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos far = helper.absolutePos(CENTER).offset(0, 0, 3000);
        chunks(level, far, true, SITE_RADIUS);
        BlockPos site = new BlockPos(far.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, far.getX(), far.getZ()) - 1, far.getZ());
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        Settlement s = claimed(helper, player, site, 13, false);
        helper.startSequence()
                // 强制加载的区块要过几刻才开始更新实体
                .thenWaitUntil(() -> helper.assertTrue(ticking(level, site, SITE_RADIUS),"强制加载的区块还没开始更新实体"))
                .thenExecute(() -> {
                    List<PirateFighter> fighters = SettlementRaids.testBegin(level, entry(helper, s), player);
                    helper.assertTrue(!fighters.isEmpty(), "中心在更新实体却没放战机");
                    for (PirateFighter fighter : fighters) {
                        helper.assertTrue(level.isPositionEntityTicking(fighter.blockPosition()), "战机放在了不更新实体的地方：" + fighter.blockPosition());
                        double d = s.distance(fighter.getX(), fighter.getZ());
                        helper.assertTrue(d < 139, "出发点没往总督那边拉近：" + d);
                    }
                    Settlements.forget(level.getServer(), s.id());
                    fighters.forEach(Entity::discard);
                    chunks(level, far, false, SITE_RADIUS);
                })
                .thenSucceed();
    }

    /** {@code center} 周围 {@code radius} 格内（按方形）都在更新实体。 */
    private static boolean ticking(ServerLevel level, BlockPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx += 8) {
            for (int dz = -radius; dz <= radius; dz += 8) {
                if (!level.isPositionEntityTicking(center.offset(dx, 0, dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    // ---- 鱼塘

    /**
     * 鱼塘的水：区块加载着现读并记下；区块没加载用记下的，不去加载区块；记下的水跟着存档走，旧存档里没有这一项照样读得出来。
     */
    static void pondWaterCached(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = helper.absolutePos(CENTER);
        chunks(level, center, true, SITE_RADIUS);
        Settlement here = Settlements.testSite(level, center, center.asLong() * 31 + 14, Race.TRADER);
        built(here, 0, BuildingType.FISHING_POND, 0);
        SettlementData.Entry loaded = Settlements.register(server, here);
        BlockPos pond = Settlements.testPlotPos(loaded, 0, 0, -1, 0);
        BlockState below = level.getBlockState(pond.below());
        BlockState was = level.getBlockState(pond);
        level.setBlock(pond.below(), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(pond, Blocks.WATER.defaultBlockState(), 2);
        Fishing.Water fresh = Fishing.water(level, pond).orElseThrow();
        helper.assertValueEqual(Settlements.testPondWater(level, loaded, 0), fresh, "加载着时读到的水");
        helper.assertValueEqual(loaded.ponds()[0], fresh, "加载着时记下的水");
        level.setBlock(pond, was, 2);
        level.setBlock(pond.below(), below, 2);

        BlockPos far = helper.absolutePos(CENTER).offset(-3000, 0, 3000);
        Settlement away = Settlements.testSite(level, far.atY(64), far.asLong() * 31 + 15, Race.TRADER);
        built(away, 0, BuildingType.FISHING_POND, 2);
        SettlementData.Entry unloaded = Settlements.register(server, away);
        BlockPos farPond = Settlements.testPlotPos(unloaded, 0, 0, -1, 0);
        helper.assertFalse(level.isLoaded(farPond), "远处的鱼塘一开始就加载着");
        Fishing.Water deep = new Fishing.Water(Habitat.DEEP, 20, false);
        unloaded.ponds()[0] = deep;
        helper.assertValueEqual(Settlements.testPondWater(level, unloaded, 0), deep, "没加载时用的水");
        helper.assertFalse(level.isLoaded(farPond), "为了读鱼塘的水加载了区块");
        helper.assertTrue(!Fishing.pondCatch(level, farPond, deep, level.getRandom(), 2).isEmpty(), "按记下的水什么也没出");
        helper.assertFalse(level.isLoaded(farPond), "按记下的水出鱼时加载了区块");

        // 存档来回一趟
        DynamicOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        SettlementData data = new SettlementData();
        data.add(away, "test|test").ponds()[0] = deep;
        Tag saved = SettlementData.CODEC.encodeStart(ops, data).getOrThrow();
        SettlementData back = SettlementData.CODEC.parse(ops, saved).getOrThrow();
        helper.assertValueEqual(back.entry(away.id()).orElseThrow().ponds()[0], deep, "读回来的鱼塘的水");
        ((CompoundTag) saved).getListOrEmpty("settlements").getCompoundOrEmpty(0).remove("ponds");
        SettlementData old = SettlementData.CODEC.parse(ops, saved).getOrThrow();
        SettlementData.Entry oldEntry = old.entry(away.id()).orElseThrow();
        helper.assertTrue(oldEntry.ponds()[0] == null, "旧存档里没有的水读出了东西");
        helper.assertValueEqual(oldEntry.settlement().plot(0).type(), BuildingType.FISHING_POND, "旧存档读回来的鱼塘");

        Settlements.forget(server, here.id());
        Settlements.forget(server, away.id());
        chunks(level, center, false, SITE_RADIUS);
        helper.succeed();
    }

    // ---- 接管

    /** 接管整平地基时，地基上原来的箱子连箱子带里面的东西一起掉出来，不会凭空没了。 */
    static void claimDropsContainers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos center = helper.absolutePos(CENTER);
        chunks(level, center, true, SITE_RADIUS);
        Settlement site = Settlements.testSite(level, center, center.asLong() * 31 + 16, Race.TRADER);
        SettlementData.Entry entry = Settlements.register(server, site);
        BlockPos chest = Settlements.testPlotPos(entry, 0, 0, 6, 0);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        ((ChestBlockEntity) level.getBlockEntity(chest)).setItem(0, new ItemStack(Items.DIAMOND, 5));
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        player.setPos(center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5);
        Component problem = Settlements.claim(player, level, site, true);
        helper.assertTrue(problem == null, "接管不了：" + (problem == null ? "" : problem.getString()));
        helper.assertFalse(level.getBlockState(chest).is(Blocks.CHEST), "地基上的箱子没清掉");
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(chest).inflate(2));
        int chests = 0;
        int diamonds = 0;
        for (ItemEntity drop : drops) {
            chests += drop.getItem().is(Items.CHEST) ? drop.getItem().getCount() : 0;
            diamonds += drop.getItem().is(Items.DIAMOND) ? drop.getItem().getCount() : 0;
        }
        helper.assertValueEqual(chests, 1, "掉出来的箱子");
        helper.assertValueEqual(diamonds, 5, "箱子里掉出来的钻石");
        drops.forEach(Entity::discard);
        Settlements.forget(server, site.id());
        chunks(level, center, false, SITE_RADIUS);
        helper.succeed();
    }

    // ---- 发掘场的收藏家

    /** 有发掘场地标、没有发掘场（0.14 生成的区块）的地方不放收藏家；摆上他的桌子就放。 */
    static void digSiteNeedsCamp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // 收藏家站在中心往北 6 格、往东 2 格，桌子在中心往北 6 格：中心放在场地南边，两样都落在石台上
        BlockPos center = helper.absolutePos(new BlockPos(1, 0, 6));
        Landmark landmark = new Landmark(LandmarkType.DIG_SITE, center.getX(), center.getZ(), 0, 0, center.asLong(), center.asLong() * 31, 0);
        Vec3 near = Vec3.atCenterOf(helper.absolutePos(CENTER.above()));
        helper.assertFalse(NpcSpawner.testDigSiteCollector(level, landmark, near), "没有发掘场也放了收藏家");
        helper.setBlock(new BlockPos(1, 1, 0), Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        helper.assertTrue(NpcSpawner.testDigSiteCollector(level, landmark, near), "收藏家的桌子摆着也不放收藏家");
        helper.succeed();
    }
}
