package io.github.sweetcornna.universe.datagen;

import java.util.Locale;

import io.github.sweetcornna.universe.core.microbe.Gene;
import io.github.sweetcornna.universe.core.microbe.Metabolism;
import io.github.sweetcornna.universe.core.microbe.Morphology;
import io.github.sweetcornna.universe.core.microbe.SampleSource;

/**
 * M20 微生物和基因的文字（子代理 C，ADR-0036 §五）：采样管和样本、三台设备的界面、基因和菌的分类、改造种苗、水培槽施肥。
 * 琼脂、培养基、测序芯片、编辑试剂、菌种管、改造种苗的名字在 {@link ChemBioLanguage}。
 */
final class MicrobeLanguage {
    /** 同时写中文和英文的一条。 */
    interface Both {
        void add(String key, String zh, String en);
    }

    private MicrobeLanguage() {
    }

    static void add(Both both) {
        items(both);
        machines(both);
        genes(both);
        taxonomy(both);
        jei(both);
    }

    private static void items(Both both) {
        both.add("item.universe.sample_tube", "采样管", "Sample Tube");
        both.add("item.universe.sample", "样本", "Sample");
        both.add("item.universe.sample.of", "%s样本", "%s Sample");
        both.add("item.universe.sample.tissue_of", "%s组织", "%s Tissue");
        both.add("item.universe.strain_tube.of", "菌种管（%s）", "Culture Tube (%s)");
        both.add("item.universe.strain_tube.edited", "改造菌种管（%s）", "Engineered Culture (%s)");
        both.add("item.universe.engineered_seedling.of", "改造%s种苗", "Engineered %s Seedling");

        both.add("tooltip.universe.sample_tube.summary", "对着土、水、冰、岩石、矿石右键采样\n对着动物、鱼和资源植物右键取组织\n另一只手拿着杂物或鱼时右键空气也行",
                "Right-click soil, water, ice, rock or ore to take a sample\nRight-click a creature, fish or resource plant for tissue\n"
                        + "Or hold junk or a fish in your other hand and use it");
        both.add("tooltip.universe.sample.summary", "环境样本放进培养箱分离菌种\n组织样本放进测序仪读基因",
                "Culture environmental samples in an incubator\nSequence tissue samples to read their genes");
        both.add("tooltip.universe.sample.where", "采自%s，%s", "From %s, %s");
        both.add("tooltip.universe.sample.sequence", "放进测序仪读基因", "Sequence it to read its genes");
        both.add("tooltip.universe.sample.culture", "放进培养箱分离菌种", "Culture it in an incubator");

        both.add("tooltip.universe.strain_tube.summary", "放进培养箱扩增，放进测序仪鉴定\n测过序的菌能在基因编辑台上改",
                "Grow more in an incubator, identify it in a sequencer\nSequenced strains can be edited at a gene editor");
        both.add("tooltip.universe.strain_tube.from", "采自%s", "Isolated from %s");
        both.add("tooltip.universe.strain_tube.unidentified", "测序以后才知道是什么菌", "Sequence it to identify");
        both.add("tooltip.universe.strain_tube.kind", "%s · %s", "%s · %s");
        both.add("tooltip.universe.strain_tube.temperature", "生长温度 %s–%s K，最适 %s", "Grows at %s–%s K, best at %s");
        both.add("tooltip.universe.strain_tube.doubling", "繁殖一代 %s（游戏里 %s 秒）", "Doubles every %s (%s s in game)");
        both.add("tooltip.universe.strain_tube.genes", "基因：%s", "Genes: %s");
        both.add("tooltip.universe.strain_tube.added", "编辑进 %s/%s：%s", "Edited in %s/%s: %s");
        both.add("tooltip.universe.strain_tube.domesticated", "已驯化，繁殖速度 %s", "Domesticated: growth %s");

        both.add("tooltip.universe.engineered_seedling.summary", "种在水培槽里，或者种在星球的土上\n右键水培槽种下",
                "Plant it in a hydroponic tray or in planet soil\nRight-click a tray to plant it");
        both.add("tooltip.universe.engineered_seedling.added", "编辑进 %s/%s：%s", "Edited in %s/%s: %s");
        both.add("tooltip.universe.engineered_seedling.planets", "能种在：%s", "Grows on: %s");
        both.add("tooltip.universe.engineered_seedling.tray_only", "只能种在水培槽里", "Hydroponic trays only");
        both.add("tooltip.universe.engineered_seedling.tray", "水培槽里生长 %s，产量 %s", "In a tray: growth %s, yield %s");
        both.add("message.universe.engineered_seedling.unmet", "种不活：%s", "Won't survive here: %s");

        both.add("tooltip.universe.agar.summary", "海带熬出来的胶\n和糖、盐配成培养基", "Gel boiled out of kelp\nMix with sugar and salt for culture medium");
        both.add("tooltip.universe.culture_medium.summary", "培养箱分离、扩增一次用一份", "The incubator uses one per run");
        both.add("tooltip.universe.sequencing_chip.summary", "测序仪测一次用一块", "The sequencer uses one per run");
        both.add("tooltip.universe.editing_kit.summary", "基因编辑台编辑一次用一份", "The gene editor uses one per edit");

        both.add("message.universe.sample_tube.nothing", "%s里没有微生物", "No microbes in this %s");
        both.add("message.universe.sample_tube.taken", "采到%s样本", "Took a %s sample");
        both.add("message.universe.sequencer.new_genes", "基因库新增：%s", "New in the gene library: %s");
        both.add("message.universe.hydroponic_tray.fertilized", "施了肥，还管 %s 茬", "Fertilized: %s harvests left");
        both.add("message.universe.hydroponic_tray.fertilizer_full", "肥已经够了", "Already fully fertilized");
    }

    private static void machines(Both both) {
        both.add("block.universe.incubator", "培养箱", "Incubator");
        both.add("block.universe.sequencer", "测序仪", "Sequencer");
        both.add("block.universe.gene_editor", "基因编辑台", "Gene Editor");
        both.add("tooltip.universe.incubator.summary", "环境样本加培养基，分离出一管菌种\n菌种管加培养基，扩增成两管\n温度设成采样处的温度才长得出来\n用电 40 kW",
                "Sample plus culture medium isolates a strain\nCulture tube plus medium grows two tubes\nSet it to the temperature where you sampled\nUses 40 kW");
        both.add("tooltip.universe.sequencer.summary", "测菌种管、组织样本或鱼，基因记进全服的基因库\n每次用一块测序芯片\n用电 40 kW",
                "Sequences culture tubes, tissue samples or fish into the server's gene library\nUses one flow cell per run\nUses 40 kW");
        both.add("tooltip.universe.gene_editor.summary", "把基因库里的基因编辑进菌种或植物产物\n植物产物编辑后变成改造种苗\n每次用一份编辑试剂\n用电 60 kW",
                "Edits a gene from the library into a strain or a crop\nEdited crops become engineered seedlings\nUses one editing kit per edit\nUses 60 kW");

        String i = "gui.universe.incubator.";
        both.add(i + "empty", "放进样本或菌种管", "Insert a sample or culture tube");
        both.add(i + "tissue", "组织样本要放进测序仪", "Tissue goes in the sequencer");
        both.add(i + "no_medium", "缺培养基", "Needs culture medium");
        both.add(i + "no_growth", "这个温度长不了", "Nothing grows at this temperature");
        both.add(i + "output_full", "产出格满了", "Output full");
        both.add(i + "isolating", "正在分离：%s", "Isolating: %s");
        both.add(i + "expanding", "正在扩增：%s", "Growing: %s");
        both.add(i + "sampled_at", "采样处 %s", "Sampled at %s");
        both.add(i + "domesticating", "在这个温度扩增会驯化它", "Growing it here domesticates it");
        both.add(i + "last_nothing", "上一份样本什么也没长出来", "The last sample grew nothing");
        both.add(i + "last_grew", "上一份样本分离出了菌种", "The last sample yielded a strain");
        both.add(i + "temperature", "温度 %s", "Temperature %s");

        String s = "gui.universe.sequencer.";
        both.add(s + "empty", "放进菌种管、组织样本或鱼", "Insert a culture tube, tissue sample or fish");
        both.add(s + "no_chip", "缺测序芯片", "Needs a flow cell");
        both.add(s + "output_full", "产出格满了", "Output full");
        both.add(s + "working", "正在测序：%s", "Sequencing: %s");
        both.add(s + "last_known", "上一次：没有新东西", "Last run: nothing new");
        both.add(s + "last_genes", "上一次的新基因：%s", "Last run, new genes: %s");
        both.add(s + "last_strain", "上一次：新菌株", "Last run: new strain");
        both.add(s + "last_strain_genes", "上一次：新菌株，新基因 %s", "Last run: new strain, new genes: %s");
        both.add(s + "library", "基因库：%s/%s 种基因，%s 株菌", "Library: %s/%s genes, %s strains");

        String g = "gui.universe.gene_editor.";
        both.add(g + "empty", "放进菌种管或植物产物", "Insert a culture tube or a crop");
        both.add(g + "not_editable", "这个编辑不了", "This can't be edited");
        both.add(g + "no_gene", "在下面选一个基因", "Pick a gene below");
        both.add(g + "not_in_library", "基因库里没有%s", "%s isn't in the library");
        both.add(g + "already", "已经有%s了", "Already has %s");
        both.add(g + "full", "最多编辑进 %s 个基因", "At most %s added genes");
        both.add(g + "no_kit", "缺编辑试剂", "Needs an editing kit");
        both.add(g + "output_full", "产出格满了", "Output full");
        both.add(g + "working", "正在把%s编辑进%s", "Adding %s to %s");
        both.add(g + "has", "已有：%s", "Has: %s");
        both.add(g + "gene_tooltip", "%s：%s", "%s: %s");
        both.add(g + "unknown_gene", "还没测到这个基因", "Not sequenced yet");
    }

    private static void genes(Both both) {
        for (Gene gene : Gene.values()) {
            both.add("gene.universe." + gene.id(), gene.nameZh(), gene.nameEn());
        }
        use(both, Gene.ANTIFREEZE_PROTEIN, "耐寒。改造植物能种在冷的星球上", "Cold tolerance. Edited plants survive cold planets");
        use(both, Gene.THERMOSTABLE_ENZYMES, "耐热。改造植物能种在热的星球上", "Heat tolerance. Edited plants survive hot planets");
        use(both, Gene.DNA_REPAIR, "耐辐射", "Radiation tolerance");
        use(both, Gene.PROTON_PUMP, "耐酸，耐有毒的大气", "Tolerates acid and toxic air");
        use(both, Gene.COMPATIBLE_SOLUTES, "耐盐，耐旱", "Tolerates salt and drought");
        use(both, Gene.UV_SHIELD, "挡住稀薄大气下的强紫外", "Blocks the harsh UV under thin air");
        use(both, Gene.NITROGENASE, "自己固氮：水培槽里不用施肥，产量多四分之一；生物反应器里固氮",
                "Fixes its own nitrogen: no fertilizer in a tray, 25% more yield; nitrogen fixation in a bioreactor");
        use(both, Gene.CARBON_CONCENTRATING, "光合更快：水培槽里长得快一半", "Faster photosynthesis: grows 50% faster in a tray");
        use(both, Gene.LUCIFERASE, "会发光。实验室里拿它当报告基因", "Makes cells glow. Labs use it as a reporter gene");
        use(both, Gene.METHANOGENESIS, "生物反应器里把二氧化碳和氢变成甲烷", "Turns CO₂ and hydrogen into methane in a bioreactor");
        use(both, Gene.IRON_SULFUR_OXIDATION, "生物反应器里浸矿", "Leaches ore in a bioreactor");
        use(both, Gene.PETASE, "生物反应器里分解杂物里的塑料", "Breaks down plastic junk in a bioreactor");
        use(both, Gene.OXYGENIC_PHOTOSYNTHESIS, "放氧。生物反应器养藻，播种站拿它改造大气",
                "Releases oxygen. Grows algae in a bioreactor; seeding stations use it to terraform");
    }

    private static void use(Both both, Gene gene, String zh, String en) {
        both.add("gene.universe." + gene.id() + ".use", zh, en);
    }

    private static void taxonomy(Both both) {
        for (SampleSource source : SampleSource.values()) {
            both.add("microbe.universe.source." + source.id(), source.nameZh(), source.nameEn());
        }
        for (Metabolism metabolism : Metabolism.values()) {
            both.add("microbe.universe.metabolism." + metabolism.name().toLowerCase(Locale.ROOT), metabolism.nameZh(), metabolism.nameEn());
        }
        for (Morphology morphology : Morphology.values()) {
            String en = switch (morphology) {
                case COCCUS -> "Coccus";
                case BACILLUS -> "Bacillus";
                case SPIRILLUM -> "Spirillum";
                case FILAMENT -> "Filamentous";
                case ARCHAEON -> "Archaeon";
            };
            both.add("microbe.universe.morphology." + morphology.name().toLowerCase(Locale.ROOT), morphology.nameZh(), en);
        }
        both.add("microbe.universe.temperature", "%s K（%s °C）", "%s K (%s °C)");
        both.add("microbe.universe.minutes", "%s 分钟", "%s min");
        both.add("microbe.universe.hours", "%s 小时", "%s h");
        both.add("microbe.universe.none", "无", "none");
        both.add("microbe.universe.separator", "、", ", ");
        both.add("microbe.universe.unidentified", "未鉴定的菌株", "Unidentified Strain");
        both.add("discovery.universe.kind.microbe", "微生物", "Microbe");
        both.add("gui.universe.discovery.microbes", "微生物 %s", "Microbes %s");
    }

    private static void jei(Both both) {
        both.add("jei.universe.info.sample_tube", "采样管对着土、水、热泉结壳、酸液、冰雪、盐、岩石、沉积物、矿石右键采环境样本，对着异星动物、鱼和资源植物右键取组织。"
                + "采样时记下当地的温度，培养箱要设成差不多的温度才分离得出来。地球上也能采，采到的是地球的菌。",
                "Right-click soil, water, geyser crust, acid, ice, salt, rock, sediment or ore for an environmental sample, or a creature, fish "
                        + "or resource plant for tissue. The tube records the local temperature; set the incubator close to it to isolate anything. "
                        + "Earth samples give Earth microbes.");
        both.add("jei.universe.info.incubator", "环境样本加一份培养基，分离出一管菌种；菌种管加一份培养基扩增成两管。温度能设 0–120 °C，菌只在自己的温度范围里长。"
                + "贴着温度范围的边扩增算驯化，驯化过的繁殖更快。",
                "A sample plus culture medium isolates one culture tube; a tube plus medium grows two. Set 0–120 °C; each strain grows only "
                        + "within its own range. Growing it near the edge of that range domesticates it, and domesticated strains grow faster.");
        both.add("jei.universe.info.sequencer", "测菌种管、组织样本或鱼，用一块测序芯片。测出的基因记进全服共用的基因库；第一次测出异星的菌株会记进发现日志，给勘探数据。",
                "Sequences a culture tube, tissue sample or fish using one flow cell. Genes go into the server-wide gene library. "
                        + "The first alien strain of each kind goes into the discovery log and pays survey data.");
        both.add("jei.universe.info.gene_editor", "放进测过序的菌种管或资源植物的产物，在界面里选基因库里的一个基因，用一份编辑试剂编辑进去。一个最多编辑进四个基因，编辑得越多长得越慢。"
                + "植物产物编辑后变成改造种苗。",
                "Insert a sequenced culture tube or a resource plant's crop, pick a gene from the library and spend an editing kit. "
                        + "Up to four added genes; each one slows growth. Edited crops become engineered seedlings.");
        both.add("jei.universe.info.engineered_seedling", "基因编辑台编辑资源植物的产物得到。带上对应的耐受基因，就能种在本来活不了的星球上（真空和过热不行）。"
                + "种在水培槽里每茬有四分之一的机会多给一株种苗。水培槽拿硝酸铵右键施肥，一袋管三茬，施了肥的那茬长得快一半。",
                "Made by editing a resource plant's crop at a gene editor. With the right tolerance genes it grows on planets it otherwise "
                        + "couldn't (never in vacuum or extreme heat). In a hydroponic tray each harvest has a 1 in 4 chance of an extra seedling. "
                        + "Right-click a tray with ammonium nitrate to fertilize it: one bag covers three harvests, each 50% faster.");
    }
}
