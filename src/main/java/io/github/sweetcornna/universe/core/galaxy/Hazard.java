package io.github.sweetcornna.universe.core.galaxy;

/** 环境危害（玩法设计 §3.2）。每颗星球 0–2 种。缺氧不算危害，由生命维持单独处理。 */
public enum Hazard {
    COLD("寒冷", "Cold"),
    HEAT("高温", "Heat"),
    TOXIC("毒性", "Toxicity"),
    RADIATION("辐射", "Radiation");

    private final String nameZh;
    private final String nameEn;

    Hazard(String nameZh, String nameEn) {
        this.nameZh = nameZh;
        this.nameEn = nameEn;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }
}
