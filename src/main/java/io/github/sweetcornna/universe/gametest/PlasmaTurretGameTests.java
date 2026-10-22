package io.github.sweetcornna.universe.gametest;

import java.util.List;

import io.github.sweetcornna.universe.core.darkfog.HiveLayout;
import io.github.sweetcornna.universe.core.darkfog.HiveState;
import io.github.sweetcornna.universe.core.darkfog.SpaceUnitKind;
import io.github.sweetcornna.universe.core.darkfog.UnitKind;
import io.github.sweetcornna.universe.core.fleet.Fleet;
import io.github.sweetcornna.universe.core.fleet.Frigate;
import io.github.sweetcornna.universe.core.fleet.FrigateType;
import io.github.sweetcornna.universe.core.fleet.HiveSortie;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogUnit;
import io.github.sweetcornna.universe.darkfog.hive.DarkFogHives;
import io.github.sweetcornna.universe.darkfog.turret.TurretBlockEntity;
import io.github.sweetcornna.universe.darkfog.turret.Turrets;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.factory.power.FuelGeneratorBlockEntity;
import io.github.sweetcornna.universe.fleet.FleetTerminal;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * 等离子炮塔、近程等离子炮塔、打高空的规则和护卫舰清剿（ADR-0028 §八）的游戏内测试，由 {@link UniverseGameTests} 登记。
 * 炮塔的测试场地是 17×8×17 的机库，摆法和 {@link DarkFogTurretGameTests} 一样。
 *
 * <p>高空的目标用一个打了 {@link Turrets#TEST_HIGH_ALTITUDE_TAG} 标签的游骑兵代替（枪骑轰炸的实体由别处做）。
 */
final class PlasmaTurretGameTests {
    /** 炮塔放在场地中间，感应塔、人造恒星在它西边。 */
    private static final BlockPos TURRET = new BlockPos(8, 1, 8);

    private PlasmaTurretGameTests() {
    }

    /** 通电、装了等离子胶囊的等离子炮塔打死一个突袭者，用掉胶囊，记一次击毁。 */
    static void plasmaKillsRaider(GameTestHelper helper) {
        power(helper);
        TurretBlockEntity turret = turret(helper, UniverseBlocks.PLASMA_TURRET.get());
        turret.setItem(TurretBlockEntity.AMMO_SLOT, new ItemStack(UniverseItems.PLASMA_CAPSULE.get(), 4));
        DarkFogUnit raider = spawn(helper, UnitKind.RAIDER, new BlockPos(8, 1, 14));
        helper.succeedWhen(() -> {
            helper.assertTrue(!raider.isAlive(), "突袭者还活着：" + raider.getHealth());
            helper.assertTrue(turret.kills() >= 1, "没记击毁");
            helper.assertTrue(turret.getItem(TurretBlockEntity.AMMO_SLOT).getCount() < 4, "没用掉胶囊");
        });
    }

    /** 近程等离子炮塔不要弹药：通电就打。 */
    static void shortRangeNeedsNoAmmo(GameTestHelper helper) {
        power(helper);
        TurretBlockEntity turret = turret(helper, UniverseBlocks.SR_PLASMA_TURRET.get());
        helper.assertTrue(turret.getContainerSize() == 0, "近程等离子炮塔有弹药格");
        DarkFogUnit raider = spawn(helper, UnitKind.RAIDER, new BlockPos(8, 1, 13));
        float full = raider.getMaxHealth();
        helper.succeedWhen(() -> helper.assertTrue(!raider.isAlive() || raider.getHealth() < full, "近程等离子炮塔没打"));
    }

    /** 高斯炮塔够不着高空：头顶上的目标不瞄、不打。 */
    static void gaussIgnoresHighAltitude(GameTestHelper helper) {
        power(helper);
        TurretBlockEntity turret = turret(helper, UniverseBlocks.GAUSS_TURRET.get());
        turret.setItem(TurretBlockEntity.AMMO_SLOT, new ItemStack(UniverseItems.MAGNUM_AMMO_BOX.get(), 3));
        DarkFogUnit high = highAltitude(helper);
        float full = high.getMaxHealth();
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(high.getHealth() >= full, "高斯炮塔打了高空的：" + high.getHealth());
            helper.assertTrue(turret.target() == null, "高斯炮塔瞄着高空的");
            helper.succeed();
        });
    }

    /** 导弹炮塔打高空。 */
    static void missileAimsAtHighAltitude(GameTestHelper helper) {
        power(helper);
        TurretBlockEntity turret = turret(helper, UniverseBlocks.MISSILE_TURRET.get());
        turret.setItem(TurretBlockEntity.AMMO_SLOT, new ItemStack(UniverseItems.MISSILE_SET.get(), 3));
        DarkFogUnit high = highAltitude(helper);
        helper.succeedWhen(() -> helper.assertTrue(turret.target() == high, "导弹炮塔没瞄高空的"));
    }

    /** 等离子炮塔打高空，打得着。 */
    static void plasmaHitsHighAltitude(GameTestHelper helper) {
        power(helper);
        TurretBlockEntity turret = turret(helper, UniverseBlocks.PLASMA_TURRET.get());
        turret.setItem(TurretBlockEntity.AMMO_SLOT, new ItemStack(UniverseItems.PLASMA_CAPSULE.get(), 4));
        DarkFogUnit high = highAltitude(helper);
        float full = high.getMaxHealth();
        helper.succeedWhen(() -> helper.assertTrue(!high.isAlive() || high.getHealth() < full, "等离子炮塔没打高空的"));
    }

    /**
     * 护卫舰清剿：三艘战斗舰打一座 10 级的巢穴，段段都赢；领的时候巢穴少了单位、核心还在，带回信用点和黑雾的掉落。
     * 用的是一座不自动结算的测试巢穴，放在最后一个恒星系，免得和巢穴自己的测试（母星系）抢。
     */
    static void sortieStrikesHive(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<StarSystem> systems = Planets.galaxy(server).systems();
        int system = systems.get(systems.size() - 1).index();
        HiveState hive = DarkFogHives.createInert(server, system, 10);
        try {
            hive.setUnits(SpaceUnitKind.LANCER, 6);
            Fleet fleet = new Fleet();
            fleet.setFreighter(new Fleet.Freighter(0, 1, Grade.C));
            for (int i = 0; i < 3; i++) {
                fleet.hire(new Frigate(i, "TS-10" + i, FrigateType.COMBAT, new int[] {30, 0, 0, 0}, List.of(), 10, 0, false));
            }
            helper.assertTrue(fleet.canSortie(List.of(0, 1, 2)) == null, "派不出去：" + fleet.canSortie(List.of(0, 1, 2)));
            fleet.sortie(system, HiveSortie.offer(hive.level()), List.of(0, 1, 2), 0, Rng.of(3));
            helper.assertTrue(fleet.taken().isEmpty(), "清剿占了远征任务");
            fleet.hurry(1);
            Fleet.Voyage back = fleet.voyages().get(0);
            helper.assertTrue(back.done(1) && back.hive() == system, "没回来或者目标不对：" + back);
            int won = HiveSortie.successes(back.events());
            helper.assertTrue(won == back.events().size(), "强队没全赢：" + won + "/" + back.events().size());
            Fleet.Claim claim = fleet.claim(back);
            FleetTerminal.Strike strike = FleetTerminal.strike(server, back, claim.rewards());
            helper.assertTrue(strike.hive().found() && strike.hive().unitsKilled() > 0 && strike.hive().level() == 10, "巢穴没挨打：" + strike.hive());
            helper.assertTrue(hive.alive(HiveLayout.CORE), "清剿打掉了核心");
            FleetTerminal.Haul haul = FleetTerminal.haul(back, strike.rewards());
            helper.assertTrue(haul.credits() > 0, "没带回信用点");
            helper.assertTrue(haul.items().stream().anyMatch(stack -> stack.is(Items.STONE)), "没带回黑雾的掉落：" + haul.items());
        } finally {
            DarkFogHives.removeInert(server, system);
        }
        helper.succeed();
    }

    /** 一个打了高空标签的游骑兵，在炮塔南边头顶上。 */
    private static DarkFogUnit highAltitude(GameTestHelper helper) {
        DarkFogUnit ranger = spawn(helper, UnitKind.RANGER, new BlockPos(8, 4, 13));
        ranger.addTag(Turrets.TEST_HIGH_ALTITUDE_TAG);
        return ranger;
    }

    /** 炮塔西边一座电力感应塔，再往西一座烧反物质燃料棒的人造恒星。 */
    private static void power(GameTestHelper helper) {
        tall(helper, new BlockPos(5, 1, 8), UniverseBlocks.TESLA_TOWER.get().defaultBlockState());
        BlockPos star = new BlockPos(2, 1, 8);
        tall(helper, star, UniverseBlocks.ARTIFICIAL_STAR.get().defaultBlockState());
        helper.getBlockEntity(star, FuelGeneratorBlockEntity.class)
                .setItem(FuelGeneratorBlockEntity.FUEL_SLOT, new ItemStack(UniverseItems.ANTIMATTER_FUEL_ROD.get(), 4));
    }

    private static TurretBlockEntity turret(GameTestHelper helper, Block block) {
        helper.setBlock(TURRET, block);
        return helper.getBlockEntity(TURRET, TurretBlockEntity.class);
    }

    private static DarkFogUnit spawn(GameTestHelper helper, UnitKind kind, BlockPos at) {
        BlockPos pos = helper.absolutePos(at);
        return DarkFogUnit.spawn(helper.getLevel(), kind, -1, 0, Vec3.atBottomCenterOf(pos), pos, DarkFogUnit.Mode.DEFEND, null);
    }

    private static void tall(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.setBlock(pos, state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(pos.above(), state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.UPPER));
    }
}
