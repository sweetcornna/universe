package io.github.sweetcornna.universe.world.planet;

import io.github.sweetcornna.universe.core.galaxy.Planet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * 星球的世界边界（ADR-0026，玩法设计 §2.3）：以原点为中心、边长等于星球的世界尺寸。离边界 {@link #WARNING} 格以内
 * 外骨骼面板提示距离，屏幕边缘泛红。母星（主世界）不设边界。
 *
 * <p>加边界以前的存档里可能有人停在边界外：进星球维度时把他挪回边界以内的地面上。
 */
public final class PlanetBorder {
    /** 离边界多少格开始提示。 */
    public static final int WARNING = 64;
    /** 把人挪回来时离边界留几格。 */
    private static final int MARGIN = 16;

    private PlanetBorder() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(LevelEvent.Load.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                Planets.planet(level).ifPresent(planet -> apply(level, planet));
            }
        });
        gameBus.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                bringInside(player);
            }
        });
        gameBus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                bringInside(player);
            }
        });
    }

    private static void apply(ServerLevel level, Planet planet) {
        WorldBorder border = level.getWorldBorder();
        if (border.getCenterX() != 0 || border.getCenterZ() != 0) {
            border.setCenter(0, 0);
        }
        if (border.getSize() != planet.worldSize()) {
            border.setSize(planet.worldSize());
        }
        border.setWarningBlocks(WARNING);
    }

    private static void bringInside(ServerPlayer player) {
        ServerLevel level = player.level();
        if (Planets.planet(level).isEmpty()) {
            return;
        }
        WorldBorder border = level.getWorldBorder();
        if (border.isWithinBounds(player.getX(), player.getZ(), -1)) {
            return;
        }
        double half = Math.max(0, border.getSize() / 2 - MARGIN);
        int x = (int) Math.floor(Math.clamp(player.getX(), border.getCenterX() - half, border.getCenterX() + half));
        int z = (int) Math.floor(Math.clamp(player.getZ(), border.getCenterZ() - half, border.getCenterZ() + half));
        // 目标区块可能还没加载：没加载时取到的高度是世界底部，人会掉出世界
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        player.stopRiding();
        player.teleportTo(x + 0.5, y, z + 0.5);
        player.sendSystemMessage(Component.translatable("message.universe.planet_border.moved", BlockPos.containing(x, y, z).toShortString()));
    }
}
