package io.github.sweetcornna.universe.core.fleet;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.core.procgen.Rng;

/**
 * 一艘护卫舰（ADR-0024）。四项基础属性加上专长和特性就是出远征时用的属性；每出 {@link #RANK_EVERY} 次远征升一级。
 *
 * <p>编号是货船舰队里的序号，船名是"字母字母-数字"的舷号，不用翻译。
 */
public final class Frigate {
    /** 专长那一项加多少。 */
    public static final int SPECIALIST = 15;
    /** 支援舰四项各加多少。 */
    public static final int SUPPORT_ALL = 3;
    /** 每出几次远征升一级。 */
    public static final int RANK_EVERY = 4;
    /** 雇来时四项基础属性的总和范围。 */
    static final int MIN_BASE = 1;
    static final int MAX_BASE = 12;

    private final int id;
    private final String designation;
    private final FrigateType type;
    private final int[] base;
    private final List<FrigateTrait> traits;
    private final int fuel;
    private int expeditions;
    private boolean damaged;

    public Frigate(int id, String designation, FrigateType type, int[] base, List<FrigateTrait> traits, int fuel, int expeditions,
            boolean damaged) {
        if (base.length != Area.values().length) {
            throw new IllegalArgumentException("need " + Area.values().length + " stats");
        }
        this.id = id;
        this.designation = designation;
        this.type = type;
        this.base = base.clone();
        this.traits = new ArrayList<>(traits);
        this.fuel = fuel;
        this.expeditions = expeditions;
        this.damaged = damaged;
    }

    /** 交易终端上挂出来的一艘。 */
    public static Frigate roll(int id, Rng rng) {
        FrigateType type = rng.weighted(List.of(FrigateType.values()), FrigateType::weight);
        int[] base = new int[Area.values().length];
        int total = rng.range(MIN_BASE, MAX_BASE);
        for (int i = 0; i < total; i++) {
            base[rng.nextInt(base.length)]++;
        }
        List<FrigateTrait> traits = new ArrayList<>();
        int count = rng.range(0, 3);
        for (int i = 0; i < count; i++) {
            FrigateTrait trait = rng.pick(FrigateTrait.of(rng.chance(0.6)));
            if (!traits.contains(trait)) {
                traits.add(trait);
            }
        }
        int fuel = type == FrigateType.SUPPORT ? rng.range(2, 5) : rng.range(8, 12);
        String designation = "" + (char) ('A' + rng.nextInt(26)) + (char) ('A' + rng.nextInt(26)) + "-" + rng.range(100, 999);
        return new Frigate(id, designation, type, base, traits, fuel, 0, false);
    }

    public int id() {
        return this.id;
    }

    public String designation() {
        return this.designation;
    }

    public FrigateType type() {
        return this.type;
    }

    public int base(Area area) {
        return this.base[area.ordinal()];
    }

    public int[] baseStats() {
        return this.base.clone();
    }

    public List<FrigateTrait> traits() {
        return List.copyOf(this.traits);
    }

    public int baseFuel() {
        return this.fuel;
    }

    public int expeditions() {
        return this.expeditions;
    }

    public boolean damaged() {
        return this.damaged;
    }

    public void setDamaged(boolean damaged) {
        this.damaged = damaged;
    }

    /** 出远征时这一项的属性：基础 + 专长 + 支援 + 特性，不低于 0。 */
    public int stat(Area area) {
        int value = this.base[area.ordinal()];
        if (this.type.specialty() == area) {
            value += SPECIALIST;
        }
        if (this.type == FrigateType.SUPPORT) {
            value += SUPPORT_ALL;
        }
        for (FrigateTrait trait : this.traits) {
            if (trait.area() == area) {
                value += trait.amount();
            }
        }
        return Math.max(0, value);
    }

    /** 出一次远征要多少吨燃料，至少 1。 */
    public int fuelCost() {
        int value = this.fuel;
        for (FrigateTrait trait : this.traits) {
            if (trait.area() == null) {
                value += trait.amount();
            }
        }
        return Math.max(1, value);
    }

    /** 等级：雇来时 1 级。 */
    public int rank() {
        return 1 + this.expeditions / RANK_EVERY;
    }

    /** 四项属性的合计，交易终端按它定价。 */
    public int total() {
        int sum = 0;
        for (Area area : Area.values()) {
            sum += this.stat(area);
        }
        return sum;
    }

    /**
     * 远征回来：次数加一，到了升级的时候随机加一项属性（六成加在专长上），一半的机会把一个坏特性换成好的。
     *
     * @return 这次升没升级
     */
    public boolean returned(Rng rng) {
        this.expeditions++;
        if (this.expeditions % RANK_EVERY != 0) {
            return false;
        }
        Area area = this.type.specialty() != null && rng.chance(0.6) ? this.type.specialty() : rng.pick(Area.values());
        this.base[area.ordinal()] += rng.range(2, 4);
        if (rng.chance(0.5)) {
            for (int i = 0; i < this.traits.size(); i++) {
                if (!this.traits.get(i).good()) {
                    List<FrigateTrait> good = FrigateTrait.of(true);
                    good.removeAll(this.traits);
                    if (good.isEmpty()) {
                        this.traits.remove(i);
                    } else {
                        this.traits.set(i, rng.pick(good));
                    }
                    break;
                }
            }
        }
        return true;
    }
}
