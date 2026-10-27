package io.github.sweetcornna.universe.core.trade;

import java.util.Map;

/**
 * 原料的基准价（信用点）。别的东西的价钱都从这里顺着配方推出来（{@link ValueGraph}）。
 *
 * <p>以一个铁矿 4 信用点为准；以 {@code #} 开头的是物品标签，适配层展开成标签里的每样东西。由设备从世界里抽出来、
 * 没有配方的东西（原油、水、氢、重氢、临界光子）和探索得到的东西也在这里定价。
 */
public final class RawValues {
    public static final Map<String, Double> DEFAULT = withExtras(Map.ofEntries(
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
            // 营养处理器用完把桶还回来（ADR-0026），只算牛奶
            Map.entry("minecraft:milk_bucket", 2.0),
            Map.entry("minecraft:cod", 2.0),
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
            // 地面守卫机的零件（ADR-0026）：三级通缉以上才出四足机，五级才有步行机
            Map.entry("universe:quadruped_servo", 350.0),
            Map.entry("universe:walker_brain", 1500.0),
            Map.entry("universe:pirate_scrap", 60.0),
            Map.entry("universe:alien_meat", 3.0),
            Map.entry("universe:alien_hide", 4.0),
            // 喂养动物的产物（ADR-0017）
            Map.entry("universe:alien_egg", 5.0),
            Map.entry("universe:alien_fluff", 5.0),
            Map.entry("universe:bio_gel", 6.0),
            // 资源植物的产物（ADR-0026）：遍地都是的便宜，引力果是违禁品，卖得贵
            Map.entry("universe:oxygen", 1.0),
            Map.entry("universe:sodium", 1.2),
            Map.entry("universe:star_bulb", 3.0),
            Map.entry("universe:frost_crystal", 1.5),
            Map.entry("universe:solanium", 6.0),
            Map.entry("universe:gamma_root", 2.0),
            Map.entry("universe:fungal_mould", 2.0),
            Map.entry("universe:cactus_flesh", 3.0),
            Map.entry("universe:gravitino_ball", 120.0),
            Map.entry("universe:marrow_bulb", 4.0),
            Map.entry("universe:kelp_pod", 1.2),
            // 水下（ADR-0026）：生鱼和鳕鱼一样；活珍珠只能卖，深水海底平均六七个区块一颗，一趟下潜捞上几千；
            // 定得比 Mk.II 部件还贵，海就比造船来钱快了。没撬开的蚌里有一颗
            Map.entry("universe:alien_fish", 2.0),
            Map.entry("universe:living_pearl", 600.0),
            Map.entry("universe:clam", 600.0),
            // 野外的地标（ADR-0026）：原作里化石两千到两万、废料五千到两万五、风暴晶体六千，这里和活珍珠一样按八分之一定。
            // 化石坑一处 6–10 块，平均一块 500 左右；打捞舱平均两块多废料；雷击石要在雷暴里追着雷找
            Map.entry("universe:shell_fossil", 250.0),
            Map.entry("universe:arthropod_fossil", 800.0),
            Map.entry("universe:skull_fossil", 2500.0),
            Map.entry("universe:salvaged_scrap", 600.0),
            Map.entry("universe:fulgurite", 750.0),
            // 只能在空间站买、在坠毁飞船里拆到的 Mk.II 部件（ADR-0017）
            Map.entry("universe:ship_thruster_mk2", 260.0),
            Map.entry("universe:fuel_tank_mk2", 220.0),
            Map.entry("universe:impulse_engine_mk2", 1400.0),
            Map.entry("universe:warp_drive_mk2", 2200.0),
            Map.entry("universe:alloy_hull_plating", 24.0),
            // 应急信号接收器只在空间站买、在废弃货船里找到（ADR-0024）
            Map.entry("universe:emergency_receiver", 500.0)));

    /** 后来各个玩法自己的价钱表（鱼、骨头、打捞物……）并进来，表在各自的 core 类里。 */
    private static Map<String, Double> withExtras(Map<String, Double> base) {
        Map<String, Double> all = new java.util.HashMap<>(base);
        all.putAll(io.github.sweetcornna.universe.core.fishing.FishValues.prices());
        // M19 钓鱼（A）的价钱（all.putAll(...)）加在这行上面
        // M19 聚落（B）的价钱（all.putAll(...)）加在这行上面
        // 40 种骨头（ADR-0034 §三）：常见 300、少见 700、稀有 1500、奇异种 3500，再按部位乘（头骨最贵、尾骨最便宜）
        all.putAll(io.github.sweetcornna.universe.core.fossil.CollectorTrade.rawValues());
        // M19 化石（C）的价钱（all.putAll(...)）加在这行上面
        all.putAll(io.github.sweetcornna.universe.core.deepspace.DeepSpacePrices.VALUES);
        // M19 深空和重力枪（D）的价钱（all.putAll(...)）加在这行上面
        // M19 科技布局和局部危害（E）的价钱（all.putAll(...)）加在这行上面
        all.putAll(io.github.sweetcornna.universe.core.chem.GasPrices.VALUES);
        // M20 化学（A）的价钱（all.putAll(...)）加在这行上面
        // M20 反应釜（B）的价钱（all.putAll(...)）加在这行上面
        // 样本、菌种、改造种苗没有配方：采来的、培养出来的、编辑出来的（ADR-0036 §五）
        all.putAll(Map.of("universe:sample", 2.0, "universe:strain_tube", 30.0, "universe:engineered_seedling", 60.0));
        // M20 微生物和基因（C）的价钱（all.putAll(...)）加在这行上面
        // M20 生物反应器（D）的价钱（all.putAll(...)）加在这行上面
        // M20 星球改造（E）的价钱（all.putAll(...)）加在这行上面
        return Map.copyOf(all);
    }

    private RawValues() {
    }
}
