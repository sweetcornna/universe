package io.github.sweetcornna.universe.chem.reactor;

import io.github.sweetcornna.universe.core.terraform.EffectiveEnvironment;
import io.github.sweetcornna.universe.terraform.Terraforming;
import net.minecraft.server.level.ServerLevel;

/**
 * 反应釜保温用的环境温度（ADR-0036 §四）：所在星球现在的平均地表温度（改造过的按现用的，§六），
 * 不是星球的地方（太空、下界）按 15 °C 算。
 */
public final class ReactorClimate {
    /** 不在星球上时的环境温度（K）。 */
    public static final double DEFAULT_AMBIENT = 288;

    private ReactorClimate() {
    }

    public static double ambient(ServerLevel level) {
        return Terraforming.environment(level).map(EffectiveEnvironment::temperature).orElse(DEFAULT_AMBIENT);
    }
}
