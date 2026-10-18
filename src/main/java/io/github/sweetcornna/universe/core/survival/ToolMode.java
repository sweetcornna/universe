package io.github.sweetcornna.universe.core.survival;

/** 多功能工具的模式（ADR-0014）：采矿激光或等离子弹，默认 G 键切换。存在工具的数据组件上。 */
public enum ToolMode {
    MINING,
    COMBAT;

    public ToolMode next() {
        return this == MINING ? COMBAT : MINING;
    }
}
