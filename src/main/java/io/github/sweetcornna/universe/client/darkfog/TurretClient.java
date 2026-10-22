package io.github.sweetcornna.universe.client.darkfog;

import io.github.sweetcornna.universe.darkfog.turret.TurretMissile;
import io.github.sweetcornna.universe.darkfog.turret.TurretShell;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** 炮塔的客户端（ADR-0028 §五、§八）：界面、转台和炮管的渲染器、炮弹、导弹和等离子团。 */
public final class TurretClient {
    private TurretClient() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(RegisterMenuScreensEvent.class, event -> {
            event.register(UniverseMenus.GAUSS_TURRET.menuType(), TurretScreen::new);
            event.register(UniverseMenus.LASER_TURRET.menuType(), TurretScreen::new);
            event.register(UniverseMenus.IMPLOSION_CANNON.menuType(), TurretScreen::new);
            event.register(UniverseMenus.MISSILE_TURRET.menuType(), TurretScreen::new);
            event.register(UniverseMenus.PLASMA_TURRET.menuType(), TurretScreen::new);
            event.register(UniverseMenus.SR_PLASMA_TURRET.menuType(), TurretScreen::new);
        });
        modBus.addListener(ModelEvent.RegisterStandalone.class, TurretRenderer::registerModels);
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerBlockEntityRenderer(UniverseBlockEntities.GAUSS_TURRET.get(), TurretRenderer::new);
            event.registerBlockEntityRenderer(UniverseBlockEntities.LASER_TURRET.get(), TurretRenderer::new);
            event.registerBlockEntityRenderer(UniverseBlockEntities.IMPLOSION_CANNON.get(), TurretRenderer::new);
            event.registerBlockEntityRenderer(UniverseBlockEntities.MISSILE_TURRET.get(), TurretRenderer::new);
            event.registerBlockEntityRenderer(UniverseBlockEntities.PLASMA_TURRET.get(), TurretRenderer::new);
            event.registerBlockEntityRenderer(UniverseBlockEntities.SR_PLASMA_TURRET.get(), TurretRenderer::new);
            event.<TurretShell>registerEntityRenderer(UniverseEntities.TURRET_SHELL.get(), TurretProjectileRenderer::shell);
            event.<TurretMissile>registerEntityRenderer(UniverseEntities.TURRET_MISSILE.get(), TurretProjectileRenderer::missile);
            event.registerEntityRenderer(UniverseEntities.TURRET_PLASMA.get(), TurretPlasmaRenderer::new);
        });
    }
}
