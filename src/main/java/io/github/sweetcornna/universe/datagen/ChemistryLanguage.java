package io.github.sweetcornna.universe.datagen;

/** M20 化学的文字（子代理 A，ADR-0036 §一–§四）：大气提取器、电解槽、大气成分的显示、手册的化学一章。 */
final class ChemistryLanguage {
    /** 同时写中文和英文的一条。 */
    interface Both {
        void add(String key, String zh, String en);
    }

    private ChemistryLanguage() {
    }

    static void add(Both both) {
        machines(both);
        atmosphere(both);
        guide(both);
    }

    private static void machines(Both both) {
        both.add("block.universe.atmospheric_extractor", "大气提取器", "Atmospheric Extractor");
        both.add("block.universe.electrolyzer", "电解槽", "Electrolyzer");
        both.add("tooltip.universe.atmospheric_extractor.summary", "放在星球地表，头顶要露天\n界面里选一种气体\n用电 0.48 kW，大气越稀越费电，最多 1.44 kW",
                "Place on a planet's surface under open sky\nPick a gas in its menu\nUses 0.48 kW, more in thin air, up to 1.44 kW");
        both.add("tooltip.universe.electrolyzer.summary", "电解水、食盐水、熔盐、二氧化碳和含铜浸出液\n用电 10 kW",
                "Splits water, brine, molten salt, CO₂ and copper leach solution\nUses 10 kW");
        both.add("tooltip.universe.methalox_fuel.summary", "拿着右键燃料舱加注\n一罐 2500，发射燃料是 1000",
                "Right-click a fuel tank to fill it\n2500 per canister; launch fuel gives 1000");
        both.add("gui.universe.atmospheric_extractor.no_atmosphere", "这里没有大气", "No atmosphere here");
        both.add("gui.universe.atmospheric_extractor.no_sky", "头顶被挡住了", "Sky is blocked");
        both.add("gui.universe.atmospheric_extractor.collecting", "正在取%s", "Collecting %s");
        both.add("gui.universe.atmospheric_extractor.rate", "%s atm · 每分钟 %s 个", "%s atm · %s per minute");
        both.add("jei.universe.electrolysis", "电解", "Electrolysis");
        both.add("gui.universe.guide.machine.electrolysis", "电解槽", "Electrolyzer");
        both.add("jei.universe.info.atmosphere_gas", "大气提取器放在大气里有这种气体的星球地表取。每颗星球的大气成分写在发现日志的星球页和星图里。",
                "Collect it with an atmospheric extractor on a planet whose air contains it. The discovery log and the star map list each planet's air.");
    }

    private static void atmosphere(Both both) {
        both.add("gui.universe.atmosphere.mix", "大气：%s", "Air: %s");
        both.add("gui.universe.atmosphere.oxygen", "氧分压 %s", "O₂ partial pressure %s");
        // 各种毒气的分压折成 ppm 除以 NIOSH 的限值再加起来，过了 1 就有毒
        both.add("gui.universe.atmosphere.toxicity", "毒性指数 %s / 1", "Toxicity index %s / 1");
        both.add("gui.universe.atmosphere.oxygen_and_toxicity", "氧分压 %s · 毒性指数 %s / 1", "O₂ %s · toxicity %s / 1");
        both.add("hud.universe.visor.air_title", "头顶的大气", "Air overhead");
        both.add("hud.universe.visor.air", "%s · %s atm", "%s · %s atm");
        both.add("hud.universe.visor.spectrum", "光谱读数", "Spectral reading");
    }

    private static void guide(Both both) {
        String g = "guide.universe.chemistry";
        both.add(g, "化学", "Chemistry");
        both.add(g + ".substances.title", "物质", "Substances");
        both.add(g + ".substances",
                "矿物、化工品和气体都照现实对上一种物质。物品说明里名字下面那行灰字是它的化学式：粗钛是钛铁矿 FeTiO₃，铜是黄铜矿 CuFeS₂，"
                        + "原版的东西也算。\n\n"
                        + "分析目镜对着矿石，也会写出是什么矿物。现实里没有的东西（奇异物质、卡西米尔晶体这些）写“?”。",
                "Minerals, chemicals and gases each map to a real substance. The grey line under an item's name is its formula: "
                        + "raw titanium is ilmenite, FeTiO₃; copper is chalcopyrite, CuFeS₂. Vanilla items count too.\n\n"
                        + "The analysis visor names the mineral when you scan an ore. Things with no real counterpart "
                        + "(strange matter, Casimir crystals and the like) show “?”.");
        both.add(g + ".atmosphere.title", "大气成分", "What's in the Air");
        both.add(g + ".atmosphere",
                "每颗星球的大气有自己的成分：宜居星球照地球，稀薄的照火星，浓密的照金星，冰冻星球有的像冥王星，有的像土卫六。"
                        + "发现日志的星球页和星图里写着前四种气体和氧分压，分析目镜朝天看也读得到。\n\n"
                        + "毒性指数是每种毒气的分压折成 ppm、除以美国 NIOSH 的限值再加起来，过了 1 就有毒。",
                "Every planet has its own mix of gases: habitable worlds follow Earth, thin air follows Mars, dense air follows Venus, "
                        + "and frozen worlds look like Pluto or Titan. The discovery log and the star map list the top four gases and the "
                        + "oxygen partial pressure; the analysis visor reads them off the sky.\n\n"
                        + "The toxicity index adds up each toxic gas's partial pressure in ppm divided by its US NIOSH limit. Above 1, the air is toxic.");
        both.add(g + ".extractor.title", "大气提取器", "Atmospheric Extractor");
        both.add(g + ".extractor",
                "放在地表，头顶要露天。界面里选一种气体，占到千分之一才能选。\n\n"
                        + "每分钟出 40 × √气压 × √分数 个，气压按标准大气压算，最多算 10。大气越稀越费电，最多是 1 atm 时的三倍。",
                "Place it on the surface with open sky above and pick a gas in its menu (0.1% or more).\n\n"
                        + "It yields 40 × √pressure × √fraction per minute, pressure in atm, capped at 10. Thin air costs more power, "
                        + "up to three times the draw at 1 atm.");
        both.add(g + ".electrolysis.title", "电解", "Electrolysis");
        both.add(g + ".electrolysis",
                "电解槽用电 10 kW。\n\n"
                        + "电解水：2 水 → 2 氢 + 1 氧，28 秒\n"
                        + "MOXIE：2 二氧化碳 → 2 一氧化碳 + 1 氧，28 秒\n\n"
                        + "毅力号在火星上用 MOXIE 从二氧化碳里取氧。冰冻星球上，便携精炼机把冰化成水，再电解，就地造氧和推进剂。"
                        + "电解出的氢拿去发电，拿回的电比电解用掉的少。",
                "The electrolyzer draws 10 kW.\n\n"
                        + "Water: 2 water → 2 hydrogen + oxygen, 28 s\n"
                        + "MOXIE: 2 CO₂ → 2 CO + oxygen, 28 s\n\n"
                        + "Perseverance's MOXIE made oxygen from Martian CO₂ this way. On frozen worlds, melt ice with a portable refiner "
                        + "and split the water. The hydrogen burns for less power than splitting took.");
        both.add(g + ".salt.title", "盐和电积", "Salt and Electrowinning");
        both.add(g + ".salt",
                "氯碱：2 盐 + 2 水 → 1 氯气 + 1 氢 + 2 氢氧化钠，24 秒\n"
                        + "熔盐：2 盐 → 2 钠 + 1 氯气，56 秒\n"
                        + "电积铜：2 含铜浸出液 → 2 铜锭 + 1 硫酸，2 秒\n"
                        + "电积金：2 含金浸出液 → 2 金锭，2 秒\n\n"
                        + "氯气和钠拿去炼钛，浸出液从生物反应器里来。",
                "Chlor-alkali: 2 salt + 2 water → Cl₂ + H₂ + 2 NaOH, 24 s\n"
                        + "Molten salt: 2 salt → 2 sodium + Cl₂, 56 s\n"
                        + "Copper: 2 copper leach solution → 2 copper ingots + sulfuric acid, 2 s\n"
                        + "Gold: 2 gold leach solution → 2 ingots, 2 s\n\n"
                        + "The chlorine and sodium go into titanium. Leach solutions come from the bioreactor.");
        both.add(g + ".titanium.title", "亨特法炼钛", "Hunter Process");
        both.add(g + ".titanium",
                "在化工厂里做，要研究“电化学”：\n\n"
                        + "2 粗钛 + 8 氯气 + 4 煤 → 4 四氯化钛 + 1 钒渣，8 秒\n"
                        + "1 四氯化钛 + 4 钠 → 1 钛锭 + 4 盐，48 秒\n\n"
                        + "一块粗钛出两个钛锭，直接冶炼只出一个，可是还原很慢。还原出的盐送回电解槽熔盐电解，正好换回四个钠和两个氯气，"
                        + "所以这条链只吃电和煤。钒渣留着做五氧化二钒催化剂。",
                "Chemical plant, after Electrochemistry:\n\n"
                        + "2 raw titanium + 8 chlorine + 4 coal → 4 TiCl₄ + 1 vanadium slag, 8 s\n"
                        + "TiCl₄ + 4 sodium → titanium + 4 salt, 48 s\n\n"
                        + "Two ingots per raw titanium, twice what smelting gives, but slow. Molten-salt electrolysis recycles the salt, "
                        + "so the chain only uses power and coal.");
        both.add(g + ".nitrates.title", "硝酸", "Nitric Acid");
        both.add(g + ".nitrates",
                "硝酸用氨和氧做（奥斯特瓦尔德法，NH₃ + 2O₂ → HNO₃ + H₂O），再和氨做成硝酸铵化肥。"
                        + "氨可以从有毒星球的大气里取，也可以在反应釜里合成。\n\n"
                        + "氰化氢（安德卢梭法）：2 甲烷 + 2 氨 + 3 氧 → 2 氰化氢 + 6 水。大气里的太少，取不出来，只给大气释放塔用。",
                "Nitric acid comes from ammonia and oxygen (the Ostwald process, NH₃ + 2O₂ → HNO₃ + H₂O); add more ammonia for "
                        + "ammonium nitrate fertilizer. Get ammonia from a toxic world's air or the reactor.\n\n"
                        + "Hydrogen cyanide (Andrussow process): 2 methane + 2 ammonia + 3 oxygen → 2 HCN + 6 water. No air holds enough "
                        + "to extract; it is only for the release tower.");
        both.add(g + ".methalox.title", "甲烷液氧", "Methalox");
        both.add(g + ".methalox",
                "1 甲烷 + 2 氧在化工厂里灌成一罐甲烷液氧燃料，要研究“合成燃料”。一罐给燃料舱加 2500，发射燃料只有 1000。\n\n"
                        + "星舰的猛禽发动机烧的就是它。在火星这样的星球上，二氧化碳和水都能就地取，甲烷和氧都造得出来。",
                "A chemical plant fills one canister of methalox from 1 methane and 2 oxygen once you research Synthetic Fuels. "
                        + "Each canister adds 2500 to a fuel tank; launch fuel adds 1000.\n\n"
                        + "Starship's Raptor engines burn the same mix. On a world like Mars both CO₂ and water are on hand, so you can make "
                        + "the methane and the oxygen there.");
    }
}
