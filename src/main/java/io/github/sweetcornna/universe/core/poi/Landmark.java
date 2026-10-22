package io.github.sweetcornna.universe.core.poi;

/**
 * 一处地标（ADR-0026）。
 *
 * @param x        中心的方块坐标
 * @param z        中心的方块坐标
 * @param cellX    所在的网格（按这种地标自己的网格）
 * @param cellZ    所在的网格
 * @param key      领奖、去过的记录用的编号（种类和网格派生）
 * @param seed     建筑的随机种子
 * @param rotation 朝向，转 90° 的几倍
 */
public record Landmark(LandmarkType type, int x, int z, int cellX, int cellZ, long key, long seed, int rotation) {
    public double distanceSq(double px, double pz) {
        double dx = this.x + 0.5 - px;
        double dz = this.z + 0.5 - pz;
        return dx * dx + dz * dz;
    }

    public double distance(double px, double pz) {
        return Math.sqrt(this.distanceSq(px, pz));
    }
}
