package io.github.sweetcornna.universe.core.mecha;

import java.util.ArrayList;
import java.util.List;

/**
 * 物流配送器派机器人的规划（ADR-0023）：每隔一会儿把一个维度里的配送器和玩家的物流栏位放在一起，算出这一轮派哪些机器人。
 *
 * <ul>
 *   <li>玩家缺货：最近的对机甲供应这种物品、有货、有空闲机器人的配送器送过去，一个机器人带不完就多派几个。</li>
 *   <li>玩家多了：最近的回收这种物品、下面还放得下的配送器派机器人去收。</li>
 *   <li>需求的配送器：派自己的机器人去最近的供应同种物品的配送器取货。</li>
 * </ul>
 *
 * 距离都按水平距离算，超出范围的不派。
 */
public final class Distribution {
    /** 配送器工作的范围（水平格数）。 */
    public static final double RANGE = 64;

    private Distribution() {
    }

    /** 对机甲的模式。 */
    public enum Icarus {
        NONE, SUPPLY, COLLECT, BOTH;

        public boolean supplies() {
            return this == SUPPLY || this == BOTH;
        }

        public boolean collects() {
            return this == COLLECT || this == BOTH;
        }
    }

    /** 对别的配送器的模式。 */
    public enum Peer {
        NONE, SUPPLY, DEMAND
    }

    /**
     * 一台配送器。
     *
     * @param stock 下面的容器里有几个这种物品
     * @param space 下面的容器还放得下几个
     * @param bots 空闲的机器人有几个（电不够时适配层给 0）
     */
    public record Node(int id, double x, double z, String item, Icarus icarus, Peer peer, int stock, int space, int bots) {
    }

    /**
     * 一个玩家的一个物流栏位要的东西（适配层已经减去了在路上的）。
     *
     * @param supply 要送来几个
     * @param collect 要收走几个
     */
    public record Request(int player, double x, double z, String item, int supply, int collect) {
    }

    public enum Kind {
        /** 带着货飞去玩家那里。 */
        DELIVER,
        /** 空着飞去玩家那里收货。 */
        COLLECT,
        /** 空着飞去另一台配送器取货。 */
        FETCH
    }

    /**
     * 派一个机器人。
     *
     * @param target 玩家（DELIVER、COLLECT）或供应的配送器（FETCH）
     */
    public record Dispatch(int node, Kind kind, int target, String item, int amount) {
    }

    public static List<Dispatch> plan(List<Node> nodes, List<Request> requests, int capacity) {
        int n = nodes.size();
        int[] stock = new int[n];
        int[] space = new int[n];
        int[] bots = new int[n];
        for (int i = 0; i < n; i++) {
            stock[i] = Math.max(0, nodes.get(i).stock());
            space[i] = Math.max(0, nodes.get(i).space());
            bots[i] = Math.max(0, nodes.get(i).bots());
        }
        List<Dispatch> dispatches = new ArrayList<>();
        for (Request request : requests) {
            int want = request.supply();
            while (want > 0) {
                int best = nearest(nodes, request.x(), request.z(), i -> nodes.get(i).item().equals(request.item())
                        && nodes.get(i).icarus().supplies() && stock[i] > 0 && bots[i] > 0);
                if (best < 0) {
                    break;
                }
                int amount = Math.min(Math.min(want, stock[best]), capacity);
                stock[best] -= amount;
                bots[best]--;
                want -= amount;
                dispatches.add(new Dispatch(nodes.get(best).id(), Kind.DELIVER, request.player(), request.item(), amount));
            }
            int extra = request.collect();
            while (extra > 0) {
                int best = nearest(nodes, request.x(), request.z(), i -> nodes.get(i).item().equals(request.item())
                        && nodes.get(i).icarus().collects() && space[i] > 0 && bots[i] > 0);
                if (best < 0) {
                    break;
                }
                int amount = Math.min(Math.min(extra, space[best]), capacity);
                space[best] -= amount;
                bots[best]--;
                extra -= amount;
                dispatches.add(new Dispatch(nodes.get(best).id(), Kind.COLLECT, request.player(), request.item(), amount));
            }
        }
        for (int d = 0; d < n; d++) {
            Node demand = nodes.get(d);
            while (demand.peer() == Peer.DEMAND && space[d] > 0 && bots[d] > 0) {
                final int self = d;
                int best = nearest(nodes, demand.x(), demand.z(), i -> i != self && nodes.get(i).item().equals(demand.item())
                        && nodes.get(i).peer() == Peer.SUPPLY && stock[i] > 0);
                if (best < 0) {
                    break;
                }
                int amount = Math.min(Math.min(space[d], stock[best]), capacity);
                stock[best] -= amount;
                space[d] -= amount;
                bots[d]--;
                dispatches.add(new Dispatch(demand.id(), Kind.FETCH, nodes.get(best).id(), demand.item(), amount));
            }
        }
        return dispatches;
    }

    private interface Filter {
        boolean test(int index);
    }

    private static int nearest(List<Node> nodes, double x, double z, Filter filter) {
        int best = -1;
        double bestDistance = RANGE * RANGE;
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            double d = (node.x() - x) * (node.x() - x) + (node.z() - z) * (node.z() - z);
            if (d <= bestDistance && filter.test(i)) {
                if (best < 0 || d < bestDistance) {
                    best = i;
                    bestDistance = d;
                }
            }
        }
        return best;
    }
}
