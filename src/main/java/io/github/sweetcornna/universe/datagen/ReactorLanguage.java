package io.github.sweetcornna.universe.datagen;

import java.util.Map;

import io.github.sweetcornna.universe.core.chem.reaction.Reaction;

/** 化学反应釜（ADR-0036 §四、ADR-0037）的文字。 */
final class ReactorLanguage {
    /** 列表按钮只有 86 像素宽：英文名放不下的用短名，中文用全名。 */
    private static final Map<Reaction, String> SHORT_EN = Map.ofEntries(
            Map.entry(Reaction.CONTACT, "Contact"),
            Map.entry(Reaction.SABATIER, "Sabatier"),
            Map.entry(Reaction.FISCHER_TROPSCH, "FT Synthesis"),
            Map.entry(Reaction.AMMONIA_CRACKING, "NH₃ Cracking"),
            Map.entry(Reaction.AMMONIA_OXIDATION, "NH₃ Oxidation"),
            Map.entry(Reaction.NO_OXIDATION, "NO Oxidation"),
            Map.entry(Reaction.NO2_ABSORPTION, "NO₂ Absorption"),
            Map.entry(Reaction.ANDRUSSOW, "Andrussow"),
            Map.entry(Reaction.SO3_ABSORPTION, "SO₃ Absorption"),
            Map.entry(Reaction.SULFUR_COMBUSTION, "Sulfur Burning"),
            Map.entry(Reaction.PARTIAL_OXIDATION, "Partial Ox."),
            Map.entry(Reaction.METHANE_PYROLYSIS, "CH₄ Pyrolysis"),
            Map.entry(Reaction.BOUDOUARD, "Boudouard"),
            Map.entry(Reaction.COAL_GASIFICATION, "Gasification"),
            Map.entry(Reaction.HYDROGEN_COMBUSTION, "H₂ Burning"),
            Map.entry(Reaction.METHANOL_SYNTHESIS, "Methanol"),
            Map.entry(Reaction.METHANOL_TO_OLEFINS, "MTO"),
            Map.entry(Reaction.POLYMERIZATION, "Polymerization"),
            Map.entry(Reaction.HCL_SYNTHESIS, "HCl Synthesis"),
            Map.entry(Reaction.DEACON, "Deacon"));

    private ReactorLanguage() {
    }

    static void add(ChemBioLanguage.Both both) {
        both.add("block.universe.chemical_reactor", "化学反应釜", "Chemical Reactor");
        both.add("jei.universe.reactor", "化学反应釜", "Chemical Reactor");
        both.add("tooltip.universe.chemical_reactor.summary",
                "照化学手册放反应物，釜认出反应就开始做\n做成一次，以后能直接选，接上自动化\n放对催化剂快几十倍\n产量图记着全服试过的工况",
                "Load reactants from the handbook; the reactor works out the reaction\nFinish one batch to unlock it for direct selection and automation\n"
                        + "The right catalyst is dozens of times faster\nThe yield map shows conditions anyone has run");
        for (Reaction reaction : Reaction.values()) {
            both.add("reaction.universe." + reaction.id(), reaction.nameZh(), reaction.nameEn());
            both.add("gui.universe.chemical_reactor.short." + reaction.id(), reaction.nameZh(), SHORT_EN.getOrDefault(reaction, reaction.nameEn()));
        }
        for (Reaction.Category category : Reaction.Category.values()) {
            both.add("reaction_category.universe." + category.id(), category.nameZh(), category.nameEn());
        }
        both.add("gui.universe.chemical_reactor.conditions", "%s，%s", "%s, %s");
        both.add("gui.universe.chemical_reactor.defaults", "恢复教科书工况", "Textbook values");
        both.add("gui.universe.chemical_reactor.defaults_idle", "认出反应以后才有教科书工况", "Available once a reaction is recognised");
        both.add("gui.universe.chemical_reactor.free", "自由投料", "Free feed");
        both.add("gui.universe.chemical_reactor.free.tip", "反应物随便放，釜按原料和催化剂认反应", "Load any reactants; the reactor works out the reaction");
        both.add("gui.universe.chemical_reactor.free.tip2", "做成一批就记下，以后能直接选", "Finish one batch and it can be picked directly");
        both.add("gui.universe.chemical_reactor.free.empty", "照化学手册放反应物和催化剂，认出反应就开始做。",
                "Load reactants and a catalyst.");
        both.add("gui.universe.chemical_reactor.free.unknown", "这几样东西对不上任何反应。", "No reaction uses this set of reactants.");
        both.add("gui.universe.chemical_reactor.free.ambiguous", "对得上 %s 条反应，放催化剂挑一条。", "%s reactions fit. Add a catalyst to pick one.");
        both.add("gui.universe.chemical_reactor.free.lookup", "反应式和催化剂查化学手册。", "The handbook lists equations and catalysts.");
        both.add("gui.universe.chemical_reactor.undiscovered", "还没发现：自由投料做成一批，或者读一张实验记录",
                "Not discovered: finish a batch in free feed, or read lab notes");
        both.add("gui.universe.chemical_reactor.catalyst_left", "剩 %s 批", "%s left");
        both.add("gui.universe.chemical_reactor.catalyst_wrong", "放错了", "Wrong");
        both.add("gui.universe.chemical_reactor.catalyst_empty", "空", "Empty");
        both.add("gui.universe.chemical_reactor.catalyst_unneeded", "用不着", "Unused");
        both.add("gui.universe.chemical_reactor.catalyst_idle", "待用", "Idle");
        both.add("gui.universe.chemical_reactor.catalyst_none", "不用", "None");
        both.add("gui.universe.chemical_reactor.catalyst_pick", "同一组原料对得上几条反应时，靠催化剂挑",
                "When several reactions share reactants, the catalyst picks one");
        both.add("gui.universe.chemical_reactor.status.running", "反应中", "Running");
        both.add("gui.universe.chemical_reactor.status.no_recipe", "缺配方", "No recipe");
        both.add("gui.universe.chemical_reactor.status.no_input", "缺原料", "No input");
        both.add("gui.universe.chemical_reactor.status.output_full", "产物满了", "Output full");
        both.add("gui.universe.chemical_reactor.status.no_power", "电不够", "No power");
        both.add("gui.universe.chemical_reactor.status.locked", "未发现", "Locked");
        both.add("gui.universe.chemical_reactor.status.unknown", "认不出", "Unknown");
        both.add("gui.universe.chemical_reactor.status.ambiguous", "对得上 %s 条", "%s match");
        both.add("gui.universe.chemical_reactor.status.empty", "空着", "Empty");
        both.add("gui.universe.chemical_reactor.axes", "温度 →  压强 ↑", "T →  P ↑");
        both.add("gui.universe.chemical_reactor.temperature", "温度 %s", "Temp %s");
        both.add("gui.universe.chemical_reactor.pressure", "压强 %s", "Press %s");
        both.add("gui.universe.chemical_reactor.conversion", "平衡转化率 %s · 单程转化率 %s", "Equilibrium %s · single pass %s");
        both.add("gui.universe.chemical_reactor.rate", "每分钟 %s", "Per minute: %s");
        both.add("gui.universe.chemical_reactor.amount", "%s 个%s", "%s %s");
        both.add("gui.universe.chemical_reactor.separator", "、", ", ");
        both.add("gui.universe.chemical_reactor.power_need", "开工后用电 %s", "Draws %s when running");
        both.add("gui.universe.chemical_reactor.needs_catalyst", "催化剂：%s", "Catalyst: %s");
        both.add("gui.universe.chemical_reactor.no_catalyst", "不用", "none");
        both.add("gui.universe.chemical_reactor.catalyst_life", "一个做 %s 批，做满了用掉", "Each one lasts %s batches");
        both.add("gui.universe.chemical_reactor.textbook", "教科书工况：%s", "Textbook: %s");
        both.add("gui.universe.chemical_reactor.unavailable", "数据包里没有这条反应", "Not in the data pack");
        both.add("gui.universe.chemical_reactor.cell", "%s–%s，%s–%s", "%s–%s, %s–%s");
        both.add("gui.universe.chemical_reactor.unmeasured", "还没人在这里做成过一批", "Nobody has run a batch here yet");
        both.add("gui.universe.chemical_reactor.cell_hint", "点一下设成这一格", "Click to use these conditions");
        both.add("gui.universe.chemical_reactor.slider_hint", "拖动或滚轮调节，按住 Shift 调得更细", "Drag or scroll; hold Shift for fine steps");
        // JEI 里反应釜的配方
        both.add("jei.universe.reactor.catalyst", "催化剂，不消耗：一个做 %s 批", "Catalyst, not consumed: lasts %s batches");
        both.add("jei.universe.reactor.discovered", "已发现，反应釜里能直接选", "Discovered: pick it in the reactor");
        both.add("jei.universe.reactor.listed", "未发现：自由投料做成一批", "Undiscovered: finish a batch in free feed");
        both.add("jei.universe.reactor.unlisted", "未发现：研究%s后列进化学手册", "Undiscovered: research %s to list it");
    }
}
