package io.github.sweetcornna.universe.bio;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.microbe.BioProcess;
import net.minecraft.resources.Identifier;

/**
 * 生物反应器的配方（ADR-0036 §五）：每种工艺一到两个 {@code universe:bioreactor/<工艺>[_<原料>]} 的设备配方，
 * 时间按满罐（密度 1）、产率 1、最适温度写。反应器按选的配方认出是哪种工艺，看罐里的菌有没有那个基因。
 */
public final class BioRecipes {
    /** 配方 ID 的目录，也是配方类型的名字。 */
    public static final String DIR = "bioreactor";
    /** 都在"生物工程"这个科技里解锁。 */
    public static final String RESEARCH = "bioengineering";
    /** 认过的配方 ID：生物反应器每刻要问好几次，结果只看 ID。 */
    private static final Map<Identifier, Optional<BioProcess>> PROCESSES = new ConcurrentHashMap<>();

    private BioRecipes() {
    }

    /** 配方 ID 对应的工艺；不是生物反应器的配方为 {@code null}。 */
    public static @Nullable BioProcess process(Identifier id) {
        return PROCESSES.computeIfAbsent(id, key -> Optional.ofNullable(parse(key))).orElse(null);
    }

    private static @Nullable BioProcess parse(Identifier id) {
        String prefix = DIR + "/";
        if (!id.getNamespace().equals(Universe.MOD_ID) || !id.getPath().startsWith(prefix)) {
            return null;
        }
        String name = id.getPath().substring(prefix.length());
        for (BioProcess process : BioProcess.values()) {
            if (name.equals(process.id()) || name.startsWith(process.id() + "_")) {
                return process;
            }
        }
        return null;
    }
}
