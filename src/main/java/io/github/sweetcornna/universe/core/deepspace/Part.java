package io.github.sweetcornna.universe.core.deepspace;

/**
 * 太空残骸和废弃前哨站用的部件（ADR-0034 §四）。每种部件对应一种方块；拆残骸时按部件扣结构完整度：
 * 导管 −8、反应堆屏蔽层 −5、反应堆芯 −30，别的 −1。前哨站的补给箱和数据终端用现成的方块，不算在残骸里。
 */
public enum Part {
    /** 残骸舱板：舱段的蒙皮，拆下来是废金属。 */
    PLATING(1),
    /** 桁架：主梁和支撑。 */
    FRAME(1),
    /** 散热板：从主梁伸出去的大翼。 */
    RADIATOR(1),
    /** 推进剂罐。 */
    TANK(1),
    /** 残骸导管：管线束，拆下来是电子元件，结构 −8。 */
    CONDUIT(8),
    /** 残骸货舱：拆开是一份补给。 */
    CARGO(1),
    /** 反应堆屏蔽层：包着堆芯的那一圈，结构 −5。 */
    SHIELDING(5),
    /** 反应堆芯：最值钱，拆下来结构 −30，而且 20 秒后熔毁。 */
    REACTOR(30),
    /** 舷窗。 */
    WINDOW(1),
    /** 舱内照明：残骸上是应急灯，报警时变红。 */
    LIGHT(1),
    /** 舱内地板。 */
    DECK(1),
    /** 发动机喷管。 */
    ENGINE(1),
    /** 前哨站的太阳能板。 */
    SOLAR(1),
    /** 天线。 */
    ANTENNA(1),
    /** 前哨站里能拆的设备（拆出零件）。 */
    DEVICE(1),
    /** 前哨站的数据终端（现成的方块）。 */
    TERMINAL(0),
    /** 前哨站仓库里的补给箱（现成的方块）。 */
    CRATE(0);

    private final int cost;

    Part(int cost) {
        this.cost = cost;
    }

    /** 拆掉一块扣多少结构完整度。 */
    public int cost() {
        return this.cost;
    }
}
