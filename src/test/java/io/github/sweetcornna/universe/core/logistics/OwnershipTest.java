package io.github.sweetcornna.universe.core.logistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** 控制面板远程改设置的权限：主人、队友、管理员，旧档里没主人的谁都能改。 */
class OwnershipTest {
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID CAROL = UUID.randomUUID();

    @Test
    void ownerAndLegacy() {
        assertTrue(Ownership.mayEdit(ALICE, ALICE, false, owner -> false), "主人自己");
        assertFalse(Ownership.mayEdit(ALICE, BOB, false, owner -> false), "别的玩家");
        assertTrue(Ownership.mayEdit(null, BOB, false, owner -> false), "旧档里没主人的谁都能改");
    }

    @Test
    void teammateAndOperator() {
        assertTrue(Ownership.mayEdit(ALICE, BOB, false, owner -> owner.equals(ALICE)), "队友");
        assertTrue(Ownership.mayEdit(ALICE, BOB, true, owner -> false), "管理员");
    }

    @Test
    void routeDeletedByCreatorOrStationOwner() {
        List<Optional<UUID>> aliceStation = List.of(Optional.empty(), Optional.of(ALICE));
        assertTrue(Ownership.mayDeleteRoute(BOB, List.of(), BOB, false, owner -> false), "建的人");
        assertTrue(Ownership.mayDeleteRoute(BOB, aliceStation, ALICE, false, owner -> false), "路线走了自己的站");
        assertTrue(Ownership.mayDeleteRoute(BOB, aliceStation, CAROL, false, owner -> owner.equals(ALICE)), "站主人的队友");
        assertFalse(Ownership.mayDeleteRoute(BOB, aliceStation, CAROL, false, owner -> false), "不相干的人");
        assertFalse(Ownership.mayDeleteRoute(BOB, List.of(Optional.empty()), CAROL, false, owner -> false), "没主人的站不算");
        assertTrue(Ownership.mayDeleteRoute(null, List.of(), CAROL, false, owner -> false), "旧档里没记建的人的路线");
        assertTrue(Ownership.mayDeleteRoute(BOB, List.of(), CAROL, true, owner -> false), "管理员");
    }

    @Test
    void stationAndRouteKeepTheirOwner() {
        Station station = new Station(UUID.randomUUID(), StationKind.INTERSTELLAR, "dim", 0, 64, 0, new Location(0, 1.0, 0, 0, 0, "s0/p0"));
        assertTrue(station.owner().isEmpty(), "新建的站没有主人");
        station.setOwner(ALICE);
        assertEquals(ALICE, station.owner().orElseThrow());
        assertNull(ItemRoute.planets("minecraft:iron_ingot", "s0/p0", "s0/p3").creator(), "没记建的人");
        ItemRoute route = new ItemRoute(UUID.randomUUID(), "minecraft:iron_ingot", false, "s0/p0", "s0/p3", BOB);
        assertEquals(BOB, route.creator());
        assertTrue(route.sameAs(ItemRoute.planets("minecraft:iron_ingot", "s0/p0", "s0/p3")), "建的人不同也算重复的路线");
    }
}
