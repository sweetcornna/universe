package io.github.sweetcornna.universe.core.poi;

/**
 * 一个兴趣点：种类、中心的方块坐标（只有 x、z，高度放建筑时再找地面）。
 *
 * @param cellX    所在格子
 * @param cellZ    所在格子
 * @param key      全宇宙唯一的编号（星球种子和格子派生），玩家"去过哪些兴趣点"按它记
 * @param seed     建筑和战利品用的种子
 * @param rotation 朝向，0–3，每档转 90°
 */
public record Poi(PoiType type, int x, int z, int cellX, int cellZ, long key, long seed, int rotation) {
    public double distanceSq(double px, double pz) {
        double dx = this.x + 0.5 - px;
        double dz = this.z + 0.5 - pz;
        return dx * dx + dz * dz;
    }

    public double distance(double px, double pz) {
        return Math.sqrt(this.distanceSq(px, pz));
    }
}
