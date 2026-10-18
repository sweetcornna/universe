package io.github.sweetcornna.universe.core.logistics;

/**
 * 物流站的一个物品格：放哪种物品、现在有多少、上限、本地和远程的供需设置，以及在途的预留量。
 *
 * <p>派出载具时就把货量记成在途：要运进来的记在 {@link #incoming}（算进"还缺多少"），被别的站的载具来拿的记在
 * {@link #outgoing}（算进"还能给多少"，外面的分拣器也拿不走）。这样几架载具不会同时去抢同一批货。
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

    public StationSlot(int max) {
        this.max = max;
    }

    public StationSlot(String item, int count, int max, Logic local, Logic remote, int incoming, int outgoing) {
        this.item = item;
        this.count = count;
        this.max = max;
        this.local = local;
        this.remote = remote;
        this.incoming = incoming;
        this.outgoing = outgoing;
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
        int added = Math.max(0, Math.min(amount, capacity - this.count));
        this.count += added;
        return added;
    }

    /** 拿出去（不动别人预订的那部分），返回实际拿了几个。 */
    int take(int amount) {
        int taken = Math.max(0, Math.min(amount, this.available()));
        this.count -= taken;
        return taken;
    }

    /** 被预订的货由载具取走：先算预订，再扣数量。 */
    int pickUp(int reserved) {
        this.outgoing = Math.max(0, this.outgoing - reserved);
        int taken = Math.min(reserved, this.count);
        this.count -= taken;
        return taken;
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
    }
}
