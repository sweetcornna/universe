package io.github.sweetcornna.universe.core.fishing;

/** 钓上来的杂物（ADR-0034 §一）：卖不了几个钱，锈蚀零件能回收成一点金属。 */
public enum Junk {
    RUSTED_PARTS("rusted_parts", "锈蚀零件", "Rusted Parts", 8),
    TANGLED_WEED("tangled_weed", "缠结的藻团", "Tangled Weed", 1),
    BROKEN_CANISTER("broken_canister", "破损的储罐", "Broken Canister", 6);

    private final String id;
    private final String nameZh;
    private final String nameEn;
    private final double price;

    Junk(String id, String nameZh, String nameEn, double price) {
        this.id = id;
        this.nameZh = nameZh;
        this.nameEn = nameEn;
        this.price = price;
    }

    public String id() {
        return this.id;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    public double price() {
        return this.price;
    }
}
