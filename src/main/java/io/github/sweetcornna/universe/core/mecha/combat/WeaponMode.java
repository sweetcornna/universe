package io.github.sweetcornna.universe.core.mecha.combat;

/** 机甲武器的模式（ADR-0033 §二），在机甲面板的"战斗"页设。按住潜行时哪种模式都不开火。 */
public enum WeaponMode {
    /** 打射程里最近的敌人，正在打自己的优先。 */
    AUTO,
    /** 只打准星对着的敌人。 */
    AIM,
    OFF;

    public WeaponMode next() {
        WeaponMode[] modes = values();
        return modes[(this.ordinal() + 1) % modes.length];
    }

    public static WeaponMode byOrdinal(int ordinal) {
        WeaponMode[] modes = values();
        return ordinal >= 0 && ordinal < modes.length ? modes[ordinal] : AUTO;
    }
}
