package io.github.sweetcornna.universe.gametest;

import java.util.UUID;

import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.hazard.GeyserKind;
import io.github.sweetcornna.universe.core.hazard.LocalExposure;
import io.github.sweetcornna.universe.core.hazard.SporeDrift;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.hazard.DustDevil;
import io.github.sweetcornna.universe.hazard.GeyserVentBlockEntity;
import io.github.sweetcornna.universe.hazard.Hazards;
import io.github.sweetcornna.universe.hazard.SporeCloud;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.teleport.BaseData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** 局部危害（ADR-0034 §六）的游戏内测试，由 {@link UniverseGameTests} 登记。测试世界是主世界，不按星球挑危害。 */
final class HazardGameTests {
    private HazardGameTests() {
    }

    /** 喷口马上喷：站在泉眼上的掉落物被水汽柱托上去好几格。 */
    static void geyserLiftsItems(GameTestHelper helper) {
        BlockPos vent = new BlockPos(8, 0, 8);
        helper.setBlock(vent, UniverseBlocks.GEYSER_VENTS.get(GeyserKind.HOT).get());
        ItemEntity item = helper.spawnItem(Items.COBBLESTONE, new Vec3(8.5, 1.05, 8.5));
        double start = item.getY();
        helper.getBlockEntity(vent, GeyserVentBlockEntity.class).erupt(true);
        helper.succeedWhen(() -> helper.assertTrue(item.getY() > start + 3, "掉落物没被托起来：只高了 " + (item.getY() - start)));
    }

    /**
     * 孢子云刚冒出来时还淡，不算数；浓起来以后云里的人记上一笔，毒性防护掉得快 4 倍；水汽柱里的人按喷口的危害加 20 的强度。
     * 两样同时有时各算各的。
     */
    static void localExposureMarks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = QuietPlayers.silence((ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL));
        Vec3 at = helper.absoluteVec(new Vec3(8.5, 1, 8.5));
        player.snapTo(at.x, at.y, at.z, 0, 0);
        SporeCloud cloud = SporeCloud.spawn(level, at, 0L, 0, Rng.of(7));
        helper.assertTrue(cloud != null, "孢子云没放出来");
        Vec3 eye = player.getEyePosition();
        helper.assertFalse(cloud.contains(eye), "刚冒出来的淡云就算数了");
        helper.assertTrue(Hazards.exposure(player).isEmpty(), "还没进云就记上了");
        helper.succeedWhen(() -> {
            helper.assertTrue(cloud.contains(eye), "云浓起来以后人不在云里：云在 " + cloud.position() + "，浓度 " + cloud.density());
            long now = level.getGameTime();
            Hazards.spores(player, now);
            Hazards.steam(player, Hazard.HEAT, now);
            LocalExposure exposure = Hazards.exposure(player);
            helper.assertTrue(exposure.apply(Hazard.TOXIC, 1) == SporeDrift.TOXIC_FACTOR, "毒性没有乘上去：" + exposure.apply(Hazard.TOXIC, 1));
            helper.assertTrue(exposure.apply(Hazard.HEAT, 1) == 1 + Hazards.STEAM_SEVERITY, "水汽柱没加高温：" + exposure.apply(Hazard.HEAT, 1));
            helper.assertTrue(exposure.apply(Hazard.COLD, 1) == 1, "别的危害也变了");
        });
    }

    /** 基地信标 32 格以内不起局部危害：飘进来的孢子云、尘卷风很快散掉；再远就不管。 */
    static void hazardsQuietNearBase(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        BaseData bases = BaseData.get(level.getServer());
        bases.register(UUID.randomUUID(), level.dimension(), base, level.getGameTime());
        try {
            helper.assertTrue(Hazards.exempt(level, base.offset(20, 0, 0)), "基地 20 格外也起危害");
            helper.assertFalse(Hazards.exempt(level, base.offset(40, 0, 0)), "基地 40 格外还当成基地");
        } catch (RuntimeException e) {
            bases.remove(level.dimension(), base);
            throw e;
        }
        SporeCloud cloud = SporeCloud.spawn(level, helper.absoluteVec(new Vec3(4.5, 1, 4.5)), 0L, 0, Rng.of(11));
        DustDevil devil = DustDevil.spawn(level, helper.absoluteVec(new Vec3(12.5, 1, 12.5)), 0, Rng.of(13));
        helper.runAfterDelay(45, () -> {
            try {
                helper.assertTrue(cloud != null && cloud.life() - cloud.age() <= 100, "基地旁边的孢子云没散");
                helper.assertTrue(devil != null && devil.life() - devil.age() <= DustDevil.FADE, "基地旁边的尘卷风没停");
            } finally {
                bases.remove(level.dimension(), base);
            }
            helper.succeed();
        });
    }
}
