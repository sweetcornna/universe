package io.github.sweetcornna.universe.core.logistics;

/**
 * 物流站的种类（ADR-0015）。电量按 ADR-0011 的换算：戴森球计划的千焦就是本模组的焦耳，千瓦除以 20 是焦耳/刻。
 */
public enum StationKind {
    /** 行星物流站：4 格，每格 5000，50 架无人机。 */
    PLANETARY(4, 5000, 50, 0, false, 180_000, 3_000),
    /** 星际物流站：5 格，每格 10000，50 架无人机、10 艘运输船，能设远程供需，有翘曲器格。 */
    INTERSTELLAR(5, 10_000, 50, 10, true, 12_000_000, 15_000),
    /** 轨道采集器：2 格（氢、重氢），只有远程供应，自己不派载具、不用电。 */
    COLLECTOR(2, 10_000, 0, 0, true, 0, 0);

    private final int slots;
    private final int slotCapacity;
    private final int maxDrones;
    private final int maxVessels;
    private final boolean remote;
    private final long battery;
    private final long maxCharge;

    StationKind(int slots, int slotCapacity, int maxDrones, int maxVessels, boolean remote, long battery, long maxCharge) {
        this.slots = slots;
        this.slotCapacity = slotCapacity;
        this.maxDrones = maxDrones;
        this.maxVessels = maxVessels;
        this.remote = remote;
        this.battery = battery;
        this.maxCharge = maxCharge;
    }

    public int slots() {
        return this.slots;
    }

    /** 一格最多放多少个。 */
    public int slotCapacity() {
        return this.slotCapacity;
    }

    public int maxDrones() {
        return this.maxDrones;
    }

    public int maxVessels() {
        return this.maxVessels;
    }

    /** 能不能和别的星球往来（远程供需）。 */
    public boolean remote() {
        return this.remote;
    }

    /** 电池容量（焦耳）。 */
    public long battery() {
        return this.battery;
    }

    /** 最大充电功率（焦耳/刻）。 */
    public long maxCharge() {
        return this.maxCharge;
    }

    /** 星际站和采集器之间才走运输船。 */
    public boolean usesVessels() {
        return this.maxVessels > 0;
    }
}
