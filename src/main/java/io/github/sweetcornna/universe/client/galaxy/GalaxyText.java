package io.github.sweetcornna.universe.client.galaxy;

import java.util.Locale;

import io.github.sweetcornna.universe.client.discovery.ClientDiscoveries;
import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.core.galaxy.ExoticForm;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.procgen.NameGenerator;
import net.minecraft.client.Minecraft;

/**
 * 星团里的名字和数值怎么显示：核心层的名字和枚举都带中英两套，中文界面用中文，其他语言用英文；
 * 发现者改过名的恒星系和天体用新名字（ADR-0012）。
 */
public final class GalaxyText {
    private GalaxyText() {
    }

    public static boolean chinese() {
        return Minecraft.getInstance().options.languageCode.startsWith("zh");
    }

    public static String pick(String zh, String en) {
        return chinese() ? zh : en;
    }

    public static String name(NameGenerator.Name name) {
        return pick(name.zh(), name.en());
    }

    public static String system(StarSystem system) {
        return ClientDiscoveries.name(DiscoveryKey.system(system.index()), system.name());
    }

    public static String planet(Planet planet) {
        return ClientDiscoveries.name(DiscoveryKey.body(planet.id()), planet.name());
    }

    /**
     * 星球的类型；奇异星球连它的地貌一起写出来（ADR-0030 §二），例如"奇异星球（巨晶）"。
     * 地貌由星球种子派生，客户端自己算得出来，不用网络传。
     */
    public static String planetType(Planet planet) {
        String type = pick(planet.type().nameZh(), planet.type().nameEn());
        return ExoticForm.of(planet)
                .map(form -> type + pick("（" + form.nameZh() + "）", " (" + form.nameEn() + ")"))
                .orElse(type);
    }

    /** 保留几位小数，小数点固定用"."。 */
    public static String number(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    /** 光度、质量这类跨好几个数量级的数：大的不带小数，小的保留两位有效数字。 */
    public static String magnitude(double value) {
        if (value >= 100) {
            return String.format(Locale.ROOT, "%,.0f", value);
        }
        if (value >= 1) {
            return number(value, value >= 10 ? 1 : 2);
        }
        int decimals = Math.min(6, Math.max(2, 1 - (int) Math.floor(Math.log10(Math.max(value, 1e-9)))));
        return number(value, decimals);
    }
}
