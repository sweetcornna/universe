package io.github.sweetcornna.universe.core.poi;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.TechModule;

/**
 * 制造设施的终端给的技术模块（ADR-0026）：三个种类不同的外骨骼或多功能工具模块，都是 A 档或 S 档，玩家挑一个。
 * 由终端的种子决定，同一台终端每次打开都是这三个。
 */
public final class FacilityModules {
    public static final int COUNT = 3;

    private FacilityModules() {
    }

    /** S 档的概率：普通恒星 20%，稀有的 30%，奇特的 40%。 */
    static double sChance(int rarity) {
        return 0.2 + 0.1 * Math.clamp(rarity, 0, 2);
    }

    /**
     * @param seed   终端的种子
     * @param rarity 恒星的稀有程度（{@code ModuleLoot.rarity}）
     */
    public static List<TechModule> offers(long seed, int rarity) {
        Rng rng = Rng.of(Seeds.derive(seed, "facility_modules"));
        List<ModuleType> types = new ArrayList<>(ModuleType.personal());
        List<TechModule> out = new ArrayList<>();
        for (int i = 0; i < COUNT && !types.isEmpty(); i++) {
            ModuleType type = types.remove(rng.nextInt(types.size()));
            Grade grade = rng.chance(sChance(rarity)) ? Grade.S : Grade.A;
            out.add(TechModule.roll(type, grade, rng));
        }
        return List.copyOf(out);
    }
}
