package io.github.sweetcornna.universe.core.trade;

import java.util.Map;

/**
 * 原料的基准价（信用点）。别的东西的价钱都从这里顺着配方推出来（{@link ValueGraph}）。
 *
 * <p>以一个铁矿 4 信用点为准；以 {@code #} 开头的是物品标签，适配层展开成标签里的每样东西。由设备从世界里抽出来、
 * 没有配方的东西（原油、水、氢、重氢、临界光子）和探索得到的东西也在这里定价。
 */
public final class RawValues {
    public static final Map<String, Double> DEFAULT = Map.ofEntries(
            // 原版：采来的
            Map.entry("minecraft:raw_iron", 4.0),
            Map.entry("minecraft:raw_copper", 4.0),
            Map.entry("minecraft:raw_gold", 12.0),
            Map.entry("minecraft:coal", 3.0),
            Map.entry("minecraft:charcoal", 3.0),
            Map.entry("minecraft:redstone", 3.0),
            Map.entry("minecraft:lapis_lazuli", 4.0),
            Map.entry("minecraft:quartz", 5.0),
            Map.entry("minecraft:diamond", 60.0),
            Map.entry("minecraft:emerald", 40.0),
            Map.entry("minecraft:glowstone_dust", 4.0),
            Map.entry("minecraft:amethyst_shard", 6.0),
            Map.entry("minecraft:cobblestone", 0.2),
            Map.entry("minecraft:cobbled_deepslate", 0.2),
            Map.entry("minecraft:dirt", 0.1),
            Map.entry("minecraft:sand", 0.3),
            Map.entry("minecraft:red_sand", 0.3),
            Map.entry("minecraft:gravel", 0.3),
            Map.entry("minecraft:clay_ball", 1.0),
            Map.entry("minecraft:flint", 0.5),
            Map.entry("minecraft:obsidian", 3.0),
            Map.entry("minecraft:ice", 0.5),
            Map.entry("minecraft:snowball", 0.1),
            Map.entry("#minecraft:logs", 2.0),
            Map.entry("#minecraft:leaves", 0.2),
            Map.entry("#c:dyes", 1.0),
            Map.entry("minecraft:bamboo", 0.5),
            Map.entry("minecraft:sugar_cane", 1.0),
            Map.entry("minecraft:wheat", 1.0),
            Map.entry("minecraft:cactus", 0.5),
            Map.entry("minecraft:kelp", 0.5),
            Map.entry("minecraft:string", 2.0),
            Map.entry("minecraft:leather", 3.0),
            Map.entry("minecraft:feather", 1.0),
            Map.entry("minecraft:bone", 1.0),
            Map.entry("minecraft:egg", 1.0),
            Map.entry("minecraft:gunpowder", 4.0),
            Map.entry("minecraft:slime_ball", 4.0),
            Map.entry("minecraft:ender_pearl", 15.0),
            Map.entry("minecraft:blaze_rod", 15.0),
            Map.entry("minecraft:honeycomb", 3.0),
            // 本模组：星球矿石掉的
            Map.entry("universe:raw_titanium", 8.0),
            Map.entry("universe:raw_silicon", 6.0),
            Map.entry("universe:raw_magnetite", 6.0),
            Map.entry("universe:raw_uranium", 14.0),
            Map.entry("universe:sulfur", 5.0),
            Map.entry("universe:fire_ice", 9.0),
            Map.entry("universe:optical_crystal", 20.0),
            Map.entry("universe:organic_crystal", 12.0),
            Map.entry("universe:salt", 3.0),
            Map.entry("universe:strange_matter", 120.0),
            // 设备抽出来的，按平衡模型（core.balance.Progression）折合的矿定价：一台原油萃取站的产量顶五台采矿机，涌泉又少，
            // 原油按半个铁矿；重氢是一个氢加上分馏塔的工时；一个临界光子要六张多太阳帆发的电
            Map.entry("universe:crude_oil", 2.0),
            Map.entry("universe:water", 0.5),
            Map.entry("universe:hydrogen", 1.5),
            Map.entry("universe:deuterium", 4.0),
            Map.entry("universe:critical_photon", 100.0),
            // 探索和战斗得到的
            Map.entry("universe:survey_data", 40.0),
            Map.entry("universe:navigation_data", 30.0),
            Map.entry("universe:sentinel_scrap", 25.0),
            Map.entry("universe:pirate_scrap", 60.0),
            Map.entry("universe:alien_meat", 3.0),
            Map.entry("universe:alien_hide", 4.0),
            // 喂养动物的产物（ADR-0017）
            Map.entry("universe:alien_egg", 5.0),
            Map.entry("universe:alien_fluff", 5.0),
            Map.entry("universe:bio_gel", 6.0),
            // 只能在空间站买、在坠毁飞船里拆到的 Mk.II 部件（ADR-0017）
            Map.entry("universe:ship_thruster_mk2", 260.0),
            Map.entry("universe:fuel_tank_mk2", 220.0),
            Map.entry("universe:impulse_engine_mk2", 1400.0),
            Map.entry("universe:warp_drive_mk2", 2200.0),
            Map.entry("universe:alloy_hull_plating", 24.0),
            // 应急信号接收器只在空间站买、在废弃货船里找到（ADR-0024）
            Map.entry("universe:emergency_receiver", 500.0));

    private RawValues() {
    }
}
