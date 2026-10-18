package io.github.sweetcornna.universe.core.logistics;

/**
 * 物流站在星团里的位置，算运输船的航程用：哪个恒星系（恒星的坐标，光年），以及离恒星多远（天文单位，卫星按它的行星算）。
 *
 * @param system 恒星系序号
 * @param au     离恒星的距离（天文单位）
 * @param body   天体路径（同一颗星球上的站之间不走运输船）
 */
public record Location(int system, double au, double starX, double starY, double starZ, String body) {
    /** 同一个恒星系里两颗星球之间最少算这么远。 */
    public static final double MIN_AU = 0.1;

    public boolean sameBody(Location other) {
        return this.system == other.system && this.body.equals(other.body);
    }

    public boolean sameSystem(Location other) {
        return this.system == other.system;
    }

    /** 同一恒星系里的距离（天文单位），按轨道半径之差估算。 */
    public double auTo(Location other) {
        return Math.max(MIN_AU, Math.abs(this.au - other.au));
    }

    /** 两个恒星系之间的距离（光年）。 */
    public double lightYearsTo(Location other) {
        double dx = this.starX - other.starX;
        double dy = this.starY - other.starY;
        double dz = this.starZ - other.starZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
