package io.github.sweetcornna.universe.core.logistics;

import java.util.UUID;

/**
 * 星际物流的物品路线（ADR-0033 §三）：某种物品从一颗星球（或一个星系）运到另一颗星球（或星系）。全局的设置，
 * 目标星球上还没有站也能设，等那边建了需求这种物品的站就按路线走。
 *
 * @param systems 星系路线；否则是星球路线
 * @param from    星球路线是天体路径（{@link Location#body()}，比如 {@code s0/p1}），星系路线是星系序号
 * @param to      同上
 */
public record ItemRoute(UUID id, String item, boolean systems, String from, String to) {
    /** 一共最多设多少条。 */
    public static final int MAX = 256;

    public static ItemRoute planets(String item, String fromBody, String toBody) {
        return new ItemRoute(UUID.randomUUID(), item, false, fromBody, toBody);
    }

    public static ItemRoute systems(String item, int fromSystem, int toSystem) {
        return new ItemRoute(UUID.randomUUID(), item, true, Integer.toString(fromSystem), Integer.toString(toSystem));
    }

    /** 从 {@code supply} 运到 {@code demand} 的这种物品走不走这条路线。 */
    public boolean matches(String item, Location supply, Location demand) {
        return this.item.equals(item) && this.startsAt(supply) && this.endsAt(demand);
    }

    /** 这个位置在出发的一头。 */
    public boolean startsAt(Location location) {
        return this.at(this.from, location);
    }

    /** 这个位置在到达的一头。 */
    public boolean endsAt(Location location) {
        return this.at(this.to, location);
    }

    private boolean at(String end, Location location) {
        return this.systems ? end.equals(Integer.toString(location.system())) : end.equals(location.body());
    }

    /** 出发的星系序号（星球路线取天体路径开头的 {@code s<n>}；读不出来是 -1）。 */
    public int fromSystem() {
        return system(this.systems, this.from);
    }

    public int toSystem() {
        return system(this.systems, this.to);
    }

    private static int system(boolean systems, String end) {
        String digits = systems ? end : end.startsWith("s") ? end.substring(1, end.indexOf('/') < 0 ? end.length() : end.indexOf('/')) : "";
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 两头一样的路线没有意义（同一颗星球上不走运输船）。 */
    public boolean valid() {
        return !this.item.isEmpty() && !this.from.isEmpty() && !this.to.isEmpty() && !this.from.equals(this.to);
    }

    /** 物品和两头都一样（新建时不让重复）。 */
    public boolean sameAs(ItemRoute other) {
        return this.item.equals(other.item) && this.systems == other.systems && this.from.equals(other.from) && this.to.equals(other.to);
    }
}
