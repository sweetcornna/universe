package io.github.sweetcornna.universe.datagen;

import io.github.sweetcornna.universe.core.chem.reaction.Reaction;

/** 化学反应釜（ADR-0036 §四）的文字。 */
final class ReactorLanguage {
    private ReactorLanguage() {
    }

    static void add(ChemBioLanguage.Both both) {
        both.add("block.universe.chemical_reactor", "化学反应釜", "Chemical Reactor");
        both.add("jei.universe.reactor", "化学反应釜", "Chemical Reactor");
        both.add("tooltip.universe.chemical_reactor.summary",
                "选一条反应，调温度和压强\n放对催化剂快几十倍，一个做 256 批\n产量图记着全服试过的工况\n加压对合成氨有利，对重整不利",
                "Pick a reaction, then set temperature and pressure\nThe right catalyst is dozens of times faster; each lasts 256 batches\n"
                        + "The yield map shows conditions anyone has run\nPressure helps ammonia and hurts reforming");
        for (Reaction reaction : Reaction.values()) {
            both.add("reaction.universe." + reaction.id(), reaction.nameZh(), reaction.nameEn());
        }
        // 按钮上的短名字
        both.add("gui.universe.chemical_reactor.short.haber", "合成氨", "Haber–Bosch");
        both.add("gui.universe.chemical_reactor.short.contact", "接触法制硫酸", "Contact");
        both.add("gui.universe.chemical_reactor.short.sabatier", "萨巴蒂尔反应", "Sabatier");
        both.add("gui.universe.chemical_reactor.short.steam_reforming", "甲烷蒸汽重整", "Reforming");
        both.add("gui.universe.chemical_reactor.short.fischer_tropsch", "费托合成", "FT Synthesis");
        both.add("gui.universe.chemical_reactor.conditions", "%s，%s", "%s, %s");
        both.add("gui.universe.chemical_reactor.conditions_byproduct", "%s，%s，副产%s", "%s, %s, by-product %s");
        both.add("gui.universe.chemical_reactor.defaults", "恢复教科书工况", "Textbook values");
        both.add("gui.universe.chemical_reactor.catalyst_left", "剩 %s 批", "%s left");
        both.add("gui.universe.chemical_reactor.catalyst_wrong", "放错了", "Wrong");
        both.add("gui.universe.chemical_reactor.catalyst_empty", "空", "Empty");
        both.add("gui.universe.chemical_reactor.status.running", "反应中", "Running");
        both.add("gui.universe.chemical_reactor.status.no_reaction", "选一条反应", "No reaction");
        both.add("gui.universe.chemical_reactor.status.no_input", "缺原料", "No input");
        both.add("gui.universe.chemical_reactor.status.output_full", "产物满了", "Output full");
        both.add("gui.universe.chemical_reactor.status.no_power", "电不够", "No power");
        both.add("gui.universe.chemical_reactor.status.locked", "未解锁", "Locked");
        both.add("gui.universe.chemical_reactor.axes", "温度 →  压强 ↑", "T →  P ↑");
        both.add("gui.universe.chemical_reactor.temperature", "温度 %s", "Temp %s");
        both.add("gui.universe.chemical_reactor.pressure", "压强 %s", "Press %s");
        both.add("gui.universe.chemical_reactor.conversion", "平衡转化率 %s · 单程转化率 %s", "Equilibrium %s · single pass %s");
        both.add("gui.universe.chemical_reactor.rate", "每分钟 %s", "Per minute: %s");
        both.add("gui.universe.chemical_reactor.amount", "%s 个%s", "%s %s");
        both.add("gui.universe.chemical_reactor.separator", "、", ", ");
        both.add("gui.universe.chemical_reactor.power_need", "开工后用电 %s", "Draws %s when running");
        both.add("gui.universe.chemical_reactor.needs_catalyst", "催化剂：%s", "Catalyst: %s");
        both.add("gui.universe.chemical_reactor.catalyst_life", "一个做 %s 批，做满了用掉", "Each one lasts %s batches");
        both.add("gui.universe.chemical_reactor.textbook", "教科书工况：%s", "Textbook: %s");
        both.add("gui.universe.chemical_reactor.unavailable", "数据包里没有这条反应", "Not in the data pack");
        both.add("gui.universe.chemical_reactor.cell", "%s–%s，%s–%s", "%s–%s, %s–%s");
        both.add("gui.universe.chemical_reactor.unmeasured", "还没人在这里做成过一批", "Nobody has run a batch here yet");
        both.add("gui.universe.chemical_reactor.cell_hint", "点一下设成这一格", "Click to use these conditions");
        both.add("gui.universe.chemical_reactor.slider_hint", "拖动或滚轮调节，按住 Shift 调得更细", "Drag or scroll; hold Shift for fine steps");
    }
}
