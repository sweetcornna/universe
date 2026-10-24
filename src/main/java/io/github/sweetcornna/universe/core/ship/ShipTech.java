package io.github.sweetcornna.universe.core.ship;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.space.DriveTier;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.TechBonuses;
import io.github.sweetcornna.universe.core.tech.TechGrid;
import io.github.sweetcornna.universe.core.tech.TechModule;

/**
 * 驾驶台里装着的飞船技术（ADR-0024）：生效的技术模块和最高一档的驱动。驾驶台的格子排成 3 × 2，相邻和超频的倍数
 * （ADR-0034 §五）在组装时按格子算好，跟着模块一起存。
 *
 * @param modules 生效的技术模块
 * @param drive   装着的最高一档驱动；没装就是普通的跃迁引擎
 * @param factors 每个模块所在格子的倍数（{@link TechGrid.Boost#factor()}），和 {@code modules} 一一对应
 */
public record ShipTech(List<TechModule> modules, DriveTier drive, List<Double> factors) {
    public static final ShipTech NONE = new ShipTech(List.of(), DriveTier.BASIC);

    public ShipTech {
        modules = List.copyOf(modules);
        factors = List.copyOf(factors);
        if (factors.size() != modules.size()) {
            throw new IllegalArgumentException(modules.size() + " modules, " + factors.size() + " factors");
        }
    }

    /** 不算格子排布的技术（商店里的样船、旧的调用）。 */
    public ShipTech(List<TechModule> modules, DriveTier drive) {
        this(modules, drive, Collections.nCopies(modules.size(), 1.0));
    }

    /**
     * 驾驶台的一格：装着一个模块或者一台驱动，另一项为空。
     *
     * @param index        第几格（从 0 起）
     * @param supercharged 这一格是不是超频格（ADR-0034 §五）
     */
    public record Slot(int index, @Nullable TechModule module, @Nullable DriveTier drive, boolean supercharged) {
        public Slot(int index, @Nullable TechModule module, @Nullable DriveTier drive) {
            this(index, module, drive, false);
        }
    }

    /**
     * 只算档次给的那几格：格子比档次多的时候（换了更差的部件、旧存档），后面几格里的东西不生效。
     *
     * @param unlocked 档次给的格数（{@link ShipRating#slots()}）
     */
    public static ShipTech of(List<Slot> slots, int unlocked) {
        List<Slot> live = new ArrayList<>();
        DriveTier drive = DriveTier.BASIC;
        List<ModuleType> types = new ArrayList<>(Collections.nCopies(TechGrid.SHIP.size(), (ModuleType) null));
        int mask = 0;
        for (Slot slot : slots) {
            if (slot.index() < 0 || slot.index() >= unlocked) {
                continue;
            }
            boolean inGrid = slot.index() < types.size();
            if (slot.module() != null) {
                live.add(slot);
                if (inGrid) {
                    types.set(slot.index(), slot.module().type());
                }
            }
            if (slot.supercharged() && inGrid) {
                mask |= 1 << slot.index();
            }
            if (slot.drive() != null) {
                drive = drive.max(slot.drive());
            }
        }
        if (live.isEmpty() && drive == DriveTier.BASIC) {
            return NONE;
        }
        List<TechModule> modules = new ArrayList<>();
        List<Double> factors = new ArrayList<>();
        for (Slot slot : live) {
            modules.add(slot.module());
            factors.add(slot.index() < types.size() ? TechGrid.SHIP.boost(types, mask, slot.index()).factor() : 1.0);
        }
        return new ShipTech(modules, drive, factors);
    }

    public TechBonuses bonuses() {
        return TechBonuses.of(this.modules, this.factors);
    }
}
