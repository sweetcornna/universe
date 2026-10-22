package io.github.sweetcornna.universe.client.darkfog.shield;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.darkfog.shield.ShieldPayloads;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 行星护盾的客户端（ADR-0028 §八）：服务端发来这个维度里展开着的穹顶，这里记下来给 {@link ShieldDomeRenderer} 画；
 * 挨打的地方闪一下，能量打光的穹顶碎开淡掉。发生器的界面也在这里登记。
 */
public final class ShieldClient {
    /** 闪光多久淡完（刻）。 */
    static final int FLASH_TICKS = 14;
    /** 打碎的穹顶多久淡完（刻）。 */
    static final int COLLAPSE_TICKS = 24;

    /** 挨打的一闪。 */
    record Flash(BlockPos generator, Vec3 at, float strength, long start) {
    }

    /** 能量打光、正在淡掉的穹顶。 */
    record Collapse(Vec3 center, float radius, long start) {
    }

    private static List<ShieldPayloads.Dome> domes = List.of();
    private static final List<Flash> FLASHES = new ArrayList<>();
    private static final List<Collapse> COLLAPSES = new ArrayList<>();

    private ShieldClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterMenuScreensEvent.class,
                event -> event.register(UniverseMenus.PLANETARY_SHIELD_GENERATOR.menuType(), ShieldScreen::new));
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> {
            event.register(ShieldPayloads.Domes.TYPE, ShieldClient::handleDomes);
            event.register(ShieldPayloads.Hit.TYPE, ShieldClient::handleHit);
        });
        gameBus.addListener(SubmitCustomGeometryEvent.class, ShieldDomeRenderer::submit);
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            domes = List.of();
            FLASHES.clear();
            COLLAPSES.clear();
        });
    }

    private static void handleDomes(ShieldPayloads.Domes payload, IPayloadContext context) {
        context.enqueueWork(() -> domes = List.copyOf(payload.domes()));
    }

    private static void handleHit(ShieldPayloads.Hit hit, IPayloadContext context) {
        context.enqueueWork(() -> {
            long now = now();
            FLASHES.add(new Flash(hit.generator(), new Vec3(hit.x(), hit.y(), hit.z()), hit.strength(), now));
            if (hit.collapsed()) {
                for (ShieldPayloads.Dome dome : domes) {
                    if (dome.generator().equals(hit.generator())) {
                        COLLAPSES.add(new Collapse(center(dome.generator()), dome.radius(), now));
                    }
                }
            }
        });
    }

    static long now() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0 : minecraft.level.getGameTime();
    }

    /** 穹顶的球心：和服务端一样，发生器下半截的顶面中心。 */
    static Vec3 center(BlockPos generator) {
        return new Vec3(generator.getX() + 0.5, generator.getY() + 1, generator.getZ() + 0.5);
    }

    static List<ShieldPayloads.Dome> domes() {
        return domes;
    }

    /** 还没淡完的闪光（顺手扔掉淡完的）。 */
    static List<Flash> flashes(long now) {
        FLASHES.removeIf(flash -> now - flash.start() > FLASH_TICKS || now < flash.start());
        return FLASHES;
    }

    static List<Collapse> collapses(long now) {
        COLLAPSES.removeIf(collapse -> now - collapse.start() > COLLAPSE_TICKS || now < collapse.start());
        return COLLAPSES;
    }

    /** 验收脚本的日志里看：客户端知道几个穹顶。 */
    public static int domeCount() {
        return domes.size();
    }
}
