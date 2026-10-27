package io.github.sweetcornna.universe.datagen;

import io.github.sweetcornna.universe.core.chem.Gas;

/** M20 化学与生物研究（ADR-0036）几条线共用物品的名字。各条线自己的文字在各自的类里。 */
final class ChemBioLanguage {
    /** 同时写中文和英文的一条。 */
    interface Both {
        void add(String key, String zh, String en);
    }

    private ChemBioLanguage() {
    }

    private static void tech(Both both, String id, String zh, String en) {
        both.add("tech.universe." + id, zh, en);
    }

    /** M20 的手册：化学一章后面的反应釜五页，生物、星球改造两章（ADR-0036 §七）。 */
    static void guide(Both both) {
        String c = "guide.universe.chemistry";
        both.add(c + ".reactor.title", "反应釜", "Chemical Reactor");
        both.add(c + ".reactor",
                "反应釜做受化学平衡限制的气相反应。界面里选一条反应，拉温度（300–1200 K）和压强（1–300 bar）两条滑块。"
                        + "刚选上的反应在教科书的工况上，从那里调起。",
                "The reactor runs gas-phase reactions that equilibrium holds back. Pick a reaction, then set temperature (300–1200 K) and "
                        + "pressure (1–300 bar). A reaction starts at its textbook conditions; tune from there.");
        both.add(c + ".reactions.title", "五条反应", "Five Reactions");
        both.add(c + ".reactions",
                "合成氨 N₂ + 3H₂ ⇄ 2NH₃\n铁，450 °C、200 bar\n\n"
                        + "接触法 2SO₂ + O₂ ⇄ 2SO₃\n五氧化二钒，430 °C、1.5 bar\n\n"
                        + "萨巴蒂尔 CO₂ + 4H₂ → CH₄ + 2H₂O\n镍，350 °C、5 bar\n\n"
                        + "蒸汽重整 CH₄ + H₂O ⇄ CO + 3H₂\n镍，850 °C、25 bar\n\n"
                        + "费托合成 CO + 2H₂ → 油 + H₂O\n铁，275 °C、25 bar",
                "Haber–Bosch N₂ + 3H₂ ⇄ 2NH₃\nIron, 450 °C, 200 bar\n\n"
                        + "Contact 2SO₂ + O₂ ⇄ 2SO₃\nVanadium pentoxide, 430 °C, 1.5 bar\n\n"
                        + "Sabatier CO₂ + 4H₂ → CH₄ + 2H₂O\nNickel, 350 °C, 5 bar\n\n"
                        + "Steam reforming CH₄ + H₂O ⇄ CO + 3H₂\nNickel, 850 °C, 25 bar\n\n"
                        + "Fischer–Tropsch CO + 2H₂ → oil + H₂O\nIron, 275 °C, 25 bar");
        both.add(c + ".equilibrium.title", "平衡和速率", "Equilibrium and Rate");
        both.add(c + ".equilibrium",
                "平衡照真实的热力学算（NIST 的热容数据）。低温平衡好但慢，高温快但平衡差，中间有一个产量最高的温度。\n\n"
                        + "气体分子变少的反应（合成氨、接触法、萨巴蒂尔）加压有利，重整反过来。费托合成不受平衡限制，温度越高副产的甲烷越多。",
                "Equilibrium follows real thermodynamics (NIST heat-capacity data). Cold favours the equilibrium but runs slowly; heat runs fast "
                        + "but shifts the equilibrium back, so there is a best temperature in between.\n\n"
                        + "Reactions that lose gas molecules (Haber–Bosch, contact, Sabatier) like pressure; reforming is the reverse. "
                        + "Fischer–Tropsch has no equilibrium limit, but more of it turns into methane as it gets hotter.");
        both.add(c + ".conditions.title", "工况图和用电", "Condition Map and Power");
        both.add(c + ".conditions",
                "界面里的“温度 × 压强”图：跑过一批的工况那一格填上颜色，越亮产量越高，没跑过的是灰的。点一格就换到那个工况。\n\n"
                        + "用电随压强的对数涨，保温和星球气温差得越多越费电，吸热的重整还要加上反应热。",
                "The temperature × pressure map fills in a cell for every condition you have run a batch at, brighter for more output; "
                        + "the rest stay grey. Click a cell to jump to it.\n\n"
                        + "Power grows with the log of pressure, with how far the set temperature is from the planet's air, and with the heat that "
                        + "endothermic reforming soaks up.");
        both.add(c + ".catalysts.title", "催化剂", "Catalysts");
        both.add(c + ".catalysts",
                "催化剂放在自己的格子里，一个做 256 批，做满了用掉。没放或放错，反应慢十几倍。\n\n"
                        + "三种都在化工厂里做：\n"
                        + "铁催化剂：1 磁铁矿 + 4 氢\n"
                        + "五氧化二钒：2 钒渣 + 1 氢氧化钠\n"
                        + "镍催化剂：1 镍锭 + 2 硝酸 + 1 氢\n\n"
                        + "镍锭从生物反应器的生物浸出里来。",
                "The catalyst has its own slot. Each one lasts 256 batches. With none, or the wrong one, the reaction runs more than ten times slower.\n\n"
                        + "All three come from the chemical plant:\n"
                        + "Iron: 1 magnetite + 4 hydrogen\n"
                        + "Vanadium pentoxide: 2 vanadium slag + 1 NaOH\n"
                        + "Nickel: 1 nickel ingot + 2 nitric acid + 1 hydrogen\n\n"
                        + "Nickel ingots come out of bioleaching in the bioreactor.");

        String b = "guide.universe.biology";
        both.add(b, "微生物和基因", "Biology");
        both.add(b + ".sampling.title", "采样", "Sampling");
        both.add(b + ".sampling",
                "拿空采样管右键方块、液体或生物，装一管样本。样本记着星球、来源和采样处的温度。\n\n"
                        + "能采的有：土、水、间歇泉的结壳、酸液、冰雪、盐、岩石、水底的泥沙、铜铁硫的矿石、垃圾，还有动物、鱼和资源植物的组织。"
                        + "这里一种菌都没有就采不到。",
                "Right-click a block, a liquid or a creature with an empty sample tube to fill it. The sample remembers the planet, the source "
                        + "and the temperature where you took it.\n\n"
                        + "Sources: soil, water, geyser crust, acid, ice and snow, salt, rock, wet mud and sand, copper, iron and sulfur ore, junk, and "
                        + "tissue from animals, fish and resource plants. If nothing lives there, the tube stays empty.");
        both.add(b + ".microbiomes.title", "各地的菌", "Local Microbes");
        both.add(b + ".microbiomes",
                "每颗星球的菌都不一样：热泉里是嗜热菌，冰里是嗜冷菌，辐射星球的岩石里有耐辐射的。\n\n"
                        + "母星上是一组真实的菌，比如沼泽泥里的巴氏甲烷八叠球菌、海水里的费氏弧菌。",
                "Every planet has its own microbes: thermophiles in hot springs, psychrophiles in ice, radiation-tough strains in the rock of "
                        + "irradiated worlds.\n\n"
                        + "The home planet has a set of real ones, such as Methanosarcina barkeri in swamp mud and Aliivibrio fischeri in seawater.");
        both.add(b + ".incubator.title", "培养箱", "Incubator");
        both.add(b + ".incubator",
                "样本加一份培养基，设一个温度（0–120 °C），2 分 24 秒长出一管菌种。只长得出温度范围对得上的菌，"
                        + "热泉的样本要设到七八十度。样本的说明里写着采样处的温度，从那里试起。",
                "A sample plus one culture medium grows a strain tube in 2 min 24 s at the temperature you set (0–120 °C). Only strains that "
                        + "grow at that temperature come up; a hot-spring sample wants 70–80 °C. Start from the temperature on the sample's tooltip.");
        both.add(b + ".expanding.title", "扩增", "Expanding a Strain");
        both.add(b + ".expanding",
                "菌种加培养基扩增成两管，1 分 36 秒。在菌能长的温度边上扩增，每轮产率高 5%，最多高 50%。\n\n"
                        + "培养基在营养加工机里做：琼脂（海带）、糖和盐。",
                "A strain tube plus medium expands into two tubes in 1 min 36 s. Expanding near the edge of a strain's range raises its "
                        + "yield by 5% each round, up to 50%.\n\n"
                        + "Culture medium is made in the nutrient processor from agar (kelp), sugar and salt.");
        both.add(b + ".sequencer.title", "测序", "Sequencing");
        both.add(b + ".sequencer",
                "测序仪放一管菌种、一份组织样本或一条鱼，加一片测序芯片，36 秒测完。认出菌名，基因记进基因库。\n\n"
                        + "2016 年，国际空间站上用纳米孔测序仪第一次在太空里测了 DNA。",
                "Put a strain tube, a tissue sample or a fish in the sequencer with one sequencing chip; it finishes in 36 s. The strain gets its "
                        + "name and its genes go into the gene library.\n\n"
                        + "In 2016 a nanopore sequencer on the ISS read DNA in space for the first time.");
        both.add(b + ".gene_library.title", "基因库", "Gene Library");
        both.add(b + ".gene_library",
                "基因库全服共享：谁测出来的基因，所有人的基因编辑台都能选。\n\n"
                        + "第一次测出一种外星菌，发现日志多一条微生物，测的人拿 3 份勘探数据。没测过的菌种只写“未鉴定的菌株”。",
                "The gene library is shared by the whole server: a gene anyone has sequenced shows up on every gene editor.\n\n"
                        + "The first time anyone sequences an alien strain, the discovery log gains a microbe entry and the player who ran it gets "
                        + "3 survey data. Strains nobody has sequenced read “unidentified strain”.");
        both.add(b + ".genes.title", "基因", "Genes");
        both.add(b + ".genes",
                "抗冻蛋白：耐寒（南极鱼）\n"
                        + "嗜热酶：耐热（栖热水生菌）\n"
                        + "DNA 修复：耐辐射（耐辐射奇球菌）\n"
                        + "质子泵：耐酸（嗜酸菌）\n"
                        + "相容溶质：耐盐、耐旱（嗜盐古菌）\n"
                        + "紫外防护色素：扛稀薄大气的紫外\n"
                        + "固氮酶：固氮（根瘤菌）\n"
                        + "碳浓缩：光合更快（蓝藻）\n"
                        + "荧光素酶：发光（费氏弧菌）\n"
                        + "产甲烷：产甲烷古菌\n"
                        + "铁硫氧化：浸矿\n"
                        + "塑料降解酶：大阪堺菌\n"
                        + "产氧光合：放氧，改造星球靠它",
                "Antifreeze protein: cold (Antarctic fish)\n"
                        + "Thermostable enzymes: heat (Thermus aquaticus)\n"
                        + "DNA repair: radiation (Deinococcus radiodurans)\n"
                        + "Proton pump: acid (acidophiles)\n"
                        + "Compatible solutes: salt, drought (halophiles)\n"
                        + "UV pigment: the UV under thin air\n"
                        + "Nitrogenase: nitrogen fixation (rhizobia)\n"
                        + "Carbon concentration: faster photosynthesis (cyanobacteria)\n"
                        + "Luciferase: light (Aliivibrio fischeri)\n"
                        + "Methanogenesis: methanogenic archaea\n"
                        + "Iron-sulfur oxidation: bioleaching\n"
                        + "PETase: Ideonella sakaiensis\n"
                        + "Oxygenic photosynthesis: makes oxygen");
        both.add(b + ".editing.title", "基因编辑", "Gene Editing");
        both.add(b + ".editing",
                "基因编辑台：放一管菌种或一株资源植物，从基因库里选一个基因，放一份编辑试剂，72 秒做出改好的菌种或一株改造种苗。"
                        + "一样东西最多加 4 个基因，每加一个长得慢一成。",
                "On the gene editor, place a strain tube or a resource plant, pick a gene from the library and add one editing kit. In 72 s you "
                        + "get an edited strain or an engineered seedling. Each one takes at most 4 added genes, and each gene slows growth by 10%.");
        both.add(b + ".tray.title", "改造种苗", "Engineered Seedlings");
        both.add(b + ".tray",
                "改造种苗能种到本来种不了的星球上：抗冻的种在冰冻星球，耐酸的种在毒雾星球。稀薄大气下还要紫外防护色素。\n\n"
                        + "改造种苗也能放进水培槽。碳浓缩的长得快，固氮的收得多、不吃化肥。每收一茬有四分之一的机会多出一株种苗。",
                "Engineered seedlings grow where the plant could not: antifreeze ones on frozen worlds, acid-proof ones on toxic worlds. "
                        + "Under thin air they also need the UV pigment.\n\n"
                        + "They go in the hydroponic tray too. Carbon concentration grows faster; nitrogenase yields more and needs no "
                        + "fertilizer. Each harvest has a one-in-four chance of an extra seedling.");
        both.add(b + ".fertilizer.title", "化肥", "Fertilizer");
        both.add(b + ".fertilizer",
                "拿硝酸铵化肥右键水培槽：一袋存 3 次，最多存 9 次，每次让下一茬快一半。\n\n"
                        + "硝酸铵在化工厂里用硝酸和氨做，见化学一章。",
                "Right-click a tray with ammonium nitrate: one bag stores 3 doses, up to 9, and each dose makes the next crop grow 50% faster.\n\n"
                        + "The chemical plant makes it from nitric acid and ammonia; see the Chemistry chapter.");
        both.add(b + ".bioreactor.title", "生物反应器", "Bioreactor");
        both.add(b + ".bioreactor",
                "放一管菌种接种（整管用掉），设温度，持续进料、持续出货。菌按 logistic 曲线长：先慢后快，满了就停，产量跟着密度走。\n\n"
                        + "菌没有工艺要的基因，什么也不出。",
                "Inoculate it with a strain tube (the whole tube is used), set the temperature, and keep it fed. The culture follows a logistic "
                        + "curve, slow, then fast, then flat, and output follows the density.\n\n"
                        + "A strain without the gene a process needs makes nothing.");
        both.add(b + ".culture.title", "养菌", "Keeping a Culture");
        both.add(b + ".culture",
                "断料两分钟以后菌开始死，罐温过了菌的上限死得更快，低于下限只是不长。密度掉到 1% 这一罐就没了，要重新接种；"
                        + "拆掉反应器，罐里的菌也没了。\n\n"
                        + "有活菌就一直用电控温：60 W，罐温和气温每差 1 K 再加 2 W。",
                "Two minutes without feed and cells start to die; above the strain's maximum they die faster, below its minimum they just "
                        + "stop growing. At 1% density the culture is lost and needs a new tube. Breaking the reactor loses it too.\n\n"
                        + "A live culture always draws power for temperature control: 60 W, plus 2 W for every kelvin between the tank and the air.");
        both.add(b + ".processes.title", "生物浸出", "Bioleaching");
        both.add(b + ".processes",
                "要铁硫氧化的菌：2 粗铜 + 硫酸 + 2 水 → 3 含铜浸出液，偶尔多出镍锭；金一样。浸出液送去电解槽电积。\n\n"
                        + "现实里世界上约两成的铜是浸出出来的。",
                "Needs iron-sulfur oxidation: 2 raw copper + sulfuric acid + 2 water → 3 copper leach solution, sometimes a nickel ingot. "
                        + "Gold works the same. Electrowin the solution in the electrolyzer.\n\n"
                        + "About a fifth of the world's copper is produced by leaching.");
        both.add(b + ".algae.title", "藻类", "Algae");
        both.add(b + ".algae",
                "要产氧光合的菌：2 二氧化碳 + 2 水 → 藻类生物质 + 2 氧。\n\n"
                        + "要光：看得到天、又是白天就用太阳，不然自己开补光灯。补光灯费的电比藻类烧掉换回的多。",
                "Needs oxygenic photosynthesis: 2 CO₂ + 2 water → algal biomass + 2 oxygen.\n\n"
                        + "It needs light: open sky by day, or else it runs grow lights, which cost more power than the biomass gives back.");
        both.add(b + ".fixation.title", "甲烷、氨和塑料", "Methane, Ammonia, Plastic");
        both.add(b + ".fixation",
                "甲烷化（产甲烷）：二氧化碳 + 4 氢 → 甲烷 + 2 水，常温，不要镍\n"
                        + "固氮（固氮酶）：氮 + 3 藻类生物质 + 4 氧 → 2 氨 + 6 二氧化碳\n"
                        + "塑料回收（塑料降解酶）：破损的储罐 + 水 → 塑料",
                "Methanation (methanogenesis): CO₂ + 4 hydrogen → methane + 2 water, at room temperature, no nickel\n"
                        + "Nitrogen fixation (nitrogenase): nitrogen + 3 algal biomass + 4 oxygen → 2 ammonia + 6 CO₂\n"
                        + "Plastic recycling (PETase): broken canister + water → plastic");

        String t = "guide.universe.terraforming";
        both.add(t, "星球改造", "Terraforming");
        both.add(t + ".thresholds.title", "四道门槛", "Four Thresholds");
        both.add(t + ".thresholds",
                "母星和气态巨行星以外的星球都能改造，全服共享，谁的设备都算数。发现日志的星球页和释放塔、播种站的界面里写着四道门槛：\n\n"
                        + "温度：平均 253–313 K\n"
                        + "毒性：毒性指数不超过 1\n"
                        + "大气柱：气压 ÷ 重力到地球的 1 倍，挡住宇宙线\n"
                        + "呼吸：总气压 0.5–3 atm、氧分压 16–50 kPa、二氧化碳不到 5000 ppm、没有毒\n\n"
                        + "毒性和二氧化碳按分压算，往大气里加别的气体冲不淡。到了门槛全服提示一次。",
                "Every planet except the home world and gas giants can be terraformed. Progress is shared by the whole server. The discovery log "
                        + "and the tower and seeder screens show four thresholds:\n\n"
                        + "Temperature: 253–313 K on average\n"
                        + "Toxicity: toxicity index at most 1\n"
                        + "Air column: pressure ÷ gravity at least Earth's, which stops cosmic rays\n"
                        + "Breathing: 0.5–3 atm total, 16–50 kPa oxygen, CO₂ under 5000 ppm, nothing toxic\n\n"
                        + "Toxicity and CO₂ go by partial pressure, so adding other gases does not dilute them. Each threshold is announced once.");
        both.add(t + ".release_tower.title", "大气释放塔", "Atmosphere Release Tower");
        both.add(t + ".release_tower",
                "放在露天的地表，每秒把一个气体物品放进大气，用电 1.2 kW。多少气体折一个大气压，看星球的大小和重力："
                        + "边长 13312、重力 0.4 g 的星球要 140 万个。\n\n"
                        + "大气提取器在改造中的星球上取走的气体也从大气里扣，太浓的大气可以一点点抽稀。",
                "Place it under open sky. It releases one gas item a second at 1.2 kW. On a 13312-block world at 0.4 g, 1 atm takes "
                        + "1.4 million items; bigger worlds and weaker gravity need more.\n\n"
                        + "Extractors on a terraformed planet draw from its air, so thick air can be thinned.");
        both.add(t + ".mirrors.title", "反射镜和遮阳板", "Mirrors and Shades");
        both.add(t + ".mirrors",
                "轨道反射镜放在星球上空的太空里，每面让星球暖 3 K，最多算 24 面。界面里能切成遮阳板，每面凉 3 K。"
                        + "这是祖布林和麦凯 1993 年给火星提的方案。\n\n"
                        + "温室气体也能加热：二氧化碳、甲烷和氨。同样多的分子，甲烷比二氧化碳强约 26 倍。",
                "Mirrors go in orbit above the planet. Each warms it by 3 K, up to 24; switch one to a shade to cool by 3 K. "
                        + "Zubrin and McKay proposed this for Mars in 1993.\n\n"
                        + "CO₂, methane and ammonia warm it too; per molecule, methane is about 26 times stronger than CO₂.");
        both.add(t + ".seeder.title", "生态播种站", "Ecology Seeder");
        both.add(t + ".seeder",
                "放在露天的地表，用电 800 W。上格放菌种管或改造种苗，下格放水。界面里的条件都对上了，每 10 秒播下一份，用掉一个。",
                "Place it on the surface under open sky; it draws 800 W. The top slot takes a strain tube or an engineered seedling, the bottom "
                        + "slot water. When every condition on the screen is met, it seeds one dose every 10 s and uses up one item.");
        both.add(t + ".pioneers.title", "先锋生物", "Pioneers");
        both.add(t + ".pioneers",
                "播下去的菌和种苗是先锋生物，要扛住星球上的寒冷、炎热、辐射、酸、紫外和干旱。播种站的界面一样一样列出来，缺基因的标红。\n\n"
                        + "先锋生物要有产氧光合才会造氧。没有天然水的星球要浇水：一个水用一分钟，四座有水的站就浇够了。",
                "Whatever you seed is a pioneer. It has to survive the planet's cold, heat, radiation, acid, UV and drought; the seeder's "
                        + "screen lists each one and marks missing genes in red.\n\n"
                        + "Only pioneers with oxygenic photosynthesis make oxygen. Planets without natural water need irrigation: one water lasts a "
                        + "minute, and four stations with water cover a planet.");
        both.add(t + ".biosphere.title", "生物圈", "Biosphere");
        both.add(t + ".biosphere",
                "播下的生物量按 logistic 曲线长，上限看液态水、温度和光照，永夜星球只有一成。长满时每小时换掉 0.03 个大气压的二氧化碳。\n\n"
                        + "站周围的裸岩和土慢慢长出灰绿、橙黄的地衣，生物量多了换成苔藓，最远 32 格。",
                "Seeded biomass follows a logistic curve. Its ceiling depends on liquid water, temperature and light; on a world in endless night "
                        + "it is a tenth. At full biomass it turns over 0.03 atm of CO₂ per hour.\n\n"
                        + "Bare rock and soil around the seeder grow grey-green and orange lichen, then moss as biomass rises, out to 32 blocks.");
        both.add(t + ".cleanup.title", "大气自净", "The Air Cleans Itself");
        both.add(t + ".cleanup",
                "大气会自己去掉毒气，照现实里各种气体的寿命，现实一天折游戏三分钟。\n\n"
                        + "氧分压过 1 kPa、有水有光，一氧化碳两个月左右（游戏三小时）氧化成二氧化碳。\n"
                        + "二氧化硫靠下雨洗掉，两天。\n"
                        + "氰化氢溶进水里，四个月。\n\n"
                        + "冰冻星球要暖过冰点才开始下雨。",
                "The air clears toxic gases on its own, at their real lifetimes, with one real day as three game minutes.\n\n"
                        + "With over 1 kPa of oxygen, water and light, carbon monoxide oxidizes to CO₂ in about two months (three game hours).\n"
                        + "Rain washes out sulfur dioxide in two days.\n"
                        + "Hydrogen cyanide dissolves into water in four months.\n\n"
                        + "Frozen worlds have to warm past freezing before it rains.");
        both.add(t + ".example.title", "一颗冷星球", "A Cold World");
        both.add(t + ".example",
                "照火星的冷星球：0.1 atm、0.4 g、210 K，有水冰。\n\n"
                        + "1. 播耐寒、耐辐射、带紫外防护的蓝藻\n"
                        + "2. 十座释放塔补氧、补一点甲烷保温、补氮，十到二十小时补到 0.55 atm\n"
                        + "3. 0.4 g 的星球上 0.55 atm 已经挡住辐射\n"
                        + "4. 氧分压过 1 kPa 以后，一氧化碳几个小时内氧化掉\n"
                        + "5. 反射镜把温度补到 260 K 上下\n\n"
                        + "之后不戴头盔也能呼吸。",
                "A Mars-like cold world: 0.1 atm, 0.4 g, 210 K, with water ice.\n\n"
                        + "1. Seed cyanobacteria with cold, radiation and UV protection\n"
                        + "2. Ten release towers add oxygen, a little methane for warmth, and nitrogen, reaching 0.55 atm in 10–20 hours\n"
                        + "3. At 0.4 g, 0.55 atm already stops the radiation\n"
                        + "4. Once oxygen passes 1 kPa, the carbon monoxide oxidizes within a few hours\n"
                        + "5. Mirrors bring the temperature up to about 260 K\n\n"
                        + "After that you can breathe without a helmet.");
    }

    static void shared(Both both) {
        for (Gas gas : Gas.values()) {
            // 氢和氧以前就有名字
            if (gas != Gas.HYDROGEN && gas != Gas.OXYGEN) {
                both.add("item.universe." + gas.item(), gas.nameZh(), gas.nameEn());
            }
        }
        both.add("item.universe.sodium_hydroxide", "氢氧化钠", "Sodium Hydroxide");
        both.add("item.universe.nitric_acid", "硝酸", "Nitric Acid");
        both.add("item.universe.ammonium_nitrate", "硝酸铵化肥", "Ammonium Nitrate Fertilizer");
        both.add("item.universe.titanium_tetrachloride", "四氯化钛", "Titanium Tetrachloride");
        both.add("item.universe.vanadium_slag", "钒渣", "Vanadium Slag");
        both.add("item.universe.copper_leach_solution", "含铜浸出液", "Copper Leach Solution");
        both.add("item.universe.gold_leach_solution", "含金浸出液", "Gold Leach Solution");
        both.add("item.universe.nickel_ingot", "镍锭", "Nickel Ingot");
        both.add("item.universe.methalox_fuel", "甲烷液氧燃料", "Methalox Fuel");
        both.add("item.universe.iron_catalyst", "铁催化剂", "Iron Catalyst");
        both.add("item.universe.vanadium_catalyst", "五氧化二钒催化剂", "Vanadium Pentoxide Catalyst");
        both.add("item.universe.nickel_catalyst", "镍催化剂", "Nickel Catalyst");
        both.add("item.universe.agar", "琼脂", "Agar");
        both.add("item.universe.culture_medium", "培养基", "Culture Medium");
        both.add("item.universe.sequencing_chip", "测序芯片", "Sequencing Flow Cell");
        both.add("item.universe.editing_kit", "基因编辑试剂", "Gene Editing Kit");
        both.add("item.universe.algal_biomass", "藻类生物质", "Algal Biomass");
        both.add("item.universe.strain_tube", "菌种管", "Culture Tube");
        both.add("item.universe.engineered_seedling", "改造种苗", "Engineered Seedling");
        tech(both, "atmospheric_chemistry", "大气化学", "Atmospheric Chemistry");
        tech(both, "electrochemistry", "电化学", "Electrochemistry");
        tech(both, "catalysis", "催化与化学平衡", "Catalysis and Equilibrium");
        tech(both, "synthetic_fuels", "合成燃料", "Synthetic Fuels");
        tech(both, "microbiology", "微生物学", "Microbiology");
        tech(both, "genomics", "基因组学", "Genomics");
        tech(both, "synthetic_biology", "合成生物学", "Synthetic Biology");
        tech(both, "bioengineering", "生物工程", "Bioengineering");
        tech(both, "terraforming", "星球改造", "Terraforming");
    }
}
