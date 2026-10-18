package io.github.sweetcornna.universe.entity.ship;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/** 能被飞船的采矿光束开采的实体（小行星，ADR-0017）：光束照着时每刻调用一次。 */
public interface ShipBeamTarget {
    void beamed(ServerLevel level, ShipEntity ship, Player pilot, float damage);
}
