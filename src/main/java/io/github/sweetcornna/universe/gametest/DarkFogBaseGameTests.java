package io.github.sweetcornna.universe.gametest;

import java.util.List;

import io.github.sweetcornna.universe.core.darkfog.BaseLayout;
import io.github.sweetcornna.universe.core.darkfog.BaseState;
import io.github.sweetcornna.universe.core.darkfog.BuildingKind;
import io.github.sweetcornna.universe.core.darkfog.Threat;
import io.github.sweetcornna.universe.core.darkfog.UnitKind;
import io.github.sweetcornna.universe.darkfog.DarkFogBases;
import io.github.sweetcornna.universe.darkfog.DarkFogData;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogBuilding;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogUnit;
import io.github.sweetcornna.universe.factory.power.GeothermalBlockEntity;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.rules.UniverseRules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 黑雾地面基地（ADR-0028 §二、§三）的游戏内测试，由 {@link UniverseGameTests} 登记。用的是"不自动放出来、不派进攻"的测试基地，
 * 建筑当场放好，不放守家的单位，免得飞到别的测试里去；测试完把基地收拾掉。
 */
final class DarkFogBaseGameTests {
    private DarkFogBaseGameTests() {
    }

    /** 打掉钻芯：基地瓦解（别的建筑一起没了），掉东西，原地留下地核；地热电站放在地核上能发电。 */
    static void coreDestroyedLeavesRemnant(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = helper.absolutePos(new BlockPos(2, 0, 2));
        BaseState base = DarkFogBases.createInert(level, ground);
        try {
            helper.assertTrue(base.alive(BaseLayout.RELAY) && base.alive(2), "刚扎下的基地没有中继站和第一座营地");
            DarkFogBuilding core = DarkFogBases.building(level, base.id(), BaseLayout.CORE).orElseThrow();
            DarkFogBuilding camp = DarkFogBases.building(level, base.id(), 2).orElseThrow();
            helper.assertTrue(core.kind() == BuildingKind.CORE && camp.kind() == BuildingKind.RAIDER_CAMP, "槽位上的建筑种类不对");
            core.hurtServer(level, level.damageSources().magic(), 1_000_000);
            helper.assertTrue(!core.isAlive(), "钻芯没被打掉");
            helper.assertTrue(base.status() == BaseState.Status.DESTROYED, "基地没瓦解：" + base.status());
            helper.assertTrue(camp.isRemoved(), "营地没跟着瓦解");
            helper.assertTrue(level.getBlockState(ground).is(UniverseBlocks.DARK_FOG_CORE_REMNANT.get()), "原地没留下地核");
            helper.assertTrue(GeothermalBlockEntity.heat(level, ground.above()) == GeothermalBlockEntity.CORE_REMNANT_HEAT, "地核上没有地热");
            List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(ground).inflate(4));
            helper.assertTrue(drops.stream().anyMatch(item -> item.getItem().is(Items.STONE)), "钻芯没掉沙土");
            helper.assertTrue(drops.stream().allMatch(item -> item.lifespan <= 2400), "掉落物不会按时消失");
            drops.forEach(ItemEntity::discard);
        } finally {
            DarkFogBases.remove(level, base.id());
        }
        helper.succeed();
    }

    /** 基地的状态存档、读档后不变；挨打攒经验升级，按时间结算盖出更多建筑。 */
    static void baseStateSurvivesSave(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BaseState base = DarkFogBases.createInert(level, helper.absolutePos(new BlockPos(2, 0, 2)));
        try {
            base.setThreat(0.4);
            base.setUnits(UnitKind.RAIDER, 7);
            Tag saved = DarkFogData.CODEC.encodeStart(NbtOps.INSTANCE, DarkFogData.get(level.getServer())).getOrThrow();
            DarkFogData loaded = DarkFogData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
            BaseState copy = loaded.planet(level.dimension().identifier().toString()).flatMap(planet -> planet.base(base.id())).orElseThrow();
            helper.assertTrue(copy.status() == BaseState.Status.ACTIVE && copy.x() == base.x() && copy.y() == base.y() && copy.z() == base.z(),
                    "位置或状态没存对");
            helper.assertTrue(Math.abs(copy.threat() - 0.4) < 1e-9 && copy.units(UnitKind.RAIDER) == 7, "威胁或单位数没存对");
            for (int slot = 0; slot < BaseLayout.size(); slot++) {
                helper.assertTrue(copy.alive(slot) == base.alive(slot), "槽位 " + slot + " 没存对");
            }
            // 挨了够升两级的打，再长两小时：2 级的游骑兵营地盖出来了
            base.hurt(400 + 600 + 1, 0);
            helper.assertTrue(base.level() == 2, "没升到 2 级：" + base.level());
            long now = level.getServer().overworld().getGameTime();
            base.backdate(2 * 60 * 1200);
            base.advanceTo(now, BaseState.Environment.IDLE);
            helper.assertTrue(base.alive(4), "2 级了还没盖游骑兵营地");
        } finally {
            DarkFogBases.remove(level, base.id());
        }
        helper.succeed();
    }

    /** 派一波：从营地里按攻击性取一部分，在目标朝基地方向 80 格以内出现，冲着目标去。 */
    static void fullThreatSendsWave(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(new BlockPos(2, 1, 2));
        BaseState base = DarkFogBases.createInert(level, target.offset(100, -1, 0));
        try {
            base.setUnits(UnitKind.RAIDER, 8);
            base.setUnits(UnitKind.RANGER, 4);
            Threat.Wave expected = Threat.wave(8, 4, base.level(), UniverseRules.darkFog(level.getServer()).aggressiveness());
            List<DarkFogUnit> units = DarkFogBases.attackNow(level, base, target);
            helper.assertTrue(units.size() == expected.total(), "这一波的人数不对：" + units.size() + "，应该是 " + expected.total());
            helper.assertTrue(base.units(UnitKind.RAIDER) == 8 - expected.raiders() && base.units(UnitKind.RANGER) == 4 - expected.rangers(),
                    "出发的没从营地里扣掉");
            Vec3 toBase = new Vec3(1, 0, 0);
            for (DarkFogUnit unit : units) {
                helper.assertTrue(unit.mode() == DarkFogUnit.Mode.ATTACK && target.equals(unit.attackTarget()), "进攻部队没冲着目标");
                Vec3 offset = unit.position().subtract(Vec3.atCenterOf(target)).multiply(1, 0, 1);
                helper.assertTrue(offset.length() <= Threat.SPAWN_DISTANCE + 8 && offset.length() >= 8, "出现的地方离目标不对：" + offset.length());
                helper.assertTrue(offset.normalize().dot(toBase) > 0.8, "没从基地那边来");
            }
            units.forEach(DarkFogUnit::discard);
        } finally {
            DarkFogBases.remove(level, base.id());
        }
        helper.succeed();
    }
}
