package io.github.sweetcornna.universe.gametest;

import java.util.List;

import io.github.sweetcornna.universe.block.ship.HelmBlockEntity;
import io.github.sweetcornna.universe.core.darkfog.HiveSites;
import io.github.sweetcornna.universe.core.deepspace.DebrisField;
import io.github.sweetcornna.universe.core.deepspace.DeepSpaceSites;
import io.github.sweetcornna.universe.core.deepspace.Integrity;
import io.github.sweetcornna.universe.core.deepspace.Layout;
import io.github.sweetcornna.universe.core.deepspace.Part;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.gravity.GravityRules;
import io.github.sweetcornna.universe.core.space.SpaceLayout;
import io.github.sweetcornna.universe.core.space.StationLayout;
import io.github.sweetcornna.universe.core.survival.ToolMode;
import io.github.sweetcornna.universe.core.survival.ToolWeapon;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.darkfog.hive.DarkFogHives;
import io.github.sweetcornna.universe.deepspace.DebrisChunk;
import io.github.sweetcornna.universe.deepspace.DeepSpace;
import io.github.sweetcornna.universe.deepspace.DerelictLightBlock;
import io.github.sweetcornna.universe.deepspace.Hulks;
import io.github.sweetcornna.universe.deepspace.Placement;
import io.github.sweetcornna.universe.deepspace.ShipEva;
import io.github.sweetcornna.universe.deepspace.ShipTractor;
import io.github.sweetcornna.universe.entity.ship.ShipAssembly;
import io.github.sweetcornna.universe.entity.ship.ShipCargoContainer;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.entity.ship.ShipTransit;
import io.github.sweetcornna.universe.gravity.GravityCoils;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 深空和引力抓取器（ADR-0034 §四、§七）的游戏内测试：残骸拆到熔毁、牵引光束收进货舱、抛货、出舱再回船、
 * 引力抓取器抓起来扔出去、推开。由 {@link UniverseGameTests} 登记。
 */
final class DeepSpaceGameTests {
    private DeepSpaceGameTests() {
    }

    // ---- 残骸

    /**
     * 一小截残骸：拆导管扣 8、拆屏蔽层扣 5；拆下反应堆芯掉出打捞的反应堆芯，灯变成警报灯，20 秒后熔毁：
     * 剩下的残骸方块炸掉，旁边的猪受了重伤。
     */
    static void hulkSalvageToMeltdown(GameTestHelper helper) {
        Layout.Builder b = new Layout.Builder();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 3; z++) {
                b.set(x, 0, z, Part.PLATING);
                b.set(x, 1, z, Part.PLATING);
            }
        }
        b.set(1, 1, 1, Part.CONDUIT);
        b.set(3, 0, 1, Part.SHIELDING);
        b.set(3, 1, 1, Part.REACTOR);
        b.set(0, 1, 1, Part.LIGHT);
        Layout layout = b.build();
        Placement placement = new Placement(helper.absolutePos(new BlockPos(1, 1, 2)), 0);
        Hulks.Wreck wreck = Hulks.place(helper.getLevel(), placement, layout, 1L, 0, null);
        Pig pig = helper.spawn(EntityTypes.PIG, new BlockPos(1, 1, 6));
        pig.setNoAi(true);
        float health = pig.getHealth();

        helper.destroyBlock(new BlockPos(2, 2, 3));
        helper.assertValueEqual(wreck.integrity().value(), 92, "拆导管以后的完整度");
        helper.destroyBlock(new BlockPos(4, 1, 3));
        helper.assertValueEqual(wreck.integrity().value(), 87, "拆屏蔽层以后的完整度");
        BlockPos core = helper.absolutePos(new BlockPos(4, 2, 3));
        helper.assertTrue(helper.getLevel().getBlockState(core).is(UniverseBlocks.REACTOR_CORE.get()), "反应堆芯不在");
        helper.getLevel().destroyBlock(core, true);
        helper.assertTrue(wreck.integrity().salvaged(), "拆下堆芯没记成拆完");
        helper.assertTrue(wreck.integrity().phase() == Integrity.Phase.MELTDOWN, "拆下堆芯没开始熔毁");
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(core).inflate(2),
                item -> item.getItem().is(UniverseItems.SALVAGED_REACTOR_CORE.get())).isEmpty(), "没掉出打捞的反应堆芯");
        BlockState light = helper.getBlockState(new BlockPos(1, 2, 3));
        helper.assertTrue(light.is(UniverseBlocks.DERELICT_LIGHT.get()) && light.getValue(DerelictLightBlock.ALARM), "灯没变成警报灯");

        helper.succeedWhen(() -> {
            helper.assertTrue(wreck.integrity().gone(), "还没熔毁");
            helper.assertBlockNotPresent(UniverseBlocks.DERELICT_PLATING.get(), new BlockPos(1, 1, 2));
            helper.assertBlockNotPresent(UniverseBlocks.DERELICT_PLATING.get(), new BlockPos(5, 2, 4));
            helper.assertTrue(!pig.isAlive() || pig.getHealth() < health - 5, "熔毁没伤到 24 格内的猪");
        });
    }

    // ---- 牵引光束、抛货

    /** 在场地中间组装一艘装了牵引光束模块、带一个箱子的小船。 */
    private static ShipEntity tractorShip(GameTestHelper helper) {
        BlockPos helm = new BlockPos(8, 1, 8);
        helper.setBlock(helm, UniverseBlocks.SHIP_HELM.get());
        helper.setBlock(new BlockPos(7, 1, 8), UniverseBlocks.SHIP_THRUSTER.get());
        helper.setBlock(new BlockPos(9, 1, 8), UniverseBlocks.FUEL_TANK.get());
        helper.setBlock(new BlockPos(8, 1, 9), Blocks.CHEST);
        HelmBlockEntity entity = helper.getBlockEntity(helm, HelmBlockEntity.class);
        entity.items().set(0, new ItemStack(UniverseItems.MODULES.get(ModuleType.TRACTOR_BEAM).get()));
        entity.setChanged();
        ShipEntity ship = ShipAssembly.assemble(helper.getLevel(), helper.absolutePos(helm), null, message -> {
        });
        helper.assertTrue(ship != null && ship.structure() != null, "没有组装出飞船");
        return ship;
    }

    /** 牵引光束开着：几格外的掉落物和一小块碎片被吸过来，收进货舱。 */
    static void tractorStowsIntoCargo(GameTestHelper helper) {
        ShipEntity ship = tractorShip(helper);
        helper.assertTrue(ShipTractor.range(ship) >= 40, "牵引光束模块没装上");
        ship.setTractor(true);
        ItemEntity iron = helper.spawnItem(Items.IRON_INGOT, new BlockPos(8, 2, 14));
        iron.setItem(new ItemStack(Items.IRON_INGOT, 5));
        DebrisChunk chunk = DebrisChunk.create(helper.getLevel(), 42L, DebrisField.Size.SMALL,
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(14, 3, 8))), null, -1);
        helper.getLevel().addFreshEntity(chunk);
        helper.succeedWhen(() -> {
            Container cargo = ShipCargoContainer.of(ship, helper.getLevel().registryAccess());
            helper.assertTrue(cargo.countItem(Items.IRON_INGOT) == 5, "铁锭没收进货舱");
            helper.assertTrue(chunk.isRemoved(), "碎片没被吸进来");
            helper.assertTrue(cargo.countItem(UniverseItems.SCRAP_METAL.get()) > 0, "碎片的废金属没收进货舱");
        });
    }

    /** 全部抛出：货舱空了，东西从船尾出去；开着牵引光束也不会马上吸回来。 */
    static void jettisonEmptiesCargo(GameTestHelper helper) {
        ShipEntity ship = tractorShip(helper);
        ShipCargoContainer cargo = ShipCargoContainer.of(ship, helper.getLevel().registryAccess());
        cargo.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
        cargo.setItem(1, new ItemStack(Items.COPPER_INGOT, 6));
        cargo.setItem(5, new ItemStack(UniverseItems.SCRAP_METAL.get(), 3));
        cargo.setChanged();
        ship.setTractor(true);
        int thrown = ShipCargoContainer.of(ship, helper.getLevel().registryAccess()).jettison(-1);
        helper.assertValueEqual(thrown, 3, "抛出的格数");
        helper.assertTrue(ShipCargoContainer.of(ship, helper.getLevel().registryAccess()).isEmpty(), "抛完货舱不空");
        List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, ship.getBoundingBox().inflate(6));
        helper.assertValueEqual(items.size(), 3, "船边的掉落物");
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(ShipCargoContainer.of(ship, helper.getLevel().registryAccess()).isEmpty(), "抛出去的东西又被牵引光束吸回来了");
            helper.succeed();
        });
    }

    // ---- 出舱

    /**
     * 太空里出舱：人放到船边、船停住不漂；主人在外面时别人上不了船；主人对着船右键回到座位上。
     * 在恒星系 0 的太空里离所有天体、空间站、兴趣点都远的地方做，测完把船、假玩家、强制加载的区块都收掉。
     */
    static void evaAndReboard(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel space = SpaceDimensions.getOrCreate(server, 0);
        BlockPos base = BlockPos.containing(openSpace(server, 0));
        int cx = base.getX() >> 4;
        int cz = base.getZ() >> 4;
        space.setChunkForced(cx, cz, true);
        space.setBlockAndUpdate(base, UniverseBlocks.SHIP_HELM.get().defaultBlockState());
        space.setBlockAndUpdate(base.west(), UniverseBlocks.SHIP_THRUSTER.get().defaultBlockState());
        space.setBlockAndUpdate(base.east(), UniverseBlocks.FUEL_TANK.get().defaultBlockState());
        ShipEntity ship = ShipAssembly.assemble(space, base, null, message -> {
        });
        helper.assertTrue(ship != null, "太空里没有组装出飞船");
        ServerPlayer owner = QuietPlayers.inLevel(space, base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
        ship.setOwner(owner.getUUID());
        helper.assertTrue(owner.startRiding(ship), "上不了船");
        Runnable cleanup = () -> {
            owner.stopRiding();
            ship.discard();
            space.removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
            space.setChunkForced(cx, cz, false);
        };
        helper.runAfterDelay(5, () -> {
            try {
                helper.assertTrue(ShipEva.exit(ship, owner), "出不了舱");
                helper.assertTrue(!owner.isPassenger(), "出舱以后还坐在船上");
                helper.assertTrue(!ship.overlapsHull(owner.getBoundingBox()), "出舱的人卡在船身里");
                helper.assertTrue(owner.distanceTo(ship) < 6, "出舱的人离船太远");
                ship.flight().restore(0.4, 0, 0.2, ship.getYRot());
            } catch (RuntimeException e) {
                cleanup.run();
                throw e;
            }
        });
        helper.runAfterDelay(10, () -> {
            try {
                helper.assertTrue(ship.getDeltaMovement().lengthSqr() < 1e-6, "没人开的船在太空里漂走了");
                Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
                stranger.snapTo(owner.getX(), owner.getY(), owner.getZ());
                helper.assertTrue(!ShipEva.mayBoard(ship, stranger), "主人出舱时别人也能上船");
                ship.interact(owner, InteractionHand.MAIN_HAND, owner.position());
                helper.assertTrue(owner.getVehicle() == ship, "主人对着船右键没回到座位上");
            } finally {
                cleanup.run();
            }
            helper.succeed();
        });
    }

    /** 太空里一块空地方：离天体的大气、恒星、空间站、深空兴趣点、黑雾巢穴都远。 */
    private static Vec3 openSpace(MinecraftServer server, int system) {
        StarSystem star = Planets.galaxy(server).system(system).orElseThrow();
        List<SpaceLayout.Body> bodies = ShipTransit.layout(server, system);
        double starRadius = SpaceLayout.starRadius(star);
        double[] station = StationLayout.site(star).map(StationLayout.Site::center).orElse(null);
        HiveSites.Site hive = DarkFogHives.site(server, star);
        List<DeepSpaceSites.Site> sites = DeepSpace.sites(server, system);
        for (int i = 0; i < 128; i++) {
            double angle = i * 0.77;
            double distance = starRadius + 3000 + i * 700;
            double x = Math.cos(angle) * distance;
            double z = Math.sin(angle) * distance;
            double y = 140;
            boolean clear = hive.distanceTo(x, y, z) > 1500;
            for (SpaceLayout.Body body : bodies) {
                clear &= body.distanceTo(x, y, z) > body.radius() + 500;
            }
            if (station != null) {
                double dx = x - station[0];
                double dy = y - station[1];
                double dz = z - station[2];
                clear &= Math.sqrt(dx * dx + dy * dy + dz * dz) > 1500;
            }
            for (DeepSpaceSites.Site site : sites) {
                clear &= site.distanceTo(x, y, z) > 1200;
            }
            if (clear) {
                return new Vec3(x, y, z);
            }
        }
        throw new IllegalStateException("恒星系 " + system + " 找不到空地方");
    }

    // ---- 引力抓取器

    /** 面朝 +x、拿着装了引力抓取器、切到引力模式的多功能工具的假玩家。 */
    private static ServerPlayer gravityUser(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(0, 1, 3)));
        player.snapTo(at.x, at.y, at.z, -90, 0);
        ItemStack tool = new ItemStack(UniverseItems.MULTITOOL.get());
        tool.set(UniverseDataComponents.TOOL_MODULES.get(),
                ItemContainerContents.fromItems(List.of(new ItemStack(UniverseItems.WEAPONS.get(ToolWeapon.GRAVITY_COIL).get()))));
        tool.set(UniverseDataComponents.TOOL_MODE.get(), ToolMode.GRAVITY);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        helper.assertTrue(GravityCoils.active(player), "工具没切到引力抓取器");
        return player;
    }

    /** 按住右键抓起面前的铁锭，悬在眼前 3 格；朝下瞄一点、左键扔出去，砸到后面的猪。 */
    static void gravityGrabAndThrow(GameTestHelper helper) {
        ServerPlayer player = gravityUser(helper);
        // 和眼睛一样高，平视就瞄得到
        ItemEntity iron = helper.spawnItem(Items.IRON_INGOT, new Vec3(3.5, 1 + player.getEyeHeight() - 0.125, 3.5));
        iron.setNoGravity(true);
        iron.setDeltaMovement(Vec3.ZERO);
        Pig pig = helper.spawn(EntityTypes.PIG, new BlockPos(6, 1, 3));
        pig.setNoAi(true);
        float health = pig.getHealth();
        boolean[] holding = {true};
        helper.onEachTick(() -> GravityCoils.hold(player, holding[0]));
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(GravityCoils.holding(player) == iron, "没抓起铁锭");
            Vec3 point = player.getEyePosition().add(player.getLookAngle().scale(GravityRules.HOLD_DISTANCE));
            helper.assertTrue(iron.getBoundingBox().getCenter().distanceTo(point) < 1.0, "抓着的铁锭没悬在眼前");
            // 猪比眼睛低，往下瞄 11°
            player.setXRot(11);
            helper.assertTrue(GravityCoils.throwHeld(player), "扔不出去");
            helper.assertTrue(GravityCoils.holding(player) == null, "扔完还抓着");
        });
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(pig.getHealth() < health, "扔出去的铁锭没砸到猪");
            holding[0] = false;
            helper.succeed();
        });
    }

    /** 潜行左键：前方锥形里的东西被推开，身后的不动。 */
    static void gravityPushCone(GameTestHelper helper) {
        ServerPlayer player = gravityUser(helper);
        ItemEntity ahead = helper.spawnItem(Items.IRON_INGOT, new Vec3(3.5, 2.4, 3.5));
        ItemEntity side = helper.spawnItem(Items.IRON_INGOT, new Vec3(1.5, 2.4, 0.5));
        for (ItemEntity item : List.of(ahead, side)) {
            item.setNoGravity(true);
            item.setDeltaMovement(Vec3.ZERO);
        }
        // 隔壁测试的东西也可能在锥形里，只要求至少推开一个
        helper.assertTrue(GravityCoils.push(player) >= 1, "一个都没推开");
        helper.assertTrue(ahead.getDeltaMovement().x > 0.3, "前面的铁锭没被往前推");
        helper.assertTrue(side.getDeltaMovement().lengthSqr() < 1e-6, "旁边的铁锭也被推了");
        helper.succeed();
    }
}
