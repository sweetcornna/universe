package io.github.sweetcornna.universe.core.microbe;

/**
 * 菌的形态，决定名字的后半截（ADR-0036 §五）。顺序和名字是存档格式的一部分，只往后加。
 *
 * @param nameZh  中文名的词尾（球菌）
 * @param algaZh  光合菌的中文词尾（球藻）：蓝藻的中文名习惯叫"藻"
 * @param latin   拉丁属名的词尾（-coccus）
 */
public enum Morphology {
    COCCUS("球菌", "球藻", "coccus"),
    BACILLUS("杆菌", "杆藻", "bacillus"),
    SPIRILLUM("螺菌", "螺旋藻", "spirillum"),
    FILAMENT("丝菌", "丝藻", "thrix"),
    ARCHAEON("古菌", "古菌", "archaeum");

    private final String nameZh;
    private final String algaZh;
    private final String latin;

    Morphology(String nameZh, String algaZh, String latin) {
        this.nameZh = nameZh;
        this.algaZh = algaZh;
        this.latin = latin;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String algaZh() {
        return this.algaZh;
    }

    public String latin() {
        return this.latin;
    }
}
