package io.github.sweetcornna.universe.client.fossil;

import java.util.Locale;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.dev.DevScript;
import io.github.sweetcornna.universe.core.fossil.BoneFamily;
import io.github.sweetcornna.universe.core.fossil.BonePart;
import io.github.sweetcornna.universe.fossil.CollectorMenu;
import io.github.sweetcornna.universe.fossil.SkeletonDisplayMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

/**
 * 化石的客户端部分（ADR-0034 §三）：底座、骨架展台上的立体骨头，石哨兵和它扔的石块，展台和收藏家的界面，界面里转着的骨架，
 * 和开发脚本的几个命令。
 *
 * <p>开发脚本（"看着的展台"是准星对着的那个）：
 *
 * <ul>
 *   <li>{@code fossilput <类群> <部位>}：把一块骨头直接放到看着的骨架展台或化石底座上
 *   <li>{@code fossilfill <类群>}：看着的骨架展台放满同一类群
 *   <li>{@code fossilopen}：打开看着的骨架展台
 *   <li>{@code fossilpack}：开着展台的界面时按"封装"，没开就直接封装看着的展台
 *   <li>{@code fossilwant <类群> <部位>}、{@code fossilsell}、{@code fossilswap}：收藏家界面里选想换的、卖展品、换骨头
 * </ul>
 */
public final class FossilClient {
    private FossilClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerBlockEntityRenderer(UniverseBlockEntities.FOSSIL_PEDESTAL.get(), FossilPedestalRenderer::new);
            event.registerBlockEntityRenderer(UniverseBlockEntities.SKELETON_DISPLAY.get(), SkeletonDisplayRenderer::new);
            event.registerEntityRenderer(UniverseEntities.STONE_SENTRY.get(), StoneSentryRenderer::new);
            event.registerEntityRenderer(UniverseEntities.SENTRY_ROCK.get(), SentryRockRenderer::new);
        });
        modBus.addListener(RegisterMenuScreensEvent.class, event -> {
            event.register(UniverseMenus.SKELETON_DISPLAY.get(), SkeletonDisplayScreen::new);
            event.register(UniverseMenus.FOSSIL_COLLECTOR.get(), CollectorScreen::new);
        });
        modBus.addListener(RegisterPictureInPictureRenderersEvent.class,
                event -> event.register(SkeletonPreview.State.class, SkeletonPreview.Renderer::new));
        DevScript.EXTRA_VERBS.put("fossilput", args -> command("put " + args[0] + " " + args[1]));
        DevScript.EXTRA_VERBS.put("fossilfill", args -> command("fill " + args[0]));
        DevScript.EXTRA_VERBS.put("fossilopen", args -> command("open"));
        DevScript.EXTRA_VERBS.put("fossilpack", args -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof SkeletonDisplayMenu menu) {
                click(menu.containerId, SkeletonDisplayMenu.PACK);
            } else {
                command("pack");
            }
        });
        DevScript.EXTRA_VERBS.put("fossilwant", args -> {
            BoneFamily family = BoneFamily.byId(args[0].toLowerCase(Locale.ROOT));
            BonePart part = BonePart.byId(args[1].toLowerCase(Locale.ROOT));
            if (family == null || part == null) {
                Universe.LOGGER.warn("Dev script: unknown bone {} {}", args[0], args[1]);
                return;
            }
            collector(CollectorMenu.PICK + family.ordinal() * BonePart.values().length + part.ordinal());
        });
        DevScript.EXTRA_VERBS.put("fossilsell", args -> collector(CollectorMenu.SELL));
        DevScript.EXTRA_VERBS.put("fossilswap", args -> collector(CollectorMenu.SWAP));
    }

    private static void command(String rest) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.connection.sendCommand(Universe.MOD_ID + " fossil " + rest);
        }
    }

    private static void collector(int button) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof CollectorMenu menu) {
            click(menu.containerId, button);
        } else {
            Universe.LOGGER.warn("Dev script: collector screen is not open");
        }
    }

    private static void click(int containerId, int button) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(containerId, button);
        }
    }
}
