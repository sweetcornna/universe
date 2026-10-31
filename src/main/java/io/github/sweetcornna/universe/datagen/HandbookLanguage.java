package io.github.sweetcornna.universe.datagen;

/** 化学手册和实验记录（ADR-0037 §四、§五）的界面文字。 */
final class HandbookLanguage {
    private HandbookLanguage() {
    }

    static void add(ChemBioLanguage.Both both) {
        both.add("gui.universe.handbook.title", "化学手册", "Chemistry Handbook");
        both.add("gui.universe.handbook.intro", "怎么用", "How to Use");
        both.add("gui.universe.handbook.intro.body",
                "照这本手册往反应釜里配料：反应物放进原料格，催化剂放进催化剂格，温度和压强调到教科书的工况。"
                        + "釜认出是哪条反应就开始做。\n\n"
                        + "做成一批，这条反应就记下了，全服都算，反应釜左边的列表里能直接选，漏斗和分拣器也能往里送料。\n\n"
                        + "研究了对应科技的反应会列出来，没列出的写着\"？？？\"。读一张实验记录，也能记下上面写的反应。\n\n"
                        + "反应式里的系数就是一批进出的物品数。反应热按写出的反应式算。",
                "Use this book to load the reactor: reactants in the input slots, the catalyst in its own slot, temperature and "
                        + "pressure at the textbook values. The reactor works out which reaction it is and starts.\n\n"
                        + "Finish one batch and the reaction is recorded for the whole server. Then you can pick it from the reactor's list, "
                        + "and hoppers can feed it.\n\n"
                        + "Reactions show up here once you research their tech; the rest read \"???\". Lab notes also record the reaction "
                        + "written on them.\n\n"
                        + "Coefficients are item counts per batch. Heat is per equation as written.");
        both.add("gui.universe.handbook.count", "已发现 %s / %s", "%s / %s found");
        both.add("gui.universe.handbook.unknown", "？？？", "???");
        both.add("gui.universe.handbook.catalyst", "催化剂：%s", "Catalyst: %s");
        both.add("gui.universe.handbook.no_catalyst", "不用", "none");
        both.add("gui.universe.handbook.conditions", "教科书工况：%s", "Textbook: %s");
        both.add("gui.universe.handbook.exothermic", "放热 %s kJ/mol", "Releases %s kJ/mol");
        both.add("gui.universe.handbook.endothermic", "吸热 %s kJ/mol", "Absorbs %s kJ/mol");
        both.add("gui.universe.handbook.reversible", "可逆，受平衡限制", "Reversible: limited by equilibrium");
        both.add("gui.universe.handbook.irreversible", "不可逆，能做到底", "Irreversible: runs to completion");
        both.add("gui.universe.handbook.discovered", "做成过，反应釜里能直接选。", "Done before. Pick it in the reactor.");
        both.add("gui.universe.handbook.undiscovered", "还没做成过。照上面配料，在反应釜里自由投料做一批。",
                "Not done yet. Load it in free feed and finish a batch.");
        both.add("gui.universe.handbook.locked", "还没列进手册。", "Not in the handbook yet.");
        both.add("gui.universe.handbook.how",
                "研究%s以后列出来。在反应釜里自由投料碰巧做成，或者读一张写着它的实验记录，也会列出来。",
                "Research %s to list it. It also shows up if you finish it by chance in free feed, or read lab notes about it.");
        both.add("tooltip.universe.chemistry_handbook", "按类列出反应：反应式、催化剂、工况", "Reactions by group: equations, catalysts, conditions");
        both.add("item.universe.lab_notes.named", "实验记录：%s", "Lab Notes: %s");
        both.add("tooltip.universe.lab_notes", "右键读，记下这条反应", "Use to record this reaction");
        both.add("tooltip.universe.lab_notes.known", "这条已经记下了", "Already recorded");
        both.add("tooltip.universe.lab_notes.blank", "字迹看不清", "Illegible");
        both.add("message.universe.lab_notes.known", "%s已经记下了。", "%s is already recorded.");
        both.add("message.universe.lab_notes.blank", "字迹看不清，读不出什么。", "The writing is illegible.");
    }
}
