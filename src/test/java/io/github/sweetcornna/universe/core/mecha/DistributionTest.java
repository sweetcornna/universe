package io.github.sweetcornna.universe.core.mecha;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.mecha.Distribution.Dispatch;
import io.github.sweetcornna.universe.core.mecha.Distribution.Icarus;
import io.github.sweetcornna.universe.core.mecha.Distribution.Kind;
import io.github.sweetcornna.universe.core.mecha.Distribution.Node;
import io.github.sweetcornna.universe.core.mecha.Distribution.Peer;
import io.github.sweetcornna.universe.core.mecha.Distribution.Request;

class DistributionTest {
    @Test
    void nearestSupplierSendsUntilTheGapIsFilled() {
        List<Node> nodes = List.of(
                new Node(1, 30, 0, "x:belt", Icarus.SUPPLY, Peer.NONE, 100, 0, 10),
                new Node(2, 10, 0, "x:belt", Icarus.SUPPLY, Peer.NONE, 12, 0, 10),
                new Node(3, 5, 0, "x:gear", Icarus.SUPPLY, Peer.NONE, 100, 0, 10));
        List<Dispatch> plan = Distribution.plan(nodes, List.of(new Request(7, 0, 0, "x:belt", 30, 0)), 8);
        // 先用近的 2 号（12 个：8 + 4），再用 1 号补 18 个（8 + 8 + 2）
        assertEquals(List.of(
                new Dispatch(2, Kind.DELIVER, 7, "x:belt", 8),
                new Dispatch(2, Kind.DELIVER, 7, "x:belt", 4),
                new Dispatch(1, Kind.DELIVER, 7, "x:belt", 8),
                new Dispatch(1, Kind.DELIVER, 7, "x:belt", 8),
                new Dispatch(1, Kind.DELIVER, 7, "x:belt", 2)), plan);
    }

    @Test
    void botsAndRangeLimitDispatches() {
        List<Node> nodes = List.of(
                new Node(1, 10, 0, "x:belt", Icarus.BOTH, Peer.NONE, 100, 100, 1),
                new Node(2, 100, 0, "x:belt", Icarus.SUPPLY, Peer.NONE, 100, 0, 10));
        List<Dispatch> plan = Distribution.plan(nodes, List.of(new Request(7, 0, 0, "x:belt", 30, 0)), 8);
        // 1 号只有一个空闲机器人，2 号在 64 格以外
        assertEquals(List.of(new Dispatch(1, Kind.DELIVER, 7, "x:belt", 8)), plan);
        // 供应模式的配送器不收货
        plan = Distribution.plan(nodes.subList(1, 2), List.of(new Request(7, 90, 0, "x:belt", 0, 5)), 8);
        assertEquals(List.of(), plan);
    }

    @Test
    void collectorsTakeTheSurplusUpToTheirSpace() {
        List<Node> nodes = List.of(new Node(4, 0, 20, "x:stone", Icarus.COLLECT, Peer.NONE, 0, 10, 5));
        List<Dispatch> plan = Distribution.plan(nodes, List.of(new Request(1, 0, 0, "x:stone", 0, 64)), 8);
        assertEquals(List.of(new Dispatch(4, Kind.COLLECT, 1, "x:stone", 8), new Dispatch(4, Kind.COLLECT, 1, "x:stone", 2)), plan);
    }

    @Test
    void demandersFetchFromTheNearestSupplier() {
        List<Node> nodes = List.of(
                new Node(1, 0, 0, "x:ore", Icarus.NONE, Peer.DEMAND, 0, 20, 2),
                new Node(2, 40, 0, "x:ore", Icarus.NONE, Peer.SUPPLY, 50, 0, 0),
                new Node(3, 20, 0, "x:ore", Icarus.NONE, Peer.SUPPLY, 3, 0, 0),
                new Node(4, 5, 0, "x:coal", Icarus.NONE, Peer.SUPPLY, 50, 0, 0));
        List<Dispatch> plan = Distribution.plan(nodes, List.of(), 8);
        assertEquals(List.of(new Dispatch(1, Kind.FETCH, 3, "x:ore", 3), new Dispatch(1, Kind.FETCH, 2, "x:ore", 8)), plan);
    }

    @Test
    void stockIsSharedBetweenRequests() {
        List<Node> nodes = List.of(new Node(1, 0, 0, "x:belt", Icarus.SUPPLY, Peer.NONE, 10, 0, 10));
        List<Dispatch> plan = Distribution.plan(nodes, List.of(new Request(1, 0, 0, "x:belt", 8, 0), new Request(2, 0, 0, "x:belt", 8, 0)), 8);
        assertEquals(List.of(new Dispatch(1, Kind.DELIVER, 1, "x:belt", 8), new Dispatch(1, Kind.DELIVER, 2, "x:belt", 2)), plan);
    }
}
