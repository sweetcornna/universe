package io.github.sweetcornna.universe.client;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** 本模组的按键：都放在"寰宇"分类下。 */
public final class UniverseKeys {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Universe.id("universe"));
    /** 科技树（默认 J）。 */
    public static final KeyMapping TECH_TREE = new KeyMapping("key.universe.tech_tree", InputConstants.Type.KEYBOARD, InputConstants.KEY_J, CATEGORY);
    /** 星图（默认 M）。 */
    public static final KeyMapping STAR_MAP = new KeyMapping("key.universe.star_map", InputConstants.Type.KEYBOARD, InputConstants.KEY_M, CATEGORY);

    private UniverseKeys() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.registerCategory(CATEGORY);
            event.register(TECH_TREE);
            event.register(STAR_MAP);
        });
    }
}
