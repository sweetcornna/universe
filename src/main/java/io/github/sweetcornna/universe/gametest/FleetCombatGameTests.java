package io.github.sweetcornna.universe.gametest;

import io.github.sweetcornna.universe.core.darkfog.BuildingKind;
import io.github.sweetcornna.universe.core.darkfog.HiveBuildingKind;
import io.github.sweetcornna.universe.core.darkfog.Support;
import io.github.sweetcornna.universe.core.darkfog.UnitKind;
import io.github.sweetcornna.universe.core.mecha.fleet.Squadrons;
import io.github.sweetcornna.universe.core.research.ResearchState;
import io.github.sweetcornna.universe.core.research.Upgrades;
import io.github.sweetcornna.universe.core.survival.Shield;
import io.github.sweetcornna.universe.darkfog.defense.ThrownUnit;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogBuilding;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogUnit;
import io.github.sweetcornna.universe.darkfog.hive.HiveBuilding;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.mecha.Mecha;
import io.github.sweetcornna.universe.mecha.combat.MechaCombat;
import io.github.sweetcornna.universe.mecha.combat.Strikes;
import io.github.sweetcornna.universe.mecha.fleet.FleetData;
import io.github.sweetcornna.universe.mecha.fleet.FleetUnit;
import io.github.sweetcornna.universe.mecha.fleet.MechaFleet;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.tech.SuitTech;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * 机甲的舰队和战斗（ADR-0033 §一、§二）的游戏内测试，由 {@link UniverseGameTests} 登记，有黑雾的都在机库场地（17×8×17）里跑。
 * 编队用 {@link MechaFleet#launch} 直接放出去，不查研究和维度；主人是假玩家（不是服务端玩家，单位用电不扣）。
 * 单位的交战距离收到 16 格，免得打到隔壁测试的黑雾。
 */
final class FleetCombatGameTests {
    private static final double TEST_RANGE = 16;

    private FleetCombatGameTests() {
    }

    /** 六架原型机的地面编队打掉一架突袭者；召回以后单位收掉，物品还在格子里。 */
    static void groundSquadronKillsRaider(GameTestHelper helper) {
        Player owner = owner(helper, new Vec3(8.5, 1, 4.5));
        DarkFogUnit raider = raider(helper, new Vec3(8.5, 1, 13.5));
        List<FleetUnit> units = launch(owner, 0, UniverseItems.PROTOTYPE.get());
        helper.assertTrue(units.size() == Squadrons.GROUND_SLOTS, "放出了 " + units.size() + " 架");
        helper.succeedWhen(() -> {
            helper.assertTrue(!raider.isAlive(), "突袭者还活着：" + raider.getHealth() + "，在打它的有 "
                    + units.stream().filter(unit -> unit.target() == raider).count() + " 架");
            MechaFleet.recallAll(owner, false);
            helper.assertTrue(MechaFleet.units(owner, 0).isEmpty() && units.stream().allMatch(FleetUnit::isRemoved), "召回以后单位还在");
            FleetData data = owner.getData(UniverseAttachments.MECHA_FLEET);
            helper.assertTrue(data.count(0) == Squadrons.GROUND_SLOTS, "召回以后格子里只剩 " + data.count(0) + " 架");
        });
    }

    /**
     * 太空舰队的三艘炮艇：没开"打建筑"时不理巢穴的光子接收器，开了就把它打掉。
     * 接收器悬在场地上方（太空里炮艇会绕到目标上下 8 格，放在地上会钻进地板）。
     */
    static void spaceFleetKillsHiveBuilding(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player owner = owner(helper, new Vec3(4.5, 1, 8.5));
        HiveBuilding building = HiveBuilding.create(level, HiveBuildingKind.PHOTON_RECEIVER, -1, 1, 0, helper.absoluteVec(new Vec3(12.5, 8, 8.5)));
        building.setPowered(false);
        building.setHealth(building.getMaxHealth() * 0.1F);
        level.addFreshEntity(building);
        float health = building.getHealth();
        int squadron = Squadrons.GROUND_MAX;
        List<FleetUnit> units = launch(owner, squadron, UniverseItems.CORVETTE.get());
        helper.assertTrue(units.size() == Squadrons.SPACE_SLOTS, "放出了 " + units.size() + " 艘");
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(building.isAlive() && building.getHealth() >= health, "没开打建筑也打了：" + building.getHealth());
            owner.getData(UniverseAttachments.MECHA_FLEET).setBuildings(squadron, true);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(owner.getData(UniverseAttachments.MECHA_FLEET).buildings(squadron), "还没开打建筑");
            helper.assertTrue(!building.isAlive(), "光子接收器还在：" + building.getHealth() + "，在打它的有 "
                    + units.stream().filter(unit -> unit.target() == building).count() + " 艘");
            MechaFleet.recallAll(owner, false);
        });
    }

    /** 能量护盾（升级加的那部分）先挡战斗伤害，摔落不挡；5 秒以后用机甲核心的电回满，每点 200 焦。 */
    static void shieldAbsorbsAndCoreRecharges(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResearchState research = Research.state(level);
        int before = research.level(Upgrades.ENERGY_SHIELD);
        research.setLevel(Upgrades.ENERGY_SHIELD, 1);
        ServerPlayer player = mecha(helper, new Vec3(8.5, 1, 8.5));
        player.getData(UniverseAttachments.MECHA).energy().fill(Mecha.capacity(level));
        MechaCombat.tickShield(player);
        Shield shield = player.getData(UniverseAttachments.EXOSUIT).shield();
        float capacity = shield.capacity();
        helper.assertTrue(capacity >= Upgrades.energyShield(1), "护盾的容量：" + capacity);
        shield.refill();
        float left = SuitTech.absorb(player, level.damageSources().explosion(null, null), 6);
        helper.assertTrue(left == 0 && Math.abs(shield.value() - (capacity - 6)) < 1e-3, "护盾没挡住爆炸：落下 " + left + "，剩 " + shield.value());
        float fall = SuitTech.absorb(player, level.damageSources().fall(), 3);
        helper.assertTrue(fall == 3 && Math.abs(shield.value() - (capacity - 6)) < 1e-3, "护盾挡了摔落：落下 " + fall);
        double energy = player.getData(UniverseAttachments.MECHA).energy().energy();
        helper.onEachTick(() -> MechaCombat.tickShield(player));
        helper.runAfterDelay(Shield.RECHARGE_DELAY - 20, () -> {
            helper.assertTrue(shield.value() < capacity - 5.9, "5 秒没到就回充了：" + shield.value());
            helper.assertTrue(player.getData(UniverseAttachments.MECHA).energy().energy() >= energy, "5 秒没到就用了电");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(shield.value() >= capacity - 1e-3, "护盾还没回满：" + shield.value() + " / " + capacity);
            double used = energy - player.getData(UniverseAttachments.MECHA).energy().energy();
            helper.assertTrue(Math.abs(used - 6 * Shield.CORE_JOULES_PER_POINT) < 50, "回充用了 " + used + " 焦");
            research.setLevel(Upgrades.ENERGY_SHIELD, before);
        });
    }

    /** 护盾爆发：用掉一半护盾，伤到旁边的突袭者；自己、旁边的猪不受伤，玩家不在打击的范围里；冷却时再按不放。 */
    static void shieldBurstHitsOnlyDarkFog(GameTestHelper helper) {
        ServerPlayer player = mecha(helper, new Vec3(8.5, 1, 8.5));
        Shield shield = player.getData(UniverseAttachments.EXOSUIT).shield();
        shield.tick(0, 10);
        shield.refill();
        DarkFogUnit raider = raider(helper, new Vec3(11.5, 1, 8.5));
        raider.setNoAi(true);
        Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(8.5, 1, 11.5));
        pig.setNoAi(true);
        Player bystander = helper.makeMockPlayer(GameType.SURVIVAL);
        float raiderHealth = raider.getHealth();
        float health = player.getHealth();
        helper.assertTrue(MechaCombat.burst(player, 40), "护盾爆发没放出来");
        helper.assertTrue(raider.getHealth() < raiderHealth, "突袭者没受伤：" + raider.getHealth());
        helper.assertTrue(pig.getHealth() >= pig.getMaxHealth(), "猪受伤了");
        helper.assertTrue(player.getHealth() >= health, "自己受伤了");
        helper.assertTrue(!Strikes.hostile(bystander, player), "别的玩家算成了敌人");
        helper.assertTrue(Math.abs(shield.value() - 5) < 1e-3, "护盾没用掉一半：剩 " + shield.value());
        helper.assertTrue(MechaCombat.burstCooldown(player) > 0 && !MechaCombat.burst(player, 40), "冷却时又放了一次");
        helper.succeed();
    }

    /** 扔出去的干扰胶囊在突袭者旁边炸开，突袭者变慢。 */
    static void jammingCapsuleSlowsRaider(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (BlockPos pos : BlockPos.betweenClosed(6, 0, 6, 10, 0, 10)) {
            helper.setBlock(pos, Blocks.SMOOTH_STONE);
        }
        DarkFogUnit raider = raider(helper, new Vec3(9.5, 1, 8.5));
        raider.setNoAi(true);
        helper.assertTrue(raider.jamFactor() >= 1, "突袭者一开始就被干扰了");
        ThrownUnit capsule = new ThrownUnit(UniverseEntities.THROWN_UNIT.get(), level);
        capsule.setItem(new ItemStack(UniverseItems.JAMMING_CAPSULE.get()));
        capsule.setOwner(helper.makeMockPlayer(GameType.SURVIVAL));
        Vec3 drop = helper.absoluteVec(new Vec3(7.5, 2.2, 8.5));
        capsule.setPos(drop.x, drop.y, drop.z);
        capsule.setDeltaMovement(0, -0.6, 0);
        level.addFreshEntity(capsule);
        helper.succeedWhen(() -> {
            helper.assertTrue(capsule.isRemoved(), "胶囊还没炸");
            helper.assertTrue(Math.abs(raider.jamFactor() - Support.CAPSULE.factor()) < 1e-3, "突袭者的速度倍数：" + raider.jamFactor());
        });
    }

    /** 黑雾的激光塔打机甲放出来的舰队单位（主人是创造模式，塔不打人，只能打单位）。 */
    static void laserTowerShootsFleetUnit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player owner = helper.makeMockPlayer(GameType.CREATIVE);
        Vec3 at = helper.absoluteVec(new Vec3(13.5, 1, 8.5));
        owner.snapTo(at.x, at.y, at.z, 90, 0);
        DarkFogBuilding tower = DarkFogBuilding.create(level, BuildingKind.LASER_TOWER, -1, 0, helper.absoluteVec(new Vec3(3.5, 1, 8.5)));
        level.addFreshEntity(tower);
        List<FleetUnit> units = launch(owner, 0, UniverseItems.PROTOTYPE.get());
        helper.succeedWhen(() -> {
            helper.assertTrue(units.stream().anyMatch(unit -> !unit.isAlive() || unit.getHealth() < unit.getMaxHealth()),
                    "激光塔没打舰队单位，瞄着的是 " + tower.aimedEntity());
            MechaFleet.recallAll(owner, false);
        });
    }

    // ---- 布置

    /** 编队的主人：站在 {@code pos} 朝南的假玩家。 */
    private static Player owner(GameTestHelper helper, Vec3 pos) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(pos);
        owner.snapTo(at.x, at.y, at.z, 0, 0);
        owner.yBodyRot = 0;
        return owner;
    }

    /** 穿着外骨骼胸甲的生存模式服务端假玩家。 */
    private static ServerPlayer mecha(GameTestHelper helper, Vec3 pos) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(pos);
        player.snapTo(at.x, at.y, at.z, 0, 0);
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(UniverseItems.EXOSUIT_CHESTPLATE.get()));
        return player;
    }

    /** 编队的格子装满 {@code item}，放出去，交战距离收窄。 */
    private static List<FleetUnit> launch(Player owner, int squadron, Item item) {
        FleetData data = owner.getData(UniverseAttachments.MECHA_FLEET);
        for (int slot = 0; slot < Squadrons.slots(squadron); slot++) {
            data.setSlot(squadron, slot, new ItemStack(item));
        }
        MechaFleet.launch(owner, squadron);
        List<FleetUnit> units = MechaFleet.units(owner, squadron);
        for (FleetUnit unit : units) {
            unit.restrictRange(TEST_RANGE);
            unit.thinkNow();
        }
        return units;
    }

    /** 一只 0 级、不属于任何基地的突袭者，守着自己出生的地方。 */
    private static DarkFogUnit raider(GameTestHelper helper, Vec3 pos) {
        Vec3 absolute = helper.absoluteVec(pos);
        return DarkFogUnit.spawn(helper.getLevel(), UnitKind.RAIDER, -1, 0, absolute, BlockPos.containing(absolute), DarkFogUnit.Mode.DEFEND, null);
    }
}
