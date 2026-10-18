package io.github.sweetcornna.universe.core.tech;

/** 技术模块装在哪里（ADR-0014）。飞船模块到 M7 再加。 */
public enum ModuleHost {
    EXOSUIT("外骨骼", "Exosuit"),
    MULTITOOL("多功能工具", "Multitool");

    private final String nameZh;
    private final String nameEn;

    ModuleHost(String nameZh, String nameEn) {
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
