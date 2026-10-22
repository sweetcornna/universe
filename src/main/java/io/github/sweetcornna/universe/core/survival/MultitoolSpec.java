package io.github.sweetcornna.universe.core.survival;

import java.util.Map;

import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.Stat;

/**
 * 一把多功能工具的类型、档次和坏了几格（ADR-0026 §六）。
 *
 * <p>技术槽 C 4、B 5、A 6、S 8 格。坏的格子是排在最后的那几格，放不进东西，每格用
 * {@value #REPAIR_SCRAP} 个守卫机残骸修好。守卫塔里取出来的守卫机型工具一半的格子是坏的。
 * 修好的和新做的工具是 {@link #STARTER}（C 档手枪）；没有类型的旧工具（以前做的）按 {@link #LEGACY} 算：A 档手枪，6 格，和以前一样。
 *
 * @param broken 坏了几格（从最后一格往前数）
 */
public record MultitoolSpec(MultitoolClass type, Grade grade, int broken) {
    /** 槽数：C、B、A、S。要排在 {@link #LEGACY} 前面，构造时要用。 */
    private static final int[] SLOTS = {4, 5, 6, 8};
    public static final MultitoolSpec LEGACY = new MultitoolSpec(MultitoolClass.PISTOL, Grade.A, 0);
    /** 修好的和新做的工具：C 档手枪，4 格。谁都能做，折价只按做它的材料算。 */
    public static final MultitoolSpec STARTER = new MultitoolSpec(MultitoolClass.PISTOL, Grade.C, 0);
    /** 槽数最多的（S 档）。 */
    public static final int MAX_SLOTS = 8;
    /** 修一格坏槽要几个守卫机残骸。 */
    public static final int REPAIR_SCRAP = 3;

    public MultitoolSpec {
        broken = Math.clamp(broken, 0, slots(grade));
    }

    public static int slots(Grade grade) {
        return SLOTS[grade.ordinal()];
    }

    /** 守卫塔里取出来的守卫机型工具：一半的格子是坏的（向下取整）。 */
    public static MultitoolSpec sentinel(Grade grade) {
        return new MultitoolSpec(MultitoolClass.SENTINEL, grade, slots(grade) / 2);
    }

    /** 一共几格（含坏的）。 */
    public int slots() {
        return slots(this.grade);
    }

    /** 能用的格数。 */
    public int working() {
        return this.slots() - this.broken;
    }

    /** 第 {@code slot} 格（从 0 数）是不是坏的。 */
    public boolean isBroken(int slot) {
        return slot >= this.working() && slot < this.slots();
    }

    /** 修好一格；没有坏格时原样返回。 */
    public MultitoolSpec repaired() {
        return this.broken == 0 ? this : new MultitoolSpec(this.type, this.grade, this.broken - 1);
    }

    /** 专长加成。 */
    public double bonus() {
        return this.type.bonus(this.grade);
    }

    /** 专长落在哪几项属性上、各多少。 */
    public Map<Stat, Double> specialty() {
        return this.type.specialty(this.grade);
    }
}
