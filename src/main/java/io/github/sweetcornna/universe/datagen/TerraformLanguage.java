package io.github.sweetcornna.universe.datagen;

/** M20 星球改造的文字（子代理 E，ADR-0036 §六）：大气释放塔、生态播种站、轨道反射镜、地衣、门槛表、阶段提示和调试命令。 */
final class TerraformLanguage {
    /** 同时写中文和英文的一条。 */
    interface Both {
        void add(String key, String zh, String en);
    }

    private static final String C = "commands.universe.terraform.";

    private TerraformLanguage() {
    }

    static void add(Both both) {
        machines(both);
        seeder(both);
        thresholds(both);
        stages(both);
        commands(both);
    }

    private static void machines(Both both) {
        both.add("block.universe.atmosphere_release_tower", "大气释放塔", "Atmosphere Release Tower");
        both.add("block.universe.orbital_mirror", "轨道反射镜", "Orbital Mirror");
        both.add("tooltip.universe.atmosphere_release_tower.summary", "放在能改造的星球地表，头顶要露天\n放进任意气体，每秒放出一个\n用电 1.2 kW",
                "Place on a terraformable planet's surface under open sky\nTakes any gas and releases one per second\nUses 1.2 kW");
        both.add("tooltip.universe.orbital_mirror.summary", "放在太空里，离能改造的星球 200 格以内\n每面让星球暖 3 K，切成遮阳板凉 3 K\n各最多 24 面算数，不用电",
                "Place in space within 200 blocks of a terraformable planet\nEach one warms the planet by 3 K, or cools it by 3 K as a shade\nUp to 24 of each count; needs no power");
        both.add("gui.universe.release_tower.not_terraformable", "这里不能改造，要放在母星以外能登陆的星球上",
                "Can't terraform here. Use a landable planet other than the homeworld");
        both.add("gui.universe.release_tower.no_sky", "头顶被挡住了", "Sky is blocked");
        both.add("gui.universe.release_tower.no_gas", "放进气体", "Insert gas");
        both.add("gui.universe.release_tower.releasing", "正在放出%s", "Releasing %s");
        both.add("gui.universe.release_tower.released", "这座塔放出了 %s 个", "Released so far: %s");
        both.add("gui.universe.orbital_mirror.not_in_space", "只能在太空里用", "Only works in space");
        both.add("gui.universe.orbital_mirror.no_planet", "附近没有能改造的星球", "No terraformable planet in range");
        both.add("gui.universe.orbital_mirror.target", "对着%s", "Facing %s");
        both.add("gui.universe.orbital_mirror.mode_mirror", "反射镜：把阳光照到星球上，暖 %s K", "Mirror: adds sunlight, +%s K");
        both.add("gui.universe.orbital_mirror.mode_shade", "遮阳板：挡住一部分阳光，凉 %s K", "Shade: blocks sunlight, −%s K");
        both.add("gui.universe.orbital_mirror.count", "星球上空：反射镜 %s 面，遮阳板 %s 面", "Around the planet: %s mirrors, %s shades");
        both.add("gui.universe.orbital_mirror.net", "合计 %s K，每种最多 %s 面算数", "Net %s K; up to %s of each count");
        both.add("gui.universe.orbital_mirror.to_shade", "切成遮阳板", "Switch to shade");
        both.add("gui.universe.orbital_mirror.to_mirror", "切成反射镜", "Switch to mirror");
        both.add("message.universe.orbital_mirror.not_in_space", "轨道反射镜只能放在太空里", "Orbital mirrors only work in space");
        both.add("message.universe.orbital_mirror.no_planet", "这个星系没有能改造的星球", "This system has no terraformable planet");
        both.add("message.universe.orbital_mirror.too_far", "离%1$s太远：还差 %3$s 格（%4$s方，要在大气层外 %5$s 格内）",
                "Too far from %2$s: %3$s blocks short (to the %4$s; must be within %5$s blocks of its atmosphere)");
    }

    private static void seeder(Both both) {
        String g = "gui.universe.ecology_seeder.";
        both.add("block.universe.ecology_seeder", "生态播种站", "Ecology Seeder");
        both.add("block.universe.gray_lichen", "灰绿地衣", "Gray-Green Lichen");
        both.add("block.universe.orange_lichen", "橙黄地衣", "Orange Lichen");
        both.add("tooltip.universe.ecology_seeder.summary",
                "放在能改造的星球地表，头顶要露天\n放进菌种管或改造种苗，每样条件都有基因扛住才种得活\n星球没有水时要加水，四座有水的站浇满\n周围会慢慢长出地衣和苔藓。用电 800 W",
                "Place on a terraformable planet's surface under open sky\nInsert a strain tube or engineered seedling; it only takes if its genes cover every condition\nOn a dry planet it needs water; four watered seeders give full irrigation\nLichen and moss slowly spread around it. Uses 800 W");
        both.add(g + "seed_slot", "菌种", "Seed");
        both.add(g + "water_slot", "水", "Water");
        both.add(g + "not_terraformable", "这里不能改造，要放在母星以外能登陆的星球上",
                "Can't terraform here. Use a landable planet other than the homeworld");
        both.add(g + "no_sky", "头顶被挡住了", "Sky is blocked");
        both.add(g + "no_seed", "放进菌种管或改造种苗", "Insert a strain tube or engineered seedling");
        both.add(g + "cannot_survive", "种不活：标红的条件扛不住", "Won't take: see the red rows");
        both.add(g + "seeding", "正在播种 %s%%", "Seeding %s%%");
        both.add(g + "seeded", "这座站播了 %s 份", "Seeded so far: %s");
        both.add(g + "natural_water", "星球上有水，不用浇", "The planet has water of its own");
        both.add(g + "no_water", "没水，不浇", "No water, not irrigating");
        both.add(g + "watering", "在浇水，还够 %s 秒；这颗星球 %s 座站在浇", "Irrigating, %ss of water left; %s seeders on this planet");
        both.add(g + "biomass", "星球的生物量 %s，周围 %s 格内长地衣", "Planet biomass %s; lichen spreads within %s blocks");
        both.add(g + "conditions", "%s要扛住这些", "What %s has to survive");
        both.add(g + "needs", "要%s", "needs %s");
        both.add(g + "has", "有", "has it");
        both.add(g + "lacks", "没有", "missing");
        both.add(g + "dies", "活不了", "fatal");
        both.add(g + "condition.photosynthesis", "光合作用", "Photosynthesis");
        both.add(g + "condition.cold", "寒冷", "Cold");
        both.add(g + "condition.too_cold", "太冷", "Too cold");
        both.add(g + "condition.heat", "高温", "Heat");
        both.add(g + "condition.too_hot", "太热", "Too hot");
        both.add(g + "condition.radiation", "辐射", "Radiation");
        both.add(g + "condition.toxic", "酸", "Acid");
        both.add(g + "condition.uv", "紫外线", "UV");
        both.add(g + "condition.dry", "干旱", "Drought");
        both.add(g + "condition.no_water", "没有水", "No water");
        both.add(g + "fatal.too_cold", "低于 200 K", "below 200 K");
        both.add(g + "fatal.too_hot", "高于 395 K", "above 395 K");
        both.add(g + "fatal.no_water", "往站里加水", "add water");
    }

    /** 门槛表的名字，界面窄，写短一点。 */
    private static void thresholds(Both both) {
        both.add("gui.universe.terraform.planet", "%s的门槛", "%s: thresholds");
        both.add("gui.universe.terraform.threshold.temperature", "平均温度", "Temperature");
        both.add("gui.universe.terraform.threshold.toxic_gases", "毒性指数", "Toxicity");
        both.add("gui.universe.terraform.threshold.pressure", "大气柱", "Air column");
        both.add("gui.universe.terraform.threshold.breathing_pressure", "气压", "Pressure");
        both.add("gui.universe.terraform.threshold.oxygen", "氧分压", "O₂ pressure");
        both.add("gui.universe.terraform.threshold.carbon_dioxide", "二氧化碳", "CO₂");
    }

    /** 到了阶段时的全服提示：本来有的危害没了就说危害没了，本来就没有的说环境成了什么样。 */
    private static void stages(Both both) {
        String s = "message.universe.terraform.stage.";
        both.add(s + "cold_gone", "%s：寒冷危害消失了", "%s: the cold hazard is gone");
        both.add(s + "heat_gone", "%s：高温危害消失了", "%s: the heat hazard is gone");
        both.add(s + "temperate", "%s：平均气温到了零下 20 到 40 °C", "%s: mean temperature is now between −20 and 40 °C");
        both.add(s + "toxic_gone", "%s：毒性危害消失了", "%s: the toxic hazard is gone");
        both.add(s + "clean_air", "%s：大气里的毒气降到了安全线以下", "%s: toxic gases are below the safe limit");
        both.add(s + "radiation_gone", "%s：大气厚到挡得住宇宙射线，辐射危害消失了", "%s: the air now stops cosmic rays; the radiation hazard is gone");
        both.add(s + "radiation_halved", "%s：大气挡住了一半辐射，致密恒星的那一半还在", "%s: the air now stops half the radiation; the compact star's share remains");
        both.add(s + "shielded", "%s：大气柱和地球的一样厚了", "%s: the air column now matches Earth's");
        both.add(s + "breathable", "%s：空气能直接呼吸了", "%s: the air is breathable");
    }

    private static void commands(Both both) {
        both.add(C + "not_terraformable", "不能改造的星球：%s", "Not a terraformable planet: %s");
        both.add(C + "unknown_gas", "没有这种气体：%s", "Unknown gas: %s");
        both.add(C + "info", "%1$s：气压 %2$s atm（大气柱 %3$s 倍地球），平均 %4$s K，生物量 %5$s，反射镜 %6$s 面，遮阳板 %7$s 面，先锋生物 %8$s 种",
                "%1$s: %2$s atm (air column %3$s× Earth), mean %4$s K, biomass %5$s, %6$s mirrors, %7$s shades, %8$s pioneer strains");
        both.add(C + "info.mix", "成分：%s", "Composition: %s");
        both.add(C + "info.deltas", "加减过的气体：%s", "Gas added or removed: %s");
        both.add(C + "info.threshold", "%s：%s（要 %s）%s", "%s: %s (needs %s) %s");
        both.add(C + "info.met", "到了", "met");
        both.add(C + "info.unmet", "没到", "not met");
        both.add(C + "info.stages", "到了的阶段：%s", "Stages reached: %s");
        both.add(C + "gas", "%1$s：%3$s %2$s 个，现在气压 %4$s atm，平均 %5$s K", "%1$s: %2$s %3$s, now %4$s atm, mean %5$s K");
        both.add(C + "biomass", "%s 的生物量设成了 %s", "Set biomass on %s to %s");
        both.add(C + "mirrors", "%s 上空的反射镜设成了 %s 面", "Set mirrors around %s to %s");
        both.add(C + "shades", "%s 上空的遮阳板设成了 %s 面", "Set shades around %s to %s");
        both.add(C + "advance", "%1$s 快进了 %2$s 刻：气压 %3$s atm，平均 %4$s K，生物量 %5$s",
                "Advanced %1$s by %2$s ticks: %3$s atm, mean %4$s K, biomass %5$s");
        both.add(C + "reset", "%s 的改造状态清空了", "Cleared terraforming on %s");
    }
}
