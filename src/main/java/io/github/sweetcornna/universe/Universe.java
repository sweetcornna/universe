package io.github.sweetcornna.universe;

import io.github.sweetcornna.universe.darkfog.turret.Turrets;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import io.github.sweetcornna.universe.asteroid.Asteroids;
import io.github.sweetcornna.universe.blueprint.Blueprints;
import io.github.sweetcornna.universe.blueprint.Construction;
import io.github.sweetcornna.universe.civ.CivCommand;
import io.github.sweetcornna.universe.civ.NpcSpawner;
import io.github.sweetcornna.universe.darkfog.DarkFogBases;
import io.github.sweetcornna.universe.darkfog.DarkFogCommand;
import io.github.sweetcornna.universe.logistics.Distributors;
import io.github.sweetcornna.universe.mecha.Flyers;
import io.github.sweetcornna.universe.mecha.Mecha;
import io.github.sweetcornna.universe.discovery.Discoveries;
import io.github.sweetcornna.universe.dyson.Dyson;
import io.github.sweetcornna.universe.entity.FaunaSpawner;
import io.github.sweetcornna.universe.entity.TamedCreatures;
import io.github.sweetcornna.universe.entity.ship.ShipDealer;
import io.github.sweetcornna.universe.fleet.FleetCommand;
import io.github.sweetcornna.universe.world.planet.PlanetBorder;
import io.github.sweetcornna.universe.world.planet.ResourcesCommand;
import io.github.sweetcornna.universe.world.space.BlackHoleCommand;
import io.github.sweetcornna.universe.portal.PortalCommand;
import io.github.sweetcornna.universe.encounter.EncounterCommand;
import io.github.sweetcornna.universe.encounter.SpaceEncounters;
import io.github.sweetcornna.universe.portal.Portals;
import io.github.sweetcornna.universe.fleet.Freighters;
import io.github.sweetcornna.universe.entity.ship.ShipSummon;
import io.github.sweetcornna.universe.entity.ship.ShipTransit;
import io.github.sweetcornna.universe.entity.ship.ShipWeapons;
import io.github.sweetcornna.universe.exocraft.Exocrafts;
import io.github.sweetcornna.universe.exocraft.VehicleSummoning;
import io.github.sweetcornna.universe.factory.FactoryAnchorBlock;
import io.github.sweetcornna.universe.factory.FactoryCapabilities;
import io.github.sweetcornna.universe.factory.belt.BeltNetwork;
import io.github.sweetcornna.universe.factory.power.LevelPower;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipes;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.gametest.UniverseGameTests;
import io.github.sweetcornna.universe.logistics.Logistics;
import io.github.sweetcornna.universe.mission.Missions;
import io.github.sweetcornna.universe.network.UniverseNetwork;
import io.github.sweetcornna.universe.perf.BenchCommand;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.pirate.Pirates;
import io.github.sweetcornna.universe.poi.Pois;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseCreativeTabs;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseGameRules;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import io.github.sweetcornna.universe.registry.UniverseRecipeTypes;
import io.github.sweetcornna.universe.registry.UniverseSounds;
import io.github.sweetcornna.universe.registry.UniverseStats;
import io.github.sweetcornna.universe.registry.UniverseTriggers;
import io.github.sweetcornna.universe.registry.UniverseWorldgen;
import io.github.sweetcornna.universe.rules.GalaxySettings;
import io.github.sweetcornna.universe.sentinel.SentinelPillars;
import io.github.sweetcornna.universe.sentinel.Sentinels;
import io.github.sweetcornna.universe.station.MultitoolCabinet;
import io.github.sweetcornna.universe.station.ShipRepair;
import io.github.sweetcornna.universe.stats.ProductionLog;
import io.github.sweetcornna.universe.story.Story;
import io.github.sweetcornna.universe.survival.PlayerSurvival;
import io.github.sweetcornna.universe.tech.SuitTech;
import io.github.sweetcornna.universe.teleport.Teleporters;
import io.github.sweetcornna.universe.trade.Trading;
import io.github.sweetcornna.universe.world.CrashSite;
import io.github.sweetcornna.universe.world.DimensionCommand;
import io.github.sweetcornna.universe.world.RuntimeDimensions;
import io.github.sweetcornna.universe.world.planet.Fulgurites;
import io.github.sweetcornna.universe.world.planet.PlanetWeather;
import io.github.sweetcornna.universe.world.space.SpaceCommand;
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
        UniverseSounds.register(modBus);
        UniverseStats.register(modBus);
        UniverseTriggers.register(modBus);
        UniverseMenus.register(modBus);
        UniverseDataComponents.register(modBus);
        UniverseRecipeTypes.register(modBus);
        UniverseCreativeTabs.register(modBus);
        UniverseWorldgen.register(modBus);
        UniverseNetwork.registerEvents(modBus);
        UniverseGameTests.register(modBus);

        IEventBus gameBus = NeoForge.EVENT_BUS;
        RuntimeDimensions.registerEvents(modBus, gameBus);
        PlanetWeather.registerEvents(gameBus);
        Fulgurites.registerEvents(gameBus);
        PlanetBorder.registerEvents(gameBus);
        FaunaSpawner.registerEvents(gameBus);
        TamedCreatures.registerEvents(gameBus);
        Teleporters.registerEvents(gameBus);
        Missions.registerEvents(gameBus);
        ShipTransit.registerEvents(gameBus);
        ShipSummon.registerEvents(gameBus);
        ShipDealer.registerEvents(gameBus);
        MultitoolCabinet.registerEvents(gameBus);
        Freighters.registerEvents(gameBus);
        Portals.registerEvents(gameBus);
        SentinelPillars.registerEvents(gameBus);
        PlayerSurvival.registerEvents(gameBus);
        CrashSite.registerEvents(gameBus);
        LevelPower.registerEvents(gameBus);
        BeltNetwork.registerEvents(gameBus);
        MachineRecipes.registerEvents(gameBus);
        Research.registerEvents(gameBus);
        Discoveries.registerEvents(gameBus);
        SuitTech.registerEvents(gameBus);
        Pois.registerEvents(gameBus);
        Sentinels.registerEvents(gameBus);
        FactoryCapabilities.registerEvents(modBus);
        FactoryAnchorBlock.registerEvents(modBus);
        Logistics.registerEvents(gameBus);
        ProductionLog.registerEvents(gameBus);
        Dyson.registerEvents(gameBus);
        Trading.registerEvents(gameBus);
        ShipWeapons.registerEvents(gameBus);
        Pirates.registerEvents(gameBus);
        SpaceEncounters.registerEvents(gameBus);
        Asteroids.registerEvents(gameBus);
        NpcSpawner.registerEvents(gameBus);
        Exocrafts.registerEvents(gameBus);
        VehicleSummoning.registerEvents(gameBus);
        ShipRepair.registerEvents(gameBus);
        Story.registerEvents(gameBus);
        Blueprints.registerEvents(gameBus);
        Construction.registerEvents(gameBus);
        Distributors.registerEvents(gameBus);
        Mecha.registerEvents(gameBus);
        Flyers.registerEvents(gameBus);
        ServerPerf.registerEvents(gameBus);
        BenchCommand.registerEvents(gameBus);
        gameBus.addListener(RegisterCommandsEvent.class, event -> DimensionCommand.register(event.getDispatcher()));
        gameBus.addListener(RegisterCommandsEvent.class, event -> SpaceCommand.register(event.getDispatcher()));
        gameBus.addListener(RegisterCommandsEvent.class, event -> FleetCommand.register(event.getDispatcher()));
        gameBus.addListener(RegisterCommandsEvent.class, event -> BlackHoleCommand.register(event.getDispatcher()));
        gameBus.addListener(RegisterCommandsEvent.class, event -> PortalCommand.register(event.getDispatcher()));
        gameBus.addListener(RegisterCommandsEvent.class, event -> ResourcesCommand.register(event.getDispatcher()));
        gameBus.addListener(RegisterCommandsEvent.class, event -> EncounterCommand.register(event.getDispatcher()));
        gameBus.addListener(RegisterCommandsEvent.class, event -> CivCommand.register(event.getDispatcher()));
        DarkFogBases.registerEvents(gameBus);
        gameBus.addListener(RegisterCommandsEvent.class, event -> DarkFogCommand.register(event.getDispatcher()));
        io.github.sweetcornna.universe.darkfog.facility.FacilityRepairs.registerEvents(modBus, gameBus);
        Turrets.registerEvents(modBus);
        UniverseGameRules.register(modBus);
        GalaxySettings.registerEvents(gameBus);
        // M16 第二块（ADR-0028 §八）
        io.github.sweetcornna.universe.darkfog.hive.DarkFogHives.registerEvents(gameBus);
        gameBus.addListener(RegisterCommandsEvent.class,
                event -> io.github.sweetcornna.universe.darkfog.hive.HiveCommand.register(event.getDispatcher()));
        // M16 黑雾：巢穴（H）的注册加在这行上面
        io.github.sweetcornna.universe.darkfog.hive.HiveBuilding.registerEvents(modBus);
        // M16 黑雾：巢穴建筑（B）的注册加在这行上面
        io.github.sweetcornna.universe.darkfog.space.SpaceUnits.registerEvents(modBus, gameBus);
        // M16 黑雾：太空单位（U）的注册加在这行上面
        io.github.sweetcornna.universe.darkfog.flight.DarkFogFlights.registerEvents(gameBus);
        io.github.sweetcornna.universe.darkfog.flight.DarkFogFlights.registerModEvents(modBus);
        gameBus.addListener(RegisterCommandsEvent.class,
                event -> io.github.sweetcornna.universe.darkfog.flight.FlightCommand.register(event.getDispatcher()));
        // M16 黑雾：中继站和种子（R）的注册加在这行上面
        // M16 黑雾：等离子炮塔（T）的注册加在这行上面
        io.github.sweetcornna.universe.darkfog.shield.Shields.registerEvents(gameBus);
        // M16 黑雾：行星护盾（S）的注册加在这行上面
        io.github.sweetcornna.universe.registry.UniverseFluids.register(modBus);
        io.github.sweetcornna.universe.fluid.AcidEffects.registerEvents(modBus, gameBus);
        // M17 收尾：酸液（A）的注册加在这行上面
        // M17 收尾：奇异星球（B）的注册加在这行上面
        io.github.sweetcornna.universe.factory.fluid.FluidBridge.registerEvents(gameBus);
        // M17 收尾：互通（C）的注册加在这行上面
        // M17 收尾：戴森球（D）的注册加在这行上面
        // M17 收尾：工厂物流（E）的注册加在这行上面
        io.github.sweetcornna.universe.survival.JetpackFlames.registerEvents(gameBus);
        // M17 收尾：飞船和星球（F）的注册加在这行上面
        io.github.sweetcornna.universe.mecha.fleet.MechaFleet.registerEvents(modBus, gameBus);
        io.github.sweetcornna.universe.mecha.combat.MechaCombat.registerEvents(gameBus);
        // M18 舰队和战斗（A）的注册加在这行上面
        // M18 物流调度（B）的注册加在这行上面
        io.github.sweetcornna.universe.stats.Dashboards.registerEvents(gameBus);
        // M18 统计和研究（C）的注册加在这行上面
        io.github.sweetcornna.universe.beacon.HoloBeacons.registerEvents(gameBus);
        // M18 信标、透镜、矿脉、蓝图（D）的注册加在这行上面
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Universe loaded");
    }

    /** 本模组命名空间下的标识符，例如 {@code id("star_chart")} 得到 {@code universe:star_chart}。 */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
