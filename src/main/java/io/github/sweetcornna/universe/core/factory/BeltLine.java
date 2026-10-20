package io.github.sweetcornna.universe.core.factory;

import java.util.function.IntPredicate;

import org.jspecify.annotations.Nullable;

/**
 * 一条传送带线路：首尾相接、方向一致、同一档速度的一串传送带合成一条，物品的位置存在紧凑的环形数组里
 * （技术架构 §3.6）。
 *
 * <p>位置以单位计，0 是线路的起点，{@link #length()} 是尽头，一格 {@link #UNITS_PER_BLOCK} 个单位。物品按离尽头
 * 由近到远排列，相邻两个至少隔 {@link #SPACING}。每刻 {@link #tick()} 整体推进一次：最前面的物品走到尽头就交给
 * {@link #output()}，交不出去就停在尽头，后面的依次挤上来。
 *
 * <p>线路只管物品怎么走；哪几格传送带合成一条、去处接到哪里，由适配层在方块增删时重建（{@link #split}、
 * {@link #concat}）。
 */
public final class BeltLine {
    /** 一格传送带的长度（单位）。 */
    public static final int UNITS_PER_BLOCK = 200;
    /** 相邻两个物品的最小间距：一格最多放 5 个。 */
    public static final int SPACING = 40;
    /** {@link #take} 没拿到东西。 */
    public static final int EMPTY = -1;

    private final int length;
    private int speed;
    private @Nullable BeltOutput output;
    // 环形数组：第 i 个物品（0 是最前面的）在 (head + i) % capacity
    private final int[] positions;
    private final int[] items;
    private int head;
    private int count;
    // 在起点排队等着上带的物品（分拣器、设备交过来的），每刻推进完再排上去
    private final int[] waiting = new int[2];
    private int waitingCount;

    public BeltLine(int blocks, BeltTier tier) {
        this(blocks * UNITS_PER_BLOCK, tier.speed());
    }

    /**
     * @param length 长度（单位）
     * @param speed  每刻前进的单位数
     */
    public BeltLine(int length, int speed) {
        if (length <= 0 || speed < 0) {
            throw new IllegalArgumentException("length " + length + ", speed " + speed);
        }
        this.length = length;
        this.speed = speed;
        // 满载时有 length / SPACING + 1 个，自己接回自己的环线交接时会暂时多一个
        int capacity = length / SPACING + 2;
        this.positions = new int[capacity];
        this.items = new int[capacity];
    }

    public int length() {
        return this.length;
    }

    public int speed() {
        return this.speed;
    }

    public void setSpeed(int speed) {
        this.speed = Math.max(0, speed);
    }

    public @Nullable BeltOutput output() {
        return this.output;
    }

    /** 改去处要经过 {@link BeltSystem#connect}，它据此重排结算顺序。 */
    void setOutput(@Nullable BeltOutput output) {
        this.output = output;
    }

    /** 线路上的物品数（不含在起点排队的）。 */
    public int count() {
        return this.count;
    }

    /** 在起点排队、还没上带的物品数。 */
    public int waitingCount() {
        return this.waitingCount;
    }

    public boolean isEmpty() {
        return this.count == 0;
    }

    /** 第 {@code i} 个物品（0 是最前面的）的位置。 */
    public int position(int i) {
        return this.positions[this.slot(i)];
    }

    /** 第 {@code i} 个物品（0 是最前面的）的编号。 */
    public int item(int i) {
        return this.items[this.slot(i)];
    }

    /** 第一个位置小于 {@code position} 的物品是第几个，没有就是 count。位置从前往后递减，二分查找。 */
    private int firstBelow(int position) {
        int low = 0;
        int high = this.count;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (this.position(mid) < position) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }
        return low;
    }

    private int slot(int i) {
        int s = this.head + i;
        return s >= this.positions.length ? s - this.positions.length : s;
    }

    // ---- 推进

    /** 推进一刻。 */
    public void tick() {
        if (this.count == 0 && this.waitingCount == 0) {
            return;
        }
        BeltOutput out = this.output;
        if (out != null) {
            // 走到尽头的交出去；快的传送带一刻可能交出不止一个
            while (this.count > 0) {
                int p = this.positions[this.head] + this.speed;
                if (p < this.length || !out.accept(this.items[this.head], p - this.length)) {
                    break;
                }
                this.removeHead();
            }
        }
        int limit = this.length;
        int capacity = this.positions.length;
        int s = this.head;
        for (int i = 0; i < this.count; i++) {
            int p = this.positions[s] + this.speed;
            if (p > limit) {
                p = limit;
            }
            this.positions[s] = p;
            limit = p - SPACING;
            if (++s == capacity) {
                s = 0;
            }
        }
        // 排队的物品上带：就像这一刻开头放在起点、跟着走了一步
        while (this.waitingCount > 0 && this.count < capacity) {
            int p = Math.min(Math.min(this.speed, this.length), limit);
            if (p < 0) {
                break;
            }
            this.append(p, this.waiting[0]);
            limit = p - SPACING;
            this.waitingCount--;
            System.arraycopy(this.waiting, 1, this.waiting, 0, this.waitingCount);
        }
    }

    // ---- 放上、拿走

    /** 这个位置能不能放一个物品（前后都留得出间距）。 */
    public boolean canInsert(int position) {
        return this.indexFor(position) >= 0;
    }

    /**
     * 在 {@code position} 放一个物品（上游线路交过来、侧面汇入、分拣器放上来）。
     *
     * @return 放不下（太挤或者越界）返回 false
     */
    public boolean insert(int position, int item) {
        int k = this.indexFor(position);
        if (k < 0) {
            return false;
        }
        int capacity = this.positions.length;
        // 第 k 个及以后的往后挪一位
        for (int j = this.count; j > k; j--) {
            int to = this.slot(j);
            int from = to == 0 ? capacity - 1 : to - 1;
            this.positions[to] = this.positions[from];
            this.items[to] = this.items[from];
        }
        int s = this.slot(k);
        this.positions[s] = position;
        this.items[s] = item;
        this.count++;
        return true;
    }

    /**
     * 从起点放上一个物品（分拣器、设备往线路起点供货）。物品先在起点排队，这一刻推进完、起点让出间距就上带，
     * 所以每刻都供货时吞吐正好是满载；队里最多两个，满了返回 false。
     */
    public boolean offer(int item) {
        if (this.waitingCount >= this.waiting.length) {
            return false;
        }
        this.waiting[this.waitingCount++] = item;
        return true;
    }

    /** 存档用：在起点排队的物品。 */
    public int[] waiting() {
        int[] out = new int[this.waitingCount];
        System.arraycopy(this.waiting, 0, out, 0, this.waitingCount);
        return out;
    }

    /** 放在 {@code position} 时它会是第几个；放不下返回 -1。 */
    private int indexFor(int position) {
        if (position < 0 || position > this.length || this.count >= this.positions.length) {
            return -1;
        }
        int k = this.firstBelow(position);
        if (k > 0 && this.position(k - 1) - position < SPACING) {
            return -1;
        }
        if (k < this.count && position - this.position(k) < SPACING) {
            return -1;
        }
        return k;
    }

    /**
     * 拿走离 {@code position} 最近、在 {@code window} 以内、符合 {@code filter} 的一个物品（分拣器抓取）。
     *
     * @return 物品编号；没有合适的返回 {@link #EMPTY}
     */
    public int take(int position, int window, IntPredicate filter) {
        int best = -1;
        int bestDistance = window + 1;
        for (int i = this.firstBelow(position + window + 1); i < this.count; i++) {
            int p = this.position(i);
            if (p < position - window) {
                break;
            }
            int distance = Math.abs(p - position);
            if (distance < bestDistance && filter.test(this.item(i))) {
                best = i;
                bestDistance = distance;
            }
        }
        if (best < 0) {
            return EMPTY;
        }
        int item = this.item(best);
        this.removeAt(best);
        return item;
    }

    /** 拿走停在尽头的那个物品（储物仓、设备从线路尽头取货）。 */
    public int takeAtEnd(IntPredicate filter) {
        if (this.count == 0 || this.positions[this.head] < this.length || !filter.test(this.items[this.head])) {
            return EMPTY;
        }
        int item = this.items[this.head];
        this.removeHead();
        return item;
    }

    private void removeHead() {
        this.head = this.slot(1);
        this.count--;
    }

    private void removeAt(int k) {
        if (k == 0) {
            this.removeHead();
            return;
        }
        for (int j = k; j < this.count - 1; j++) {
            int to = this.slot(j);
            int from = this.slot(j + 1);
            this.positions[to] = this.positions[from];
            this.items[to] = this.items[from];
        }
        this.count--;
    }

    /** 不检查间距地接在最后（物品已经按顺序排好时用）。 */
    private void append(int position, int item) {
        int s = this.slot(this.count);
        this.positions[s] = position;
        this.items[s] = item;
        this.count++;
    }

    // ---- 拆分、合并、存档

    /**
     * 在 {@code at} 处断开（拆掉中间一格传送带、换了一段的速度时用）。去处留在后一段上。
     *
     * @return {起点到 at，at 到尽头}
     */
    public BeltLine[] split(int at) {
        if (at <= 0 || at >= this.length) {
            throw new IllegalArgumentException("split at " + at + " of " + this.length);
        }
        BeltLine first = new BeltLine(at, this.speed);
        BeltLine second = new BeltLine(this.length - at, this.speed);
        second.output = this.output;
        System.arraycopy(this.waiting, 0, first.waiting, 0, this.waitingCount);
        first.waitingCount = this.waitingCount;
        for (int i = 0; i < this.count; i++) {
            int p = this.position(i);
            if (p >= at) {
                second.append(p - at, this.item(i));
            } else {
                first.append(p, this.item(i));
            }
        }
        return new BeltLine[] {first, second};
    }

    /** 把 {@code second} 接在 {@code first} 后面合成一条（速度取前一段的，去处取后一段的）。 */
    public static BeltLine concat(BeltLine first, BeltLine second) {
        BeltLine joined = new BeltLine(first.length + second.length, first.speed);
        joined.output = second.output;
        System.arraycopy(first.waiting, 0, joined.waiting, 0, first.waitingCount);
        joined.waitingCount = first.waitingCount;
        for (int i = 0; i < second.count; i++) {
            joined.append(second.position(i) + first.length, second.item(i));
        }
        int limit = first.length + (second.count > 0 ? second.position(second.count - 1) : Integer.MAX_VALUE / 2) - SPACING;
        for (int i = 0; i < first.count; i++) {
            // 前一段停在尽头的物品和后一段起点的物品可能挤在一起，往后让一让
            int p = Math.min(first.position(i), limit);
            if (p < 0) {
                break;
            }
            joined.append(p, first.item(i));
            limit = p - SPACING;
        }
        return joined;
    }

    /**
     * 把 [{@code from}, {@code to}) 里的物品换成给定的（客户端按服务端的快照校正时用）；{@code to} 等于线路长度时
     * 连停在尽头的一起换。给定的位置不用排好序，范围外的和挤在一起的丢掉。
     */
    public void replaceRange(int from, int to, int[] newPositions, int[] newItems) {
        boolean throughEnd = to >= this.length;
        int n = 0;
        int[] p = new int[this.count + newPositions.length];
        int[] it = new int[p.length];
        for (int i = 0; i < this.count; i++) {
            int position = this.position(i);
            boolean inside = position >= from && (position < to || throughEnd && position == this.length);
            if (!inside) {
                p[n] = position;
                it[n++] = this.item(i);
            }
        }
        for (int i = 0; i < Math.min(newPositions.length, newItems.length); i++) {
            int position = newPositions[i];
            if (position >= from && (position < to || throughEnd && position <= this.length)) {
                p[n] = position;
                it[n++] = newItems[i];
            }
        }
        // 按位置从大到小排（插入排序，物品不多）
        for (int i = 1; i < n; i++) {
            int position = p[i];
            int item = it[i];
            int j = i - 1;
            while (j >= 0 && p[j] < position) {
                p[j + 1] = p[j];
                it[j + 1] = it[j];
                j--;
            }
            p[j + 1] = position;
            it[j + 1] = item;
        }
        this.head = 0;
        this.count = 0;
        int limit = this.length;
        for (int i = 0; i < n && this.count < this.positions.length; i++) {
            if (p[i] < 0 || p[i] > limit) {
                continue;
            }
            this.append(p[i], it[i]);
            limit = p[i] - SPACING;
        }
    }

    /** 存档用：从最前面到最后面的位置。 */
    public int[] positions() {
        int[] out = new int[this.count];
        for (int i = 0; i < this.count; i++) {
            out[i] = this.position(i);
        }
        return out;
    }

    /** 存档用：从最前面到最后面的物品编号。 */
    public int[] items() {
        int[] out = new int[this.count];
        for (int i = 0; i < this.count; i++) {
            out[i] = this.item(i);
        }
        return out;
    }

    /** 读档：按 {@link #positions()}、{@link #items()} 的顺序放回去，越界或挤在一起的丢掉。 */
    public static BeltLine restore(int length, int speed, int[] positions, int[] items, int[] waiting) {
        BeltLine line = new BeltLine(length, speed);
        for (int i = 0; i < Math.min(waiting.length, line.waiting.length); i++) {
            line.waiting[line.waitingCount++] = waiting[i];
        }
        int limit = length;
        for (int i = 0; i < Math.min(positions.length, items.length); i++) {
            int p = positions[i];
            if (p < 0 || p > limit) {
                continue;
            }
            line.append(p, items[i]);
            limit = p - SPACING;
        }
        return line;
    }
}
