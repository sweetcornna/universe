package io.github.sweetcornna.universe.settlement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.poi.Landmark;
import io.github.sweetcornna.universe.core.poi.SettlementLayout;
import io.github.sweetcornna.universe.core.settlement.BuildingType;
import io.github.sweetcornna.universe.core.settlement.Plot;
import io.github.sweetcornna.universe.core.settlement.Settlement;
import io.github.sweetcornna.universe.fishing.Fishing;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.world.planet.Planets;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 把聚落的数值盖成世界里的方块（ADR-0034 §二）：接管时整平 8 块地基、铺一圈边、修路；每块地基按施工到哪一段、什么档次、
 * 开没开张、坏没坏放方块。等待的 5 分钟里方块一点点长出来（从下往上，脚手架最后拆），看得出在盖。
 *
 * <p>一块地基管的是 11×11、地面往上 {@value #HEIGHT} 格的一块：这里面不在设计里的方块都清掉。区块没加载时不动，
 * 加载以后照数值补盖。
 */
final class SettlementBuilder {
    /** 一块地基往上管多高。 */
    static final int HEIGHT = 18;
    /** 往下管多深（鱼塘的水是两格深）。 */
    private static final int DEPTH = 2;
    /** 不通知邻居、不掉落：放方块时不会把灯、旗、庄稼碰掉。 */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** 等待的 5 分钟分几步长出来。 */
    static final int STEPS = 40;
    /** 整平时往下最多垫几格（悬崖边、测试场地下面是空的）。 */
    private static final int MAX_FILL = 8;
    /** 上一次盖成的样子：聚落编号#地基 → {@link #key}。 */
    private static final Map<String, Integer> APPLIED = new HashMap<>();

    private SettlementBuilder() {
    }

    /** 一块地基在世界里的位置和朝向：原点是地基中心整平以后的地面那一格。 */
    record Frame(BlockPos origin, int turns) {
        BlockPos at(int bx, int by, int bz) {
            return switch (this.turns) {
                case 1 -> this.origin.offset(-bz, by, bx);
                case 2 -> this.origin.offset(-bx, by, -bz);
                case 3 -> this.origin.offset(bz, by, -bx);
                default -> this.origin.offset(bx, by, bz);
            };
        }

        Rotation rotation() {
            return switch (this.turns) {
                case 1 -> Rotation.CLOCKWISE_90;
                case 2 -> Rotation.CLOCKWISE_180;
                case 3 -> Rotation.COUNTERCLOCKWISE_90;
                default -> Rotation.NONE;
            };
        }
    }

    static Frame frame(SettlementData.Entry entry, int index) {
        Settlement s = entry.settlement();
        Landmark landmark = s.landmark();
        int[] w = SettlementLayout.plotWorld(landmark, index, 0, 0);
        int y = entry.plotY()[index] == Integer.MIN_VALUE ? s.y() : entry.plotY()[index];
        return new Frame(new BlockPos(w[0], y, w[1]), SettlementLayout.plotWorldRotation(landmark, index));
    }

    // ---- 整平和修路

    /** 接管时：整平还没整平的地基，铺边，修路。 */
    static void prepare(ServerLevel level, SettlementData.Entry entry) {
        Settlement s = entry.settlement();
        Palette p = Palette.of(s.race());
        Landmark landmark = s.landmark();
        int half = SettlementLayout.PLOT_HALF;
        for (int i = 0; i < s.plotCount(); i++) {
            if (entry.plotY()[i] != Integer.MIN_VALUE) {
                continue;
            }
            List<Integer> heights = new ArrayList<>();
            for (int bx = -half; bx <= half; bx++) {
                for (int bz = -half; bz <= half; bz++) {
                    int[] w = SettlementLayout.plotWorld(landmark, i, bx, bz);
                    heights.add(ground(level, w[0], w[1]));
                }
            }
            heights.sort(Integer::compare);
            int target = Math.clamp(heights.get(heights.size() / 2), s.y() - 4, s.y() + 6);
            entry.plotY()[i] = target;
            for (int bx = -half - 2; bx <= half + 2; bx++) {
                for (int bz = -half - 2; bz <= half + 2; bz++) {
                    int d = Math.max(Math.abs(bx), Math.abs(bz));
                    int[] w = SettlementLayout.plotWorld(landmark, i, bx, bz);
                    int g = ground(level, w[0], w[1]);
                    int y = d <= half ? target : (int) Math.round(target + (g - target) * (d - half) / 3.0);
                    column(level, w[0], w[1], g, y, d <= half ? HEIGHT + 2 : 6);
                    if (d == half) {
                        reshape(level, new BlockPos(w[0], target, w[1]), p.border());
                    }
                }
            }
        }
        for (int[] cell : SettlementLayout.roads(landmark)) {
            int[] w = SettlementLayout.world(landmark, cell[0], cell[1]);
            int g = ground(level, w[0], w[1]);
            BlockPos pos = new BlockPos(w[0], g, w[1]);
            BlockState top = level.getBlockState(pos);
            if (!isGround(top) || top.getDestroySpeed(level, pos) < 0 || level.getBlockEntity(pos) != null) {
                continue;
            }
            put(level, pos, p.road());
            for (int up = 1; up <= 2; up++) {
                BlockState above = level.getBlockState(pos.above(up));
                if (!above.isAir() && (above.canBeReplaced() || above.is(BlockTags.FLOWERS))) {
                    put(level, pos.above(up), Blocks.AIR.defaultBlockState());
                }
            }
        }
        SettlementData.get(level.getServer()).changed();
    }

    /** 这一列天然地面的高度：从最高的方块往下，跳过树干、树叶、草和水。 */
    static int ground(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        for (int k = 0; k < 32 && y > level.getMinY(); k++, y--) {
            if (isGround(level.getBlockState(pos.setY(y)))) {
                return y;
            }
        }
        return y;
    }

    static boolean isGround(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES) && !state.canBeReplaced()
                && state.isSolid();
    }

    /** 一列挪到 {@code y}：低了用原来地面下面的土垫高（最多 {@value #MAX_FILL} 格），高了削掉；顶上留原来的地面方块，上面清空。 */
    private static void column(ServerLevel level, int x, int z, int ground, int y, int clear) {
        BlockPos at = new BlockPos(x, ground, z);
        BlockState surface = level.getBlockState(at);
        if (!isGround(surface) || level.getBlockEntity(at) != null || surface.getDestroySpeed(level, at) < 0) {
            surface = Planets.planet(level).isPresent() ? UniverseBlocks.PLANET_REGOLITH.get().defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
        }
        BlockState fill = level.getBlockState(at.below());
        if (!isGround(fill) || level.getBlockEntity(at.below()) != null) {
            fill = surface.is(Blocks.GRASS_BLOCK) ? Blocks.DIRT.defaultBlockState() : surface;
        }
        for (int h = Math.max(ground + 1, y - MAX_FILL); h < y; h++) {
            reshape(level, new BlockPos(x, h, z), fill);
        }
        reshape(level, new BlockPos(x, y, z), surface);
        for (int h = y + 1; h <= Math.max(ground, y) + clear; h++) {
            reshape(level, new BlockPos(x, h, z), Blocks.AIR.defaultBlockState());
        }
    }

    /**
     * 整平时放一个方块。原来那里的东西不是聚落自己的：打不坏的不动；有方块实体的（箱子、机器、告示牌）像挖掉一样连方块带里面的东西掉出来，
     * 不会凭空没了。
     */
    private static void reshape(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState there = level.getBlockState(pos);
        if (there == state || there.getDestroySpeed(level, pos) < 0) {
            return;
        }
        if (there.hasBlockEntity()) {
            level.destroyBlock(pos, true);
        }
        put(level, pos, state);
    }

    // ---- 每块地基的样子

    /** 一块地基该盖成的样子（不含等待中的进度）。 */
    record State(@Nullable BuildingType type, int stage, int grade, boolean opened, boolean damaged) {
        static State of(Plot plot) {
            return new State(plot.type(), plot.stage(), plot.grade(), plot.opened(), plot.damaged());
        }

        /** 等完以后的样子。 */
        State next(Plot plot) {
            if (this.stage < BuildingType.STAGES) {
                return new State(this.type, this.stage + 1, this.grade, this.opened, false);
            }
            return plot.upgrading() ? new State(this.type, this.stage, Math.min(BuildingType.GRADES - 1, this.grade + 1), this.opened, false) : this;
        }
    }

    /** 记在存档里的签名：样子变了它就变。 */
    static int signature(State state, long seed) {
        return Objects.hash(state, seed);
    }

    /** 这个样子的方块（建筑坐标，打包见 {@link BuildingDesigns#pack}）；y ≥ 1 不在表里的是空气，y ≤ 0 不在表里的不动。 */
    static Map<Long, BlockState> view(Settlement s, State st) {
        Palette p = Palette.of(s.race());
        Map<Long, BlockState> view = new HashMap<>();
        int half = SettlementLayout.PLOT_HALF;
        for (int bx = -half; bx <= half; bx++) {
            for (int bz = -half; bz <= half; bz++) {
                if (Math.max(Math.abs(bx), Math.abs(bz)) == half) {
                    view.put(BuildingDesigns.pack(bx, 0, bz), p.border());
                }
            }
        }
        BuildingType type = st.type();
        if (type == null) {
            // 空地：四角插四根桩，桩头一块布
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    view.put(BuildingDesigns.pack(sx * (half - 1), 1, sz * (half - 1)), p.fence());
                    view.put(BuildingDesigns.pack(sx * (half - 1), 2, sz * (half - 1)), p.carpet(false));
                }
            }
            return view;
        }
        int hx = type.halfX();
        int hz = type.halfZ();
        BuildingDesigns.Kit kit = BuildingDesigns.design(type, s.race(), st.grade());
        int top = Math.max(3, kit.top);
        if (st.stage() < BuildingType.STAGES) {
            // 脚手架和一堆料
            if (st.stage() > 0) {
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sz = -1; sz <= 1; sz += 2) {
                        for (int y = 1; y <= Math.min(top, 12); y++) {
                            view.put(BuildingDesigns.pack(sx * Math.min(half, hx + 1), y, sz * Math.min(half, hz + 1)),
                                    Blocks.SCAFFOLDING.defaultBlockState().setValue(ScaffoldingBlock.DISTANCE, 0));
                        }
                    }
                }
            } else {
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sz = -1; sz <= 1; sz += 2) {
                        view.put(BuildingDesigns.pack(sx * hx, 1, sz * hz), p.fence());
                    }
                }
                for (int x = -hx; x <= hx; x++) {
                    view.put(BuildingDesigns.pack(x, 0, -hz), Blocks.COARSE_DIRT.defaultBlockState());
                    view.put(BuildingDesigns.pack(x, 0, hz), Blocks.COARSE_DIRT.defaultBlockState());
                }
                for (int z = -hz; z <= hz; z++) {
                    view.put(BuildingDesigns.pack(-hx, 0, z), Blocks.COARSE_DIRT.defaultBlockState());
                    view.put(BuildingDesigns.pack(hx, 0, z), Blocks.COARSE_DIRT.defaultBlockState());
                }
            }
            int pileX = -Math.min(half, hx + 1);
            if (hx + 1 < half) {
                view.put(BuildingDesigns.pack(pileX - (hx + 2 < half ? 1 : 0), 1, 1), Blocks.BARREL.defaultBlockState());
                view.put(BuildingDesigns.pack(pileX - (hx + 2 < half ? 1 : 0), 1, 0),
                        Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Z));
            }
        }
        for (Map.Entry<Long, BuildingDesigns.Placed> e : kit.blocks.entrySet()) {
            BuildingDesigns.Part part = e.getValue().part();
            boolean show = switch (part) {
                case FOUNDATION -> st.stage() >= 1;
                case FRAME -> st.stage() >= 2;
                case SHELL, DETAIL -> st.stage() >= 3;
            };
            if (show) {
                view.put(e.getKey(), e.getValue().state());
            }
        }
        if (st.stage() >= BuildingType.STAGES && !st.opened()) {
            // 门口拉一条红绸，开张时剪掉
            for (int x = -1; x <= 1; x++) {
                view.put(BuildingDesigns.pack(x, 1, -Math.min(half, hz + 1)), Blocks.CARPET.red().defaultBlockState());
            }
        }
        if (st.damaged()) {
            damage(view, kit, p, s.seed(), hx, hz);
        }
        view.put(BuildingDesigns.pack(2, 1, -half), terminal());
        return view;
    }

    private static BlockState terminal() {
        return UniverseBlocks.SETTLEMENT_TERMINAL.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
    }

    /** 被打坏了：墙和顶掉一些、烧黑一些，里面结了蛛网。按种子定，每次看着一样。 */
    private static void damage(Map<Long, BlockState> view, BuildingDesigns.Kit kit, Palette p, long seed, int hx, int hz) {
        for (Map.Entry<Long, BuildingDesigns.Placed> e : kit.blocks.entrySet()) {
            long key = e.getKey();
            int y = BuildingDesigns.unpackY(key);
            if (y < 2 || e.getValue().part() == BuildingDesigns.Part.FOUNDATION) {
                continue;
            }
            int h = (int) Math.floorMod(io.github.sweetcornna.universe.core.procgen.Seeds.derive(seed, key), 100L);
            BlockState state = e.getValue().state();
            if (h < 26) {
                view.put(key, Blocks.AIR.defaultBlockState());
            } else if (h < 36 && (state.getBlock() == p.wall().getBlock() || state.getBlock() == p.roof().getBlock()
                    || state.getBlock() == p.post().getBlock())) {
                view.put(key, Blocks.CONCRETE.black().defaultBlockState());
            }
        }
        for (int x = -hx + 1; x <= hx - 1; x++) {
            for (int z = -hz + 1; z <= hz - 1; z++) {
                long key = BuildingDesigns.pack(x, 3, z);
                if (!view.containsKey(key) && Math.floorMod(io.github.sweetcornna.universe.core.procgen.Seeds.derive(seed, key), 100L) < 15) {
                    view.put(key, Blocks.COBWEB.defaultBlockState());
                }
            }
        }
    }

    /** 从 {@code a} 往 {@code b} 长出来一部分：从下往上，要拆掉的（脚手架）最后拆。 */
    static Map<Long, BlockState> blend(Map<Long, BlockState> a, Map<Long, BlockState> b, double fraction) {
        List<Long> changing = new ArrayList<>();
        java.util.Set<Long> keys = new java.util.HashSet<>(a.keySet());
        keys.addAll(b.keySet());
        for (long key : keys) {
            BlockState from = stateOr(a, key);
            BlockState to = stateOr(b, key);
            if (to != null && !Objects.equals(from, to)) {
                changing.add(key);
            }
        }
        changing.sort(Comparator.<Long>comparingInt(key -> stateOr(b, key).isAir() ? 1 : 0)
                .thenComparingInt(BuildingDesigns::unpackY)
                .thenComparingInt(key -> Math.abs(BuildingDesigns.unpackX(key)) + Math.abs(BuildingDesigns.unpackZ(key)))
                .thenComparingLong(key -> key));
        int n = (int) Math.round(Math.clamp(fraction, 0, 1) * changing.size());
        Map<Long, BlockState> result = new HashMap<>(a);
        for (int k = 0; k < n; k++) {
            long key = changing.get(k);
            result.put(key, stateOr(b, key));
        }
        return result;
    }

    private static @Nullable BlockState stateOr(Map<Long, BlockState> view, long key) {
        BlockState state = view.get(key);
        if (state != null) {
            return state;
        }
        return BuildingDesigns.unpackY(key) >= 1 ? Blocks.AIR.defaultBlockState() : null;
    }

    /**
     * 照数值盖这块地基：样子没变就不动；在等的时候按进度长出来一部分。返回这次放了方块没有。
     *
     * @param force 不管上次盖成什么样，整块重新对一遍（区块刚加载、开发脚本）
     */
    static boolean sync(ServerLevel level, SettlementData.Entry entry, int index, long now, boolean force) {
        Settlement s = entry.settlement();
        if (entry.plotY()[index] == Integer.MIN_VALUE) {
            return false;
        }
        Frame frame = frame(entry, index);
        int half = SettlementLayout.PLOT_HALF;
        if (!level.isLoaded(frame.at(-half, 0, -half)) || !level.isLoaded(frame.at(half, 0, half)) || !level.isLoaded(frame.at(-half, 0, half))
                || !level.isLoaded(frame.at(half, 0, -half))) {
            return false;
        }
        Plot plot = s.plot(index);
        if (plot.type() == BuildingType.FISHING_POND && entry.ponds()[index] == null) {
            notePond(level, entry, index);
        }
        State current = State.of(plot);
        boolean waiting = plot.readyAt() > 0;
        State next = waiting ? current.next(plot) : current;
        int step = waiting ? (int) Math.floor(plot.progress(now) * STEPS) : STEPS;
        int key = Objects.hash(signature(current, s.seed()), signature(next, s.seed()), step);
        String id = s.id() + "#" + index;
        Integer applied = APPLIED.get(id);
        if (!force && applied != null && applied == key) {
            return false;
        }
        if (!force && applied == null && !waiting && entry.shown()[index] == key) {
            APPLIED.put(id, key);
            return false;
        }
        Map<Long, BlockState> view = view(s, current);
        if (waiting && !next.equals(current)) {
            view = blend(view, view(s, next), step / (double) STEPS);
        }
        apply(level, frame, view);
        if (plot.type() == BuildingType.FISHING_POND) {
            notePond(level, entry, index);
        }
        APPLIED.put(id, key);
        if (!waiting) {
            entry.shown()[index] = key;
            SettlementData.get(level.getServer()).changed();
        }
        return true;
    }

    /** 忘掉上一次盖成的样子（聚落被删、测试收尾）。 */
    static void forget(String settlementId) {
        APPLIED.keySet().removeIf(id -> id.startsWith(settlementId + "#"));
    }

    private static void apply(ServerLevel level, Frame frame, Map<Long, BlockState> view) {
        int half = SettlementLayout.PLOT_HALF;
        Rotation rotation = frame.rotation();
        List<BlockPos> shaped = new ArrayList<>();
        for (int by = -DEPTH; by <= HEIGHT; by++) {
            for (int bx = -half; bx <= half; bx++) {
                for (int bz = -half; bz <= half; bz++) {
                    BlockState wanted = view.get(BuildingDesigns.pack(bx, by, bz));
                    if (wanted == null) {
                        if (by < 1) {
                            continue;
                        }
                        wanted = Blocks.AIR.defaultBlockState();
                    }
                    wanted = wanted.rotate(rotation);
                    BlockPos pos = frame.at(bx, by, bz);
                    BlockState there = level.getBlockState(pos);
                    if (there == wanted) {
                        continue;
                    }
                    // 别人的打不坏的方块不动
                    if (there.getDestroySpeed(level, pos) < 0 && !there.is(UniverseBlocks.SETTLEMENT_TERMINAL.get())
                            && !there.is(UniverseBlocks.SETTLEMENT_TURRET.get())) {
                        continue;
                    }
                    put(level, pos, wanted);
                    if (connects(wanted)) {
                        shaped.add(pos);
                    }
                }
            }
        }
        // 栏杆、玻璃板、矮墙、台阶接上旁边的方块
        for (int by = 1; by <= HEIGHT; by++) {
            for (int bx = -half; bx <= half; bx++) {
                for (int bz = -half; bz <= half; bz++) {
                    BlockPos pos = frame.at(bx, by, bz);
                    if (connects(level.getBlockState(pos))) {
                        shaped.add(pos);
                    }
                }
            }
        }
        for (BlockPos pos : shaped) {
            BlockState state = level.getBlockState(pos);
            BlockState updated = Block.updateFromNeighbourShapes(state, level, pos);
            if (updated != state && !updated.isAir()) {
                level.setBlock(pos, updated, FLAGS);
            }
        }
    }

    private static boolean connects(BlockState state) {
        Block block = state.getBlock();
        return block instanceof CrossCollisionBlock || block instanceof WallBlock || block instanceof StairBlock;
    }

    /** 放一个方块：箱子之类的先把里面的东西倒出来。 */
    static void put(ServerLevel level, BlockPos pos, BlockState state) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof Container container && !level.getBlockState(pos).is(state.getBlock())) {
            Containers.dropContents(level, pos, container);
        }
        level.setBlock(pos, state, FLAGS);
    }

    // ---- 施工的动静

    /** 施工中的一步：几团灰。 */
    static void dust(ServerLevel level, Frame frame, int top) {
        BlockPos c = frame.at(0, Math.max(1, top / 2), 0);
        level.sendParticles(ParticleTypes.CLOUD, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, 6, 2.0, 1.5, 2.0, 0.01);
    }

    /** 开张：一轮烟花。 */
    static void fireworks(ServerLevel level, Frame frame, Palette palette) {
        int[] colors = {palette.clothA().getFireworkColor(), palette.clothB().getFireworkColor(), DyeColor.YELLOW.getFireworkColor()};
        for (int k = 0; k < 5; k++) {
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            FireworkExplosion.Shape shape = k % 2 == 0 ? FireworkExplosion.Shape.LARGE_BALL : FireworkExplosion.Shape.STAR;
            rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + k % 2, List.of(new FireworkExplosion(shape,
                    IntList.of(colors[k % colors.length], colors[(k + 1) % colors.length]), IntList.of(DyeColor.WHITE.getFireworkColor()), true,
                    k % 2 == 1))));
            int dx = (k - 2) * 2;
            BlockPos at = frame.at(dx, 1, -SettlementLayout.PLOT_HALF);
            level.addFreshEntity(new FireworkRocketEntity(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, rocket));
        }
    }

    /** 这块地基上建筑的中心偏上（海盗扫射瞄的地方）。 */
    static BlockPos center(SettlementData.Entry entry, int index) {
        return frame(entry, index).at(0, 3, 0);
    }

    /** 防御炮台的炮塔在哪。 */
    static BlockPos turret(SettlementData.Entry entry, int index) {
        return frame(entry, index).at(0, 3, 0);
    }

    /** 鱼塘的水底那一格（钓鱼按这里的水域挑鱼）。 */
    static BlockPos pond(SettlementData.Entry entry, int index) {
        return frame(entry, index).at(0, -1, 0);
    }

    /**
     * 每天出鱼按的水：鱼塘的区块加载着就现读，顺手记下；没加载用记下的，免得每天结算时为了读水把区块同步加载起来；
     * 从来没记过才现读。读不到水当是淡水的池塘。
     */
    static Fishing.Water pondWater(ServerLevel level, SettlementData.Entry entry, int index) {
        BlockPos pos = pond(entry, index);
        Fishing.Water cached = entry.ponds()[index];
        if (cached != null && !level.isLoaded(pos)) {
            return cached;
        }
        Optional<Fishing.Water> water = Fishing.water(level, pos);
        water.ifPresent(w -> remember(level, entry, index, w));
        return water.orElse(Fishing.POND_WATER);
    }

    /** 记下鱼塘现在的水（区块要加载着）；还没灌水就不动。 */
    private static void notePond(ServerLevel level, SettlementData.Entry entry, int index) {
        Fishing.water(level, pond(entry, index)).ifPresent(water -> remember(level, entry, index, water));
    }

    private static void remember(ServerLevel level, SettlementData.Entry entry, int index, Fishing.Water water) {
        if (!water.equals(entry.ponds()[index])) {
            entry.ponds()[index] = water;
            SettlementData.get(level.getServer()).changed();
        }
    }

    /** 施工终端在哪。 */
    static BlockPos terminalPos(SettlementData.Entry entry, int index) {
        return frame(entry, index).at(2, 1, -SettlementLayout.PLOT_HALF);
    }
}
