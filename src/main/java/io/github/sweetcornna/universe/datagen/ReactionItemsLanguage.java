package io.github.sweetcornna.universe.datagen;

/** 化学反应扩充（ADR-0037）新加的物品的名字和说明：反应产物、催化剂、铂、实验记录、化学手册。 */
final class ReactionItemsLanguage {
    private ReactionItemsLanguage() {
    }

    static void add(ChemBioLanguage.Both both) {
        both.add("message.universe.reaction_discovered", "新发现 · 化学反应：%s", "New discovery · Reaction: %s");
        both.add("item.universe.sulfur_trioxide", "三氧化硫", "Sulfur Trioxide");
        both.add("item.universe.nitric_oxide", "一氧化氮", "Nitric Oxide");
        both.add("item.universe.nitrogen_dioxide", "二氧化氮", "Nitrogen Dioxide");
        both.add("item.universe.hydrogen_chloride", "氯化氢", "Hydrogen Chloride");
        both.add("item.universe.methanol", "甲醇", "Methanol");
        both.add("item.universe.ethylene", "乙烯", "Ethylene");
        both.add("item.universe.urea", "尿素", "Urea");
        both.add("item.universe.platinum_ingot", "铂锭", "Platinum Ingot");
        both.add("item.universe.copper_zinc_catalyst", "铜锌催化剂", "Copper-Zinc Catalyst");
        both.add("item.universe.platinum_rhodium_gauze", "铂铑网", "Platinum-Rhodium Gauze");
        both.add("item.universe.zeolite_catalyst", "ZSM-5 沸石", "ZSM-5 Zeolite");
        both.add("item.universe.ziegler_natta_catalyst", "齐格勒–纳塔催化剂", "Ziegler–Natta Catalyst");
        both.add("item.universe.copper_chloride_catalyst", "氯化铜催化剂", "Copper Chloride Catalyst");
        both.add("item.universe.chemistry_handbook", "化学手册", "Chemistry Handbook");
        both.add("item.universe.lab_notes", "实验记录", "Lab Notes");
        // 按住 Shift 看的说明：用途不明显的几样
        both.add("tooltip.universe.urea.summary", "水培槽右键施肥\n和一袋硝酸铵一样多", "Use on a hydroponic tray to fertilize\nSame as a bag of ammonium nitrate");
        both.add("tooltip.universe.platinum_ingot.summary", "生物浸出铜时偶尔多出一个\n做铂铑网用", "Sometimes left over from copper bioleaching\nUsed for platinum-rhodium gauze");
    }
}
