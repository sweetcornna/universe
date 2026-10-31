package io.github.sweetcornna.universe.datagen;

import io.github.sweetcornna.universe.core.microbe.BioProcess;

/** 生物反应器（ADR-0036 §五）的文字。 */
final class BioreactorLanguage {
    private BioreactorLanguage() {
    }

    static void add(ChemBioLanguage.Both both) {
        both.add("block.universe.bioreactor", "生物反应器", "Bioreactor");
        both.add("jei.universe.bioreactor", "生物反应器", "Bioreactor");
        both.add("jei.universe.bioreactor.gene", "菌要带%s基因", "Strain needs %s");
        both.add("jei.universe.bioreactor.gene_light", "菌要带%s基因，要光", "Strain needs %s, plus light");
        both.add("tooltip.universe.bioreactor.summary",
                "放一管菌种接种，选工艺，设温度\n菌要带工艺的基因\n断料、断电两分钟就开始死",
                "Inoculate with a culture tube, pick a process, set the temperature\nThe strain needs the process's gene\n"
                        + "Cut its feed or power for two minutes and it starts dying");
        both.add("jei.universe.info.bioreactor",
                "菌种格里放一管菌种，罐空着时整管倒进去接种。配方的时间是罐长满、菌在最适温度时的，刚接种的一罐要几分钟才长满。"
                        + "罐里的菌要带工艺的基因：生物浸出要铁硫氧化，藻类培养要产氧光合，生物甲烷化要产甲烷，生物固氮要固氮酶，"
                        + "塑料回收要塑料降解酶。藻类要光，看不到天时开补光灯，费电。",
                "Put a culture tube in the strain slot; an empty tank takes the whole tube as inoculum. Recipe times assume a full tank "
                        + "at the strain's best temperature, and a fresh tank takes a few minutes to fill. The strain needs the process's gene: "
                        + "iron-sulfur oxidation for bioleaching, oxygenic photosynthesis for algae, methanogenesis for biomethanation, "
                        + "nitrogenase for nitrogen fixation, PETase for plastic recycling. Algae need light; without open sky the grow "
                        + "lights come on and draw a lot of power.");
        for (BioProcess process : BioProcess.values()) {
            both.add("bioprocess.universe." + process.id(), process.nameZh(), process.nameEn());
        }
        String g = "gui.universe.bioreactor.";
        both.add(g + "status.no_culture", "罐里没有活菌", "No live culture");
        both.add(g + "status.wrong_strain", "这罐菌做不了", "Wrong strain");
        both.add(g + "status.bad_temperature", "这个温度菌不长", "Too hot or cold");
        both.add(g + "time", "满罐 %s 秒一批", "%s s per batch, full tank");
        both.add(g + "empty", "空罐", "Empty tank");
        both.add(g + "empty_hint", "左边放一管菌种", "Put a culture tube on the left");
        both.add(g + "state.growing", "长着", "Growing");
        both.add(g + "state.starving", "断料", "Starving");
        both.add(g + "state.dying", "在死", "Dying");
        both.add(g + "state.lost", "死光了，重新接种", "Dead; inoculate again");
        both.add(g + "state_density", "%s · 密度 %s", "%s · density %s");
        both.add(g + "flush", "倒掉", "Flush");
        both.add(g + "flush_hint", "倒掉这罐，好换别的菌", "Empty the tank to switch strains");
        both.add(g + "needs_gene", "选的工艺要%s基因", "This process needs %s");
        both.add(g + "can_do", "能做：%s", "Can run: %s");
        both.add(g + "setpoint", "设定 %s", "Set %s");
        both.add(g + "setpoint_growth", "设定 %s · 生长 %s", "Set %s · growth %s");
        both.add(g + "sunlight", "日光", "Sunlight");
        both.add(g + "grow_lights", "补光灯", "Grow lights");
        both.add(g + "genes", "基因：%s", "Genes: %s");
        both.add(g + "doubling", "最快 %s 翻一倍（游戏里 %s 秒）", "Doubles every %s at best (%s s in game)");
        both.add(g + "productivity", "产率 %s", "Productivity %s");
        both.add(g + "curve", "最近两分钟的密度", "Density, last two minutes");
        both.add(g + "curve_hint", "上面的虚线是满罐", "The top line is a full tank");
        both.add(g + "tank", "罐温 %s", "Tank %s");
        both.add(g + "ambient", "星球气温 %s", "Planet %s");
        both.add(g + "range", "能长 %s–%s，最适 %s", "Grows %s–%s, best at %s");
        both.add(g + "temperature_hint", "离星球气温越远越费电；断电时罐温跟着气温走", "Further from the planet's temperature costs more power; "
                + "without power the tank drifts to it");
        both.add(g + "strain_slot", "菌种格", "Strain slot");
        both.add(g + "strain_slot_hint", "罐空着时整管倒进去接种", "An empty tank takes the whole tube");
    }
}
