package io.github.sweetcornna.universe.client.discovery;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.core.discovery.Catalog;
import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.core.discovery.DiscoveryLog;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.procgen.NameGenerator.Name;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * 发现日志里的东西在客户端怎么称呼：改过名用新名字，否则用程序生成的名字（物种要现算动植物，结果缓存起来），
 * 矿物用方块的名字。
 */
public final class DiscoveryText {
    private static final Map<DiscoveryKey, Optional<Name>> GENERATED = new HashMap<>();
    private static Galaxy cachedGalaxy;

    private DiscoveryText() {
    }

    public static String name(DiscoveryKey key) {
        Optional<String> custom = ClientDiscoveries.entry(key).map(DiscoveryLog.Entry::customName);
        if (custom.isPresent()) {
            return custom.get();
        }
        if (key.kind() == DiscoveryKey.Kind.MINERAL) {
            Identifier id = Identifier.tryParse(key.mineral());
            return id == null ? key.mineral()
                    : BuiltInRegistries.BLOCK.getOptional(id).map(block -> block.getName().getString()).orElse(key.mineral());
        }
        return generated(key).map(GalaxyText::name).orElse("?");
    }

    /** 程序生成的名字。 */
    public static Optional<Name> generated(DiscoveryKey key) {
        Galaxy galaxy = ClientGalaxy.galaxy().orElse(null);
        if (galaxy == null) {
            return Optional.empty();
        }
        if (galaxy != cachedGalaxy) {
            GENERATED.clear();
            cachedGalaxy = galaxy;
        }
        return GENERATED.computeIfAbsent(key, k -> Catalog.name(k, galaxy));
    }
}
