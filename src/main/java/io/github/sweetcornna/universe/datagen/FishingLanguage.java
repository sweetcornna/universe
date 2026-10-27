package io.github.sweetcornna.universe.datagen;

import io.github.sweetcornna.universe.core.fishing.Bait;
import io.github.sweetcornna.universe.core.fishing.FishSpecies;
import io.github.sweetcornna.universe.core.fishing.FishTable;
import io.github.sweetcornna.universe.core.fishing.Habitat;
import io.github.sweetcornna.universe.core.fishing.Junk;
import io.github.sweetcornna.universe.core.fishing.Rarity;

/** M19 的文字：钓鱼（子代理 A，ADR-0034 §一）。鱼、饵、杂物的名字在 core 的表里，这里照着写出来。 */
final class FishingLanguage {
    /** 同时写中文和英文的一条。 */
    interface Both {
        void add(String key, String zh, String en);
    }

    private FishingLanguage() {
    }

    static void add(Both both) {
        items(both);
        terms(both);
        messages(both);
        hud(both);
        screens(both);
        guide(both);
    }

    private static void items(Both both) {
        for (FishSpecies species : FishTable.ALL) {
            both.add("item.universe." + species.itemId(), species.nameZh(), species.nameEn());
        }
        for (Bait bait : Bait.specials()) {
            both.add("item.universe." + bait.id(), bait.nameZh(), bait.nameEn());
        }
        for (Junk junk : Junk.values()) {
            both.add("item.universe." + junk.id(), junk.nameZh(), junk.nameEn());
        }
        both.add("item.universe.message_bottle", "漂流瓶", "Message in a Bottle");
        both.add("item.universe.sealed_cache", "密封补给舱", "Sealed Supply Pod");
        both.add("item.universe.fishing_rig", "钓鱼器", "Fishing Rig");
        both.add("item.universe.fishing_rig.desc", "甩出浮标钓鱼，最远 24 格；按住右键收线，潜行右键放饵",
                "Casts a bobber up to 24 m. Hold right-click to reel in, sneak + right-click for bait");
        both.add("block.universe.fish_trap", "捕鱼笼", "Fish Trap");
        both.add("entity.universe.fishing_bobber", "浮标", "Bobber");
        both.add("message.universe.multitool.mode.fishing", "多功能工具：钓鱼器", "Multitool: Fishing Rig");
        both.add("hud.universe.multitool.mode.fishing", "钓鱼器", "Fishing Rig");
        both.add("tech.universe.fishing", "钓鱼", "Fishing");

        both.add("tooltip.universe.fish.kind", "%s · %s · %s", "%s · %s · %s");
        both.add("tooltip.universe.fish.release", "对着水右键放生", "Right-click water to release");
        both.add("tooltip.universe.bait.stink_bait", "等鱼的时间减半", "Halves the wait for a bite");
        both.add("tooltip.universe.bait.spinner_lure", "少见、稀有的鱼来得多一倍", "Doubles the odds of uncommon and rare fish");
        both.add("tooltip.universe.bait.deep_sinker", "大鱼、巨鱼来得多一倍，钓上来也偏重", "Doubles the odds of large and huge fish, and they run heavier");
        both.add("tooltip.universe.bait.glow_bait", "夜里的鱼白天也会咬（少一半），夜里多一倍",
                "Night fish bite by day at half the odds, and twice as often at night");
        both.add("tooltip.universe.bait.static_lure", "风暴里才出来的鱼不下风暴也会咬（三分之一）",
                "Storm fish bite in calm weather at a third of the odds");
        both.add("tooltip.universe.bait.star_core_bait", "传说的鱼咬钩的概率 ×4", "Legendary odds ×4");
        both.add("tooltip.universe.bait.hint", "放进钓鱼器的饵格：钓鱼模式下潜行右键", "Goes in the Fishing Rig's bait slot: sneak + right-click in fishing mode");
        both.add("tooltip.universe.message_bottle", "里面卷着一张纸条，右键打开", "There's a note rolled up inside. Right-click to open");
        both.add("tooltip.universe.sealed_cache", "货船掉下来的补给舱，右键撬开", "A supply pod lost from a freighter. Right-click to pry it open");
    }

    private static void terms(Both both) {
        for (Habitat habitat : Habitat.values()) {
            both.add("fishing.universe.habitat." + habitat.key(), habitat.nameZh(), habitat.nameEn());
        }
        for (Rarity rarity : Rarity.values()) {
            both.add("fishing.universe.rarity." + rarity.key(), rarity.nameZh(), rarity.nameEn());
        }
        both.add("fishing.universe.size.small", "小型", "Small");
        both.add("fishing.universe.size.medium", "中型", "Medium");
        both.add("fishing.universe.size.large", "大型", "Large");
        both.add("fishing.universe.size.huge", "巨型", "Huge");
        both.add("fishing.universe.time.day", "白天", "Daytime");
        both.add("fishing.universe.time.night", "夜里", "Night");
        both.add("fishing.universe.weather.storm", "风暴", "Storms");
        both.add("fishing.universe.depth.shallow", "浅水", "Shallows");
        both.add("fishing.universe.depth.deep", "深水", "Deep water");
        both.add("fishing.universe.anytime", "随时", "Any time");
        both.add("fishing.universe.kg", "%s 千克", "%s kg");
    }

    private static void messages(Both both) {
        String base = "message.universe.fishing.";
        both.add(base + "lava", "熔岩里钓不了鱼", "Nothing lives in lava");
        both.add(base + "not_water", "浮标没落进水里", "The bobber missed the water");
        both.add(base + "missed", "鱼跑了，咬钩时要马上右键", "It got away. Right-click as soon as it bites");
        both.add(base + "snapped", "线断了，饵也没了", "The line snapped and took the bait");
        both.add(base + "escaped", "线松了太久，鱼脱钩了", "The line went slack and it threw the hook");
        both.add(base + "released", "放生了%s。接下来三次甩竿，鱼来得快一些", "Released %s. Fish bite sooner for your next three casts");
        both.add(base + "bottle_no_map", "这里没有能标出来的地方，瓶子先留着", "Nothing around here to mark. Keep the bottle for later");
        both.add(base + "bottle_blank", "纸条泡烂了，什么也看不清", "The note is soaked through. You can't make anything out");
        both.add(base + "bottle", "纸条上画着草图：往%2$s %3$s 格有一处%1$s，已标在导航上", "A sketch on the note: %1$s, %3$s m %2$s. Marked as your waypoint");
        both.add(base + "cache_opened", "补给舱撬开了", "The pod cracks open");
    }

    private static void hud(Both both) {
        String base = "hud.universe.fishing.";
        both.add(base + "approach", "水下有动静", "Something's moving down there");
        both.add(base + "grounded", "浮标搁在岸上了，右键收回", "The bobber is on dry ground. Right-click to reel it in");
        both.add(base + "waiting", "等鱼咬钩", "Waiting for a bite");
        both.add(base + "bite", "咬钩了！按%s", "Bite! Press %s");
        both.add(base + "tension", "张力", "Tension");
        both.add(base + "dash", "在挣！松手", "It's running! Ease off");
        both.add(base + "warning", "要挣了", "About to run");
        both.add(base + "slack", "线太松", "Slack line");
        both.add(base + "caught", "钓上来了", "Caught");
        both.add(base + "first", "新品种", "New species");
        both.add(base + "record", "新纪录", "New record");
    }

    private static void screens(Both both) {
        both.add("gui.universe.bait.title", "饵", "Bait");
        both.add("gui.universe.bait.empty", "不放饵也能钓，就是来得慢", "You can fish without bait, it just takes longer");
        both.add("gui.universe.bait.food", "吃的：等鱼的时间 ×0.8", "Food: wait ×0.8");
        both.add("gui.universe.bait.dish", "做好的菜：等鱼的时间 ×0.6，少见以上 +10%%", "Cooked dish: wait ×0.6, +10%% uncommon and better");
        both.add("gui.universe.bait.rule", "每钩住一条用掉一个", "One is used per hooked catch");

        both.add("gui.universe.fish_trap.status.working", "在捕鱼", "Fishing");
        both.add("gui.universe.fish_trap.status.dry", "没整个泡在水里", "Not fully under water");
        both.add("gui.universe.fish_trap.status.full", "满了，取出来才接着捕", "Full. Empty it to keep going");
        both.add("gui.universe.fish_trap.bait", "饵", "Bait");
        both.add("gui.universe.fish_trap.water", "水域：%s", "Water: %s");
        both.add("gui.universe.fish_trap.no_water", "无", "none");
        both.add("gui.universe.fish_trap.baited", "有饵，大约一分半一条", "Baited: about one every 90 s");
        both.add("gui.universe.fish_trap.unbaited", "没饵，大约三分钟一条", "No bait: about one every 3 min");

        both.add("gui.universe.discovery.tab.fishing", "钓鱼", "Fishing");
        both.add("gui.universe.fishing.footer", "点水域换页，滚轮也行 · 点一格看详情 · K 关闭", "Click or scroll to switch waters · click a fish for details · K to close");
        both.add("gui.universe.fishing.habitats", "水域", "Waters");
        both.add("gui.universe.fishing.species", "图鉴 %s/%s 种", "%s/%s species");
        both.add("gui.universe.fishing.caught", "钓上 %s 条，放生 %s 条", "%s caught, %s released");
        both.add("gui.universe.fishing.pick", "点一格看详情", "Click a fish for details");
        both.add("gui.universe.fishing.conditions", "什么时候咬钩：%s", "Bites: %s");
        both.add("gui.universe.fishing.record", "最重 %s，在%s钓到", "Heaviest %s, caught on %s");
        both.add("gui.universe.fishing.count", "钓过 %s 条", "Caught %s");
        both.add("gui.universe.fishing.unknown", "还没钓到过", "Not caught yet");
        both.add("gui.universe.fishing.hint", "去对得上的水域照着条件钓。不是每颗星球上都有，换一颗试试。",
                "Fish the right water under the right conditions. Not every planet has it, so try another.");
        String where = "gui.universe.fishing.where.";
        both.add(where + "fresh", "星原、林地、河谷、湿地的水，主世界的河", "Plains, woods, valleys and wetlands; rivers back home");
        both.add(where + "warm", "雨林、菌林的水，灼热星球的温泉", "Jungle and fungal forest water; hot springs on scorched worlds");
        both.add(where + "coastal", "海滩和海里水深不到 12 格的地方", "Beaches, and seas shallower than 12 m");
        both.add(where + "deep", "深海，或者海里水深 12 格以上", "Deep ocean, or sea 12 m deep and more");
        both.add(where + "ice", "冰洋、冻原、雪原、冰川的水，冰冻星球", "Frozen seas, tundra, snowfields, glaciers; frozen worlds");
        both.add(where + "acid", "酸海、毒沼的酸液，毒雾星球", "Acid seas and toxic marshes; toxic worlds");
        both.add(where + "cave", "头顶见不到天的水：洞里的河和湖", "Water with no sky above: cave rivers and lakes");
        both.add(where + "exotic", "奇异星球和辐射星球上的水", "Water on exotic and irradiated worlds");
    }

    /** 手册的「钓鱼」一章。 */
    private static void guide(Both both) {
        String g = "guide.universe.fishing";
        both.add(g, "钓鱼", "Fishing");
        both.add(g + ".rig.title", "钓鱼器", "Fishing Rig");
        both.add(g + ".rig",
                "研究「钓鱼」（交 4 块异星生鱼、6 个磁线圈）后在装配机里做钓鱼器，装进多功能工具的技术槽，按 G 切过去。\n\n"
                        + "右键甩竿，浮标最远 24 格，要落进水或酸液里\n"
                        + "再右键收回来\n"
                        + "等 8–20 秒，快咬钩时水下有鱼影游过来\n"
                        + "浮标往下一沉就右键，慢了鱼会跑",
                "Research Fishing (4 raw alien fish, 6 magnetic coils) and make a Fishing Rig in an assembler. Fit it to the multitool; "
                        + "G switches to it.\n\n"
                        + "Right-click: cast up to 24 m into water or acid\n"
                        + "Right-click again: reel in\n"
                        + "A bite takes 8–20 s; a shadow swims up first\n"
                        + "Bobber dips: right-click fast or it gets away");
        both.add(g + ".reeling.title", "收线", "Reeling In");
        both.add(g + ".reeling",
                "钩住以后按住右键收线，松开放线。屏幕下方有张力条和进度条。\n\n"
                        + "张力在绿区里进度涨得最快，太紧涨一半，太松不涨\n"
                        + "鱼隔一阵猛冲，看到「要挣了」先松手\n"
                        + "张力到顶线就断，饵也没了\n"
                        + "线松着超过 2 秒，鱼会脱钩\n"
                        + "小鱼五六秒，传说的大鱼二十秒上下\n"
                        + "和平难度下咬钩就算钓上",
                "Once it's hooked, hold right-click to reel and let go to give line. Two bars show at the bottom: tension and progress.\n\n"
                        + "Progress is fastest with tension in the green, half speed when tight, none when slack\n"
                        + "Fish make runs. When it says About to run, ease off\n"
                        + "Max tension snaps the line and you lose the bait\n"
                        + "Slack for over 2 s and it throws the hook\n"
                        + "Small fish take five or six seconds, legendary ones about twenty\n"
                        + "On Peaceful, a bite is a catch");
        both.add(g + ".waters.title", "水域", "Waters");
        both.add(g + ".waters",
                "鱼分 8 种水域，每种 12 种：普通 5、少见 4、稀有 2、传说 1。\n\n"
                        + "淡水：星原、林地、河谷、湿地，主世界的河\n"
                        + "暖水：雨林、菌林，灼热星球的温泉\n"
                        + "近海：海滩，水深不到 12 格的海\n"
                        + "深海：深海，12 格以上的海\n"
                        + "冰下：冰冻星球，冻原、雪原、冰川\n"
                        + "酸水：毒雾星球的酸海和毒沼\n"
                        + "暗河：头顶看不到天的水\n"
                        + "奇异：奇异星球和辐射星球",
                "There are 8 waters with 12 fish each: 5 common, 4 uncommon, 2 rare, 1 legendary.\n\n"
                        + "Fresh: plains, woods, valleys, wetlands; rivers back home\n"
                        + "Warm: jungles, fungal forests, hot springs on scorched worlds\n"
                        + "Coastal: beaches and seas under 12 m deep\n"
                        + "Deep: deep ocean, or sea 12 m and deeper\n"
                        + "Ice: frozen worlds, tundra, snowfields, glaciers\n"
                        + "Acid: acid seas and marshes on toxic worlds\n"
                        + "Cave: any water with no sky above\n"
                        + "Exotic: exotic and irradiated worlds");
        both.add(g + ".conditions.title", "条件和图鉴", "Conditions and the Log");
        both.add(g + ".conditions",
                "每颗星球的鱼不一样：普通的都有，别的只有一部分。没见过的鱼，换一颗同类的星球再钓。\n\n"
                        + "有的鱼只在白天、夜里或者风暴里咬钩\n"
                        + "有的要浅水（4 格以内）或深水（8 格以上）\n"
                        + "水越深，鱼越重\n"
                        + "发现日志的「钓鱼」页列出 96 种，没钓到的写着条件\n"
                        + "十条里有一条是杂物，偶尔钓上漂流瓶、密封补给舱，很少有技术模块",
                "Each planet has every common fish and some of the rest. Missing one? Try another world of the same type.\n\n"
                        + "Some bite only by day, at night or in storms\n"
                        + "Some want shallow water (4 m or less) or deep (8 m or more)\n"
                        + "Deeper water, heavier fish\n"
                        + "The Fishing page of the discovery log lists all 96, with conditions for the ones you haven't caught\n"
                        + "One catch in ten is junk. Now and then a message in a bottle or a sealed supply pod, rarely a tech module");
        both.add(g + ".bait.title", "饵", "Bait");
        both.add(g + ".bait",
                "钓鱼模式下潜行右键打开饵格。每钩住一条用掉一个；不放饵也能钓，只是慢。\n\n"
                        + "能吃的东西：等鱼 ×0.8；做好的菜 ×0.6\n"
                        + "腥饵：等鱼的时间减半\n"
                        + "亮片拟饵：少见、稀有的多一倍\n"
                        + "深水坠饵：大鱼、巨鱼多一倍，也更重\n"
                        + "夜光饵：白天也钓得到夜里的鱼\n"
                        + "静电拟饵：不下风暴也钓得到风暴里的鱼\n"
                        + "星核饵：传说的 ×4",
                "Sneak + right-click in fishing mode for the bait slot. One per catch; no bait means longer waits.\n"
                        + "Any food: wait ×0.8; cooked dishes ×0.6\n"
                        + "Stink Bait: half the wait\n"
                        + "Spinner Lure: twice the uncommon and rare\n"
                        + "Deep Sinker: large and huge ×2, heavier\n"
                        + "Glow Bait: night fish bite by day\n"
                        + "Static Lure: storm fish bite in calm weather\n"
                        + "Star Core Bait: legendary ×4");
        both.add(g + ".release_trap.title", "放生和捕鱼笼", "Release and Traps");
        both.add(g + ".release_trap",
                "拿着鱼对着水右键放生。接下来 3 次甩竿，等鱼的时间减半，少见以上的多 15%。\n\n"
                        + "捕鱼笼要整个泡在水里，按所在的水域捕鱼，只出普通和少见的：\n"
                        + "有饵大约一分半一条，没饵三分钟一条\n"
                        + "9 格存货，满了就停\n"
                        + "漏斗、分拣器能取",
                "Release a fish by right-clicking water with it. "
                        + "Your next 3 casts wait half as long, with 15% better odds for uncommon and up.\n\n"
                        + "A fish trap works fully submerged and catches local common and uncommon fish:\n"
                        + "Baited: one per ~90 s; unbaited: one per 3 min\n"
                        + "9 slots. It stops when full\n"
                        + "Hoppers and sorters can empty it");
        both.add(g + ".seafood.title", "海鲜", "Seafood");
        both.add(g + ".seafood",
                "生鱼能吃，也能放进熔炉、烟熏炉、营火烤。营养处理器里能做七道海鲜：\n\n"
                        + "烤鱼排、鱼汤、生鱼片拼盘、熏鱼：管饱\n"
                        + "深海炖菜：回氧气\n"
                        + "冰鱼汤：保暖\n"
                        + "传说鱼宴：要一条传说的鱼，危害防护回满\n\n"
                        + "鱼在空间站按档次和个头卖钱，传说的巨鱼值几千信用点。",
                "Eat fish raw or cook it. The nutrient processor makes seven dishes:\n"
                        + "Grilled Fillet, Fish Soup, Sashimi Platter, Smoked Fish: filling\n"
                        + "Deep Sea Stew: restores oxygen\n"
                        + "Ice Fish Soup: keeps you warm\n"
                        + "Legendary Feast: takes a legendary fish, refills hazard protection\n"
                        + "Stations pay by rarity and size. A huge legendary is worth thousands.");
    }
}
