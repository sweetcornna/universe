package io.github.sweetcornna.universe.factory.belt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.IntPredicate;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.factory.BeltGraph;
import io.github.sweetcornna.universe.core.factory.BeltLine;
import io.github.sweetcornna.universe.core.factory.BeltOutput;
import io.github.sweetcornna.universe.core.factory.BeltSystem;
import io.github.sweetcornna.universe.core.factory.BeltTier;
import io.github.sweetcornna.universe.core.factory.Cargo;
import io.github.sweetcornna.universe.core.factory.Proliferation;
import io.github.sweetcornna.universe.network.BeltLinesPayload;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * 一个世界里的传送带（ADR-0011）：包着核心层的 {@link BeltSystem}，服务端和客户端各有一份，用同一套规则从已加载的
 * 传送带建出线路，每刻各自推进。
 *
 * <p>方块实体加载时登记、卸载或拆掉时退出；增删只记下"脏"的位置，下一刻开头一起重建：把受影响的线路拆成每格的
 * 物品，按 {@link BeltGraph} 重新串链，再拼回去。
 *
 * <p>服务端在线路有外部变化（放上、拿走、重建、尽头的储物仓满了或空出来）时，把整条线路的快照发给看得见它的玩家，
 * 同一条线路至少隔几刻发一次，另外每 5 秒全部校正一次。快照按方块位置对应，两边的区块加载情况不同也不要紧。
 */
public final class BeltNetwork {
    public static final int UNITS = BeltLine.UNITS_PER_BLOCK;
    public static final int HALF = UNITS / 2;
    /** 同一条线路两次同步之间至少隔几刻。 */
    private static final int SYNC_INTERVAL = 5;
    /** 每隔多少刻把所有线路校正一次。 */
    private static final int CORRECTION_INTERVAL = 100;

    private static final Map<Level, BeltNetwork> LEVELS = new WeakHashMap<>();

    private final Level level;
    private final BeltSystem system = new BeltSystem();
    private final Long2ObjectMap<Segment> segments = new Long2ObjectOpenHashMap<>();
    private final Map<BeltLine, LineInfo> lines = new IdentityHashMap<>();
    /** 要重建的位置（增删的格子和它们的出入口）。 */
    private final LongSet dirty = new LongOpenHashSet();
    /** 出口前面的方块变了（放下或拆掉储物仓），要重新看去处的位置。 */
    private final LongSet outputsDirty = new LongOpenHashSet();
    /** 还没串进线路的格子上的物品。 */
    private final Long2ObjectMap<BlockItems> pending = new Long2ObjectOpenHashMap<>();
    private final Set<LineInfo> unsynced = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<LineInfo> withSink = Collections.newSetFromMap(new IdentityHashMap<>());
    private final BeltGraph.Lookup<Segment> lookup = (x, y, z) -> this.segments.get(BlockPos.asLong(x, y, z));
    private long ticks;

    private BeltNetwork(Level level) {
        this.level = level;
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(LevelTickEvent.Pre.class, event -> {
            Level level = event.getLevel();
            if (level.tickRateManager().runsNormally()) {
                BeltNetwork network = LEVELS.get(level);
                if (network != null) {
                    long start = ServerPerf.begin();
                    network.tick();
                    ServerPerf.end("belts", start);
                }
            }
        });
        gameBus.addListener(LevelEvent.Unload.class, event -> {
            if (event.getLevel() instanceof Level level) {
                LEVELS.remove(level);
            }
        });
    }

    public static BeltNetwork of(Level level) {
        return LEVELS.computeIfAbsent(level, BeltNetwork::new);
    }

    /** 已经有的时候返回它；退出时用，免得给正在卸载的世界又建一个。 */
    public static @Nullable BeltNetwork existing(Level level) {
        return LEVELS.get(level);
    }

    // ---- 货物（ADR-0022）

    /** 能放上传送带的物品：不带组件（附魔、耐久、名字……），增产点数除外。 */
    public static boolean canCarry(ItemStack stack) {
        return !stack.isEmpty() && carriable(stack.getComponentsPatch());
    }

    public static boolean canCarry(ItemResource resource) {
        return !resource.isEmpty() && carriable(resource.getComponentsPatch());
    }

    private static boolean carriable(DataComponentPatch patch) {
        return patch.isEmpty() || patch.size() == 1 && patch.getPatch(UniverseDataComponents.PROLIFERATION.get()) != null;
    }

    /** 一个没喷过的物品的货物，就是它的注册表编号。 */
    public static int id(Item item) {
        return BuiltInRegistries.ITEM.getId(item);
    }

    /** 货物是哪种物品。 */
    public static Item item(int cargo) {
        return BuiltInRegistries.ITEM.byId(Cargo.item(cargo));
    }

    /** 物品堆上每个物品的增产点数。 */
    public static int level(ItemStack stack) {
        return stack.getOrDefault(UniverseDataComponents.PROLIFERATION.get(), 0);
    }

    /** 物品堆里的前 {@code count} 个（最多 {@link Cargo#MAX_STACK} 个）合成一格货物；先用 {@link #canCarry} 看能不能上带。 */
    public static int cargo(ItemStack stack, int count) {
        return Cargo.uniform(id(stack.getItem()), Math.min(count, stack.getCount()), level(stack));
    }

    public static int cargo(ItemStack stack) {
        return cargo(stack, stack.getCount());
    }

    /**
     * 几堆同一种物品合成一格货物（分拣器手里的）。
     *
     * @return 种类不同、带别的组件或者超过 {@link Cargo#MAX_STACK} 个时返回 {@link BeltLine#EMPTY}
     */
    public static int cargo(List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return BeltLine.EMPTY;
        }
        Item type = stacks.getFirst().getItem();
        int count = 0;
        int points = 0;
        for (ItemStack stack : stacks) {
            if (!canCarry(stack) || stack.getItem() != type) {
                return BeltLine.EMPTY;
            }
            count += stack.getCount();
            points += stack.getCount() * level(stack);
        }
        return count > Cargo.MAX_STACK ? BeltLine.EMPTY : Cargo.of(id(type), count, points);
    }

    /** {@code count} 个物品，每个 {@code level} 点。 */
    public static ItemStack stack(Item item, int count, int level) {
        ItemStack stack = new ItemStack(item, count);
        if (level > 0) {
            stack.set(UniverseDataComponents.PROLIFERATION.get(), Math.min(level, Proliferation.MAX_LEVEL));
        }
        return stack;
    }

    /** 一格货物变回物品堆：点数分不匀时分成两堆，多一点的在前（点数一点不丢）。 */
    public static List<ItemStack> stacks(int cargo) {
        Item item = item(cargo);
        int count = Cargo.count(cargo);
        int points = Cargo.points(cargo);
        int low = points / count;
        int high = points % count;
        if (high == 0) {
            return List.of(stack(item, count, low));
        }
        return List.of(stack(item, high, low + 1), stack(item, count - high, low));
    }

    /** 画出来的样子：一个物品，喷过的带着点数。 */
    public static ItemStack display(int cargo) {
        return stack(item(cargo), 1, Cargo.level(cargo));
    }

    // ---- 登记

    /** 一格传送带加载或放下。 */
    public void add(BlockPos pos, BlockState state, @Nullable BlockItems items) {
        long key = pos.asLong();
        if (this.segments.containsKey(key)) {
            this.remove(pos);
        }
        Segment segment = new Segment(pos.immutable(), state);
        this.segments.put(key, segment);
        if (items != null && !items.isEmpty()) {
            this.pending.put(key, items);
        }
        this.markAround(segment);
    }

    /**
     * 一格传送带拆掉或卸载。
     *
     * @return 这格上的物品
     */
    public @Nullable BlockItems remove(BlockPos pos) {
        long key = pos.asLong();
        Segment segment = this.segments.get(key);
        if (segment == null) {
            return this.pending.remove(key);
        }
        if (segment.line != null) {
            // 整条线路拆开了：其余的格子也要重新串，不然它们一直挂在 pending 里不动
            LineInfo info = segment.line;
            this.dissolve(info);
            for (Segment other : info.blocks) {
                this.dirty.add(other.pos.asLong());
            }
        }
        this.segments.remove(key);
        this.markAround(segment);
        return this.pending.remove(key);
    }

    /** 朝向或坡度变了：带着物品重新登记。 */
    public void update(BlockPos pos, BlockState state) {
        Segment segment = this.segments.get(pos.asLong());
        if (segment != null && segment.matches(state)) {
            return;
        }
        BlockItems items = this.remove(pos);
        this.add(pos, state, items);
    }

    /** 出口前面的方块变了（储物仓放下或拆掉）。 */
    public void outputChanged(BlockPos pos) {
        this.outputsDirty.add(pos.asLong());
    }

    /** 本格、本格上面（下坡的入口）、出口和出口下面（下坡）都要重新看。 */
    private void markAround(Segment segment) {
        this.dirty.add(segment.pos.asLong());
        this.dirty.add(segment.pos.above().asLong());
        int[] e = BeltGraph.exit(segment);
        this.dirty.add(BlockPos.asLong(e[0], e[1], e[2]));
        this.dirty.add(BlockPos.asLong(e[0], e[1] - 1, e[2]));
    }

    public @Nullable Segment segment(BlockPos pos) {
        return this.segments.get(pos.asLong());
    }

    // ---- 物品

    /** 这格上的物品（存档用），位置相对本格的起点。 */
    public @Nullable BlockItems itemsAt(BlockPos pos) {
        long key = pos.asLong();
        Segment segment = this.segments.get(key);
        if (segment == null || segment.line == null) {
            return this.pending.get(key);
        }
        return segment.items();
    }

    /** 在这格的 {@code at}（0–{@link #UNITS}）处放一格货物。 */
    public boolean insertAt(BlockPos pos, int at, int cargo) {
        this.rebuildIfDirty();
        Segment segment = this.segments.get(pos.asLong());
        if (segment == null || segment.line == null) {
            return false;
        }
        if (!segment.line.line.insert(segment.offset() + at, cargo)) {
            return false;
        }
        this.markChanged(segment.line);
        return true;
    }

    public boolean canInsertAt(BlockPos pos, int at) {
        this.rebuildIfDirty();
        Segment segment = this.segments.get(pos.asLong());
        return segment != null && segment.line != null && segment.line.line.canInsert(segment.offset() + at);
    }

    /**
     * 拿走这格上离中间最近、符合 {@code filter} 的一格货物（分拣器）。
     *
     * @return 货物；没有返回 {@link BeltLine#EMPTY}
     */
    public int take(BlockPos pos, IntPredicate filter) {
        this.rebuildIfDirty();
        Segment segment = this.segments.get(pos.asLong());
        if (segment == null || segment.line == null) {
            return BeltLine.EMPTY;
        }
        int item = segment.line.line.take(segment.offset() + HALF, HALF, filter);
        if (item != BeltLine.EMPTY) {
            this.markChanged(segment.line);
        }
        return item;
    }

    /** 拿走这格上的所有物品（玩家空手右键）。 */
    public List<ItemStack> takeAll(BlockPos pos) {
        this.rebuildIfDirty();
        Segment segment = this.segments.get(pos.asLong());
        if (segment == null || segment.line == null) {
            return List.of();
        }
        BlockItems items = segment.items();
        if (items.isEmpty()) {
            return List.of();
        }
        LineInfo info = segment.line;
        int from = segment.offset();
        boolean last = segment.index == info.blocks.length - 1;
        info.line.replaceRange(from, last ? info.line.length() : from + UNITS, new int[0], new int[0]);
        this.markChanged(info);
        List<ItemStack> out = new ArrayList<>(items.items().length);
        for (int cargo : items.items()) {
            out.addAll(stacks(cargo));
        }
        return out;
    }

    private void markChanged(LineInfo info) {
        if (this.level instanceof ServerLevel) {
            this.unsynced.add(info);
        }
    }

    // ---- 每刻

    private void tick() {
        this.ticks++;
        this.rebuildIfDirty();
        this.system.tick();
        if (this.level instanceof ServerLevel serverLevel) {
            for (LineInfo info : this.withSink) {
                Sink sink = info.sink;
                if (sink != null && sink.blocked != sink.reported) {
                    sink.reported = sink.blocked;
                    this.unsynced.add(info);
                }
            }
            if (this.ticks % CORRECTION_INTERVAL == 0) {
                this.unsynced.addAll(this.lines.values());
            }
            this.flushSync(serverLevel);
        }
    }

    // ---- 重建

    private void rebuildIfDirty() {
        if (!this.dirty.isEmpty() || !this.outputsDirty.isEmpty()) {
            this.rebuild();
        }
    }

    private void rebuild() {
        // 受影响的格子：脏位置上的、出口落在脏位置的、它们前面的，以及这些格子所在线路的全部
        Set<Segment> seeds = Collections.newSetFromMap(new IdentityHashMap<>());
        for (long key : this.dirty) {
            BlockPos pos = BlockPos.of(key);
            seeds.addAll(BeltGraph.feeders(pos.getX(), pos.getY(), pos.getZ(), this.lookup));
            Segment segment = this.segments.get(key);
            if (segment != null) {
                seeds.add(segment);
                int[] e = BeltGraph.exit(segment);
                this.addIfPresent(seeds, e[0], e[1], e[2]);
                this.addIfPresent(seeds, e[0], e[1] - 1, e[2]);
                seeds.addAll(BeltGraph.feeders(e[0], e[1], e[2], this.lookup));
            }
        }
        this.dirty.clear();
        Set<Segment> touched = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<LineInfo> dissolved = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Segment segment : seeds) {
            if (segment.line != null) {
                dissolved.add(segment.line);
            } else {
                touched.add(segment);
            }
        }
        for (LineInfo info : dissolved) {
            Collections.addAll(touched, info.blocks);
            this.dissolve(info);
        }

        // 重新串链
        List<LineInfo> built = new ArrayList<>();
        for (BeltGraph.Chain<Segment> chain : BeltGraph.chains(touched, this.lookup)) {
            built.add(this.build(chain));
        }

        // 去处：新线路、尽头接到受影响格子上的旧线路、出口前面换了方块的线路
        Set<LineInfo> outputs = Collections.newSetFromMap(new IdentityHashMap<>());
        outputs.addAll(built);
        for (Segment segment : touched) {
            this.addEnds(outputs, segment.pos.getX(), segment.pos.getY(), segment.pos.getZ());
            int[] entry = BeltGraph.entry(segment);
            this.addEnds(outputs, entry[0], entry[1], entry[2]);
        }
        for (long key : this.outputsDirty) {
            BlockPos pos = BlockPos.of(key);
            this.addEnds(outputs, pos.getX(), pos.getY(), pos.getZ());
        }
        this.outputsDirty.clear();
        for (LineInfo info : outputs) {
            if (this.lines.get(info.line) == info) {
                this.connect(info);
            }
        }

        if (this.level instanceof ServerLevel serverLevel) {
            this.unsynced.addAll(built);
            // 弯道的外观跟着线路走
            for (Segment segment : touched) {
                this.updateCurve(serverLevel, segment);
            }
        }
    }

    private void addIfPresent(Set<Segment> set, int x, int y, int z) {
        Segment segment = this.lookup.at(x, y, z);
        if (segment != null) {
            set.add(segment);
        }
    }

    /** 出口落在 (x, y, z)、又是线路尽头的那些线路。 */
    private void addEnds(Set<LineInfo> out, int x, int y, int z) {
        for (Segment feeder : BeltGraph.feeders(x, y, z, this.lookup)) {
            LineInfo info = feeder.line;
            if (info != null && info.blocks[info.blocks.length - 1] == feeder) {
                out.add(info);
            }
        }
    }

    /** 拆掉一条线路，物品按格放回 {@link #pending}。 */
    private void dissolve(LineInfo info) {
        int n = info.blocks.length;
        int[] positions = info.line.positions();
        int[] items = info.line.items();
        List<List<int[]>> perBlock = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            perBlock.add(new ArrayList<>());
        }
        for (int k = 0; k < positions.length; k++) {
            int block = Math.min(positions[k] / UNITS, n - 1);
            perBlock.get(block).add(new int[] {positions[k] - block * UNITS, items[k]});
        }
        for (int i = 0; i < n; i++) {
            Segment segment = info.blocks[i];
            segment.line = null;
            segment.index = 0;
            BlockItems items1 = BlockItems.of(perBlock.get(i));
            if (!items1.isEmpty()) {
                this.pending.put(segment.pos.asLong(), items1);
            }
        }
        this.system.remove(info.line);
        this.lines.remove(info.line);
        this.unsynced.remove(info);
        this.withSink.remove(info);
    }

    private LineInfo build(BeltGraph.Chain<Segment> chain) {
        List<Segment> nodes = chain.nodes();
        int n = nodes.size();
        BeltTier tier = nodes.get(0).tier;
        // 物品按位置从前往后（从大到小）排
        List<int[]> all = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            BlockItems items = this.pending.remove(nodes.get(i).pos.asLong());
            if (items != null) {
                for (int k = 0; k < items.positions().length; k++) {
                    all.add(new int[] {items.positions()[k] + i * UNITS, items.items()[k]});
                }
            }
        }
        all.sort((a, b) -> Integer.compare(b[0], a[0]));
        int[] positions = new int[all.size()];
        int[] items = new int[all.size()];
        for (int k = 0; k < all.size(); k++) {
            positions[k] = all.get(k)[0];
            items[k] = all.get(k)[1];
        }
        BeltLine line = BeltLine.restore(n * UNITS, tier.speed(), positions, items, new int[0]);
        LineInfo info = new LineInfo(line, nodes.toArray(new Segment[0]), chain.loop());
        for (int i = 0; i < n; i++) {
            nodes.get(i).line = info;
            nodes.get(i).index = i;
        }
        this.system.add(line);
        this.lines.put(line, info);
        return info;
    }

    /** 线路尽头交给谁：下一条线路、侧面汇入，或者正前方的储物仓。 */
    private void connect(LineInfo info) {
        Segment end = info.blocks[info.blocks.length - 1];
        BeltOutput output = null;
        info.sink = null;
        this.withSink.remove(info);
        BeltGraph.Target<Segment> target = BeltGraph.target(end, this.lookup);
        if (target != null) {
            Segment next = target.belt();
            if (next.line != null) {
                // 环线的尾巴接回起点；弯道虽然是从侧面进的，也接在那格的起点
                boolean joined = BeltGraph.previous(next, this.lookup) == end;
                output = BeltOutput.into(next.line.line, next.offset() + (target.side() && !joined ? HALF : 0));
            }
        } else {
            int[] e = BeltGraph.exit(end);
            BlockPos pos = new BlockPos(e[0], e[1], e[2]);
            if (this.level.isLoaded(pos) && this.level.getBlockEntity(pos) instanceof BeltReceiver) {
                Sink sink = new Sink(pos);
                info.sink = sink;
                this.withSink.add(info);
                output = sink;
            }
        }
        this.system.connect(info.line, output);
    }

    private void updateCurve(ServerLevel level, Segment segment) {
        BlockState state = level.getBlockState(segment.pos);
        if (!(state.getBlock() instanceof BeltBlock)) {
            return;
        }
        BeltCurve curve = BeltCurve.of(BeltGraph.curve(segment, this.lookup));
        if (state.getValue(BeltBlock.CURVE) != curve) {
            level.setBlock(segment.pos, state.setValue(BeltBlock.CURVE, curve), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    // ---- 同步

    private void flushSync(ServerLevel level) {
        if (this.unsynced.isEmpty()) {
            return;
        }
        Map<ServerPlayer, List<BeltLinesPayload.Line>> out = new HashMap<>();
        Iterator<LineInfo> it = this.unsynced.iterator();
        while (it.hasNext()) {
            LineInfo info = it.next();
            if (this.ticks - info.lastSync < SYNC_INTERVAL) {
                continue;
            }
            it.remove();
            info.lastSync = this.ticks;
            Set<ServerPlayer> players = new HashSet<>();
            for (long chunk : info.chunks) {
                players.addAll(level.getChunkSource().chunkMap.getPlayers(ChunkPos.unpack(chunk), false));
            }
            if (players.isEmpty()) {
                continue;
            }
            BeltLinesPayload.Line snapshot = this.snapshot(info);
            for (ServerPlayer player : players) {
                out.computeIfAbsent(player, p -> new ArrayList<>()).add(snapshot);
            }
        }
        out.forEach((player, list) -> PacketDistributor.sendToPlayer(player, new BeltLinesPayload(list)));
    }

    private BeltLinesPayload.Line snapshot(LineInfo info) {
        byte[] steps = new byte[info.blocks.length - 1];
        for (int k = 1; k < info.blocks.length; k++) {
            BlockPos a = info.blocks[k - 1].pos;
            BlockPos b = info.blocks[k].pos;
            steps[k - 1] = BeltLinesPayload.step(b.getX() - a.getX(), b.getY() - a.getY(), b.getZ() - a.getZ());
        }
        return new BeltLinesPayload.Line(info.blocks[0].pos.asLong(), steps, info.line.positions(), info.line.items(),
                info.sink != null && info.sink.blocked);
    }

    /** 客户端：按服务端的快照校正。快照里的格子按位置找到自己的线路，一段一段换掉。 */
    public void apply(BeltLinesPayload.Line snapshot) {
        this.rebuildIfDirty();
        int n = snapshot.steps().length + 1;
        BlockPos[] blocks = new BlockPos[n];
        blocks[0] = BlockPos.of(snapshot.start());
        for (int k = 1; k < n; k++) {
            int[] d = BeltLinesPayload.delta(snapshot.steps()[k - 1]);
            blocks[k] = blocks[k - 1].offset(d[0], d[1], d[2]);
        }
        int k = 0;
        while (k < n) {
            Segment first = this.segments.get(blocks[k].asLong());
            if (first == null || first.line == null) {
                k++;
                continue;
            }
            LineInfo info = first.line;
            int k0 = k;
            while (k + 1 < n) {
                Segment next = this.segments.get(blocks[k + 1].asLong());
                if (next == null || next.line != info || next.index != first.index + (k + 1 - k0)) {
                    break;
                }
                k++;
            }
            boolean serverEnd = k == n - 1;
            boolean clientEnd = first.index + (k - k0) == info.blocks.length - 1;
            int from = first.offset();
            int to = clientEnd ? info.line.length() : (first.index + k - k0 + 1) * UNITS;
            int lo = k0 * UNITS;
            int hi = (k + 1) * UNITS;
            int count = 0;
            int[] p = new int[snapshot.positions().length];
            int[] it = new int[p.length];
            for (int i = 0; i < Math.min(snapshot.positions().length, snapshot.items().length); i++) {
                int position = snapshot.positions()[i];
                if (position >= lo && (position < hi || serverEnd && position == hi)) {
                    p[count] = position - lo + from;
                    it[count++] = snapshot.items()[i];
                }
            }
            info.line.replaceRange(from, to, Arrays.copyOf(p, count), Arrays.copyOf(it, count));
            if (serverEnd && clientEnd && info.sink != null) {
                info.sink.blocked = snapshot.blocked();
            }
            k++;
        }
    }

    // ---- 数据

    /** 一格上的物品：位置相对本格的起点（0–{@link #UNITS}），和货物（{@link Cargo}）一一对应。 */
    public record BlockItems(int[] positions, int[] items) {
        public static final BlockItems EMPTY = new BlockItems(new int[0], new int[0]);

        static BlockItems of(List<int[]> pairs) {
            if (pairs.isEmpty()) {
                return EMPTY;
            }
            int[] positions = new int[pairs.size()];
            int[] items = new int[pairs.size()];
            for (int i = 0; i < pairs.size(); i++) {
                positions[i] = pairs.get(i)[0];
                items[i] = pairs.get(i)[1];
            }
            return new BlockItems(positions, items);
        }

        public boolean isEmpty() {
            return this.positions.length == 0;
        }
    }

    /** 一格传送带。 */
    public static final class Segment implements BeltGraph.Node {
        final BlockPos pos;
        final int facing;
        final BeltGraph.Slope slope;
        final BeltTier tier;
        @Nullable LineInfo line;
        int index;

        Segment(BlockPos pos, BlockState state) {
            this.pos = pos;
            this.facing = state.getValue(BeltBlock.FACING).get2DDataValue();
            this.slope = state.getValue(BeltBlock.SLOPE).core();
            this.tier = state.getBlock() instanceof BeltBlock belt ? belt.tier() : BeltTier.MK1;
        }

        boolean matches(BlockState state) {
            return state.getBlock() instanceof BeltBlock belt && belt.tier() == this.tier
                    && state.getValue(BeltBlock.FACING).get2DDataValue() == this.facing && state.getValue(BeltBlock.SLOPE).core() == this.slope;
        }

        int offset() {
            return this.index * UNITS;
        }

        /** 这格在线路上的物品；渲染器用 {@link #line()}、{@link #from()} 自己读。 */
        BlockItems items() {
            LineInfo info = this.line;
            if (info == null) {
                return BlockItems.EMPTY;
            }
            List<int[]> pairs = new ArrayList<>();
            int from = this.offset();
            boolean last = this.index == info.blocks.length - 1;
            BeltLine beltLine = info.line;
            for (int i = 0; i < beltLine.count(); i++) {
                int p = beltLine.position(i);
                if (p >= from && (p < from + UNITS || last && p == from + UNITS)) {
                    pairs.add(new int[] {p - from, beltLine.item(i)});
                }
            }
            return BlockItems.of(pairs);
        }

        /** 所在的线路；还没串好时为空。 */
        public @Nullable BeltLine line() {
            return this.line == null ? null : this.line.line;
        }

        /** 这格在线路上的起点（单位）。 */
        public int from() {
            return this.offset();
        }

        /** 是线路的最后一格（停在尽头的物品算这格的）。 */
        public boolean last() {
            return this.line != null && this.index == this.line.blocks.length - 1;
        }

        @Override
        public int x() {
            return this.pos.getX();
        }

        @Override
        public int y() {
            return this.pos.getY();
        }

        @Override
        public int z() {
            return this.pos.getZ();
        }

        @Override
        public int facing() {
            return this.facing;
        }

        @Override
        public BeltGraph.Slope slope() {
            return this.slope;
        }

        @Override
        public BeltTier tier() {
            return this.tier;
        }
    }

    /** 一条线路和它的格子。 */
    static final class LineInfo {
        final BeltLine line;
        final Segment[] blocks;
        final boolean loop;
        /** 经过的区块，同步时找看得见的玩家。 */
        final long[] chunks;
        @Nullable Sink sink;
        long lastSync = Long.MIN_VALUE / 2;

        LineInfo(BeltLine line, Segment[] blocks, boolean loop) {
            this.line = line;
            this.blocks = blocks;
            this.loop = loop;
            LongSet chunks = new LongOpenHashSet();
            for (Segment segment : blocks) {
                chunks.add(ChunkPos.pack(segment.pos));
            }
            this.chunks = chunks.toLongArray();
        }
    }

    /**
     * 线路尽头的储物仓。服务端真的往里放，记下放不下（堵住）的状态；客户端不知道储物仓里有什么，按服务端同步过来的
     * 堵住状态决定收不收。
     */
    final class Sink implements BeltOutput {
        final BlockPos pos;
        boolean blocked;
        boolean reported;

        Sink(BlockPos pos) {
            this.pos = pos;
        }

        @Override
        public boolean accept(int cargo, int overshoot) {
            Level level = BeltNetwork.this.level;
            if (!(level instanceof ServerLevel)) {
                return !this.blocked;
            }
            boolean accepted = level.getBlockEntity(this.pos) instanceof BeltReceiver receiver && receiver.acceptFromBelt(cargo);
            this.blocked = !accepted;
            return accepted;
        }
    }
}
