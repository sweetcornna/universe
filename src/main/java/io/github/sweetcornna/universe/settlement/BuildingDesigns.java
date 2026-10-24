package io.github.sweetcornna.universe.settlement;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.settlement.BuildingType;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * 十种建筑（ADR-0034 §二）按种族的样子：每座建筑在自己的坐标系里，原点是地基中心的地面那一格，门在 -z 那一面，y = 0 是地面。
 *
 * <p>方块分四部分，施工的三段按部分放出来：第一段是{@link Part#FOUNDATION 地基}，第二段起{@link Part#FRAME 框架}，
 * 第三段是{@link Part#SHELL 墙和顶}加上{@link Part#DETAIL 里面的摆设}。升档以后多一层细节：B 档多腰线、花盆、旗子，
 * A 档多灯和金属装饰，S 档多贵重的装饰和顶上的标志。
 */
final class BuildingDesigns {
    enum Part {
        FOUNDATION,
        FRAME,
        SHELL,
        DETAIL
    }

    /** 一个方块和它属于哪部分。 */
    record Placed(BlockState state, Part part) {
    }

    /** 收方块：{@link #at} 之后放的只在档次够了才算。 */
    static final class Kit {
        final Palette p;
        final Race race;
        final int grade;
        private int minGrade;
        private Part part = Part.SHELL;
        final Map<Long, Placed> blocks = new LinkedHashMap<>();
        int top;

        Kit(Race race, int grade) {
            this.p = Palette.of(race);
            this.race = race;
            this.grade = grade;
        }

        Kit at(int minGrade) {
            this.minGrade = minGrade;
            return this;
        }

        Kit part(Part part) {
            this.part = part;
            this.minGrade = 0;
            return this;
        }

        void set(int x, int y, int z, BlockState state) {
            if (this.minGrade > this.grade) {
                return;
            }
            this.blocks.put(pack(x, y, z), new Placed(state, this.part));
            if (!state.isAir()) {
                this.top = Math.max(this.top, y);
            }
        }

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                    for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                        this.set(x, y, z, state);
                    }
                }
            }
        }

        /** 一圈墙（不含四角）。 */
        void ring(int hx, int hz, int y0, int y1, BlockState state) {
            for (int y = y0; y <= y1; y++) {
                for (int x = -hx; x <= hx; x++) {
                    for (int z = -hz; z <= hz; z++) {
                        boolean edge = Math.abs(x) == hx || Math.abs(z) == hz;
                        boolean corner = Math.abs(x) == hx && Math.abs(z) == hz;
                        if (edge && !corner) {
                            this.set(x, y, z, state);
                        }
                    }
                }
            }
        }

        /** 四角立柱。 */
        void corners(int hx, int hz, int y0, int y1, BlockState state) {
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    this.fill(sx * hx, y0, sz * hz, sx * hx, y1, sz * hz, state);
                }
            }
        }

        boolean has(int x, int y, int z) {
            Placed placed = this.blocks.get(pack(x, y, z));
            return placed != null && !placed.state().isAir();
        }
    }

    static long pack(int x, int y, int z) {
        return ((long) (x & 0xFFFF) << 32) | ((long) (y & 0xFFFF) << 16) | (z & 0xFFFF);
    }

    static int unpackX(long key) {
        return (short) (key >> 32);
    }

    static int unpackY(long key) {
        return (short) (key >> 16);
    }

    static int unpackZ(long key) {
        return (short) key;
    }

    private BuildingDesigns() {
    }

    /** 这种建筑盖完了的样子（{@code grade} 档）。 */
    static Kit design(BuildingType type, Race race, int grade) {
        Kit k = new Kit(race, grade);
        switch (type) {
            case OFFICE -> office(k);
            case HABITATION -> habitation(k);
            case FARM -> farm(k);
            case WORKSHOP -> workshop(k);
            case SALOON -> saloon(k);
            case MARKET -> market(k);
            case WATCHTOWER -> watchtower(k);
            case TURRET -> turret(k);
            case LANDING_PAD -> landingPad(k);
            case FISHING_POND -> pond(k);
        }
        return k;
    }

    // ---- 常用的几样

    private static BlockState facing(Block block, Direction direction) {
        return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, direction);
    }

    private static BlockState log(BlockState post, Direction.Axis axis) {
        return post.hasProperty(RotatedPillarBlock.AXIS) ? post.setValue(RotatedPillarBlock.AXIS, axis) : post;
    }

    /** 门：两格高，下半在 y。 */
    private static void door(Kit k, int x, int y, int z, Direction facing) {
        BlockState door = k.p.door().defaultBlockState().setValue(DoorBlock.FACING, facing);
        k.set(x, y, z, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        k.set(x, y + 1, z, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static BlockState barrel(Direction facing) {
        return Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, facing);
    }

    /** 立着的旗：一根栏杆，顶上一面旗。 */
    private static void flag(Kit k, int x, int y0, int height, int z, DyeColor color, int rotation) {
        k.fill(x, y0, z, x, y0 + height - 1, z, k.p.fence());
        k.set(x, y0 + height, z, Blocks.BANNER.pick(color).defaultBlockState().setValue(BannerBlock.ROTATION, rotation));
    }

    private static BlockState wallBanner(DyeColor color, Direction facing) {
        return Blocks.WALL_BANNER.pick(color).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
    }

    /** 灯柱：一根栏杆，顶上一盏灯。 */
    private static void lampPost(Kit k, int x, int y0, int height, int z) {
        k.fill(x, y0, z, x, y0 + height - 1, z, k.p.fence());
        k.set(x, y0 + height, z, k.p.lamp());
    }

    /**
     * 屋顶：商旅族是人字形的布顶（屋脊顺着 {@code alongZ} 的方向），学者族是带矮墙和天窗的平顶，戍卫族是带垛口的平顶。
     * {@code y} 是屋顶最下面一层。
     */
    private static void roof(Kit k, int hx, int hz, int y, boolean alongZ) {
        switch (k.race) {
            case TRADER -> gable(k, hx, hz, y, alongZ);
            case SCHOLAR -> {
                k.fill(-hx, y, -hz, hx, y, hz, k.p.roof());
                for (int x = -hx; x <= hx; x++) {
                    for (int z = -hz; z <= hz; z++) {
                        if (Math.abs(x) == hx || Math.abs(z) == hz) {
                            k.set(x, y + 1, z, k.p.slab(false));
                        }
                    }
                }
                // 天窗
                if (hx >= 2 && hz >= 2) {
                    k.fill(-hx + 2, y, -hz + 2, hx - 2, y, hz - 2, Blocks.STAINED_GLASS.lightBlue().defaultBlockState());
                }
            }
            case WARDEN -> {
                k.fill(-hx, y, -hz, hx, y, hz, k.p.roof());
                for (int x = -hx; x <= hx; x++) {
                    for (int z = -hz; z <= hz; z++) {
                        boolean edge = Math.abs(x) == hx || Math.abs(z) == hz;
                        if (edge && ((x + z) & 1) == 0) {
                            k.set(x, y + 1, z, k.p.wall());
                        }
                    }
                }
            }
        }
    }

    /** 人字形的顶：两边的台阶往屋脊收，两头的山墙补墙，屋檐往外挑一格。 */
    private static void gable(Kit k, int hx, int hz, int y, boolean alongZ) {
        int half = alongZ ? hx : hz;
        int length = alongZ ? hz : hx;
        for (int level = 0; level <= half; level++) {
            int across = half - level;
            for (int along = -length - 1; along <= length + 1; along++) {
                if (across == 0) {
                    set(k, alongZ, 0, y + level, along, k.p.wool(true));
                    continue;
                }
                Direction up = alongZ ? Direction.EAST : Direction.SOUTH;
                set(k, alongZ, -across, y + level, along, k.p.stair(up, false));
                set(k, alongZ, across, y + level, along, k.p.stair(up.getOpposite(), false));
            }
            // 山墙
            if (across > 0) {
                for (int a = -across + 1; a <= across - 1; a++) {
                    set(k, alongZ, a, y + level, -length, k.p.wall());
                    set(k, alongZ, a, y + level, length, k.p.wall());
                }
            }
        }
    }

    /** 按屋脊的方向放：{@code across} 是横着的那一轴，{@code along} 是顺着屋脊的那一轴。 */
    private static void set(Kit k, boolean alongZ, int across, int y, int along, BlockState state) {
        if (alongZ) {
            k.set(across, y, along, state);
        } else {
            k.set(along, y, across, state);
        }
    }

    /** 四面墙：四角立柱，中间墙，每面正中开窗（前面是门）；{@code y1} 那一层是一圈横梁。 */
    private static void shell(Kit k, int hx, int hz, int y1, boolean windows) {
        k.part(Part.FRAME);
        k.corners(hx, hz, 1, y1, k.p.post());
        for (int x = -hx + 1; x <= hx - 1; x++) {
            k.set(x, y1, -hz, log(k.p.post(), Direction.Axis.X));
            k.set(x, y1, hz, log(k.p.post(), Direction.Axis.X));
        }
        for (int z = -hz + 1; z <= hz - 1; z++) {
            k.set(-hx, y1, z, log(k.p.post(), Direction.Axis.Z));
            k.set(hx, y1, z, log(k.p.post(), Direction.Axis.Z));
        }
        k.part(Part.SHELL);
        k.ring(hx, hz, 1, y1 - 1, k.p.wall());
        if (windows) {
            for (int y = 2; y <= Math.min(3, y1 - 1); y++) {
                for (int x = -hx + 1; x <= hx - 1; x++) {
                    if (Math.abs(x) <= hx - 2 || hx <= 2) {
                        if (x != 0 || hx > 2) {
                            k.set(x, y, hz, k.p.window());
                        }
                    }
                }
                for (int z = -hz + 1; z <= hz - 1; z++) {
                    if (Math.abs(z) <= Math.max(0, hz - 2)) {
                        k.set(-hx, y, z, k.p.window());
                        k.set(hx, y, z, k.p.window());
                    }
                }
            }
        }
        // B 档：一道腰线
        k.at(1);
        for (int x = -hx + 1; x <= hx - 1; x++) {
            k.set(x, 1, -hz, k.p.band());
            k.set(x, 1, hz, k.p.band());
        }
        for (int z = -hz + 1; z <= hz - 1; z++) {
            k.set(-hx, 1, z, k.p.band());
            k.set(hx, 1, z, k.p.band());
        }
        k.at(0);
    }

    /** 地基：占地的地面换成地基的石料，里面铺地板。 */
    private static void foundation(Kit k, int hx, int hz, boolean floor) {
        k.part(Part.FOUNDATION);
        k.fill(-hx, 0, -hz, hx, 0, hz, k.p.found());
        if (floor) {
            k.part(Part.SHELL);
            k.fill(-hx + 1, 0, -hz + 1, hx - 1, 0, hz - 1, k.p.floor());
        }
    }

    /** 门口两边：B 档两盆花，A 档两根灯柱，S 档门楣换成贵重的方块。 */
    private static void entrance(Kit k, int hz, int lintel) {
        k.part(Part.DETAIL).at(1);
        k.set(-2, 1, -hz - 1, Blocks.POTTED_RED_TULIP.defaultBlockState());
        k.set(2, 1, -hz - 1, Blocks.POTTED_ORANGE_TULIP.defaultBlockState());
        k.at(2);
        lampPost(k, -1, 1, 1, -hz - 1);
        lampPost(k, 1, 1, 1, -hz - 1);
        k.at(3);
        k.set(0, lintel, -hz, k.p.rich());
        k.at(0);
    }

    // ---- 十种建筑

    /** 总督办公室：5×5，高一层半，门口一根旗杆；里面一张讲台、两个书架，A 档以上屋顶多一圈金属。 */
    private static void office(Kit k) {
        foundation(k, 2, 2, true);
        shell(k, 2, 2, 5, true);
        door(k, 0, 1, -2, Direction.SOUTH);
        k.part(Part.SHELL);
        roof(k, 2, 2, 6, true);
        k.part(Part.DETAIL);
        k.set(0, 1, 1, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.NORTH));
        k.set(-1, 1, 1, Blocks.BOOKSHELF.defaultBlockState());
        k.set(1, 1, 1, Blocks.BOOKSHELF.defaultBlockState());
        k.set(-1, 1, -1, k.p.carpet(false));
        k.set(1, 1, -1, k.p.carpet(false));
        k.set(0, 1, -1, k.p.carpet(true));
        k.set(0, 1, 0, k.p.carpet(true));
        k.set(0, 4, 0, k.p.hanging());
        flag(k, -4, 1, 6, -3, k.p.clothA(), 8);
        entrance(k, 2, 3);
        // B 档：前墙挂两面旗
        k.at(1);
        k.set(-2, 4, -3, wallBanner(k.p.clothA(), Direction.NORTH));
        k.set(2, 4, -3, wallBanner(k.p.clothB(), Direction.NORTH));
        // A 档：屋檐下一圈金属
        k.at(2);
        for (int x = -2; x <= 2; x++) {
            k.set(x, 5, -2, k.p.metal());
            k.set(x, 5, 2, k.p.metal());
        }
        // S 档：第二根旗杆，顶上一根发光的尖顶
        k.at(3);
        flag(k, 4, 1, 6, -3, k.p.clothB(), 8);
        k.set(0, k.race == Race.TRADER ? 9 : 7, 0, Blocks.END_ROD.defaultBlockState());
        k.at(0);
    }

    /** 住宅：5×5 一层，里面一张床、一个木桶、一盏灯；B 档门口花盆，A 档灯柱和侧院，S 档门口的遮阳棚。 */
    private static void habitation(Kit k) {
        foundation(k, 2, 2, true);
        shell(k, 2, 2, 4, true);
        door(k, 0, 1, -2, Direction.SOUTH);
        k.part(Part.SHELL);
        roof(k, 2, 2, 5, true);
        k.part(Part.DETAIL);
        BlockState bed = Blocks.BED.pick(k.p.clothA()).defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
        k.set(-1, 1, 0, bed.setValue(BedBlock.PART, BedPart.FOOT));
        k.set(-1, 1, 1, bed.setValue(BedBlock.PART, BedPart.HEAD));
        k.set(1, 1, 1, barrel(Direction.UP));
        k.set(1, 1, 0, Blocks.FLOWER_POT.defaultBlockState());
        k.set(0, 3, 0, k.p.hanging());
        entrance(k, 2, 3);
        // A 档：侧院一圈矮栏杆，里面几盆花
        k.at(2);
        for (int z = -1; z <= 2; z++) {
            k.set(-4, 1, z, k.p.fence());
        }
        k.set(-3, 1, 2, k.p.fence());
        k.set(-3, 1, -1, k.p.fence());
        k.set(-3, 1, 0, Blocks.POTTED_BLUE_ORCHID.defaultBlockState());
        k.set(-3, 1, 1, Blocks.POTTED_ALLIUM.defaultBlockState());
        // S 档：门口的布棚
        k.at(3);
        for (int x = -1; x <= 1; x++) {
            k.set(x, 3, -3, Blocks.WOOL_SLAB.pick(x == 0 ? k.p.clothB() : k.p.clothA()).defaultBlockState()
                    .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
        }
        k.at(0);
    }

    /** 农场：7×7 的围栏，四行庄稼夹一道水渠；B 档种上胡萝卜，A 档土豆和干草垛、角上的灯，S 档南瓜和稻草人。 */
    private static void farm(Kit k) {
        k.part(Part.FOUNDATION);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                k.set(x, 0, z, x == 0 ? k.p.found() : Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmlandBlock.MOISTURE, 7));
            }
        }
        k.fill(-3, 0, -3, 3, 0, -3, k.p.found());
        k.fill(-3, 0, 3, 3, 0, 3, k.p.found());
        k.fill(-3, 0, -2, -3, 0, 2, k.p.found());
        k.fill(3, 0, -2, 3, 0, 2, k.p.found());
        k.part(Part.FRAME);
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if ((Math.abs(x) == 3 || Math.abs(z) == 3) && !(x == 0 && z == -3)) {
                    k.set(x, 1, z, k.p.fence());
                }
            }
        }
        k.set(0, 1, -3, Blocks.SPRUCE_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.NORTH));
        k.part(Part.SHELL);
        for (int z = -2; z <= 2; z++) {
            k.set(0, 0, z, Blocks.WATER.defaultBlockState());
            for (int x : new int[] {-2, -1, 1, 2}) {
                Block crop = Blocks.WHEAT;
                if (k.grade >= 1 && x == 2) {
                    crop = Blocks.CARROTS;
                }
                if (k.grade >= 2 && x == -2) {
                    crop = Blocks.POTATOES;
                }
                k.set(x, 1, z, crop.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE));
            }
        }
        k.part(Part.DETAIL);
        k.set(-3, 1, 3, Blocks.COMPOSTER.defaultBlockState());
        k.at(2);
        k.set(3, 2, 3, k.p.lamp());
        k.set(-3, 2, -3, k.p.lamp());
        k.set(3, 2, -3, k.p.lamp());
        k.set(-4, 1, 3, Blocks.HAY_BLOCK.defaultBlockState());
        k.set(-4, 1, 2, Blocks.HAY_BLOCK.defaultBlockState());
        k.set(-4, 2, 3, Blocks.HAY_BLOCK.defaultBlockState());
        k.at(3);
        // 南瓜换掉一角的庄稼，中间立一个稻草人
        k.set(1, 1, 2, Blocks.PUMPKIN.defaultBlockState());
        k.set(2, 1, 2, Blocks.PUMPKIN.defaultBlockState());
        k.set(-1, 1, -2, k.p.fence());
        k.set(-1, 2, -2, Blocks.HAY_BLOCK.defaultBlockState());
        k.set(-1, 3, -2, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        k.at(0);
    }

    /** 工坊：7×5，前面敞开两根柱子撑着，后墙一溜机器，角上一根冒烟的烟囱；A 档多一台切石机和灯，S 档金属装饰。 */
    private static void workshop(Kit k) {
        foundation(k, 3, 2, true);
        k.part(Part.FRAME);
        k.corners(3, 2, 1, 4, k.p.post());
        k.fill(-1, 1, -2, -1, 4, -2, k.p.post());
        k.fill(1, 1, -2, 1, 4, -2, k.p.post());
        for (int x = -2; x <= 2; x++) {
            k.set(x, 4, -2, log(k.p.post(), Direction.Axis.X));
            k.set(x, 4, 2, log(k.p.post(), Direction.Axis.X));
        }
        k.part(Part.SHELL);
        // 后墙和两边，前面只有两角一截矮墙
        for (int x = -2; x <= 2; x++) {
            k.fill(x, 1, 2, x, 3, 2, k.p.wall());
        }
        for (int z = -1; z <= 1; z++) {
            k.fill(-3, 1, z, -3, 3, z, k.p.wall());
            k.fill(3, 1, z, 3, 3, z, k.p.wall());
        }
        k.set(-3, 2, 0, k.p.window());
        k.set(3, 2, 0, k.p.window());
        k.set(-2, 1, -2, k.p.wall());
        k.set(2, 1, -2, k.p.wall());
        roof(k, 3, 2, 5, false);
        // 烟囱
        k.fill(3, 5, 2, 3, 7, 2, k.p.wall());
        k.set(3, 8, 2, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        k.part(Part.DETAIL);
        k.set(-2, 1, 1, Blocks.SMITHING_TABLE.defaultBlockState());
        k.set(-1, 1, 1, Blocks.BLAST_FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.NORTH));
        k.set(0, 1, 1, Blocks.CRAFTING_TABLE.defaultBlockState());
        k.set(1, 1, 1, Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, Direction.EAST));
        k.set(2, 1, 1, barrel(Direction.UP));
        k.set(2, 2, 1, barrel(Direction.NORTH));
        k.set(2, 1, -1, Blocks.GRINDSTONE.defaultBlockState());
        k.set(0, 3, 0, k.p.hanging());
        k.at(1);
        k.set(-2, 1, -1, barrel(Direction.UP));
        k.set(-2, 2, -1, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.BOTTOM));
        k.at(2);
        k.set(-2, 1, 0, Blocks.STONECUTTER.defaultBlockState());
        k.set(-2, 3, 0, k.p.hanging());
        k.set(2, 3, 0, k.p.hanging());
        k.at(3);
        k.set(-3, 4, -2, k.p.rich());
        k.set(3, 4, -2, k.p.rich());
        k.set(0, 4, -2, k.p.metal());
        k.at(0);
    }

    /** 酒馆：7×7，门口两根柱子挂灯，里面一条吧台、两张桌子；B 档前墙挂旗，A 档门廊的灯，S 档门楣和顶上的铃。 */
    private static void saloon(Kit k) {
        foundation(k, 3, 3, true);
        shell(k, 3, 3, 5, true);
        door(k, 0, 1, -3, Direction.SOUTH);
        k.part(Part.SHELL);
        roof(k, 3, 3, 6, true);
        k.part(Part.DETAIL);
        // 吧台
        for (int x = -2; x <= 2; x++) {
            k.set(x, 1, 1, x == 0 ? Blocks.SMOOTH_STONE_SLAB.defaultBlockState() : barrel(Direction.NORTH));
        }
        k.set(-1, 2, 1, Blocks.BREWING_STAND.defaultBlockState());
        k.set(2, 1, 2, barrel(Direction.UP));
        k.set(-2, 1, 2, barrel(Direction.UP));
        // 两张桌子和凳子
        for (int x : new int[] {-2, 2}) {
            k.set(x, 1, -1, k.p.fence());
            k.set(x, 2, -1, k.p.carpet(x > 0));
            BlockState stool = k.p.stair(x < 0 ? Direction.WEST : Direction.EAST, false);
            k.set(x + (x < 0 ? 1 : -1), 1, -1, stool);
        }
        k.set(-1, 4, 0, k.p.hanging());
        k.set(1, 4, 0, k.p.hanging());
        k.set(0, 4, -2, k.p.hanging());
        k.at(1);
        k.set(-2, 3, -4, wallBanner(k.p.clothA(), Direction.NORTH));
        k.set(2, 3, -4, wallBanner(k.p.clothB(), Direction.NORTH));
        k.at(0);
        entrance(k, 3, 3);
        k.at(3);
        k.set(0, k.race == Race.TRADER ? 10 : 7, 0,
                Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR).setValue(BellBlock.FACING, Direction.NORTH));
        k.at(0);
    }

    /** 市场：7×7 的敞棚，两色相间的布顶，四边是货摊，正中吊一口铃；B 档摊上多些货，A 档灯，S 档顶上的旗。 */
    private static void market(Kit k) {
        foundation(k, 3, 3, false);
        k.part(Part.SHELL);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                k.set(x, 0, z, ((x + z) & 1) == 0 ? k.p.floor() : k.p.found());
            }
        }
        k.part(Part.FRAME);
        k.corners(3, 3, 1, 4, k.p.post());
        k.fill(0, 1, 3, 0, 4, 3, k.p.post());
        k.fill(-3, 1, 0, -3, 4, 0, k.p.post());
        k.fill(3, 1, 0, 3, 4, 0, k.p.post());
        k.part(Part.SHELL);
        // 布顶：一条一条两色相间，中间高一格
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                boolean middle = Math.abs(x) <= 1 && Math.abs(z) <= 1;
                k.set(x, middle ? 6 : 5, z, k.p.wool((x & 1) != 0));
            }
        }
        k.part(Part.DETAIL);
        k.set(0, 5, 0, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING).setValue(BellBlock.FACING, Direction.NORTH));
        // 货摊
        for (int z = -2; z <= 2; z += 2) {
            k.set(-2, 1, z, barrel(Direction.EAST));
            k.set(2, 1, z, barrel(Direction.WEST));
        }
        k.set(-1, 1, 2, Blocks.HAY_BLOCK.defaultBlockState());
        k.set(1, 1, 2, barrel(Direction.NORTH));
        k.at(1);
        k.set(-2, 2, -2, Blocks.MELON.defaultBlockState());
        k.set(2, 2, -2, Blocks.PUMPKIN.defaultBlockState());
        k.set(-2, 2, 2, Blocks.DRIED_KELP_BLOCK.defaultBlockState());
        k.set(2, 2, 0, Blocks.HAY_BLOCK.defaultBlockState());
        k.at(2);
        k.set(-3, 5, -3, k.p.lamp());
        k.set(3, 5, -3, k.p.lamp());
        k.set(-3, 5, 3, k.p.lamp());
        k.set(3, 5, 3, k.p.lamp());
        k.set(-2, 4, 0, k.p.hanging());
        k.set(2, 4, 0, k.p.hanging());
        k.at(3);
        flag(k, 0, 7, 2, 0, k.p.clothB(), 8);
        k.set(-3, 4, 0, k.p.rich());
        k.set(3, 4, 0, k.p.rich());
        k.at(0);
    }

    /** 瞭望塔：3×3 的塔身 11 格高，里面一架梯子，顶上 5×5 的平台带栏杆和顶棚；S 档顶上一根发光的尖顶。 */
    private static void watchtower(Kit k) {
        foundation(k, 1, 1, false);
        k.part(Part.FRAME);
        k.corners(1, 1, 1, 13, k.p.post());
        k.part(Part.SHELL);
        for (int y = 1; y <= 10; y++) {
            k.set(0, y, 1, y % 4 == 3 ? k.p.window() : k.p.wall());
            k.set(-1, y, 0, y % 4 == 3 ? k.p.window() : k.p.wall());
            k.set(1, y, 0, y % 4 == 3 ? k.p.window() : k.p.wall());
            if (y > 2) {
                k.set(0, y, -1, y % 4 == 3 ? k.p.window() : k.p.wall());
            }
        }
        door(k, 0, 1, -1, Direction.SOUTH);
        for (int y = 1; y <= 11; y++) {
            k.set(0, y, 0, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        }
        // 平台
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                if (Math.abs(x) <= 1 && Math.abs(z) <= 1 && Math.abs(x) == 1 && Math.abs(z) == 1) {
                    continue;
                }
                k.set(x, 11, z, k.p.slab(true));
                if (Math.abs(x) == 2 || Math.abs(z) == 2) {
                    k.set(x, 12, z, k.p.fence());
                }
            }
        }
        k.fill(-2, 14, -2, 2, 14, 2, k.p.slab(false));
        k.part(Part.DETAIL);
        k.set(0, 13, 0, k.p.hanging());
        k.at(1);
        k.set(0, 10, -2, wallBanner(k.p.clothA(), Direction.NORTH));
        k.at(2);
        k.set(-2, 13, -2, k.p.lamp());
        k.set(2, 13, 2, k.p.lamp());
        k.set(2, 13, -2, k.p.metal());
        k.set(-2, 13, 2, k.p.metal());
        k.at(3);
        k.set(0, 15, 0, k.p.rich());
        k.set(0, 16, 0, Blocks.END_ROD.defaultBlockState());
        k.at(0);
    }

    /** 防御炮台：3×3 的石台，一圈沙袋（矮墙），正中一根立柱顶着炮塔；A 档多两盏灯，S 档贵重的底座。 */
    private static void turret(Kit k) {
        foundation(k, 1, 1, false);
        k.part(Part.FRAME);
        k.set(0, 1, 0, k.p.metal());
        k.set(0, 2, 0, k.p.post());
        k.part(Part.SHELL);
        k.ring(1, 1, 1, 1, k.p.wall());
        k.corners(1, 1, 1, 1, k.p.trim());
        k.set(0, 3, 0, UniverseBlocks.SETTLEMENT_TURRET.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) == 2 && ((x + z) & 1) == 0) {
                    k.set(x, 1, z, k.p.slab(false));
                }
            }
        }
        k.part(Part.DETAIL);
        k.at(1);
        k.set(-1, 2, -1, k.p.slab(false));
        k.set(1, 2, 1, k.p.slab(false));
        k.at(2);
        k.set(-1, 2, 1, k.p.lamp());
        k.set(1, 2, -1, k.p.lamp());
        k.at(3);
        k.set(0, 1, 0, k.p.rich());
        k.at(0);
    }

    /** 着陆坪：9×9 的平台，正中一个 H 字，四角嵌灯，角上一根控制柱；A 档四角立灯，S 档贵重的边。 */
    private static void landingPad(Kit k) {
        k.part(Part.FOUNDATION);
        k.fill(-4, 0, -4, 4, 0, 4, k.p.found());
        k.part(Part.FRAME);
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) == 4) {
                    k.set(x, 0, z, k.p.trim());
                }
            }
        }
        k.part(Part.SHELL);
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                k.set(x, 0, z, k.p.floor());
            }
        }
        BlockState paint = Blocks.CONCRETE.pick(k.p.clothA() == DyeColor.WHITE ? DyeColor.YELLOW : k.p.clothA()).defaultBlockState();
        for (int z = -2; z <= 2; z++) {
            k.set(-2, 0, z, paint);
            k.set(2, 0, z, paint);
        }
        k.set(-1, 0, 0, paint);
        k.set(0, 0, 0, paint);
        k.set(1, 0, 0, paint);
        k.corners(4, 4, 0, 0, k.p.light());
        k.part(Part.DETAIL);
        k.set(4, 1, -3, k.p.post());
        k.set(4, 2, -3, k.p.metal());
        k.set(4, 3, -3, k.p.lamp());
        k.at(1);
        for (int a = -2; a <= 2; a += 4) {
            k.set(a, 0, -4, k.p.light());
            k.set(a, 0, 4, k.p.light());
        }
        k.at(2);
        k.corners(4, 4, 1, 1, Blocks.END_ROD.defaultBlockState());
        k.at(3);
        for (int x = -3; x <= 3; x += 2) {
            k.set(x, 0, -4, k.p.rich());
            k.set(x, 0, 4, k.p.rich());
        }
        k.at(0);
    }

    /** 鱼塘：7×7，石沿围着 5×5、两格深的水，前面一条木栈桥伸进去，水面几片荷叶；A 档灯和长凳，S 档贵重的石沿。 */
    private static void pond(Kit k) {
        k.part(Part.FOUNDATION);
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) == 3) {
                    k.set(x, 0, z, k.p.found());
                }
            }
        }
        k.part(Part.FRAME);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                k.set(x, -2, z, k.p.found());
                k.set(x, -1, z, Blocks.AIR.defaultBlockState());
                k.set(x, 0, z, Blocks.AIR.defaultBlockState());
            }
        }
        k.part(Part.SHELL);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                k.set(x, -1, z, Blocks.WATER.defaultBlockState());
                k.set(x, 0, z, Blocks.WATER.defaultBlockState());
            }
        }
        // 栈桥
        for (int z = -2; z <= 0; z++) {
            k.set(0, 0, z, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
                    net.minecraft.world.level.block.state.properties.SlabType.TOP));
        }
        k.set(-1, 1, 1, Blocks.LILY_PAD.defaultBlockState());
        k.set(2, 1, -1, Blocks.LILY_PAD.defaultBlockState());
        k.set(-2, 1, -2, Blocks.LILY_PAD.defaultBlockState());
        k.part(Part.DETAIL);
        // 晾鱼的架子
        k.set(3, 1, -3, k.p.fence());
        k.set(3, 2, -3, k.p.fence());
        k.set(3, 3, -3, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP));
        k.set(-3, 1, -3, barrel(Direction.UP));
        k.at(1);
        k.set(1, 1, 2, Blocks.LILY_PAD.defaultBlockState());
        k.set(-3, 1, 3, Blocks.POTTED_FERN.defaultBlockState());
        k.at(2);
        lampPost(k, 3, 1, 2, 3);
        lampPost(k, -3, 1, 2, 3);
        k.set(-1, 1, -3, k.p.stair(Direction.SOUTH, false));
        k.set(1, 1, -3, k.p.stair(Direction.SOUTH, false));
        k.at(3);
        for (int a = -3; a <= 3; a += 3) {
            k.set(a, 0, 3, k.p.rich());
            k.set(-3, 0, a, k.p.rich());
            k.set(3, 0, a, k.p.rich());
        }
        k.at(0);
    }
}
