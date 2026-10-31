package io.github.sweetcornna.universe.microbe;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.microbe.Gene;
import io.github.sweetcornna.universe.core.microbe.Metabolism;
import io.github.sweetcornna.universe.core.microbe.MicrobeStrain;
import io.github.sweetcornna.universe.core.microbe.Morphology;
import io.github.sweetcornna.universe.core.microbe.SampleSource;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * 微生物和基因的显示文字（ADR-0036 §五），服务端、客户端都能用：基因、来源、代谢走语言文件，
 * 菌名是程序起的，中文界面用中文名、别的语言用拉丁名。生物反应器、生态播种站也用这里的写法。
 */
public final class MicrobeText {
    /** 客户端的星团（客户端启动时换成 {@code ClientGalaxy::galaxy}）：物品说明里写星球名用。 */
    public static volatile Supplier<Optional<Galaxy>> clientGalaxy = Optional::empty;

    private MicrobeText() {
    }

    /** 这一边的星团：服务端线程上读服务端的，别的线程读客户端的。 */
    public static Optional<Galaxy> galaxy() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.isSameThread()) {
            return Optional.of(Planets.galaxy(server));
        }
        return clientGalaxy.get();
    }

    /** 天体路径对应的星球名；查不到写路径本身。 */
    public static Component planet(String path) {
        return galaxy().flatMap(galaxy -> Microbiomes.planet(galaxy, path))
                .map(planet -> name(planet.name().zh(), planet.name().en()))
                .orElse(Component.literal(path));
    }

    /** 两种语言的名字，按界面语言取一个（语言文件里 {@code universe.name} 只取对应的参数）。 */
    public static Component name(String zh, String en) {
        return Component.translatable("universe.name", zh, en);
    }

    public static Component strain(MicrobeStrain strain) {
        return name(strain.nameZh(), strain.nameEn());
    }

    public static Component gene(Gene gene) {
        return Component.translatable("gene.universe." + gene.id());
    }

    /** 几个基因连成一串："抗冻蛋白、DNA 修复"；没有写"无"。 */
    public static Component genes(Collection<Gene> genes) {
        if (genes.isEmpty()) {
            return Component.translatable("microbe.universe.none");
        }
        MutableComponent list = Component.empty();
        boolean first = true;
        for (Gene gene : Gene.values()) {
            if (!genes.contains(gene)) {
                continue;
            }
            if (!first) {
                list.append(Component.translatable("microbe.universe.separator"));
            }
            first = false;
            list.append(gene(gene));
        }
        return list;
    }

    public static Component source(SampleSource source) {
        return Component.translatable("microbe.universe.source." + source.id());
    }

    public static Component metabolism(Metabolism metabolism) {
        return Component.translatable("microbe.universe.metabolism." + metabolism.name().toLowerCase(Locale.ROOT));
    }

    public static Component morphology(Morphology morphology) {
        return Component.translatable("microbe.universe.morphology." + morphology.name().toLowerCase(Locale.ROOT));
    }

    /** "352 K（79 °C）"。 */
    public static Component temperature(double kelvin) {
        return Component.translatable("microbe.universe.temperature", Math.round(kelvin), Math.round(kelvin - 273.15));
    }

    /** 现实的小时数：小于 1 写分钟。 */
    public static Component hours(double hours) {
        if (hours < 1) {
            return Component.translatable("microbe.universe.minutes", Math.max(1, Math.round(hours * 60)));
        }
        String text = hours < 10 ? String.format(Locale.ROOT, "%.1f", hours) : String.format(Locale.ROOT, "%.0f", hours);
        return Component.translatable("microbe.universe.hours", text);
    }

    /** 产率加成写成 "+15%"。 */
    public static String bonus(double share) {
        return String.format(Locale.ROOT, "+%.0f%%", share * 100);
    }
}
