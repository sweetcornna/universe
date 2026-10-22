package io.github.sweetcornna.universe.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.darkfog.ShieldMath;
import io.github.sweetcornna.universe.darkfog.shield.PlanetaryShieldBlockEntity;
import io.github.sweetcornna.universe.darkfog.shield.ShieldData;
import io.github.sweetcornna.universe.darkfog.shield.Shields;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.factory.power.FuelGeneratorBlockEntity;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * 行星护盾（ADR-0028 §八）的游戏内测试，由 {@link UniverseGameTests} 登记。穹顶只充到刚展开（半径 32 格），
 * 测完把能量清零，免得罩住隔壁测试里枪骑的炸弹。
 */
final class ShieldGameTests {
    /** 数据层的测试用的维度：没有这个世界，只在存档里有记录。 */
    private static final ResourceKey<Level> FAKE = ResourceKey.create(Registries.DIMENSION, Universe.id("gametest_shields"));

    private ShieldGameTests() {
    }

    /**
     * 人造恒星供电，发生器从展开线下面一点充上去，展开穹顶：盖住头顶 20 格的点，40 格外的不盖；
     * 中继站那种只看水平距离的查法也认得。
     */
    static void chargesAndRaises(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        tall(helper, new BlockPos(1, 1, 1), UniverseBlocks.TESLA_TOWER.get().defaultBlockState());
        BlockPos star = new BlockPos(5, 1, 1);
        tall(helper, star, UniverseBlocks.ARTIFICIAL_STAR.get().defaultBlockState());
        helper.getBlockEntity(star, FuelGeneratorBlockEntity.class)
                .setItem(FuelGeneratorBlockEntity.FUEL_SLOT, new ItemStack(UniverseItems.ANTIMATTER_FUEL_ROD.get(), 4));
        BlockPos pos = new BlockPos(3, 1, 4);
        tall(helper, pos, UniverseBlocks.PLANETARY_SHIELD_GENERATOR.get().defaultBlockState());
        PlanetaryShieldBlockEntity generator = helper.getBlockEntity(pos, PlanetaryShieldBlockEntity.class);
        Vec3 center = Vec3.atLowerCornerOf(helper.absolutePos(pos)).add(0.5, 1, 0.5);
        helper.startSequence().thenExecuteAfter(2, () -> {
            helper.assertFalse(generator.raised(), "刚放下就展开了");
            generator.setEnergy(ShieldMath.RAISE_ENERGY - 5 * ShieldMath.CHARGE_POWER, false);
        }).thenWaitUntil(() -> {
            helper.assertTrue(generator.raised(), "充到展开线还没展开：能量 " + generator.energy() + "，满足率 " + generator.data().get(0));
            helper.assertTrue(Shields.covers(level, center.add(0, 20, 0)), "头顶 20 格不在穹顶里");
            helper.assertFalse(Shields.covers(level, center.add(0, 40, 0)), "刚展开的穹顶罩到了 40 格外");
            helper.assertTrue(Shields.covers(level.getServer(), level.dimension(), center.x + 25, center.z), "水平 25 格不算盖着");
            helper.assertTrue(generator.data().get(PlanetaryShieldBlockEntity.STATUS) == PlanetaryShieldBlockEntity.RAISED, "界面的状态不是展开");
        }).thenExecute(() -> generator.setEnergy(0, false)).thenSucceed();
    }

    /**
     * 展开着的穹顶挡伤害：挡得住的全挡、扣能量；挡不住的扣光能量、穹顶收起，返回没挡住的。
     * 从穹顶外面落下来的东西在外壳上被截住。
     */
    static void absorbsAndCollapses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(3, 1, 3);
        tall(helper, pos, UniverseBlocks.PLANETARY_SHIELD_GENERATOR.get().defaultBlockState());
        PlanetaryShieldBlockEntity generator = helper.getBlockEntity(pos, PlanetaryShieldBlockEntity.class);
        Vec3 center = Vec3.atLowerCornerOf(helper.absolutePos(pos)).add(0.5, 1, 0.5);
        helper.startSequence().thenExecuteAfter(2, () -> {
            double energy = ShieldMath.RAISE_ENERGY + 10_000;
            generator.setEnergy(energy, true);
            Vec3 hit = center.add(3, 15, -2);
            helper.assertTrue(Shields.absorb(level, hit, 5) == 0, "5 点伤害没全挡住");
            helper.assertTrue(Math.abs(generator.energy() - (energy - 5 * ShieldMath.JOULES_PER_DAMAGE)) < 1e-6, "能量没按伤害扣：" + generator.energy());
            Optional<Vec3> entry = Shields.intercept(level, center.add(0, 80, 0), center);
            helper.assertTrue(entry.isPresent(), "从上面落下来没碰到穹顶");
            double height = ShieldMath.height(ShieldMath.radius(generator.energy()));
            helper.assertTrue(Math.abs(entry.get().y - center.y - height) < 0.01, "碰到的高度不对：" + (entry.get().y - center.y) + "，穹顶高 " + height);
            helper.assertTrue(Shields.intercept(level, center.add(0, 10, 0), center.add(0, 80, 0)).isEmpty(), "从里面往外飞也算碰到");
            float before = (float) (generator.energy() / ShieldMath.JOULES_PER_DAMAGE);
            float left = Shields.absorb(level, hit, 5000);
            helper.assertTrue(Math.abs(left - (5000 - before)) < 0.01, "没挡住的伤害不对：" + left + "，能量够挡 " + before);
            helper.assertFalse(generator.raised(), "能量打光了穹顶还在");
            helper.assertTrue(generator.energy() == 0, "能量没扣光：" + generator.energy());
            helper.assertFalse(Shields.covers(level, hit), "收起的穹顶还算盖着");
            helper.assertTrue(Shields.absorb(level, hit, 7) == 7, "没有穹顶也挡了伤害");
        }).thenSucceed();
    }

    /**
     * 存档那一层：一颗星球上只有前 80 台工作，拆掉一台后面的补上；展开的穹顶星球没加载也查得到。
     * 用一个没有世界的维度，不碰别的测试。
     */
    static void limitAndOfflineCovers(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ShieldData data = ShieldData.get(server);
        List<BlockPos> placed = new ArrayList<>();
        try {
            for (int i = 0; i <= ShieldMath.MAX_PER_PLANET; i++) {
                BlockPos pos = new BlockPos(i * 400, 64, 0);
                data.register(FAKE, pos);
                placed.add(pos);
            }
            ShieldData.Generator last = data.find(FAKE, placed.getLast()).orElseThrow();
            helper.assertTrue(data.workingCount(FAKE) == ShieldMath.MAX_PER_PLANET, "工作的台数：" + data.workingCount(FAKE));
            helper.assertFalse(last.working(), "第 81 台也在工作");
            ShieldData.Generator first = data.find(FAKE, placed.getFirst()).orElseThrow();
            data.set(first, new ShieldMath.State(ShieldMath.FULL_ENERGY, true));
            helper.assertTrue(Shields.covers(server, FAKE, 150, 10), "罩满的穹顶 150 格内不算盖着");
            helper.assertFalse(Shields.covers(server, FAKE, 170, 0), "170 格外也算盖着");
            data.set(last, new ShieldMath.State(ShieldMath.FULL_ENERGY, true));
            helper.assertFalse(Shields.covers(server, FAKE, placed.getLast().getX(), 0), "不工作的那台也展开了");
            data.remove(FAKE, placed.removeFirst());
            helper.assertTrue(last.working(), "前面拆掉一台，第 81 台还是不工作");
            helper.assertTrue(Shields.covers(server, FAKE, placed.getLast().getX() + 100, 0), "补上来的那台存着的穹顶不算数");
        } finally {
            placed.forEach(pos -> data.remove(FAKE, pos));
        }
        helper.succeed();
    }

    private static void tall(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.setBlock(pos, state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(pos.above(), state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.UPPER));
    }
}
