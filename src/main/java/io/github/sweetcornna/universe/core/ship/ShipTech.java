package io.github.sweetcornna.universe.core.ship;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.space.DriveTier;
import io.github.sweetcornna.universe.core.tech.TechBonuses;
import io.github.sweetcornna.universe.core.tech.TechModule;

/**
 * 驾驶台里装着的飞船技术（ADR-0024）：生效的技术模块和最高一档的驱动。
 *
 * @param modules 生效的技术模块
 * @param drive   装着的最高一档驱动；没装就是普通的跃迁引擎
 */
public record ShipTech(List<TechModule> modules, DriveTier drive) {
    public static final ShipTech NONE = new ShipTech(List.of(), DriveTier.BASIC);

    public ShipTech {
        modules = List.copyOf(modules);
    }

    /**
     * 驾驶台的一格：装着一个模块或者一台驱动，另一项为空。
     *
     * @param index 第几格（从 0 起）
     */
    public record Slot(int index, @Nullable TechModule module, @Nullable DriveTier drive) {
    }

    /**
     * 只算档次给的那几格：格子比档次多的时候（换了更差的部件、旧存档），后面几格里的东西不生效。
     *
     * @param unlocked 档次给的格数（{@link ShipRating#slots()}）
     */
    public static ShipTech of(List<Slot> slots, int unlocked) {
        List<TechModule> modules = new ArrayList<>();
        DriveTier drive = DriveTier.BASIC;
        for (Slot slot : slots) {
            if (slot.index() < 0 || slot.index() >= unlocked) {
                continue;
            }
            if (slot.module() != null) {
                modules.add(slot.module());
            }
            if (slot.drive() != null) {
                drive = drive.max(slot.drive());
            }
        }
        return modules.isEmpty() && drive == DriveTier.BASIC ? NONE : new ShipTech(modules, drive);
    }

    public TechBonuses bonuses() {
        return TechBonuses.of(this.modules);
    }
}
