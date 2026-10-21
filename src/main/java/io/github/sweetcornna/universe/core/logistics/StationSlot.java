package io.github.sweetcornna.universe.core.logistics;

import io.github.sweetcornna.universe.core.factory.Proliferation;

/**
 * 物流站的一个物品格：放哪种物品、现在有多少、上限、本地和远程的供需设置，以及在途的预留量。
 *
 * <p>派出载具时就把货量记成在途：要运进来的记在 {@link #incoming}（算进"还缺多少"），被别的站的载具来拿的记在
 * {@link #outgoing}（算进"还能给多少"，外面的分拣器也拿不走）。这样几架载具不会同时去抢同一批货。
 *
 * <p>喷过增产剂的货（ADR-0022）：格子记这些物品增产点数的总和，不超过每个 {@link Proliferation#MAX_LEVEL} 点。
 * 不指定点数拿走的按比例带走点数，载具运货时点数跟着货走。
 */
public final class StationSlot {
    /** 物品 ID；空字符串表示没设。 */
    private String item = "";
    private int count;
    private int max;
    private Logic local = Logic.STORAGE;
    private Logic remote = Logic.STORAGE;
    private int incoming;
    private int outgoing;
    private long points;

    public StationSlot(int max) {
        this.max = max;
    }

    public StationSlot(String item, int count, int max, Logic local, Logic remote, int incoming, int outgoing) {
        this(item, count, max, local, remote, incoming, outgoing, 0);
    }

    public StationSlot(String item, int count, int max, Logic local, Logic remote, int incoming, int outgoing, long points) {
        this.item = item;
        this.count = count;
        this.max = max;
        this.local = local;
        this.remote = remote;
        this.incoming = incoming;
        this.outgoing = outgoing;
        this.setPoints(points);
    }

    public String item() {
        return this.item;
    }

    public boolean isEmpty() {
        return this.item.isEmpty();
    }

    public int count() {
        return this.count;
    }

    public int max() {
        return this.max;
    }

    public Logic local() {
        return this.local;
    }

    public Logic remote() {
        return this.remote;
    }

    public int incoming() {
        return this.incoming;
    }

    public int outgoing() {
        return this.outgoing;
    }

    /** 增产点数的总和。 */
    public long points() {
        return this.points;
    }

    /** 每个物品平均几点（向下取整）：自动化从这一格拿货时按这个数给。 */
    public int level() {
        return this.count <= 0 ? 0 : (int) Math.min(Proliferation.MAX_LEVEL, this.points / this.count);
    }

    /** {@code whole} 个物品共 {@code points} 点，其中 {@code part} 个分到的点数（向下取整）。 */
    public static long share(long points, int part, int whole) {
        return whole <= 0 || part >= whole ? Math.max(0, points) : Math.max(0, points * Math.max(0, part) / whole);
    }

    /** 能给出去的：现有的减去别人预订的。 */
    public int available() {
        return Math.max(0, this.count - this.outgoing);
    }

    /** 还缺多少才到上限（算上正在运来的）。 */
    public int need() {
        return Math.max(0, this.max - this.count - this.incoming);
    }

    /** 能不能换物品：空了、也没有在途的货。 */
    public boolean canChangeItem() {
        return this.count == 0 && this.incoming == 0 && this.outgoing == 0;
    }

    /**
     * 设定物品。
     *
     * @return 换成功了（格子不空或有在途的货时不能换）
     */
    public boolean setItem(String item) {
        if (!this.canChangeItem()) {
            return false;
        }
        this.item = item;
        if (item.isEmpty()) {
            this.local = Logic.STORAGE;
            this.remote = Logic.STORAGE;
        }
        return true;
    }

    public void setMax(int max) {
        this.max = Math.max(0, max);
    }

    public void setLocal(Logic local) {
        this.local = local;
    }

    public void setRemote(Logic remote) {
        this.remote = remote;
    }

    /** 加进来（不超过 {@code capacity}），返回实际加了几个。 */
    int add(int amount, int capacity) {
        return this.add(amount, 0, capacity);
    }

    /** 加进来 {@code amount} 个、共 {@code points} 点；放不下的那部分按比例带走点数（见 {@link #share}）。 */
    int add(int amount, long points, int capacity) {
        int added = Math.max(0, Math.min(amount, capacity - this.count));
        this.count += added;
        this.setPoints(this.points + share(points, added, amount));
        return added;
    }

    /** 拿出去（不动别人预订的那部分），按比例带走点数，返回实际拿了几个。 */
    int take(int amount) {
        int taken = Math.max(0, Math.min(amount, this.available()));
        this.remove(taken, share(this.points, taken, this.count));
        return taken;
    }

    /** 拿出去，每个带走 {@code level} 点（自动化按 {@link #level} 拿的时候）。 */
    int take(int amount, int level) {
        int taken = Math.max(0, Math.min(amount, this.available()));
        this.remove(taken, (long) taken * Math.max(0, level));
        return taken;
    }

    /** 被预订的货由载具取走：先算预订，再扣数量，按比例带走点数。 */
    int pickUp(int reserved) {
        this.outgoing = Math.max(0, this.outgoing - reserved);
        int taken = Math.max(0, Math.min(reserved, this.count));
        this.remove(taken, share(this.points, taken, this.count));
        return taken;
    }

    private void remove(int amount, long points) {
        this.count -= amount;
        this.setPoints(this.points - points);
    }

    void reserveIncoming(int amount) {
        this.incoming += amount;
    }

    void releaseIncoming(int amount) {
        this.incoming = Math.max(0, this.incoming - amount);
    }

    void reserveOutgoing(int amount) {
        this.outgoing += amount;
    }

    void releaseOutgoing(int amount) {
        this.outgoing = Math.max(0, this.outgoing - amount);
    }

    /** 直接改数量（存取口、拆了再放回来的时候）。 */
    void setCount(int count) {
        this.count = Math.max(0, count);
        this.setPoints(this.points);
    }

    /** 直接改点数，不超过每个物品 {@link Proliferation#MAX_LEVEL} 点。 */
    void setPoints(long points) {
        this.points = Math.max(0, Math.min(points, (long) this.count * Proliferation.MAX_LEVEL));
    }
}
