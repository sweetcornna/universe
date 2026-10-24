package io.github.sweetcornna.universe.core.survival;

import java.util.Set;

/**
 * 多功能工具的模式（ADR-0014、ADR-0026 §六）：采矿激光、爆能枪，再加上装在工具槽里的武器。默认 G 键切换，
 * 存在工具的数据组件上（按名字存，只能往后加）。
 */
public enum ToolMode {
    MINING(null),
    COMBAT(null),
    SCATTER_BLASTER(ToolWeapon.SCATTER_BLASTER),
    PULSE_SPITTER(ToolWeapon.PULSE_SPITTER),
    BLAZE_JAVELIN(ToolWeapon.BLAZE_JAVELIN),
    PLASMA_LAUNCHER(ToolWeapon.PLASMA_LAUNCHER),
    FISHING(ToolWeapon.FISHING_RIG),
    GRAVITY(ToolWeapon.GRAVITY_COIL);

    private final ToolWeapon weapon;

    ToolMode(ToolWeapon weapon) {
        this.weapon = weapon;
    }

    /** 这个模式用的武器；采矿和爆能枪是工具自带的，返回 null。 */
    public ToolWeapon weapon() {
        return this.weapon;
    }

    public static ToolMode of(ToolWeapon weapon) {
        for (ToolMode mode : values()) {
            if (mode.weapon == weapon) {
                return mode;
            }
        }
        throw new IllegalArgumentException(String.valueOf(weapon));
    }

    /** 开火的模式（不是采矿，也不是钓鱼器、引力抓取器这种附件）。 */
    public boolean combat() {
        return this != MINING && (this.weapon == null || !this.weapon.attachment());
    }

    /** 实际用的模式：选中的武器已经从工具上拆下来了，退回爆能枪。 */
    public ToolMode effective(Set<ToolWeapon> installed) {
        return this.weapon != null && !installed.contains(this.weapon) ? COMBAT : this;
    }

    /** 下一个模式：采矿 → 爆能枪 → 装了的武器（按枚举顺序）→ 采矿。 */
    public ToolMode next(Set<ToolWeapon> installed) {
        ToolMode[] modes = values();
        for (int i = 1; i <= modes.length; i++) {
            ToolMode candidate = modes[(this.ordinal() + i) % modes.length];
            if (candidate.weapon == null || installed.contains(candidate.weapon)) {
                return candidate;
            }
        }
        return MINING;
    }

    /** 没装武器的工具：采矿和爆能枪来回切。 */
    public ToolMode next() {
        return this.next(Set.of());
    }

    /** 按住扳机时的升温是采矿激光的几倍。 */
    public float heatFactor() {
        return switch (this) {
            case MINING -> 1;
            case COMBAT -> PlasmaWeapon.HEAT_FACTOR;
            default -> this.weapon.heatFactor();
        };
    }
}
