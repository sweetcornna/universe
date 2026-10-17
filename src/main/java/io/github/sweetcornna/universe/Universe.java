package io.github.sweetcornna.universe;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * 模组主类：注册表和事件监听都从这里接入。
 */
@Mod(Universe.MOD_ID)
public final class Universe {
    public static final String MOD_ID = "universe";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Universe(IEventBus modBus, ModContainer container) {
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Universe loaded");
    }

    /** 本模组命名空间下的标识符，例如 {@code id("star_chart")} 得到 {@code universe:star_chart}。 */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
