package io.github.sweetcornna.universe;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import io.github.sweetcornna.universe.entity.FaunaSpawner;
import io.github.sweetcornna.universe.entity.ship.ShipTransit;
import io.github.sweetcornna.universe.network.UniverseNetwork;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.survival.PlayerSurvival;
import io.github.sweetcornna.universe.world.CrashSite;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseCreativeTabs;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import io.github.sweetcornna.universe.factory.FactoryCapabilities;
import io.github.sweetcornna.universe.factory.belt.BeltNetwork;
import io.github.sweetcornna.universe.factory.power.LevelPower;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipes;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.registry.UniverseRecipeTypes;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.UniverseWorldgen;
import io.github.sweetcornna.universe.world.DimensionCommand;
import io.github.sweetcornna.universe.world.RuntimeDimensions;
import io.github.sweetcornna.universe.world.planet.PlanetWeather;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 模组主类：注册表和事件监听都从这里接入。
 */
@Mod(Universe.MOD_ID)
public final class Universe {
    public static final String MOD_ID = "universe";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Universe(IEventBus modBus, ModContainer container) {
        modBus.addListener(this::commonSetup);
        UniverseBlocks.register(modBus);
        UniverseBlockEntities.register(modBus);
        UniverseAttachments.register(modBus);
        UniverseItems.register(modBus);
        UniverseEntities.register(modBus);
        UniverseMenus.register(modBus);
        UniverseDataComponents.register(modBus);
        UniverseRecipeTypes.register(modBus);
        UniverseCreativeTabs.register(modBus);
        UniverseWorldgen.register(modBus);
        UniverseNetwork.registerEvents(modBus);

        IEventBus gameBus = NeoForge.EVENT_BUS;
        RuntimeDimensions.registerEvents(modBus, gameBus);
        PlanetWeather.registerEvents(gameBus);
        FaunaSpawner.registerEvents(gameBus);
        ShipTransit.registerEvents(gameBus);
        PlayerSurvival.registerEvents(gameBus);
        CrashSite.registerEvents(gameBus);
        LevelPower.registerEvents(gameBus);
        BeltNetwork.registerEvents(gameBus);
        MachineRecipes.registerEvents(gameBus);
        Research.registerEvents(gameBus);
        FactoryCapabilities.registerEvents(modBus);
        gameBus.addListener(RegisterCommandsEvent.class, event -> DimensionCommand.register(event.getDispatcher()));
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Universe loaded");
    }

    /** 本模组命名空间下的标识符，例如 {@code id("star_chart")} 得到 {@code universe:star_chart}。 */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
