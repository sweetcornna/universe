package io.github.sweetcornna.universe.core.dyson;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * 戴森云的一条轨道（ADR-0016）：半径、倾角、升交点经度，和轨道上的太阳帆。帆按到期的时刻分批记，
 * 到期时刻向上取整到整分钟（{@link #BUCKET} 刻），一条轨道最多九十来批。
 */
public final class SwarmOrbit {
    /** 到期时刻按多少刻取整。 */
    public static final long BUCKET = 1200;

    private final int id;
    private double radius;
    private double inclination;
    private double ascendingNode;
    /** 到期时刻 → 帆数。 */
    private final TreeMap<Long, Integer> sails = new TreeMap<>();
    private long count;

    public SwarmOrbit(int id, double radius, double inclination, double ascendingNode) {
        this.id = id;
        this.radius = radius;
        this.inclination = inclination;
        this.ascendingNode = ascendingNode;
    }

    public int id() {
        return this.id;
    }

    public double radius() {
        return this.radius;
    }

    public double inclination() {
        return this.inclination;
    }

    public double ascendingNode() {
        return this.ascendingNode;
    }

    void set(double radius, double inclination, double ascendingNode) {
        this.radius = radius;
        this.inclination = inclination;
        this.ascendingNode = ascendingNode;
    }

    /** 轨道上现在有几张帆。 */
    public long sails() {
        return this.count;
    }

    /** 加几张帆，在 expiry 时刻（取整到整分钟）消散。 */
    public void add(int count, long expiry) {
        if (count <= 0) {
            return;
        }
        long bucket = Math.ceilDiv(expiry, BUCKET) * BUCKET;
        this.sails.merge(bucket, count, Integer::sum);
        this.count += count;
    }

    /** 去掉 now 时已到期的帆，返回去掉几张。 */
    long expire(long now) {
        long removed = 0;
        Iterator<Map.Entry<Long, Integer>> it = this.sails.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Integer> entry = it.next();
            if (entry.getKey() > now) {
                break;
            }
            removed += entry.getValue();
            it.remove();
        }
        this.count -= removed;
        return removed;
    }

    /** 最早到期的那批帆的到期时刻；没有帆时是 {@link Long#MAX_VALUE}。 */
    long earliest() {
        return this.sails.isEmpty() ? Long.MAX_VALUE : this.sails.firstKey();
    }

    /** 从最早到期的那批里取最多 max 张，返回取了几张。 */
    int takeEarliest(int max) {
        if (this.sails.isEmpty() || max <= 0) {
            return 0;
        }
        Map.Entry<Long, Integer> first = this.sails.firstEntry();
        int taken = Math.min(max, first.getValue());
        if (taken == first.getValue()) {
            this.sails.pollFirstEntry();
        } else {
            this.sails.put(first.getKey(), first.getValue() - taken);
        }
        this.count -= taken;
        return taken;
    }

    /** 存档用：到期时刻和帆数，按时刻排好。 */
    public long[] expiries() {
        return this.sails.keySet().stream().mapToLong(Long::longValue).toArray();
    }

    public int[] counts() {
        return this.sails.values().stream().mapToInt(Integer::intValue).toArray();
    }
}
