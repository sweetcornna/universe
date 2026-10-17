package io.github.sweetcornna.universe.core.terrain;

/** 地表材质的种类。适配层把它们映射到具体的星球方块，颜色由星球调色板决定。 */
public enum Surface {
    TURF,
    SOIL,
    SAND,
    ROCK,
    REGOLITH,
    SNOW,
    ICE,
    ASH,
    CRYSTAL,
    /** 按高度分层着色的岩层，用在方山和峡谷的崖壁上。 */
    STRATA
}
