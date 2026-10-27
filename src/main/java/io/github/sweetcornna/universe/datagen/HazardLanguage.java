package io.github.sweetcornna.universe.datagen;

import io.github.sweetcornna.universe.core.hazard.HazardKind;

/** M19 的文字：科技的相邻和超频、局部危害（子代理 E，ADR-0034 §五、§六）。 */
final class HazardLanguage {
    /** 同时写中文和英文的一条。 */
    interface Both {
        void add(String key, String zh, String en);
    }

    private HazardLanguage() {
    }

    static void add(Both both) {
        techLayout(both);
        blocks(both);
        hud(both);
        commands(both);
        guide(both);
        both.add("gamerule.universe.local_hazards", "局部危害频率（%）", "Local Hazard Frequency (%)");
        both.add("gamerule.universe.local_hazards.description",
                "火山喷发、孢子云、尘卷风多久来一次。0 时这些和间歇泉都停下。0–300。",
                "How often eruptions, spore clouds and dust devils happen. At 0 they stop, geysers too. 0–300.");
    }

    private static void techLayout(Both both) {
        both.add("gui.universe.tech.boost.adjacent", "相邻 +%s%%", "Adjacent +%s%%");
        both.add("gui.universe.tech.boost.supercharged", "超频 ×%s", "Supercharged ×%s");
        both.add("gui.universe.tech.boost.both", "相邻 +%s%%，超频 ×%s", "Adjacent +%s%%, supercharged ×%s");
        both.add("gui.universe.tech.supercharged_slot", "超频格：装进来的模块数值 ×%s。同种模块挨着放，每挨一个再 +5%%。",
                "Supercharged slot: a module here gets ×%s. Same-type modules side by side add 5%% per neighbor.");
        both.add("gui.universe.tech.supercharged_locked", "超频格，还没解锁。解锁以后装进来的模块数值 ×%s。",
                "Supercharged slot, still locked. Once unlocked, a module here gets ×%s.");
    }

    private static void blocks(Both both) {
        String[][] kinds = {
                {"hot", "热泉喷口", "Scalding Geyser Vent", "硫华", "Sulfur Sinter"},
                {"toxic", "毒泉喷口", "Toxic Geyser Vent", "盐华", "Salt Crust"},
                {"cold", "冷泉喷口", "Cryo Geyser Vent", "霜华", "Frost Crust"},
                {"strange", "异泉喷口", "Strange Geyser Vent", "硅华", "Silica Sinter"},
        };
        for (String[] kind : kinds) {
            both.add("block.universe." + kind[0] + "_geyser_vent", kind[1], kind[2]);
            both.add("block.universe." + kind[0] + "_geyser_crust", kind[3], kind[4]);
        }
        both.add("entity.universe.lava_bomb", "熔岩弹", "Lava Bomb");
        both.add("entity.universe.spore_cloud", "孢子云", "Spore Cloud");
        both.add("entity.universe.dust_devil", "尘卷风", "Dust Devil");
        for (HazardKind kind : HazardKind.values()) {
            both.add("hazard.universe.kind." + kind.id(), kind.nameZh(), kind.nameEn());
        }
    }

    private static void hud(Both both) {
        both.add("hud.universe.hazard.geyser", "脚下咕嘟作响，间歇泉要喷了", "The ground gurgles. Geyser about to blow");
        both.add("hud.universe.hazard.steam", "水汽柱里：%s防护掉得飞快", "In the steam: %s protection draining fast");
        both.add("hud.universe.hazard.spores", "孢子云：毒性防护消耗 ×%s", "Spore cloud: toxic protection drains ×%s");
        both.add("hud.universe.hazard.eruption_warning", "地面在震动", "The ground is shaking");
        both.add("hud.universe.hazard.eruption", "火山喷发，当心落下来的熔岩弹", "Eruption! Watch for falling lava bombs");
        both.add("hud.universe.hazard.dust_devil", "尘卷风过来了", "Dust devil incoming");
    }

    private static void commands(Both both) {
        both.add("commands.universe.hazard.started", "在附近放出了%s", "Started nearby: %s");
        both.add("commands.universe.hazard.failed", "这里放不了%s", "Can't start here: %s");
        both.add("commands.universe.hazard.cleared", "清掉了 %s 处局部危害", "Cleared %s local hazards");
    }

    /** 手册「生存」一章里的两页：模块的摆法、局部危害。 */
    private static void guide(Both both) {
        String g = "guide.universe.survival";
        both.add(g + ".tech_layout.title", "模块的摆法", "Module Layout");
        both.add(g + ".tech_layout",
                "技术格排成网格：外骨骼 4×3，多功能工具 4×2，驾驶台 3×2。\n\n"
                        + "同一种模块上下左右挨着，每挨一个 +5%，最多 +20%\n"
                        + "带橙色光圈的是超频格，放进去的模块 ×1.25，和相邻的加成相乘\n"
                        + "外骨骼 3 个超频格，可能落在还没开的格子上；多功能工具、驾驶台各 2 个\n"
                        + "超频格的位置每人、每件工具、每座驾驶台都不一样\n"
                        + "同种模块还是只算最好的三个\n"
                        + "鼠标停在模块上能看到加成",
                "Tech slots form a grid: 4×3 on the exosuit, 4×2 on the multitool, 3×2 on the helm.\n\n"
                        + "Same-type modules side by side get +5% per neighbor, up to +20%\n"
                        + "Slots with an orange glow are supercharged: ×1.25, multiplied with the neighbor bonus\n"
                        + "The exosuit has 3, which may sit on slots you haven't opened yet; the multitool and helm have 2 each\n"
                        + "Where they are differs per player, per tool and per helm\n"
                        + "Only the best three of a type still count\n"
                        + "Hover a module to see its bonus");
        both.add(g + ".local_hazards.title", "局部危害", "Local Hazards");
        both.add(g + ".local_hazards",
                "有的星球上会碰到局部危害，来之前 HUD 上有提示。\n\n"
                        + "间歇泉：冰冻、毒雾、灼热、奇异星球上。安静一阵，咕嘟 3 秒，喷 5 秒。水汽柱 15 格高，能把人托上去，也烫人、毒人或冻人。喷口边的结壳能挖\n"
                        + "火山喷发：灼热星球上，先震 3 秒，再下 20 秒熔岩弹\n"
                        + "孢子云：毒雾星球的毒沼、菌林里，云里毒性防护掉得快 4 倍\n"
                        + "尘卷风：有风的荒漠、峡谷里，把东西卷起来甩出去，摔下来才伤人\n\n"
                        + "基地信标和接管的聚落附近不会有。世界设置里能调频率。",
                "Some planets have local hazards, with a HUD warning first. None near base beacons or claimed settlements; "
                        + "world settings set how often.\n"
                        + "Geysers (frozen, toxic, scorched, exotic): a 3 s gurgle, then 5 s of steam 15 m high that lifts you and scalds, poisons "
                        + "or freezes. The crust round the vent can be mined\n"
                        + "Eruptions (scorched): 3 s of shaking, then 20 s of lava bombs\n"
                        + "Spore clouds (toxic marshes, fungal forests): toxic protection drains 4× as fast\n"
                        + "Dust devils (windy deserts, canyons): fling things up; only the fall hurts");
    }
}
