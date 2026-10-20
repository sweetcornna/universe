package io.github.sweetcornna.universe.datagen;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;
import io.github.sweetcornna.universe.core.poi.PoiType;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/** 语言文件。中英文放在一起写，免得漏翻（项目约定同时维护 zh_cn 和 en_us）。 */
abstract class UniverseLanguageProvider extends LanguageProvider {
    private final boolean chinese;

    private UniverseLanguageProvider(PackOutput output, String locale, boolean chinese) {
        super(output, Universe.MOD_ID, locale);
        this.chinese = chinese;
    }

    static final class Chinese extends UniverseLanguageProvider {
        Chinese(PackOutput output) {
            super(output, "zh_cn", true);
        }
    }

    static final class English extends UniverseLanguageProvider {
        English(PackOutput output) {
            super(output, "en_us", false);
        }
    }

    private void both(String key, String zh, String en) {
        this.add(key, this.chinese ? zh : en);
    }

    @Override
    protected void addTranslations() {
        both("itemGroup.universe", "寰宇", "Universe");
        both("universe.bed.no_sleep_on_planet", "不能在这颗星球上睡觉", "You can't sleep on this planet");
        for (BiomeKind kind : BiomeKind.values()) {
            both("biome.universe." + kind.serializedName(), kind.nameZh(), kind.nameEn());
        }

        block(UniverseBlocks.PLANET_ROCK.get(), "星岩", "Planet Rock");
        block(UniverseBlocks.PLANET_COBBLESTONE.get(), "星岩碎石", "Planet Cobblestone");
        block(UniverseBlocks.PLANET_SOIL.get(), "星壤", "Planet Soil");
        block(UniverseBlocks.PLANET_TURF.get(), "星原草皮", "Planet Turf");
        block(UniverseBlocks.PLANET_REGOLITH.get(), "风化层", "Regolith");
        block(UniverseBlocks.PLANET_SAND.get(), "星沙", "Planet Sand");
        block(UniverseBlocks.PLANET_SNOW.get(), "星雪", "Planet Snow");
        block(UniverseBlocks.PLANET_ICE.get(), "星冰", "Planet Ice");
        block(UniverseBlocks.PLANET_ASH.get(), "灰烬", "Ash");
        block(UniverseBlocks.PLANET_CRYSTAL.get(), "星晶", "Planet Crystal");
        block(UniverseBlocks.PLANET_STRATA.get(), "岩层", "Planet Strata");
        // 植物方块按形态命名，具体物种名以后由分析目镜显示
        UniverseBlocks.PLANTS.forEach((form, plant) -> block(plant.get(), "异星" + form.nameZh(), "Alien " + form.nameEn()));
        block(UniverseBlocks.PLANET_LOG.get(), "异星原木", "Alien Log");
        block(UniverseBlocks.PLANET_LEAVES.get(), "异星树叶", "Alien Leaves");
        block(UniverseBlocks.PLANET_CAP.get(), "肉质块", "Fleshy Block");
        block(UniverseBlocks.PLANET_BLOOM.get(), "异星花果", "Alien Bloom");

        both("entity.universe.alien_creature", "异星生物", "Alien Creature");
        both("entity.universe.alien_flyer", "异星飞兽", "Alien Flyer");
        // 物种名：实体把中英文两个名字都作为参数传进来，各语言挑自己的
        both("entity.universe.species", "%1$s", "%2$s");
        item(UniverseItems.ALIEN_MEAT.get(), "异兽生肉", "Raw Alien Meat");
        item(UniverseItems.COOKED_ALIEN_MEAT.get(), "熟异兽肉", "Cooked Alien Meat");
        item(UniverseItems.ALIEN_HIDE.get(), "异兽皮", "Alien Hide");
        // 喂养（ADR-0017）
        item(UniverseItems.HERBIVORE_FEED.get(), "草食饲料", "Herbivore Feed");
        item(UniverseItems.CARNIVORE_FEED.get(), "肉食饲料", "Carnivore Feed");
        item(UniverseItems.ALIEN_EGG.get(), "异星蛋", "Alien Egg");
        item(UniverseItems.COOKED_ALIEN_EGG.get(), "烤异星蛋", "Roasted Alien Egg");
        item(UniverseItems.ALIEN_FLUFF.get(), "异星绒毛", "Alien Fluff");
        item(UniverseItems.BIO_GEL.get(), "生物胶质", "Bio Gel");
        both("discovery.universe.diet.herbivore", "草食", "Herbivore");
        both("discovery.universe.diet.carnivore", "肉食", "Carnivore");
        both("message.universe.creature.wrong_food", "%s不吃这个", "%s won't eat that");
        both("message.universe.creature.not_hungry", "%1$s还不饿（%2$s秒）", "%1$s isn't hungry (%2$ss)");
        both("message.universe.creature.tamed", "%s已被驯服", "%s has been tamed");
        both("message.universe.creature.staying", "%s：待命", "%s: Staying");
        both("message.universe.creature.following", "%s：跟随", "%s: Following");
        both("tooltip.universe.herbivore_feed.summary", "温顺、易受惊的动物吃\n喂动物有机会驯服，5 分钟产出一次\n驯服后空手潜行右键：待命 / 跟随\n驯服后空手右键：骑乘（体型较大的走兽）",
                "Eaten by docile and skittish creatures\nFeeding can tame them; they produce once every 5 minutes\nTamed, sneak + right-click empty-handed: stay / follow\nTamed, right-click empty-handed: ride (larger walkers)");
        both("tooltip.universe.carnivore_feed.summary", "有领地意识的动物和捕食者吃\n喂动物有机会驯服，5 分钟产出一次\n驯服后空手潜行右键：待命 / 跟随\n驯服后空手右键：骑乘（体型较大的走兽）",
                "Eaten by territorial creatures and predators\nFeeding can tame them; they produce once every 5 minutes\nTamed, sneak + right-click empty-handed: stay / follow\nTamed, right-click empty-handed: ride (larger walkers)");
        both("tooltip.universe.hold_shift", "按住 %s 查看说明", "Hold %s for details");
        item(UniverseItems.ALIEN_CREATURE_SPAWN_EGG.get(), "异星生物刷怪蛋", "Alien Creature Spawn Egg");
        item(UniverseItems.ALIEN_FLYER_SPAWN_EGG.get(), "异星飞兽刷怪蛋", "Alien Flyer Spawn Egg");
        this.ships();
        this.ores();
        this.suit();
        this.multitool();
        this.factory();
        this.trade();
        this.teleport();
        this.missions();
        this.endgame();
    }

    /** 终局（ADR-0018）：反物质、宇宙矩阵、两种发电设备和它们的科技。 */
    private void endgame() {
        item(UniverseItems.ANTIMATTER.get(), "反物质", "Antimatter");
        item(UniverseItems.ANNIHILATION_CONSTRAINT_SPHERE.get(), "湮灭约束球", "Annihilation Constraint Sphere");
        item(UniverseItems.ANTIMATTER_FUEL_ROD.get(), "反物质燃料棒", "Antimatter Fuel Rod");
        item(UniverseItems.COMPOSITE_MATRIX.get(), "复合矩阵", "Composite Matrix");
        item(UniverseItems.UNIVERSE_MATRIX.get(), "宇宙矩阵", "Universe Matrix");
        block(UniverseBlocks.MINI_FUSION_POWER_PLANT.get(), "微型聚变发电站", "Mini Fusion Power Plant");
        block(UniverseBlocks.ARTIFICIAL_STAR.get(), "人造恒星", "Artificial Star");
        both("tooltip.universe.mini_fusion_power_plant.summary", "最大 15 kW，烧氘核燃料棒\n一根满负荷烧 6 分钟，只按电网需要烧",
                "Up to 15 kW, burns deuteron fuel rods\nOne rod lasts 6 minutes at full load; burns only what the grid needs");
        both("tooltip.universe.artificial_star.summary", "最大 72 kW，烧反物质燃料棒\n一根满负荷烧 15 分钟，只按电网需要烧",
                "Up to 72 kW, burns antimatter fuel rods\nOne rod lasts 15 minutes at full load; burns only what the grid needs");
        both("tooltip.universe.composite_matrix.summary", "做宇宙矩阵的半成品\n蓝、红、黄矩阵各一个", "Intermediate for universe matrices\nOne each of blue, red and yellow matrix");
        both("gui.universe.fuel_generator.fuel", "燃料棒", "Fuel rod");
        tech("mini_fusion_power", "微型聚变发电", "Mini Fusion Power Generation");
        tech("dirac_inversion", "狄拉克逆变机制", "Dirac Inversion Mechanism");
        tech("controlled_annihilation", "可控湮灭反应", "Controlled Annihilation Reaction");
        tech("artificial_star", "人造恒星", "Artificial Star");
        tech("universe_matrix", "宇宙矩阵", "Universe Matrix");
        tech("mission_complete", "任务完成", "Mission Complete");
        both("tech.universe.mission_complete.effect", "通关。研究完以后可以接着玩", "Finishes the game. You can keep playing afterwards");
        // 升级科技
        both("tech.universe.leveled", "%s %s", "%s %s");
        both("tech.universe.vein_utilization", "矿脉利用", "Vein Utilization");
        both("tech.universe.research_speed", "研究速度", "Research Speed");
        both("tech.universe.carrier_engine", "物流载具引擎", "Logistics Carrier Engine");
        both("tech.universe.carrier_capacity", "物流载具容量", "Logistics Carrier Capacity");
        both("tech.universe.ray_efficiency", "射线传输效率", "Ray Transmission Efficiency");
        both("tech.universe.sail_lifetime", "太阳帆寿命", "Solar Sail Life");
        both("upgrade.universe.vein_utilization.effect", "采矿 +%s%%，矿耗 −%s%%", "Mining +%s%%, vein use −%s%%");
        both("upgrade.universe.research_speed.effect", "研究速度 +%s%%", "Research speed +%s%%");
        both("upgrade.universe.carrier_engine.effect", "载具速度 +%s%%", "Carrier speed +%s%%");
        both("upgrade.universe.carrier_capacity.effect", "无人机装 %s，运输船装 %s", "Drones carry %s, vessels %s");
        both("upgrade.universe.ray_efficiency.effect", "射线损耗 −%s%%", "Ray loss −%s%%");
        both("upgrade.universe.sail_lifetime.effect", "太阳帆寿命 %s 分钟", "Sail life %s min");
        both("gui.universe.tech_tree.upgrades", "升级", "Upgrades");
        both("gui.universe.tech_tree.card.maxed", "已满级", "Maxed");
        both("gui.universe.tech_tree.maxed", "已满级", "Max level reached");
        both("gui.universe.tech_tree.effect", "效果", "Effect");
        both("gui.universe.tech_tree.none", "无", "None");
    }

    /** 空间站任务板（ADR-0017）。 */
    private void missions() {
        block(UniverseBlocks.MISSION_BOARD.get(), "任务板", "Mission Board");
        both("gui.universe.mission.kind.delivery", "送货", "Delivery");
        both("gui.universe.mission.kind.bounty", "清剿", "Bounty");
        both("gui.universe.mission.kind.mining", "采矿", "Mining");
        both("gui.universe.mission.kind.survey", "测绘", "Survey");
        both("gui.universe.mission.goal.delivery", "送来 %2$s 个%1$s", "Deliver %2$s × %1$s");
        both("gui.universe.mission.goal.bounty", "击落 %s 架海盗战机", "Destroy %s pirate fighters");
        both("gui.universe.mission.goal.mining", "打碎 %s 颗小行星", "Break up %s asteroids");
        both("gui.universe.mission.goal.survey", "在%1$s星系分析 %2$s 种动植物", "Analyze %2$s species in the %1$s system");
        both("gui.universe.mission.reward", "报酬 %s 信用点", "Reward: %s credits");
        both("gui.universe.mission.bonus.navigation_data", "，外加 %s 份导航数据", " + %s Navigation Data");
        both("gui.universe.mission.bonus.module", "，外加一个技术模块", " + a tech module");
        both("gui.universe.mission.accept", "接受", "Accept");
        both("gui.universe.mission.in_progress", "进行中", "Active");
        both("gui.universe.mission.done", "已完成", "Done");
        both("gui.universe.mission.busy_hint", "先交付或放弃手上的任务", "Hand in or abandon your current mission first");
        both("gui.universe.mission.active", "手上的任务", "Current mission");
        both("gui.universe.mission.none", "没有任务", "None");
        both("gui.universe.mission.progress", "进度 %s / %s", "Progress: %s / %s");
        both("gui.universe.mission.from", "已完成，回%s交付", "Done. Hand in at %s");
        both("gui.universe.mission.hand_in", "交付", "Hand In");
        both("gui.universe.mission.abandon", "放弃", "Abandon");
        both("gui.universe.mission.refresh", "每个游戏日换一批任务", "New missions every in-game day");
        both("hud.universe.mission.ready", "回%s交付", "Return to %s");
        both("message.universe.mission.busy", "手上已经有任务了", "You already have a mission");
        both("message.universe.mission.done_today", "今天已经做过这个任务", "You've already done this one today");
        both("message.universe.mission.accepted", "已接受任务", "Mission accepted");
        both("message.universe.mission.abandoned", "已放弃任务", "Mission abandoned");
        both("message.universe.mission.wrong_station", "这个任务要回%s交付", "Hand this mission in at %s");
        both("message.universe.mission.missing", "还差 %s 个%s", "%s more %s needed");
        both("message.universe.mission.unfinished", "还没完成（%s/%s）", "Not finished yet (%s/%s)");
        both("message.universe.mission.ready", "任务目标完成，回%s交付", "Objective complete. Return to %s");
        both("message.universe.mission.complete", "任务完成，获得 %s 信用点", "Mission complete: %s credits");
        both("message.universe.mission.bonus", "另外获得：%2$s ×%1$s", "Bonus: %2$s ×%1$s");
    }

    /** 基地信标和传送（ADR-0017）。 */
    private void teleport() {
        block(UniverseBlocks.BASE_BEACON.get(), "基地信标", "Base Beacon");
        block(UniverseBlocks.TELEPORTER.get(), "传送器", "Teleporter");
        block(UniverseBlocks.STATION_TELEPORTER.get(), "空间站传送器", "Station Teleporter");
        both("tooltip.universe.base_beacon.summary", "放下后登记为你的基地\n右键：给基地改名\n传送器能把你送到这里",
                "Registers a base for you when placed\nRight-click: rename the base\nTeleporters can send you here");
        both("tooltip.universe.teleporter.summary", "接入电网后充电，充满一次能传送一次（约 10 秒）\n右键：选目的地\n能去你的基地和进过的空间站，跟着你的宠物一起走",
                "Charges from the power grid. One full charge per jump (about 10 s)\nRight-click: pick a destination\nGoes to your bases and any station you've been inside. Following pets come along");
        tech("teleport_network", "传送网络", "Teleport Network");
        both("gui.universe.base.default_name", "%s · 基地 %s", "%s Base %s");
        both("gui.universe.base.rename_hint", "基地名称，留空用默认名", "Base name. Leave blank for the default");
        both("gui.universe.teleporter.energy", "电量 %s / %s kJ", "Charge %s / %s kJ");
        both("gui.universe.teleporter.free", "由空间站供电", "Powered by the station");
        both("gui.universe.teleporter.here", "你在这里", "You are here");
        both("gui.universe.teleporter.system", "%s星系", "%s system");
        both("gui.universe.teleporter.empty", "还没有能去的地方。放一个基地信标，或者先进一次空间站",
                "Nowhere to go yet. Place a Base Beacon or visit a space station first");
        both("message.universe.base.registered", "已登记基地：%s", "Base registered: %s");
        both("message.universe.base.owned", "这是%s的基地", "This base belongs to %s");
        both("message.universe.teleport.arrived", "已传送到 %s", "Teleported to %s");
        both("message.universe.teleport.too_far", "离传送器太远了", "Too far from the teleporter");
        both("message.universe.teleport.not_charged", "传送器还没充满电", "The teleporter isn't charged yet");
        both("message.universe.teleport.unreachable", "去不了那里", "Can't reach that destination");
        both("message.universe.teleport.beacon_missing", "%s的基地信标不见了，已从列表里删掉", "The beacon at %s is gone. Removed from the list");
        both("message.universe.teleport.no_room", "目的地没有落脚的地方", "No room to stand at the destination");
    }

    /** 空间站和交易（ADR-0017）。 */
    private void trade() {
        block(UniverseBlocks.TRADE_TERMINAL.get(), "交易终端", "Trade Terminal");
        block(UniverseBlocks.SHIP_REPAIR.get(), "飞船维修台", "Ship Repair Console");
        both("message.universe.repair.no_ship", "机库里没有飞船", "There is no ship in the hangar");
        both("message.universe.repair.intact", "飞船无需维修", "The ship doesn't need repairs");
        both("message.universe.repair.quote", "修复 %s 点船体需要 %s 信用点，再次点击确认", "Repairing %s hull costs %s credits. Click again to confirm");
        both("message.universe.repair.poor", "信用点不足（需要 %s，现有 %s）", "Not enough credits (%s needed, %s held)");
        both("message.universe.repair.done", "已修复 %s 点船体，花费 %s 信用点，护盾已充满", "Repaired %s hull for %s credits. Shields recharged");
        block(UniverseBlocks.STATION_HULL.get(), "空间站外壳", "Station Hull");
        block(UniverseBlocks.STATION_FLOOR.get(), "空间站地板", "Station Floor");
        block(UniverseBlocks.STATION_GLASS.get(), "空间站玻璃", "Station Glass");
        block(UniverseBlocks.STATION_LIGHT.get(), "空间站灯板", "Station Light Panel");
        block(UniverseBlocks.STATION_PAD.get(), "停机坪标线", "Landing Pad Marking");
        block(UniverseBlocks.STATION_TRIM.get(), "空间站灯带", "Station Light Strip");
        both("hud.universe.station", "空间站 · %s 格", "Station · %s m");
        both("gui.universe.star_map.economy", "空间站：%s · %s", "Station: %s · %s");
        both("hud.universe.station.credits", "%s 信用点", "%s credits");
        both("message.universe.station.welcome", "欢迎来到%s空间站", "Welcome to %s Station");
        both("gui.universe.trade.title", "交易终端", "Trade Terminal");
        both("gui.universe.trade.station", "%s空间站", "%s Station");
        both("gui.universe.trade.credits", "%s 信用点", "%s credits");
        both("gui.universe.trade.economy", "%s · %s", "%s · %s");
        both("gui.universe.trade.economy.mining", "采矿经济", "Mining");
        both("gui.universe.trade.economy.manufacturing", "制造经济", "Manufacturing");
        both("gui.universe.trade.economy.technology", "科技经济", "Technology");
        both("gui.universe.trade.economy.trading", "贸易经济", "Trading");
        both("gui.universe.trade.economy.scientific", "科研经济", "Scientific");
        both("gui.universe.trade.economy.power", "能源经济", "Power Generation");
        both("gui.universe.trade.economy.materials", "先进材料经济", "Advanced Materials");
        both("gui.universe.trade.wealth.poor", "贫穷", "Poor");
        both("gui.universe.trade.wealth.average", "中等", "Average");
        both("gui.universe.trade.wealth.wealthy", "富裕", "Wealthy");
        both("gui.universe.trade.category.raw", "原料", "Raw");
        both("gui.universe.trade.category.refined", "冶炼品", "Refined");
        both("gui.universe.trade.category.component", "零件", "Components");
        both("gui.universe.trade.category.advanced", "高级品", "Advanced");
        both("gui.universe.trade.category.energy", "能源", "Energy");
        both("gui.universe.trade.category.bio", "生物产物", "Biological");
        both("gui.universe.trade.category.tech", "科技", "Technology");
        both("gui.universe.trade.category.demanded", "%s · 这里缺", "%s · in demand");
        both("gui.universe.trade.category.supplied", "%s · 本地产", "%s · local");
        both("gui.universe.trade.separator", "、", ", ");
        both("gui.universe.trade.demands", "缺：%s", "Wants: %s");
        both("gui.universe.trade.supplies", "产：%s", "Makes: %s");
        both("gui.universe.trade.sell", "卖出（%s）", "Sell (%s)");
        both("gui.universe.trade.buy", "购买（%s）", "Buy (%s)");
        both("gui.universe.trade.item", "物品", "Item");
        both("gui.universe.trade.held", "持有", "Held");
        both("gui.universe.trade.stock", "存货", "Stock");
        both("gui.universe.trade.unit", "单价", "Each");
        both("gui.universe.trade.all_total", "全部卖出", "Sell all");
        both("gui.universe.trade.stack_total", "买一组", "Buy stack");
        both("gui.universe.trade.empty_sell", "背包里没有可出售的物品", "Nothing in your inventory sells here");
        both("gui.universe.trade.empty_buy", "暂无商品", "Nothing for sale");
        both("gui.universe.trade.hint_sell", "单击卖一件 · Shift 卖一组 · Ctrl 全部卖掉", "Click: sell one · Shift: a stack · Ctrl: all of it");
        both("gui.universe.trade.hint_buy", "单击买一件 · Shift 买一组（钱不够就少买几件）", "Click: buy one · Shift: a stack (as many as you can afford)");
        both("gui.universe.trade.quote_sell", "一件 %s · 一组（%s）%s · 全部（%s）%s", "One %s · stack (%s) %s · all (%s) %s");
        both("gui.universe.trade.quote_buy", "一件 %s · 一组（%s）%s", "One %s · stack (%s) %s");
        both("gui.universe.trade.sold_out", "已售罄", "Sold out");
        both("message.universe.trade.no_signal", "没有空间站信号", "No station signal");
        both("message.universe.trade.sold", "卖出 %s 件%s，获得 %s 信用点", "Sold %s × %s for %s credits");
        both("message.universe.trade.bought", "购买 %s 件%s，花费 %s 信用点", "Bought %s × %s for %s credits");
        both("message.universe.trade.nothing_to_sell", "背包里没有这件物品", "You don't have that item");
        both("message.universe.trade.too_cheap", "单件价值不足 1 信用点", "Worth less than 1 credit each");
        both("message.universe.trade.out_of_stock", "已售罄", "Sold out");
        both("message.universe.trade.not_enough", "信用点不足（单价 %s）", "Not enough credits (%s each)");
    }

    private void ships() {
        block(UniverseBlocks.SHIP_HELM.get(), "驾驶台", "Ship Helm");
        block(UniverseBlocks.SHIP_THRUSTER.get(), "推进器", "Thruster");
        both("tooltip.universe.ship_thruster.summary", "推力 12：1 g 重力下托起 12 吨\n推重比大于 1 才能起飞",
                "Thrust 12: lifts 12 t at 1 g\nNeeds thrust-to-weight above 1 to take off");
        block(UniverseBlocks.FUEL_TANK.get(), "燃料舱", "Fuel Tank");
        block(UniverseBlocks.IMPULSE_ENGINE.get(), "脉冲引擎", "Impulse Engine");
        block(UniverseBlocks.WARP_DRIVE.get(), "跃迁引擎", "Warp Drive");
        both("tooltip.universe.warp_drive.summary", "一台射程 10 光年，每多装一台 +5\n最多算 4 台\n每次跃迁消耗 1 个空间翘曲器",
                "One drive: 10 ly range, +5 per extra drive\nCounts up to 4 drives\nEach warp uses 1 space warper");
        item(UniverseItems.SPACE_WARPER.get(), "空间翘曲器", "Space Warper");
        item(UniverseItems.SURVEY_DATA.get(), "勘探数据", "Survey Data");
        block(UniverseBlocks.SHIP_SEAT.get(), "乘客座椅", "Passenger Seat");
        block(UniverseBlocks.PHOTON_CANNON.get(), "光子炮", "Photon Cannon");
        block(UniverseBlocks.MINING_BEAM.get(), "采矿光束", "Mining Beam");
        block(UniverseBlocks.SHIELD_GENERATOR.get(), "护盾发生器", "Shield Generator");
        block(UniverseBlocks.SHIP_THRUSTER_MK2.get(), "Mk.II 推进器", "Thruster Mk.II");
        both("tooltip.universe.ship_thruster_mk2.summary", "推力 20：1 g 重力下托起 20 吨\n推重比大于 1 才能起飞",
                "Thrust 20: lifts 20 t at 1 g\nNeeds thrust-to-weight above 1 to take off");
        block(UniverseBlocks.FUEL_TANK_MK2.get(), "Mk.II 燃料舱", "Fuel Tank Mk.II");
        block(UniverseBlocks.IMPULSE_ENGINE_MK2.get(), "Mk.II 脉冲引擎", "Impulse Engine Mk.II");
        block(UniverseBlocks.WARP_DRIVE_MK2.get(), "Mk.II 跃迁引擎", "Warp Drive Mk.II");
        both("tooltip.universe.warp_drive_mk2.summary", "每台 +10 光年射程（普通跃迁引擎 +5）\n和普通跃迁引擎合计最多算 4 台",
                "+10 ly range per drive (+5 for a standard one)\nCounts up to 4 drives, standard ones included");
        block(UniverseBlocks.ALLOY_HULL_PLATING.get(), "合金船体板", "Alloy Hull Plating");
        String[][] colors = {
                {"white", "白色"}, {"orange", "橙色"}, {"magenta", "品红色"}, {"light_blue", "淡蓝色"},
                {"yellow", "黄色"}, {"lime", "黄绿色"}, {"pink", "粉红色"}, {"gray", "灰色"},
                {"light_gray", "淡灰色"}, {"cyan", "青色"}, {"purple", "紫色"}, {"blue", "蓝色"},
                {"brown", "棕色"}, {"green", "绿色"}, {"red", "红色"}, {"black", "黑色"}};
        for (String[] color : colors) {
            String en = java.util.Arrays.stream(color[0].split("_"))
                    .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                    .collect(java.util.stream.Collectors.joining(" "));
            both("block.universe.hull_plating_" + color[0], color[1] + "船体板", en + " Hull Plating");
        }
        item(UniverseItems.LAUNCH_FUEL.get(), "发射燃料", "Launch Fuel");
        both("entity.universe.ship", "飞船", "Ship");
        both("entity.universe.plasma_bolt", "等离子弹", "Plasma Bolt");
        both("entity.universe.sentinel", "守卫机", "Sentinel");
        both("entity.universe.heavy_sentinel", "重型守卫机", "Heavy Sentinel");
        both("entity.universe.pirate_fighter", "海盗战机", "Pirate Fighter");
        // 探索车（ADR-0017）
        both("entity.universe.exocraft", "探索车", "Exocraft");
        item(UniverseItems.EXOCRAFT.get(), "探索车", "Exocraft");
        both("tooltip.universe.exocraft.fuel", "燃料 %s / %s", "Fuel %s / %s");
        both("tooltip.universe.exocraft.summary", "对着地面使用：放置\n右键：驾驶\n潜行右键：打开货箱\n手持燃料右键：加燃料\n驾驶时按攻击键：采矿激光\n驾驶时手持多功能工具右键：扫描\n用镐敲三下：收起，保留燃料和货物",
                "Use on the ground: deploy\nRight-click: drive\nSneak + right-click: open cargo\nRight-click with fuel: refuel\nAttack while driving: mining laser\nRight-click with a multi-tool while driving: scan\nHit three times with a pickaxe: pack up, keeping fuel and cargo");

        both("message.universe.exocraft.no_space", "不能在太空中放置", "Can't be deployed in space");
        both("message.universe.exocraft.no_room", "空间不足", "Not enough room");
        both("message.universe.exocraft.tank_full", "油箱装不下（%s / %s）", "Not enough room in the tank (%s / %s)");
        both("message.universe.exocraft.fuel", "燃料：%s / %s", "Fuel: %s / %s");
        both("message.universe.exocraft.need_pickaxe", "需要用镐收起", "Use a pickaxe to pack it up");
        both("message.universe.exocraft.cargo_full", "货箱已满", "Cargo full");
        both("hud.universe.exocraft.speed", "速度 %s 格/秒", "Speed %s blocks/s");
        both("hud.universe.exocraft.cargo", "货箱 %s / %s 格", "Cargo %s / %s slots");
        both("hud.universe.exocraft.fuel", "燃料 %s / %s", "Fuel %s / %s");
        both("hud.universe.exocraft.laser", "采矿激光", "Mining laser");
        both("hud.universe.exocraft.laser_overheated", "采矿激光（过热）", "Mining laser (overheated)");
        both("hud.universe.exocraft.keys", "%s 采矿激光  %s 下车", "%s Mining laser  %s Dismount");
        both("hud.universe.exocraft.no_fuel", "没有燃料", "Out of fuel");
        both("hud.universe.exocraft.target", "%s · %s 格", "%s · %s blocks");
        item(UniverseItems.PIRATE_SCRAP.get(), "海盗残骸", "Pirate Scrap");
        item(UniverseItems.SENTINEL_SCRAP.get(), "守卫机残骸", "Sentinel Scrap");
        item(UniverseItems.SENTINEL_SPAWN_EGG.get(), "守卫机刷怪蛋", "Sentinel Spawn Egg");
        item(UniverseItems.HEAVY_SENTINEL_SPAWN_EGG.get(), "重型守卫机刷怪蛋", "Heavy Sentinel Spawn Egg");
        both("message.universe.sentinel.wanted", "通缉等级 %s", "Wanted level %s");
        both("message.universe.sentinel.heavy", "通缉等级 %s：重型守卫机正在赶来", "Wanted level %s: heavy sentinel inbound");
        both("message.universe.sentinel.escaped", "已摆脱守卫机", "Escaped the sentinels");
        both("message.universe.sentinel.left_planet", "已离开星球，守卫机停止追捕", "Left the planet. The sentinels gave up");
        both("hud.universe.sentinel.wanted", "守卫机通缉", "SENTINELS");
        both("hud.universe.sentinel.seen", "已被守卫机发现", "Spotted by sentinels");
        both("hud.universe.sentinel.escaping", "躲藏中 %s 秒", "Hiding %ss");
        both("hud.universe.sentinel.alert", "警戒", "Alert");
        both("gui.universe.star_map.sentinels", "守卫：%s", "Sentinels: %s");
        both("universe.bed.no_sleep_in_space", "太空里不能睡觉", "You can't sleep in space");

        both("gui.universe.tech_tree", "科技树", "Tech Tree");
        both("gui.universe.tech_tree.hint", "拖动平移，滚轮滚动，%s 关闭", "Drag to pan, scroll to move, %s to close");
        both("gui.universe.tech_tree.idle", "没有在研究", "Not researching");
        both("gui.universe.tech_tree.current", "正在研究：%s（%s / %s 点）", "Researching: %s (%s / %s points)");
        both("gui.universe.tech_tree.card.done", "已完成", "Done");
        both("gui.universe.tech_tree.card.hand_in", "上交物品", "Hand in items");
        both("gui.universe.tech_tree.card.points", "%s / %s 点", "%s / %s points");
        both("gui.universe.tech_tree.done", "已完成", "Researched");
        both("gui.universe.tech_tree.researching", "研究中：给矩阵研究站供上矩阵", "Researching: keep the matrix labs supplied");
        both("gui.universe.tech_tree.available_hand_in", "可以完成：上交下面的物品", "Available: hand in the items below");
        both("gui.universe.tech_tree.available_lab", "可以研究：选为当前研究，再给矩阵研究站供上矩阵", "Available: select it, then supply matrix labs with matrices");
        both("gui.universe.tech_tree.locked", "需要先完成：%s", "Requires: %s");
        both("gui.universe.tech_tree.separator", "、", ", ");
        both("gui.universe.tech_tree.cost", "需要", "Cost");
        both("gui.universe.tech_tree.per_point", "每点各 1 个，共 %s 点", "1 of each per point, %s points");
        both("gui.universe.tech_tree.unlocks", "解锁", "Unlocks");
        both("gui.universe.tech_tree.points", "%s / %s 点", "%s / %s points");
        both("gui.universe.tech_tree.hand_in", "上交", "Hand In");
        both("gui.universe.tech_tree.missing", "物品不够", "Missing Items");
        both("gui.universe.tech_tree.start", "开始研究", "Research");
        both("gui.universe.tech_tree.stop", "停止研究", "Stop");
        both("gui.universe.tech_tree.locked_button", "未解锁", "Locked");
        both("gui.universe.tech_tree.click", "点击查看详情", "Click for details");
        both("gui.universe.matrix_lab.idle", "未在研究（按 %s 打开科技树）", "Not researching (press %s for the tech tree)");
        both("gui.universe.matrix_lab.running", "研究中", "Researching");
        both("gui.universe.matrix_lab.no_matrix", "缺少需要的矩阵", "Missing matrices");
        both("gui.universe.matrix_lab.no_power", "电力不足", "Not enough power");
        both("gui.universe.matrix_lab.waiting", "等待中", "Waiting");
        both("gui.universe.matrix_lab.tech_tree", "科技树", "Tech Tree");
        both("key.universe.tech_tree", "打开科技树", "Open Tech Tree");
        both("key.category.universe.universe", "寰宇", "Universe");
        both("key.universe.star_map", "打开星图", "Open Star Map");
        both("key.universe.analyze", "分析目镜（手持多功能工具）", "Analysis Visor (hold the multitool)");
        both("key.universe.discovery_log", "打开发现日志", "Open Discovery Log");
        both("key.universe.tech", "技术配置（模块和货舱）", "Tech Configuration (modules and cargo)");
        both("key.universe.tool_mode", "多功能工具：切换采矿/战斗模式", "Multitool: Toggle Mining/Combat Mode");
        both("key.universe.stats", "生产统计", "Production Statistics");
        // 生产统计面板（ADR-0015）
        both("gui.universe.stats.title", "生产统计", "Production Statistics");
        both("gui.universe.stats.all", "全部星球", "All planets");
        both("gui.universe.stats.space", "%s 太空", "%s space");
        both("gui.universe.stats.range.0", "1 分钟", "1 min");
        both("gui.universe.stats.range.1", "10 分钟", "10 min");
        both("gui.universe.stats.range.2", "1 小时", "1 hour");
        both("gui.universe.stats.generation", "发电能力 %s", "Generation %s");
        both("gui.universe.stats.demand", "用电需求 %s", "Demand %s");
        both("gui.universe.stats.charging", "给蓄电器和物流站充电 %s", "Charging storage %s");
        both("gui.universe.stats.satisfaction", "满足率 %s%%", "Satisfaction %s%%");
        both("gui.universe.stats.no_power", "这里没有电网", "No power grid here");
        both("gui.universe.stats.item", "物品", "Item");
        both("gui.universe.stats.produced", "产出/分", "Made/min");
        both("gui.universe.stats.consumed", "消耗/分", "Used/min");
        both("gui.universe.stats.net", "净值/分", "Net/min");
        both("gui.universe.stats.empty", "这段时间没有生产记录", "Nothing was produced in this period");
        both("gui.universe.stats.empty_hint", "配方设备、采矿机、抽水站、萃取站、分馏塔、轨道采集器开工后，以及火力发电厂烧的燃料会记在这里；物流站搬运的不算",
                "Machines, miners, pumps, extractors, fractionators, orbital collectors and fuel burned in thermal plants show up here; logistics transfers don't count");
        both("gui.universe.stats.waiting", "正在获取数据……", "Loading…");
        both("gui.universe.star_map", "星图", "Star Map");
        both("gui.universe.star_map.unknown", "还没收到星团的数据", "Galaxy data not received yet");
        both("gui.universe.star_map.hint", "左键拖动旋转 · 右键拖动平移 · 滚轮缩放 · 双击或 F 居中 · H 回到当前恒星系 · %s 关闭",
                "Drag to rotate · Right-drag to pan · Scroll to zoom · Double-click or F to center · H for current system · %s to close");
        both("gui.universe.star_map.location", "当前位置：%s", "Location: %s");
        both("gui.universe.star_map.location_unknown", "当前位置：星团之外", "Location: outside the cluster");
        both("gui.universe.star_map.systems", "%s 个恒星系", "%s star systems");
        both("gui.universe.star_map.light_years", "%s 光年", "%s ly");
        both("gui.universe.star_map.here", "你在这里", "You are here");
        both("gui.universe.star_map.distance", "距离：%s 光年", "Distance: %s ly");
        both("gui.universe.star_map.from_home", "距母星系：%s 光年", "From home: %s ly");
        both("gui.universe.star_map.mass", "质量：%s 个太阳", "Mass: %s suns");
        both("gui.universe.star_map.star_stats", "光度 %s · 表面 %s K · 质量 %s", "Luminosity %s · %s K · mass %s");
        both("gui.universe.star_map.planets", "行星（%s）", "Planets (%s)");
        both("gui.universe.star_map.gas_giant", "不能登陆 · 半径 %s", "Cannot land · radius %s");
        both("gui.universe.star_map.details", "%s°C · %s g · 大气：%s", "%s°C · %s g · atmosphere: %s");
        both("gui.universe.star_map.hazards", "危害：%s", "Hazards: %s");
        both("gui.universe.star_map.warp", "跃迁", "Warp");
        both("gui.universe.star_map.warp.range", "跃迁射程：%s 光年", "Warp range: %s ly");
        both("gui.universe.star_map.warp.here", "已在此恒星系", "Already in this system");
        both("gui.universe.star_map.warp.ready", "就绪：消耗 1 个空间翘曲器（剩余 %s）", "Ready: uses 1 space warper (%s left)");
        both("gui.universe.star_map.warp.ready_creative", "就绪：创造模式不消耗空间翘曲器", "Ready: no warper used in Creative");
        both("message.universe.warp.not_piloting", "需要在太空中驾驶装有跃迁引擎的飞船", "Requires piloting a ship with a warp drive in space");
        both("message.universe.warp.not_in_space", "需要先进入太空", "You must be in space");
        both("message.universe.warp.busy", "跃迁引擎正在充能", "The warp drive is already spooling");
        both("message.universe.warp.no_drive", "飞船上没有跃迁引擎", "This ship has no warp drive");
        both("message.universe.warp.bad_target", "无效的跃迁目标", "Invalid warp destination");
        both("message.universe.warp.out_of_range", "超出跃迁射程（%s / %s 光年）", "Out of warp range (%s / %s ly)");
        both("message.universe.warp.near_star", "离恒星太近，无法跃迁", "Too close to the star to warp");
        both("message.universe.warp.near_body", "离%1$s太近，无法跃迁", "Too close to %2$s to warp");
        both("message.universe.warp.no_warper", "需要空间翘曲器", "Requires a space warper");
        both("message.universe.warp.cancelled", "跃迁中断", "Warp aborted");
        both("message.universe.warp.arrived", "已抵达%1$s恒星系", "Arrived at the %2$s system");
        both("hud.universe.warp.spooling", "跃迁引擎充能中，目标：%s", "Warp drive spooling: %s");
        both("hud.universe.warp.spooling_unknown", "跃迁引擎充能中", "Warp drive spooling");
        both("hud.universe.ship.warp", "跃迁射程 %s 光年 · 翘曲器 %s · %s 打开星图", "Warp %s ly · warpers %s · %s for star map");
        both("message.universe.sorter.filter", "分拣器过滤：%s", "Sorter filter: %s");
        both("message.universe.sorter.no_filter", "分拣器过滤：无", "Sorter filter: none");
        both("message.universe.storage_tank.empty", "储液罐：空（容量 %s）", "Tank: empty (capacity %s)");
        both("message.universe.storage_tank.contents", "%s：%s / %s", "%s: %s / %s");
        both("tooltip.universe.storage_tank.empty", "空（可装一种流体，容量 %s）", "Empty (one fluid, capacity %s)");
        both("tooltip.universe.storage_tank.contents", "%1$s ×%2$s（容量 %3$s）", "%1$s ×%2$s (capacity %3$s)");
        both("message.universe.factory_anchor.placed", "工厂锚已启动（%1$s×%1$s 区块常加载）", "Factory anchor active (%1$s×%1$s chunks kept loaded)");
        both("message.universe.factory_anchor.info", "常加载 %1$s×%1$s 区块（%2$s 格见方），所在世界不会卸载",
                "Keeps %1$s×%1$s chunks (%2$s blocks across) and this world loaded");
        // 物流站（ADR-0015）
        both("message.universe.station.full", "物流站已满", "Station full");
        both("message.universe.station.drones", "无人机 %s / %s", "Drones %s / %s");
        both("message.universe.station.vessels", "运输船 %s / %s", "Vessels %s / %s");
        both("message.universe.station.warpers", "空间翘曲器 %s / %s", "Space warpers %s / %s");
        both("message.universe.orbital_collector.not_in_space", "只能放在太空中的气态巨行星附近", "Must be placed in space near a gas giant");
        both("message.universe.orbital_collector.no_giant", "这个星系没有气态巨行星", "This system has no gas giant");
        both("message.universe.orbital_collector.too_far", "离%1$s太远：还差 %3$s 格（%4$s方，需在大气层外 %5$s 格内）",
                "Too far from %2$s: %3$s blocks short (to the %4$s; must be within %5$s blocks of its atmosphere)");
        both("tooltip.universe.station.slot", "%s × %s", "%s × %s");
        both("tooltip.universe.station.vehicles", "无人机 %s · 运输船 %s · 翘曲器 %s", "Drones %s · Vessels %s · Warpers %s");
        both("gui.universe.station.energy", "电量 %s / %s", "Energy %s / %s");
        both("gui.universe.station.full", "已充满", "Full");
        both("gui.universe.station.no_surplus", "无余电", "No spare");
        both("gui.universe.station.unpowered", "未接电网", "No grid");
        both("gui.universe.station.range", "无人机航程 %s 格", "Drone range %s");
        both("gui.universe.station.range_hint", "无人机只飞往这个距离以内的物流站；点 − / + 每次调 250 格",
                "Drones only fly to stations within this range; − / + change it by 250");
        both("gui.universe.station.item", "物品", "Item");
        both("gui.universe.station.local", "本地", "Local");
        both("gui.universe.station.remote", "远程", "Remote");
        both("gui.universe.station.limit", "上限", "Limit");
        both("gui.universe.station.empty_slot", "未设定", "Not set");
        both("gui.universe.station.logic.storage", "仓储", "Store");
        both("gui.universe.station.logic.supply", "供应", "Supply");
        both("gui.universe.station.logic.demand", "需求", "Demand");
        both("gui.universe.station.port.drones", "放入物流无人机；空手点击取出空闲的无人机",
                "Drop logistics drones here; click with an empty hand to take idle ones");
        both("gui.universe.station.port.vessels", "放入物流运输船；空手点击取出空闲的运输船",
                "Drop logistics vessels here; click with an empty hand to take idle ones");
        both("gui.universe.station.port.warpers", "放入空间翘曲器：去别的星系每趟用 2 个；空手点击全部取出",
                "Drop space warpers here: each interstellar trip uses 2; click with an empty hand to take them all");
        both("gui.universe.station.set_hint", "手持物品点击：设定这一格的物品", "Click with an item to assign it to this slot");
        both("gui.universe.station.deposit_hint", "手持同种物品点击：存进去", "Click with the same item to deposit it");
        both("gui.universe.station.withdraw_hint", "空手点击：取出一组", "Click with an empty hand to take a stack");
        both("gui.universe.station.stock", "存量 %s / 上限 %s（最多 %s）", "Stock %s / limit %s (max %s)");
        both("gui.universe.station.incoming", "在途 %s", "Incoming %s");
        both("gui.universe.station.scroll_hint", "滚轮调整上限", "Scroll to change the limit");
        both("gui.universe.station.local_title", "本地：同一星球上的物流站，无人机运送", "Local: stations on this planet, served by drones");
        both("gui.universe.station.remote_title", "远程：其他星球上的星际物流站，运输船运送",
                "Remote: interstellar stations on other worlds, served by vessels");
        both("gui.universe.station.logic_hint.local.storage", "仓储：只存放，不派无人机，也不供给别的站",
                "Storage: just holds items; no drone deliveries in or out");
        both("gui.universe.station.logic_hint.local.supply", "供应：同一星球上需求这种物品的站会派无人机来取",
                "Supply: stations on this planet that demand it send drones to fetch it");
        both("gui.universe.station.logic_hint.local.demand", "需求：派无人机从同一星球的供应站运来，直到上限",
                "Demand: sends drones to fetch it from supplying stations on this planet, up to the limit");
        both("gui.universe.station.logic_hint.remote.storage", "仓储：不和其他星球往来", "Storage: no trade with other worlds");
        both("gui.universe.station.logic_hint.remote.supply", "供应：其他星球需求这种物品的星际物流站会派运输船来取",
                "Supply: interstellar stations elsewhere that demand it send vessels to fetch it");
        both("gui.universe.station.logic_hint.remote.demand", "需求：派运输船从其他星球的供应站运来，直到上限",
                "Demand: sends vessels to fetch it from supplying stations on other worlds, up to the limit");
        both("gui.universe.station.cycle_hint", "点击切换：仓储 → 供应 → 需求", "Click to cycle: Storage → Supply → Demand");
        both("gui.universe.station.limit_hint", "上限 −/+ 100，按住 Shift 一次 1000", "Limit −/+ 100 (Shift: 1000)");
        both("gui.universe.station.clear_hint", "清除这一格的设定（格子空了、也没有在途的货时）",
                "Clear this slot (only when it's empty and nothing is in transit)");
        both("gui.universe.orbital_collector.hint", "不用接电网 · 等星际物流站的运输船来取货", "No power needed; vessels collect it");
        both("gui.universe.orbital_collector.rate", "产量", "Rate");
        both("gui.universe.fractionator.input", "氢", "H");
        both("gui.universe.fractionator.idle", "缺少氢", "No hydrogen");
        both("gui.universe.fractionator.rate", "每秒处理 %s 个（最多 %s）", "%s per second (max %s)");
        both("gui.universe.fractionator.produced", "已分出重氢 %s 个", "Deuterium produced: %s");
        both("gui.universe.fractionator.hint", "氢从正面输出，重氢从顶部输出", "Hydrogen out the front, deuterium out the top");
        both("message.universe.fuel_tank.full", "燃料舱已满", "Fuel tank full");
        both("message.universe.fuel_tank.level", "燃料：%s / %s", "Fuel: %s / %s");
        both("message.universe.ship.too_large", "飞船过大（超过 %s 个方块），或与地面相连", "Ship too large (over %s blocks) or attached to the ground");
        both("message.universe.ship.split_multiblock", "%s 只有一部分在船上", "Only part of the %s is on the ship");
        both("message.universe.ship.two_helms", "一艘船只能有一个驾驶台", "A ship can only have one helm");
        both("message.universe.ship.no_thrusters", "飞船没有推进器", "The ship has no thrusters");
        both("message.universe.ship.assembled", "飞船就绪：%s 个方块，%s 吨，推重比 %s，燃料 %s/%s",
                "Ship ready: %s blocks, %s t, thrust-to-weight %s, fuel %s/%s");
        both("message.universe.ship.too_heavy", "推力不足，无法在此起飞", "Not enough thrust to take off here");
        both("message.universe.ship.blocked", "空间不足，无法停船", "Not enough room to park");
        both("message.universe.ship.too_hot", "离恒星太近", "Too close to the star");
        both("message.universe.ship.cannot_land", "%1$s是气态巨行星，无法着陆", "%2$s is a gas giant and can't be landed on");
        both("message.universe.ship.entered_planet", "进入 %1$s 的大气层", "Entering the atmosphere of %2$s");
        both("message.universe.ship.entered_space", "进入太空", "Entering space");
        both("message.universe.ship.boarded_pilot", "空格上升，Shift 下降，着陆后按住 Shift 停船",
                "Space to climb, Shift to descend. Hold Shift after landing to park");
        both("message.universe.ship.boarded_passenger", "着陆后按 Shift 下船", "Press Shift after landing to get off");
        both("message.universe.ship.no_beam", "这艘船没有采矿光束", "This ship has no mining beam");
        both("message.universe.ship.no_cannon", "这艘船没有光子炮", "This ship has no photon cannon");
        both("message.universe.ship.mode_cannon", "武器：光子炮", "Weapon: photon cannon");
        both("message.universe.ship.mode_beam", "武器：采矿光束", "Weapon: mining beam");
        both("message.universe.ship.destroyed", "飞船被击毁，已拖回空间站（船体 25%%），损失了 %s 件货物",
                "Ship destroyed. Towed back to the station at 25%% hull, %s cargo items lost");
        both("message.universe.pirate.scanning", "海盗正在扫描货舱", "Pirates are scanning your cargo");
        both("message.universe.pirate.incoming", "海盗来袭：%s 架战机", "Pirate attack: %s fighters");
        both("message.universe.pirate.destroyed", "击落海盗战机，赏金 %s 信用点", "Pirate fighter destroyed, %s credit bounty");
        both("message.universe.pirate.destroyed_stowed", "击落海盗战机，赏金 %s 信用点，残骸已收入货舱", "Pirate fighter destroyed, %s credit bounty. Salvage stowed");
        both("message.universe.pirate.repelled", "已击退海盗（击落 %s 架）", "Pirate attack repelled (%s destroyed)");
        both("message.universe.pirate.fled_station", "海盗撤退了", "The pirates retreated");
        both("message.universe.pirate.escaped", "已摆脱海盗", "Escaped the pirates");
        both("message.universe.pirate.lost", "海盗跟丢了", "The pirates lost track of you");
        both("message.universe.pirate.looted", "货物被海盗抢走了", "The pirates took your cargo");
        both("message.universe.pirate.jammed", "脉冲引擎受到干扰", "Impulse drive jammed");
        both("hud.universe.pirate.count", "海盗战机 ×%s", "Pirate fighters ×%s");
        both("hud.universe.pirate.marker", "海盗 · %s 格", "Pirate · %s m");
        both("hud.universe.pirate.target", "海盗战机", "Pirate Fighter");
        both("stat.universe.pirates_destroyed", "击落的海盗战机", "Pirate Fighters Destroyed");
        both("stat.universe.pirate_raids_repelled", "打退的海盗袭击", "Pirate Attacks Repelled");
        both("stat.universe.asteroids_mined", "打碎的小行星", "Asteroids Mined");
        // 小行星（ADR-0017）
        both("entity.universe.asteroid", "小行星", "Asteroid");
        both("hud.universe.asteroid.small", "小型小行星", "Small asteroid");
        both("hud.universe.asteroid.medium", "中型小行星", "Medium asteroid");
        both("hud.universe.asteroid.large", "大型小行星", "Large asteroid");
        both("message.universe.asteroid.mined", "小行星碎了：%s", "Asteroid shattered: %s");
        both("message.universe.asteroid.mined_stowed", "小行星碎了，收进货舱：%s", "Asteroid shattered, stowed in the hold: %s");
        both("message.universe.ship.impulse_bumped", "撞上小行星，脉冲引擎已关闭", "Hit an asteroid. Impulse drive offline");
        both("message.universe.asteroid.cargo_full", "货舱已满，%s 件物品留在了太空中", "Cargo hold full, %s items left behind");
        both("commands.universe.asteroids.not_space", "这里不是太空", "This is not space");
        both("commands.universe.asteroids.belt", "小行星带：离恒星 %s–%s 格，上下 %s 格，离这里 %s 格",
                "Asteroid belt: %s–%s from the star, ±%s vertically, %s away");
        both("commands.universe.asteroids.info", "%s 条小行星带；这里的密度 %s，已经生成 %s 颗",
                "%s asteroid belts; density here %s, %s asteroids spawned");
        both("commands.universe.asteroids.goto", "传送到小行星带：%s, 0, %s", "Teleported to the asteroid belt: %s, 0, %s");
        both("commands.universe.asteroids.spawned", "放了一颗%s", "Spawned a %s");
        both("commands.universe.asteroids.reset", "删掉了 %s 颗小行星，冷却清空", "Removed %s asteroids and cleared regrowth timers");
        both("commands.universe.pirates.started", "触发了海盗袭击：%s 架，货舱值 %s 信用点", "Pirate attack started: %s fighters, cargo worth %s credits");
        both("commands.universe.pirates.cleared", "%s 架海盗战机撤走了", "%s pirate fighters withdrew");
        both("commands.universe.pirates.info", "货舱值 %s 信用点，下次遭遇海盗的概率 %s%%；再飞 %s 秒掷一次骰子",
                "Cargo worth %s credits, %s%% chance of pirates at the next roll in %s s");
        both("commands.universe.pirates.info_idle", "货舱值 %s 信用点，下次遭遇海盗的概率 %s%%；还没开始计时（要在离空间站 600 格外的太空里驾驶飞船）",
                "Cargo worth %s credits, %s%% chance of pirates at the next roll; not counting yet (fly a ship in space beyond 600 m of the station)");
        both("commands.universe.pirates.busy", "正在被海盗袭击", "A pirate attack is already under way");
        both("commands.universe.pirates.not_piloting", "要先驾驶飞船进入太空", "Pilot a ship in space first");
        both("message.universe.ship.towed", "已拖回空间站机库，可在维修台修理", "Towed to the station hangar. Repair it at the repair console");

        both("hud.universe.ship.title", "飞船", "Ship");
        both("hud.universe.ship.status.impulse", "脉冲巡航", "Impulse");
        both("hud.universe.ship.status.space", "太空", "Space");
        both("hud.universe.ship.status.landed", "已着陆", "Landed");
        both("hud.universe.ship.status.flying", "飞行中", "Flying");
        both("hud.universe.ship.speed", "速度 %s 格/秒", "Speed %s m/s");
        both("hud.universe.ship.altitude", "高度 %s（%s 进入太空）", "Altitude %s (space at %s)");
        both("hud.universe.ship.thrust", "推重比 %s  重力 %s g", "Thrust/weight %s  Gravity %s g");
        both("hud.universe.ship.mass", "%s 个方块  %s 吨", "%s blocks  %s t");
        both("hud.universe.ship.fuel", "燃料 %s / %s", "Fuel %s / %s");
        both("hud.universe.ship.keys", "WASD 推进转向  空格 上升  Shift 下降", "WASD move  Space up  Shift down");
        both("hud.universe.ship.keys.space", "WASD 推进转向  空格/Shift 升降  疾跑键 脉冲引擎",
                "WASD move  Space/Shift up/down  Sprint: impulse");
        both("hud.universe.ship.parking", "停船中", "Parking");
        both("hud.universe.ship.hint.park", "按住 Shift 停船", "Hold Shift to park");
        both("hud.universe.ship.hint.space", "向上飞进入太空，按住 Shift 降落", "Climb to reach space, hold Shift to land");
        both("hud.universe.ship.hint.land", "飞入行星大气层即可降落", "Fly into a planet's atmosphere to land");
        both("hud.universe.ship.hint.no_fuel", "燃料耗尽", "Out of fuel");
        both("hud.universe.ship.target", "%s  %s 格", "%s  %s m");
        both("hud.universe.ship.unlandable", "（不能着陆）", " (no landing)");
        both("hud.universe.ship.above", "  ▲%s 格", "  ▲%s m");
        both("hud.universe.ship.below", "  ▼%s 格", "  ▼%s m");
        both("hud.universe.ship.hull", "船体 %s / %s", "Hull %s / %s");
        both("hud.universe.ship.shield", "护盾 %s / %s", "Shield %s / %s");
        both("hud.universe.ship.weapon", "武器：%s", "Weapon: %s");
        both("hud.universe.ship.weapon.cannon", "光子炮 ×%s", "Photon cannon ×%s");
        both("hud.universe.ship.weapon.beam", "采矿光束 ×%s", "Mining beam ×%s");
        both("hud.universe.ship.weapon.overheated", "（过热）", " (overheated)");
        both("hud.universe.ship.out_of_arc", "射界外", "Out of arc");
        both("hud.universe.ship.belt", "小行星带", "Asteroid belt");
        both("hud.universe.ship.in_belt", "◆ 在小行星带里", "◆ Inside an asteroid belt");
        both("hud.universe.ship.keys.weapons", "%s 开火  %s 切换武器", "%s fire  %s switch weapon");
        tech("ship_weapons", "飞船武器", "Ship Weapons");
        tech("ship_shields", "飞船护盾", "Ship Shields");
        tech("exocraft", "探索车", "Exocraft");
    }

    private void multitool() {
        item(UniverseItems.MULTITOOL.get(), "多功能工具", "Multitool");
        item(UniverseItems.DAMAGED_MULTITOOL.get(), "损坏的多功能工具", "Damaged Multitool");
        both("message.universe.multitool.overheated", "多功能工具过热", "Multitool overheated");
        both("message.universe.multitool.mode.mining", "多功能工具：采矿模式", "Multitool: Mining mode");
        both("message.universe.multitool.mode.combat", "多功能工具：战斗模式", "Multitool: Combat mode");
        both("hud.universe.multitool.mode.mining", "采矿", "Mining");
        both("hud.universe.multitool.mode.combat", "战斗", "Combat");
        both("item.universe.multitool.mode", "模式：%s（%s 切换）", "Mode: %s (%s to switch)");
        both("message.universe.multitool.charging", "扫描仪充能中", "Scanner recharging");
        both("message.universe.multitool.scanned", "扫描完成：%s 条矿脉，共 %s 块", "Scan complete: %s veins, %s blocks");
        both("hud.universe.multitool.overheated", "过热", "OVERHEATED");
        both("hud.universe.multitool.marker", "%s ×%s  %s 格", "%s ×%s  %sm");
        both("message.universe.crash_site.smoke", "%s方约 %s 格外升起一道烟柱", "A column of smoke rises to the %s, about %s blocks away");
        both("direction.universe.east", "东", "east");
        both("direction.universe.southeast", "东南", "southeast");
        both("direction.universe.south", "南", "south");
        both("direction.universe.southwest", "西南", "southwest");
        both("direction.universe.west", "西", "west");
        both("direction.universe.northwest", "西北", "northwest");
        both("direction.universe.north", "北", "north");
        both("direction.universe.northeast", "东北", "northeast");
        both("book.universe.probe_log.title", "探测器日志", "Probe Log");
        both("book.universe.probe_log.page.1", "UNV-7 勘测探测器 日志\n\n主引擎失效，被迫降落在这颗行星上。外壳破损，主要系统离线。\n\n致拾到者：探测器里的设备还能修。", "UNV-7 SURVEY PROBE LOG\n\nMain engine failure. Forced landing on this planet. Hull breached, primary systems offline.\n\nTo whoever finds this: the gear aboard can still be repaired.");
        both("book.universe.probe_log.page.2", "【多功能工具】\n工具在撞击中损坏。两块铜锭、两份红石和一块铁锭就能修好。\n\n按住使用键：采矿激光，连续开火会过热。\n潜行时使用：扫描附近的矿脉。", "[MULTITOOL]\nDamaged in the impact. Two copper ingots, two redstone and an iron ingot will fix it.\n\nHold USE: mining laser. It overheats if fired too long.\nSneak + USE: scan for nearby ore veins.");
        both("book.universe.probe_log.page.3", "【外骨骼】\n头盔加胸甲才能密封供氧，整套才能抵御寒冷、高温、毒性和辐射。\n\n氧气罐：玻璃瓶装进树叶的氧。\n防护电池：铜、红石和煤。\n\n喷气背包：空中再按一次跳跃。", "[EXOSUIT]\nHelmet and chestplate seal in your oxygen; the full set holds off cold, heat, toxins and radiation.\n\nOxygen capsules: bottled leaves.\nHazard cells: copper, redstone, coal.\n\nJetpack: jump again in mid-air.");
        both("book.universe.probe_log.page.4", "【飞船】\n用方块搭一艘船：驾驶台、推进器、燃料舱，船体随你装饰。在驾驶台上右键组装。\n\n燃料舱里装发射燃料（煤、火药、玻璃瓶）。\n一直往上飞，穿过天空就是太空。", "[SHIPS]\nBuild one from blocks: a helm, thrusters, fuel tanks; dress the hull as you like. Use the helm to assemble.\n\nFill the tanks with launch fuel (coal, gunpowder, a glass bottle).\nClimb past the sky to reach space.");
        both("book.universe.probe_log.page.5", "【航线】\n这颗行星有一颗卫星：没有大气、很冷，但富含钛。\n\n在太空里开脉冲引擎赶路，飞进目标的大气层就会降落。\n\n更好的设备都离不开钛。", "[COURSE]\nThis planet has a moon: airless and cold, but rich in titanium.\n\nIn space the impulse engine gets you there fast. Fly into a body's atmosphere to land.\n\nBetter equipment needs titanium.");
        both("book.universe.probe_log.page.6", "【任务记录】\n本探测器隶属寰宇勘测计划。\n其余探测器的信号都已中断。\n\n这个星系里还有更多行星等着测绘。\n\n—— 日志结束 ——", "[MISSION]\nThis probe belongs to the Universe Survey Programme.\nAll other probes have gone silent.\n\nMore worlds in this system await mapping.\n\n— END OF LOG —");
    }

    private void suit() {
        item(UniverseItems.EXOSUIT_HELMET.get(), "外骨骼头盔", "Exosuit Helmet");
        item(UniverseItems.EXOSUIT_CHESTPLATE.get(), "外骨骼胸甲", "Exosuit Chestplate");
        item(UniverseItems.EXOSUIT_LEGGINGS.get(), "外骨骼护腿", "Exosuit Leggings");
        item(UniverseItems.EXOSUIT_BOOTS.get(), "外骨骼靴子", "Exosuit Boots");
        item(UniverseItems.OXYGEN_CAPSULE.get(), "氧气罐", "Oxygen Capsule");
        item(UniverseItems.HAZARD_CELL.get(), "防护电池", "Hazard Cell");
        both("hazard.universe.cold", "寒冷", "Cold");
        both("hazard.universe.heat", "高温", "Heat");
        both("hazard.universe.toxic", "毒性", "Toxicity");
        both("hazard.universe.radiation", "辐射", "Radiation");
        both("hud.universe.suit.title", "外骨骼", "Exosuit");
        both("hud.universe.suit.planet", "%s  %s °C  %s g", "%s  %s °C  %s g");
        both("hud.universe.suit.oxygen", "氧气", "Oxygen");
        both("hud.universe.suit.jetpack", "喷气背包", "Jetpack");
        both("hud.universe.suit.unsealed", "未密封", "unsealed");
        both("hud.universe.suit.near_heat", "取暖中", "warming");
        both("hud.universe.suit.near_cold", "降温中", "cooling");
        both("hud.universe.suit.cabin", "舱内供氧", "cabin air");
        both("hud.universe.suit.sheltered", "有遮挡", "sheltered");
        both("hud.universe.suit.storm", "风暴", "storm");
        both("hud.universe.suit.warning.oxygen", "氧气即将耗尽", "Oxygen low");
        both("hud.universe.suit.warning.no_oxygen", "氧气耗尽", "Out of oxygen");
        both("hud.universe.suit.warning.hazard", "%s防护即将耗尽", "%s protection low");
        both("message.universe.suit.oxygen_used", "用掉一个氧气罐：氧气 +50%", "Used an oxygen capsule: oxygen +50%");
        both("message.universe.suit.cell_used", "用掉一个防护电池：防护 +50%", "Used a hazard cell: protection +50%");
        both("death.attack.universe.vacuum", "%1$s耗尽了氧气", "%1$s ran out of oxygen");
        both("death.attack.universe.plasma", "%1$s被%2$s的等离子弹击中", "%1$s was shot down by %2$s's plasma");
        both("death.attack.universe.plasma.item", "%1$s被%2$s用%3$s打出的等离子弹击中", "%1$s was shot down by %2$s's plasma using %3$s");
        both("death.attack.universe.cold", "%1$s冻僵了", "%1$s froze to death");
        both("death.attack.universe.heat", "%1$s被高温烤熟了", "%1$s was cooked by the heat");
        both("death.attack.universe.toxic", "%1$s吸进了太多毒气", "%1$s breathed too much toxic air");
        both("death.attack.universe.radiation", "%1$s死于辐射", "%1$s died of radiation");
    }

    private void ores() {
        block(UniverseBlocks.ORES.get(OreKind.IRON).get(), "星岩铁矿", "Planet Iron Ore");
        block(UniverseBlocks.ORES.get(OreKind.COPPER).get(), "星岩铜矿", "Planet Copper Ore");
        block(UniverseBlocks.ORES.get(OreKind.COAL).get(), "星岩煤矿", "Planet Coal Ore");
        block(UniverseBlocks.ORES.get(OreKind.TITANIUM).get(), "钛矿石", "Titanium Ore");
        block(UniverseBlocks.ORES.get(OreKind.SILICON).get(), "硅矿石", "Silicon Ore");
        block(UniverseBlocks.ORES.get(OreKind.SULFUR).get(), "硫矿石", "Sulfur Ore");
        block(UniverseBlocks.ORES.get(OreKind.FIRE_ICE).get(), "可燃冰矿", "Fire Ice Ore");
        block(UniverseBlocks.ORES.get(OreKind.OPTICAL_CRYSTAL).get(), "光学晶体矿", "Optical Crystal Ore");
        block(UniverseBlocks.ORES.get(OreKind.ORGANIC_CRYSTAL).get(), "有机晶体矿", "Organic Crystal Ore");
        block(UniverseBlocks.ORES.get(OreKind.URANIUM).get(), "铀矿石", "Uranium Ore");
        block(UniverseBlocks.ORES.get(OreKind.SALT).get(), "岩盐矿", "Rock Salt Ore");
        block(UniverseBlocks.ORES.get(OreKind.MAGNETIC).get(), "磁铁矿", "Magnetite Ore");
        block(UniverseBlocks.ORES.get(OreKind.STRANGE_MATTER).get(), "奇异物质矿", "Strange Matter Ore");
        item(UniverseItems.ORE_DROPS.get(OreKind.TITANIUM).get(), "粗钛", "Raw Titanium");
        item(UniverseItems.ORE_DROPS.get(OreKind.SILICON).get(), "粗硅", "Raw Silicon");
        item(UniverseItems.ORE_DROPS.get(OreKind.SULFUR).get(), "硫磺", "Sulfur");
        item(UniverseItems.ORE_DROPS.get(OreKind.FIRE_ICE).get(), "可燃冰", "Fire Ice");
        item(UniverseItems.ORE_DROPS.get(OreKind.OPTICAL_CRYSTAL).get(), "光学晶体", "Optical Crystal");
        item(UniverseItems.ORE_DROPS.get(OreKind.ORGANIC_CRYSTAL).get(), "有机晶体", "Organic Crystal");
        item(UniverseItems.ORE_DROPS.get(OreKind.URANIUM).get(), "粗铀", "Raw Uranium");
        item(UniverseItems.ORE_DROPS.get(OreKind.SALT).get(), "盐", "Salt");
        item(UniverseItems.ORE_DROPS.get(OreKind.MAGNETIC).get(), "粗磁铁", "Raw Magnetite");
        item(UniverseItems.ORE_DROPS.get(OreKind.STRANGE_MATTER).get(), "奇异物质", "Strange Matter");
        item(UniverseItems.ORE_SMELTED.get(OreKind.TITANIUM).get(), "钛锭", "Titanium Ingot");
        item(UniverseItems.ORE_SMELTED.get(OreKind.SILICON).get(), "高纯硅", "Silicon");
        item(UniverseItems.ORE_SMELTED.get(OreKind.URANIUM).get(), "铀锭", "Uranium Ingot");
        item(UniverseItems.ORE_SMELTED.get(OreKind.MAGNETIC).get(), "磁铁", "Magnet");
    }

    private void factory() {
        block(UniverseBlocks.TESLA_TOWER.get(), "电力感应塔", "Tesla Tower");
        block(UniverseBlocks.WIND_TURBINE.get(), "风力涡轮机", "Wind Turbine");
        block(UniverseBlocks.SOLAR_PANEL.get(), "太阳能板", "Solar Panel");
        block(UniverseBlocks.THERMAL_PLANT.get(), "火力发电厂", "Thermal Power Plant");
        block(UniverseBlocks.ACCUMULATOR.get(), "蓄电器", "Accumulator");
        block(UniverseBlocks.ARC_SMELTER.get(), "电弧熔炉", "Arc Smelter");
        block(UniverseBlocks.ASSEMBLER.get(), "制造台 Mk.I", "Assembling Machine Mk.I");
        block(UniverseBlocks.OIL_REFINERY.get(), "原油精炼厂", "Oil Refinery");
        block(UniverseBlocks.MINING_MACHINE.get(), "采矿机", "Mining Machine");
        block(UniverseBlocks.WATER_PUMP.get(), "抽水站", "Water Pump");
        block(UniverseBlocks.OIL_EXTRACTOR.get(), "原油萃取站", "Oil Extractor");
        block(UniverseBlocks.CRUDE_OIL_SEEP.get(), "原油涌泉", "Crude Oil Seep");
        block(UniverseBlocks.CONVEYOR_BELT.get(), "传送带 Mk.I", "Conveyor Belt Mk.I");
        block(UniverseBlocks.SORTER.get(), "分拣器 Mk.I", "Sorter Mk.I");
        block(UniverseBlocks.MATRIX_LAB.get(), "矩阵研究站", "Matrix Lab");
        block(UniverseBlocks.STORAGE_DEPOT.get(), "储物仓 Mk.I", "Storage Mk.I");
        block(UniverseBlocks.CONVEYOR_BELT_MK2.get(), "传送带 Mk.II", "Conveyor Belt Mk.II");
        block(UniverseBlocks.CONVEYOR_BELT_MK3.get(), "传送带 Mk.III", "Conveyor Belt Mk.III");
        block(UniverseBlocks.SORTER_MK2.get(), "分拣器 Mk.II", "Sorter Mk.II");
        block(UniverseBlocks.SORTER_MK3.get(), "分拣器 Mk.III", "Sorter Mk.III");
        block(UniverseBlocks.SPLITTER.get(), "四向分流器", "Splitter");
        block(UniverseBlocks.STORAGE_TANK.get(), "储液罐", "Storage Tank");
        block(UniverseBlocks.PLANETARY_LOGISTICS_STATION.get(), "行星物流站", "Planetary Logistics Station");
        block(UniverseBlocks.INTERSTELLAR_LOGISTICS_STATION.get(), "星际物流站", "Interstellar Logistics Station");
        block(UniverseBlocks.ORBITAL_COLLECTOR.get(), "轨道采集器", "Orbital Collector");
        block(UniverseBlocks.ASSEMBLER_MK2.get(), "制造台 Mk.II", "Assembling Machine Mk.II");
        block(UniverseBlocks.ASSEMBLER_MK3.get(), "制造台 Mk.III", "Assembling Machine Mk.III");
        block(UniverseBlocks.PLANE_SMELTER.get(), "位面熔炉", "Plane Smelter");
        block(UniverseBlocks.CHEMICAL_PLANT.get(), "化工厂", "Chemical Plant");
        block(UniverseBlocks.FRACTIONATOR.get(), "分馏塔", "Fractionator");
        block(UniverseBlocks.ADVANCED_MINING_MACHINE.get(), "高级采矿机", "Advanced Mining Machine");
        block(UniverseBlocks.FACTORY_ANCHOR.get(), "工厂锚", "Factory Anchor");
        item(UniverseItems.DEUTERIUM.get(), "重氢", "Deuterium");
        item(UniverseItems.CRYSTAL_SILICON.get(), "晶格硅", "Crystal Silicon");
        item(UniverseItems.TITANIUM_ALLOY.get(), "钛合金", "Titanium Alloy");
        item(UniverseItems.SYNTHETIC_DIAMOND.get(), "金刚石", "Synthetic Diamond");
        item(UniverseItems.PLASTIC.get(), "塑料", "Plastic");
        item(UniverseItems.SULFURIC_ACID.get(), "硫酸", "Sulfuric Acid");
        item(UniverseItems.GRAPHENE.get(), "石墨烯", "Graphene");
        item(UniverseItems.CARBON_NANOTUBE.get(), "碳纳米管", "Carbon Nanotube");
        item(UniverseItems.TITANIUM_CRYSTAL.get(), "钛晶石", "Titanium Crystal");
        item(UniverseItems.MICROCRYSTALLINE_COMPONENT.get(), "微晶元件", "Microcrystalline Component");
        item(UniverseItems.PROCESSOR.get(), "处理器", "Processor");
        item(UniverseItems.PARTICLE_BROADBAND.get(), "粒子宽带", "Particle Broadband");
        item(UniverseItems.THRUSTER.get(), "推进器", "Thruster");
        item(UniverseItems.REINFORCED_THRUSTER.get(), "加力推进器", "Reinforced Thruster");
        item(UniverseItems.ELECTROMAGNETIC_TURBINE.get(), "电磁涡轮", "Electromagnetic Turbine");
        item(UniverseItems.SUPER_MAGNETIC_RING.get(), "超级磁场环", "Super-Magnetic Ring");
        item(UniverseItems.PARTICLE_CONTAINER.get(), "粒子容器", "Particle Container");
        item(UniverseItems.LOGISTICS_DRONE.get(), "物流无人机", "Logistics Drone");
        item(UniverseItems.LOGISTICS_VESSEL.get(), "物流运输船", "Logistics Vessel");
        item(UniverseItems.STRUCTURE_MATRIX.get(), "结构矩阵", "Structure Matrix");
        item(UniverseItems.INFORMATION_MATRIX.get(), "信息矩阵", "Information Matrix");
        item(UniverseItems.GEAR.get(), "齿轮", "Gear");
        item(UniverseItems.MAGNETIC_COIL.get(), "磁线圈", "Magnetic Coil");
        item(UniverseItems.CIRCUIT_BOARD.get(), "电路板", "Circuit Board");
        item(UniverseItems.ELECTRIC_MOTOR.get(), "电动机", "Electric Motor");
        item(UniverseItems.STEEL.get(), "钢材", "Steel");
        item(UniverseItems.PRISM.get(), "棱镜", "Prism");
        item(UniverseItems.PLASMA_EXCITER.get(), "电浆激发器", "Plasma Exciter");
        item(UniverseItems.ENERGETIC_GRAPHITE.get(), "高能石墨", "Energetic Graphite");
        item(UniverseItems.WATER.get(), "水", "Water");
        item(UniverseItems.CRUDE_OIL.get(), "原油", "Crude Oil");
        item(UniverseItems.REFINED_OIL.get(), "精炼油", "Refined Oil");
        item(UniverseItems.HYDROGEN.get(), "氢", "Hydrogen");
        item(UniverseItems.ELECTROMAGNETIC_MATRIX.get(), "电磁矩阵", "Electromagnetic Matrix");
        item(UniverseItems.ENERGY_MATRIX.get(), "能量矩阵", "Energy Matrix");
        this.dyson();

        both("gui.universe.machine.unpowered", "未接入电网（需要电力感应塔）", "No power (needs a tesla tower in range)");
        both("gui.universe.machine.power", "功率 %s · 满足率 %s%%", "Power %s · %s%% satisfied");
        both("gui.universe.machine.output", "输出 %s · 负荷 %s%%", "Output %s · load %s%%");
        both("gui.universe.machine.standby", "待机：已接入电网", "Standby · connected");
        both("gui.universe.machine.output_slot", "产物", "Output");
        both("gui.universe.machine.output_full", "产物已满", "Output full");
        both("gui.universe.mining_machine.vein", "矿脉：%s · %s 个节点", "Vein: %s · %s nodes");
        both("gui.universe.mining_machine.remaining", "剩余储量 %s", "Reserve %s");
        both("gui.universe.mining_machine.rate", "满电产量 %s 个/分钟", "%s per minute at full power");
        both("gui.universe.mining_machine.none", "下方 %1$s×%1$s、深 3 格内没有矿", "No ore in the %1$s×%1$s×3 area below");
        both("gui.universe.mining_machine.hint", "放在地表的矿脉露头上", "Place it on a surface vein");
        both("gui.universe.water_pump.ok", "水源正常", "Water source OK");
        both("gui.universe.water_pump.no_water", "要放在水源方块上面", "Must sit on a water source");
        both("gui.universe.water_pump.acid", "无法抽取酸液", "Can't pump acid");
        both("gui.universe.water_pump.rate", "满电产量 %s 个/分钟", "%s per minute at full power");
        both("gui.universe.oil_extractor.rate", "涌泉产量 %s 个/秒", "Seep yield %s/s");
        both("gui.universe.oil_extractor.none", "要放在原油涌泉上面", "Must sit on a crude oil seep");
        both("gui.universe.oil_extractor.hint", "产量逐渐下降，最低 30%", "Yield declines over time, down to 30%");
        both("gui.universe.tesla_tower.grid", "电网：%s 座感应塔，%s 台设备", "Grid: %s towers, %s devices");
        both("gui.universe.tesla_tower.flow", "发电能力 %s · 用电 %s", "Capacity %s · Demand %s");
        both("gui.universe.tesla_tower.satisfaction", "满足率 %s%%", "Satisfied %s%%");
        both("gui.universe.tesla_tower.storage", "蓄电器 充 %s · 放 %s", "Accumulators: in %s · out %s");
        both("gui.universe.tesla_tower.range", "覆盖半径 %s 格，相距 %s 格内的塔自动相连", "Range %s, links towers within %s");
        both("gui.universe.wind_turbine.wind", "风力系数 %s", "Wind factor %s");
        both("gui.universe.wind_turbine.hint", "高处和雨天风力更大", "Stronger wind up high and in rain");
        both("gui.universe.solar_panel.ratio", "光照比例 %s", "Solar ratio %s");
        both("gui.universe.solar_panel.sun", "太阳高度 %s%%", "Sun height %s%%");
        both("gui.universe.solar_panel.no_sky", "上方有遮挡", "Sky blocked");
        both("gui.universe.solar_panel.no_sun", "这里没有阳光", "No sunlight here");
        both("gui.universe.solar_panel.rain", "雨天：发电 %s%%", "Rain: %s%% output");
        both("gui.universe.solar_panel.night", "夜里不发电", "No power at night");
        both("gui.universe.solar_panel.space", "太空中没有昼夜，离恒星越近发电越多", "No night in space; more power closer to the star");
        both("gui.universe.solar_panel.hint", "跟着太阳转，正午发电最多", "Tracks the sun; peaks at noon");
        both("gui.universe.thermal_plant.fuel", "炉膛", "Firebox");
        both("gui.universe.thermal_plant.hint", "只按电网需要烧燃料", "Burns only what the grid needs");
        both("gui.universe.accumulator.charging", "充电中 %s", "Charging %s");
        both("gui.universe.accumulator.discharging", "放电中 %s", "Discharging %s");
        both("gui.universe.accumulator.idle", "待机", "Idle");
        both("gui.universe.accumulator.energy", "电量 %s / %s", "Stored %s / %s");
        both("gui.universe.recipe_machine.running", "加工中", "Working");
        both("gui.universe.recipe_machine.no_recipe", "选择配方", "Select a recipe");
        both("gui.universe.recipe_machine.no_input", "缺少原料", "Missing ingredients");
        both("gui.universe.recipe_machine.output_full", "产物已满", "Output full");
        both("gui.universe.recipe_machine.no_power", "电力不足", "No power");
        both("gui.universe.recipe_machine.locked", "配方未解锁", "Recipe locked");
        both("gui.universe.recipe_machine.time", "每次 %s 秒", "%s s per cycle");
        both("gui.universe.recipe_machine.ingredients", "原料", "Ingredients");
        both("gui.universe.recipe_machine.results", "产物", "Products");
        both("gui.universe.recipe_machine.requires", "需要研究：%s", "Requires research: %s");
        research();
    }

    /** 科技名称和研究的提示。 */
    private void research() {
        tech("electromagnetism", "电磁学", "Electromagnetism");
        tech("basic_logistics", "基础物流系统", "Basic Logistics System");
        tech("automatic_metallurgy", "自动化冶金", "Automatic Metallurgy");
        tech("basic_assembling", "基础制造工艺", "Basic Assembling Processes");
        tech("electromagnetic_matrix", "电磁矩阵", "Electromagnetic Matrix");
        tech("fluid_storage", "流体储存封装", "Fluid Storage Encapsulation");
        tech("electromagnetic_drive", "电磁驱动", "Electromagnetic Drive");
        tech("thermal_power", "火力发电", "Thermal Power");
        tech("steel_smelting", "钢材冶炼", "Steel Smelting");
        tech("plasma_refining", "等离子萃取精炼", "Plasma Extract Refining");
        tech("energy_matrix", "能量矩阵", "Energy Matrix");
        tech("energy_storage", "能量储存", "Energy Storage");
        tech("x_ray_cracking", "X 射线裂解", "X-Ray Cracking");
        tech("warp_drive", "跃迁引擎", "Warp Drive");
        tech("interstellar_survey", "星际测绘", "Interstellar Survey");
        tech("warp_calibration", "跃迁校准", "Warp Calibration");
        tech("tech_modules", "技术模块", "Technology Modules");
        tech("magnetic_levitation", "磁悬浮技术", "Magnetic Levitation");
        tech("improved_logistics", "改良物流系统", "Improved Logistics System");
        tech("basic_chemical", "基础化工", "Basic Chemical Engineering");
        tech("semiconductor", "半导体材料", "Semiconductor Material");
        tech("thruster", "推进器", "Thruster");
        tech("solar_power", "太阳能收集", "Solar Collection");
        tech("processor", "处理器", "Processor");
        tech("high_strength_materials", "高强度材料", "High-Strength Material");
        tech("deuterium_fractionation", "重氢分馏", "Deuterium Fractionation");
        tech("improved_assembling", "改良制造工艺", "Improved Assembling Processes");
        tech("planetary_logistics", "行星物流系统", "Planetary Logistics System");
        tech("structure_matrix", "结构矩阵", "Structure Matrix");
        tech("efficient_logistics", "高效物流系统", "Efficient Logistics System");
        tech("titanium_alloy", "钛合金冶炼", "Titanium Alloy Smelting");
        tech("plane_smelting", "位面冶金", "Plane Smelting");
        tech("information_matrix", "信息矩阵", "Information Matrix");
        tech("interstellar_logistics", "星际物流系统", "Interstellar Logistics System");
        tech("orbital_collection", "轨道采集", "Orbital Collection");
        tech("advanced_mining", "高级采矿", "Advanced Mining");
        tech("high_speed_assembling", "高速制造工艺", "High-Speed Assembling Processes");
        this.discovery();
        this.modules();
        this.pois();
        both("research.universe.completed", "研究完成：%s", "Research complete: %s");
        both("research.universe.missing_items", "物品不足", "Not enough items");
    }

    /** 兴趣点：方块、消息、界面、数据终端的日志片段、先驱者的线索（ADR-0014）。 */
    private void pois() {
        for (PoiType type : PoiType.values()) {
            both("poi.universe." + type.serializedName(), type.nameZh(), type.nameEn());
        }
        block(UniverseBlocks.DROP_POD.get(), "掉落舱", "Drop Pod");
        block(UniverseBlocks.DATA_TERMINAL.get(), "数据终端", "Data Terminal");
        block(UniverseBlocks.MONOLITH.get(), "先驱者石碑", "Precursor Monolith");
        block(UniverseBlocks.TECH_CACHE.get(), "科技储藏箱", "Technology Cache");
        block(UniverseBlocks.SIGNAL_BOOSTER.get(), "信号增强器", "Signal Booster");
        block(UniverseBlocks.SUPPLY_CRATE.get(), "补给箱", "Supply Crate");
        block(UniverseBlocks.SMOLDERING_WRECK.get(), "冒烟的残骸", "Smoldering Wreckage");
        both("item.universe.navigation_data", "导航数据", "Navigation Data");
        both("tooltip.universe.navigation_data.summary", "坠毁飞船和据点的补给箱里有，读数据终端也会给\n也能用电路板和纸合成\n放进信号增强器，定位附近没去过的兴趣点",
                "Found in crashed ship and outpost crates, and given by data terminals\nAlso crafted from circuit boards and paper\nFeed it to a Signal Booster to find unvisited sites nearby");
        both("container.universe.supply_crate", "补给箱", "Supply Crate");
        both("message.universe.poi.found", "发现兴趣点：%s", "Point of interest discovered: %s");
        both("message.universe.poi.arrived", "到达航点：%s", "Waypoint reached: %s");
        both("message.universe.poi.no_signal", "没有信号：信号增强器只能在可着陆的星球上使用", "No signal: Signal Boosters only work on landable planets");
        both("message.universe.poi.need_navigation_data", "需要一份导航数据", "Requires one Navigation Data");
        both("message.universe.poi.none_found", "%2$s 格内没有未去过的%1$s", "No unvisited %1$s within %2$s blocks");
        both("message.universe.poi.located", "已定位%1$s：%2$s方约 %3$s 格，已设为航点", "Located a %1$s about %3$s blocks to the %2$s. Waypoint set");
        both("message.universe.drop_pod.repaired_already", "掉落舱已修复", "Already repaired");
        both("message.universe.drop_pod.maxed", "外骨骼已升级到上限", "Exosuit fully upgraded");
        both("message.universe.drop_pod.needs", "修理需要：%s", "Repair requires: %s");
        both("message.universe.drop_pod.upgraded", "外骨骼升级了：技术槽 %s/%s，货舱 %s/%s", "Exosuit upgraded: tech slots %s/%s, cargo %s/%s");
        both("message.universe.list_separator", "、", ", ");
        both("message.universe.item_count", "%2$s ×%1$s", "%2$s ×%1$s");
        both("message.universe.multitool.scanned_poi", "扫描完成：%s 条矿脉，共 %s 块；附近有 %s 个兴趣点", "Scan complete: %s veins, %s blocks; %s points of interest nearby");
        both("hud.universe.multitool.poi", "%s  %s 格", "%s  %sm");
        both("hud.universe.multitool.poi_visited", "%s  %s 格（去过）", "%s  %sm (visited)");
        both("hud.universe.waypoint", "%s · %s 格", "%s · %sm");
        both("gui.universe.terminal.title", "据点 %s 的日志", "Outpost %s Log");
        both("gui.universe.terminal.heading", "数据终端 · %s", "DATA TERMINAL · %s");
        both("gui.universe.terminal.signature", "—— %s", "— %s");
        both("gui.universe.terminal.read_before", "（已读过）", "(Already read)");
        both("gui.universe.poi.reward", "获得：%s ×%s", "Received: %s ×%s");
        both("gui.universe.poi.close", "Esc 关闭", "Esc to close");
        both("gui.universe.monolith.heading", "先驱者石碑 · 第 %s/%s 段", "PRECURSOR MONOLITH · %s of %s");
        both("gui.universe.monolith.touched_before", "（符文暗了下去，这一段你已经读过）", "(The glyphs dim. You have read this before.)");
        both("gui.universe.signal_booster.hint", "消耗一份导航数据，定位 %s 格内最近的未去过的地点",
                "Uses one Navigation Data to find the nearest unvisited site within %s blocks");
        both("gui.universe.signal_booster.data", "导航数据：%s", "Navigation Data: %s");
        both("gui.universe.signal_booster.data_creative", "导航数据：创造模式不消耗", "Navigation Data: not consumed in Creative");
        both("gui.universe.signal_booster.no_data", "没有导航数据", "No Navigation Data");
        both("gui.universe.signal_booster.current", "当前航点：%s，%s 格", "Current waypoint: %s, %s blocks");
        both("gui.universe.signal_booster.no_waypoint", "当前没有航点", "No active waypoint");
        both("poi.universe.log.opening.1", "据点 %1$s，驻扎第 %2$s 天。记录人：%3$s。",
                "Outpost %1$s, day %2$s of deployment. Log entry by %3$s.");
        both("poi.universe.log.opening.2", "%3$s 的个人日志，据点 %1$s，第 %2$s 天。",
                "Personal log of %3$s, Outpost %1$s, day %2$s.");
        both("poi.universe.log.opening.3", "自动记录已恢复。据点 %1$s，第 %2$s 天，当班：%3$s。",
                "Automatic logging restored. Outpost %1$s, day %2$s, on duty: %3$s.");
        both("poi.universe.log.opening.4", "第 %2$s 天，%1$s 号据点。我是 %3$s。如果有人收到这段记录——",
                "Day %2$s. Outpost %1$s. This is %3$s. If anyone finds this recording—");
        both("poi.universe.log.situation.1", "补给船又推迟了。%1$s 把最后一箱氧气胶囊锁进了柜子，说从今天起按天分。",
                "The supply ship is late again. %1$s locked the last crate of oxygen capsules away and says we ration from today.");
        both("poi.universe.log.situation.2", "钻机在昨晚的风暴里坏了。%1$s 修了一整夜，还是没能让它重新转起来。",
                "The drill rig died in last night's storm. %1$s worked on it all night and still couldn't get it turning.");
        both("poi.universe.log.situation.3", "这里的矿比勘测报告写的还丰富。%1$s 说只要再干一季，我们都能退休。",
                "The ore here is richer than the survey promised. %1$s says one more season and we can all retire.");
        both("poi.universe.log.situation.4", "%1$s 坚持说夜里听见地底下有敲击声。我去听了，什么也没有。",
                "%1$s insists there is knocking under the floor at night. I listened. Nothing.");
        both("poi.universe.log.situation.5", "通讯阵列只能收到杂音。%1$s 把天线调了三十多遍，信号一点没变。",
                "The comm array only picks up static. %1$s has realigned the antenna thirty times; the signal hasn't changed.");
        both("poi.universe.log.situation.6", "温差一天比一天大，外骨骼的危害防护撑不了多久。%1$s 已经开始咳嗽了。",
                "The temperature swings get worse every day and our suit protection won't hold for long. %1$s has started coughing.");
        both("poi.universe.log.situation.7", "我们在北边的山脊上发现一块刻满符号的石头。%1$s 拍了照，说那不可能是天然形成的。",
                "We found a stone covered in symbols on the northern ridge. %1$s took pictures and says it can't be natural.");
        both("poi.universe.log.situation.8", "今天又发现了一种新动物。%1$s 给它起了个难听的名字，还是登记进了发现日志。",
                "Found a new creature today. %1$s gave it an awful name and logged it in the discovery log anyway.");
        both("poi.universe.log.event.1", "下午来了三架守卫机。它们只是盯着采矿机看了一会儿，就飞走了。",
                "Three Sentinels came this afternoon. They just watched the mining rig for a while, then left.");
        both("poi.universe.log.event.2", "%1$s 出去巡查管线，到现在还没回来。信标显示信号停在东边的谷地里，一动不动。",
                "%1$s went out to check the pipeline and hasn't come back. The beacon shows the signal stopped in the eastern valley, not moving.");
        both("poi.universe.log.event.3", "一颗流星落在附近，冲击波震碎了两扇窗。我们把坠落点的坐标存进了导航数据。",
                "A meteor came down nearby and the shockwave shattered two windows. We saved the impact coordinates to the navigation data.");
        both("poi.universe.log.event.4", "储藏室的补给箱被翻过了，可门锁好好的。%1$s 说是我们自己记错了数。",
                "Someone went through the supply crates in storage, but the lock is untouched. %1$s says we must have miscounted.");
        both("poi.universe.log.event.5", "反应堆的读数开始乱跳。我们停掉了一半的设备，晚上只留一盏灯。",
                "The reactor readings have started to jump. We shut down half the equipment and keep a single light on at night.");
        both("poi.universe.log.event.6", "信号增强器收到一段反复播放的信号，不在我们的任何一个频段上。%1$s 把它录了下来。",
                "The signal booster caught a repeating transmission, not on any of our frequencies. %1$s recorded it.");
        both("poi.universe.log.event.7", "地面又塌了一块，露出一条很深的竖井。%1$s 往下扔了一根冷光棒，我们一直没听见它落地。",
                "Another section of ground collapsed, opening a deep shaft. %1$s dropped a glowstick in. We never heard it land.");
        both("poi.universe.log.event.8", "公司终于回复了：据点撤销，人员自行撤离。没说怎么撤。",
                "The company finally answered: outpost decommissioned, personnel to evacuate on their own. It didn't say how.");
        both("poi.universe.log.ending.1", "我们明早出发去着陆场。终端留着，也许下一个人用得上。",
                "We leave for the landing site at dawn. I'm leaving the terminal on. Maybe the next person can use it.");
        both("poi.universe.log.ending.2", "如果你读到这里，箱子里的东西随便拿吧，我们用不上了。",
                "If you're reading this, take whatever is in the crates. We won't need it anymore.");
        both("poi.universe.log.ending.3", "记录到此为止。电量只剩百分之四。",
                "End of entry. Power at four percent.");
        both("poi.universe.log.ending.4", "我把导航数据拷了一份留在这台终端里。朝有信号的方向走。",
                "I've left a copy of our navigation data on this terminal. Head toward the signal.");
        both("poi.universe.log.ending.5", "明天再记。今晚的极光很美，我们都出去看了。",
                "More tomorrow. The aurora tonight is beautiful. We all went out to watch.");
        both("poi.universe.log.ending.6", "（日志的其余部分已损坏，无法读取）",
                "(The rest of this entry is corrupted and cannot be read.)");
        both("poi.universe.lore.1.title", "回声", "Echo");
        both("poi.universe.lore.1.text", "旅人，你听得见我们。很久以前，我们也像你一样，从一颗小小的星球出发，抬头看着天，以为星星永远不会熄灭。",
                "Traveler, you can hear us. Long ago we too set out from one small world, looked up at the sky, and believed the stars would never go dark.");
        both("poi.universe.lore.2.title", "第一座环", "The First Ring");
        both("poi.universe.lore.2.text", "我们学会了驯服恒星。第一座环绕恒星的环合拢的那一天，我们的夜晚再也没有黑过。能量多到用不完，于是我们去找更多的恒星。",
                "We learned to tame stars. The day the first ring closed around our sun, our nights were never dark again. We had more energy than we could ever use, so we went looking for more stars.");
        both("poi.universe.lore.3.title", "群星之网", "The Web of Stars");
        both("poi.universe.lore.3.text", "一千颗恒星被我们的球壳包了起来。光沿着我们铺下的路，从一个星系流向另一个星系。我们把这叫做繁荣。",
                "A thousand stars wrapped in our shells. Light flowed along the roads we laid, from one system to the next. We called it prosperity.");
        both("poi.universe.lore.4.title", "沉默的星球", "Quiet Worlds");
        both("poi.universe.lore.4.text", "我们挖空了一颗又一颗星球。森林变成了矿坑，海洋变成了冷却池。星球不会抗议，所以我们什么也没听见。",
                "We hollowed out world after world. Forests became pits, oceans became coolant pools. Planets do not protest, so we heard nothing.");
        both("poi.universe.lore.5.title", "守卫", "Sentinels");
        both("poi.universe.lore.5.text", "后来我们造出了守卫机，让它们看守剩下的星球。它们不认主人，只认伤害。今天你遇到的，就是它们的后代。",
                "Later we built the Sentinels to watch over the worlds that remained. They know no masters, only harm. The ones you meet today are their descendants.");
        both("poi.universe.lore.6.title", "裂痕", "Fracture");
        both("poi.universe.lore.6.text", "我们为了恒星争吵。有人要拆掉球壳，让星光重新照到荒芜的星球上；有人说我们拥有的还远远不够。",
                "We quarreled over the stars. Some wanted the shells torn down so starlight could reach the barren worlds again. Others said we still did not have nearly enough.");
        both("poi.universe.lore.7.title", "暗下去的天", "Dimming");
        both("poi.universe.lore.7.text", "最远的星系开始一个接一个地暗下去。它们没有熄灭，是被包了起来。我们之中有人在暗中建造，我们始终不知道是谁。",
                "The farthest systems began to dim, one after another. They were not dying; they were being enclosed. Someone among us was building in secret, and we never learned who.");
        both("poi.universe.lore.8.title", "寂静", "The Silence");
        both("poi.universe.lore.8.text", "然后寂静来了。所有的频段同时没了声音，就像有人关上了一扇门。我们始终没弄清楚门的另一边是什么。",
                "Then came the Silence. Every channel went quiet at once, as if someone had closed a door. We never understood what lay on the other side.");
        both("poi.universe.lore.9.title", "石碑", "Monoliths");
        both("poi.universe.lore.9.text", "剩下的人立起了这些石碑。我们把记忆刻进石头，因为石头比我们活得长。你手里的勘探数据，有一部分来自我们。",
                "Those who remained raised these monoliths. We carved our memories into stone, because stone outlives us. Part of the survey data in your hands came from us.");
        both("poi.universe.lore.10.title", "中心", "The Center");
        both("poi.universe.lore.10.text", "星团的中心有一个吞下光的东西。我们最后的舰队朝它飞去。有人说那是逃离，有人说那是回家。",
                "At the heart of the cluster is something that swallows light. Our last fleet flew toward it. Some called it escape. Some called it going home.");
        both("poi.universe.lore.11.title", "选择", "Choice");
        both("poi.universe.lore.11.text", "你也会建起你的环、你的球壳。我们不会阻止你，也阻止不了。我们只想让你知道它的代价。",
                "You will build your rings and your shells too. We will not stop you; we cannot. We only want you to know what they cost.");
        both("poi.universe.lore.12.title", "最后的话", "Last Words");
        both("poi.universe.lore.12.text", "如果有一天，你的星空也开始变暗，记得回到这里，把手放在石头上。我们会听见。",
                "If one day your sky begins to dim, remember to come back here and lay your hand on the stone. We will hear you.");
    }

    /** 技术模块和外骨骼的格子（ADR-0014）。 */
    private void modules() {
        for (ModuleType type : ModuleType.values()) {
            item(UniverseItems.MODULES.get(type).get(), type.nameZh() + "模块", type.nameEn() + " Module");
        }
        String[][] stats = {
                {"oxygen", "%s 生命维持", "%s life support"},
                {"cold", "%s 防寒", "%s cold protection"},
                {"heat", "%s 隔热", "%s heat protection"},
                {"toxic", "%s 毒素防护", "%s toxin protection"},
                {"radiation", "%s 辐射防护", "%s radiation protection"},
                {"jet_time", "%s 喷气时长", "%s jetpack duration"},
                {"jet_thrust", "%s 喷气推力", "%s jetpack thrust"},
                {"speed", "%s 移动速度", "%s movement speed"},
                {"shield", "%s 护盾", "%s shield"},
                {"mining", "%s 采矿速度", "%s mining speed"},
                {"cooling", "%s 散热", "%s cooling"},
                {"scan_radius", "%s 扫描半径", "%s scan radius"},
                {"scan_cooldown", "%s 扫描充能", "%s scan recharge"},
                {"analysis", "%s 分析速度", "%s analysis speed"},
                {"damage", "%s 武器伤害", "%s weapon damage"},
                {"fire_rate", "%s 射速", "%s fire rate"}};
        for (String[] stat : stats) {
            both("tech.universe.stat." + stat[0], stat[1], stat[2]);
        }
        both("tooltip.universe.module.quality", "品质 %s%%", "Quality %s%%");
        both("tooltip.universe.module.uncalibrated", "未校准：放进背包后确定数值", "Uncalibrated: values are set once it's in your inventory");
        both("tooltip.universe.module.host.exosuit", "装在外骨骼上（U 键）", "Installs in the exosuit (U)");
        both("tooltip.universe.module.host.multitool", "装在多功能工具上（U 键）", "Installs in the multitool (U)");
        both("gui.universe.tech.title", "技术配置", "Tech Configuration");
        both("gui.universe.tech.exosuit", "外骨骼 %s/%s", "Exosuit %s/%s");
        both("gui.universe.tech.multitool", "多功能工具", "Multitool");
        both("gui.universe.tech.cargo", "货舱 %s/%s", "Cargo %s/%s");
        both("gui.universe.tech.no_tool", "主手持多功能工具时可配置其模块", "Hold a multitool in your main hand to configure it");
        both("gui.universe.tech.bonuses", "当前加成", "Bonuses");
        both("gui.universe.tech.no_bonuses", "未安装模块（同种最多叠加 3 个）", "No modules installed (up to 3 of a kind stack)");
        both("hud.universe.suit.shield", "护盾", "Shield");
        both("gui.universe.tech.suit_off", "没穿外骨骼胸甲，外骨骼模块不生效", "Chestplate not worn: exosuit modules inactive");
        both("gui.universe.tech.more", "……还有 %s 项（鼠标移到这里查看）", "…%s more (hover to see all)");
        both("hud.universe.cargo.stored", "背包满了，%s ×%s 收进货舱", "Inventory full: %s ×%s stored in cargo");
    }

    /** 发现日志、分析目镜、勘探数据（ADR-0012）。 */
    private void discovery() {
        // 服务端发来的程序生成的名字带中英文两个参数，各取一个
        both("universe.name", "%1$s", "%2$s");
        both("discovery.universe.kind.system", "恒星系", "Star system");
        both("discovery.universe.kind.body", "天体", "Celestial body");
        both("discovery.universe.kind.creature", "动物", "Creature");
        both("discovery.universe.kind.plant", "植物", "Plant");
        both("discovery.universe.kind.tree", "树木", "Tree");
        both("discovery.universe.kind.mineral", "矿物", "Mineral");
        both("discovery.universe.charted", "开局已测绘", "Charted from the start");
        both("discovery.universe.temperament.docile", "温顺", "Docile");
        both("discovery.universe.temperament.skittish", "易受惊", "Skittish");
        both("discovery.universe.temperament.territorial", "有领地意识", "Territorial");
        both("discovery.universe.temperament.predator", "捕食者", "Predator");
        String[][] plans = {{"quadruped", "四足兽", "Quadruped"}, {"hexapod", "六足虫", "Hexapod"}, {"biped", "两足兽", "Biped"},
                {"serpent", "节蠕虫", "Serpent"}, {"flyer", "飞兽", "Flyer"}};
        for (String[] plan : plans) {
            both("discovery.universe.plan." + plan[0], plan[1], plan[2]);
        }
        String[][] shapes = {{"shrub", "灌木", "Shrub"}, {"round", "阔叶树", "Broadleaf tree"}, {"conifer", "针叶树", "Conifer"},
                {"umbrella", "伞形树", "Umbrella tree"}, {"palm", "棕榈", "Palm"}, {"weeping", "垂枝树", "Weeping tree"},
                {"mushroom", "巨菌", "Giant fungus"}, {"baobab", "瓶干树", "Bottle tree"}, {"spiral", "螺旋树", "Spiral tree"},
                {"column", "柱状肉茎", "Fleshy column"}, {"crystal", "晶簇树", "Crystal tree"}};
        for (String[] shape : shapes) {
            both("discovery.universe.tree_shape." + shape[0], shape[1], shape[2]);
        }

        both("message.universe.discovery.new", "新发现 · %1$s：%2$s（勘探数据 +%3$s）", "New discovery · %1$s: %2$s (+%3$s survey data)");
        both("message.universe.discovery.complete", "%1$s 上的动植物已全部发现（勘探数据 +%2$s）",
                "Every species on %1$s has been discovered (+%2$s survey data)");
        both("message.universe.discovery.unknown", "无法识别", "Can't identify that");
        both("message.universe.discovery.known", "%1$s 已在发现日志里（%2$s）", "%1$s is already in the log (%2$s)");
        both("message.universe.discovery.renamed", "已改名为 %s", "Renamed to %s");
        both("message.universe.discovery.rename_unknown", "发现日志中没有此项", "Not in the discovery log");
        both("message.universe.discovery.rename_not_discoverer", "只有发现者能改名", "Only the discoverer can rename it");
        both("message.universe.discovery.rename_invalid_name", "名字太长或含有不能用的字符", "That name is too long or has invalid characters");

        both("hud.universe.visor.title", "分析目镜", "Analysis Visor");
        both("hud.universe.visor.need_tool", "需要手持多功能工具", "Requires a multitool in hand");
        both("hud.universe.visor.hint", "按住右键分析 · V 关闭", "Hold right-click to analyze · V to close");
        both("hud.universe.visor.no_target", "对准动物、植物、矿石或天体", "Aim at a creature, plant, ore or celestial body");
        both("hud.universe.visor.unidentified", "无法识别", "Unidentified");
        both("hud.universe.visor.new", "未记录 · 分析可得 %s 个勘探数据", "Not logged · analyzing yields %s survey data");
        both("hud.universe.visor.new_shared", "未记录", "Not logged");
        both("hud.universe.visor.known", "已记录 · 发现者 %s", "Logged · discovered by %s");
        both("hud.universe.visor.known_charted", "已记录 · 开局已测绘", "Logged · charted from the start");
        both("hud.universe.visor.analyzing", "分析中…", "Analyzing…");
        both("hud.universe.visor.discovered", "新发现", "New discovery");
        both("hud.universe.visor.reward", "勘探数据 +%s", "Survey data +%s");
        both("hud.universe.visor.creature", "%1$s · %2$s · 体长 %3$s 米", "%1$s · %2$s · %3$s m long");
        both("hud.universe.visor.husbandry", "%1$s · 产出%2$s", "%1$s · Produces %2$s");
        both("hud.universe.visor.husbandry_mount", "%1$s · 产出%2$s · 可骑乘", "%1$s · Produces %2$s · Rideable");
        both("hud.universe.visor.tamed", "主人：%s", "Owner: %s");
        both("hud.universe.visor.tamed_staying", "主人：%s · 待命", "Owner: %s · Staying");
        both("hud.universe.visor.body", "%1$s · %2$s °C · 重力 %3$s g", "%1$s · %2$s °C · %3$s g");
        both("hud.universe.visor.star", "恒星 · %s", "Star · %s");

        both("gui.universe.discovery.title", "发现日志", "Discovery Log");
        both("gui.universe.discovery.totals", "恒星系 %1$s · 天体 %2$s · 物种 %3$s · 矿物 %4$s",
                "Systems %1$s · Bodies %2$s · Species %3$s · Minerals %4$s");
        both("gui.universe.discovery.systems", "恒星系", "Star systems");
        both("gui.universe.discovery.bodies", "天体", "Bodies");
        both("gui.universe.discovery.undiscovered", "未发现的天体", "Undiscovered body");
        both("gui.universe.discovery.discoverer", "发现者：%s", "Discovered by %s");
        both("gui.universe.discovery.day", "第 %s 天", "Day %s");
        both("gui.universe.discovery.rename", "改名", "Rename");
        both("gui.universe.discovery.save", "保存", "Save");
        both("gui.universe.discovery.rename_hint", "留空保存恢复原名", "Leave empty for the original");
        both("gui.universe.discovery.creatures", "动物 %1$s / %2$s", "Creatures %1$s / %2$s");
        both("gui.universe.discovery.plants", "植物 %1$s / %2$s", "Plants %1$s / %2$s");
        both("gui.universe.discovery.trees", "树木 %1$s / %2$s", "Trees %1$s / %2$s");
        both("gui.universe.discovery.minerals", "矿物 %s", "Minerals %s");
        both("gui.universe.discovery.complete", "动植物已全部发现", "Every species discovered");
        both("gui.universe.discovery.lifeless", "没有生命", "Lifeless");
        both("gui.universe.discovery.not_landable", "不能着陆，只能从远处分析", "Cannot be landed on; analyze it from afar");
        both("gui.universe.discovery.unknown_species", "还有 %s 种没发现", "%s more to find");
        both("gui.universe.discovery.unknown_bodies", "还有 %s 个天体没发现", "%s bodies not yet found");
        both("gui.universe.discovery.species_total", "已发现物种 %s", "Species discovered: %s");
        both("gui.universe.discovery.complete_bodies", "%s 颗星球的动植物已全部发现", "Every species found on %s worlds");
        both("gui.universe.discovery.undiscovered_hint", "着陆，或在太空中用分析目镜分析", "Land on it, or analyze it with the visor from space");
        both("gui.universe.discovery.footer", "点选查看 · 发现者可以改名 · K 关闭", "Click to inspect · discoverers can rename · K to close");
        both("gui.universe.star_map.unexplored", "未探测（跃迁到此处后显示行星）", "Unexplored (warp here to chart its planets)");
        both("gui.universe.star_map.discovered_by", "发现者：%s", "Discovered by %s");
    }

    /** 戴森球（ADR-0016）：物品、设备、科技、设备界面和编辑器。 */
    private void dyson() {
        item(UniverseItems.PHOTON_COMBINER.get(), "光子合并器", "Photon Combiner");
        item(UniverseItems.SOLAR_SAIL.get(), "太阳帆", "Solar Sail");
        item(UniverseItems.FRAME_MATERIAL.get(), "框架材料", "Frame Material");
        item(UniverseItems.DYSON_SPHERE_COMPONENT.get(), "戴森球组件", "Dyson Sphere Component");
        item(UniverseItems.SMALL_CARRIER_ROCKET.get(), "小型运载火箭", "Small Carrier Rocket");
        item(UniverseItems.DEUTERON_FUEL_ROD.get(), "氘核燃料棒", "Deuteron Fuel Rod");
        item(UniverseItems.TITANIUM_GLASS.get(), "钛化玻璃", "Titanium Glass");
        item(UniverseItems.CASIMIR_CRYSTAL.get(), "卡西米尔晶体", "Casimir Crystal");
        item(UniverseItems.PLANE_FILTER.get(), "位面过滤器", "Plane Filter");
        item(UniverseItems.QUANTUM_CHIP.get(), "量子芯片", "Quantum Chip");
        item(UniverseItems.GRAVITON_LENS.get(), "引力透镜", "Graviton Lens");
        item(UniverseItems.CRITICAL_PHOTON.get(), "临界光子", "Critical Photon");
        item(UniverseItems.GRAVITY_MATRIX.get(), "引力矩阵", "Gravity Matrix");
        block(UniverseBlocks.EM_RAIL_EJECTOR.get(), "电磁轨道弹射器", "EM-Rail Ejector");
        block(UniverseBlocks.VERTICAL_LAUNCHING_SILO.get(), "垂直发射井", "Vertical Launching Silo");
        block(UniverseBlocks.RAY_RECEIVER.get(), "射线接收站", "Ray Receiver");
        block(UniverseBlocks.MINIATURE_PARTICLE_COLLIDER.get(), "微型粒子对撞机", "Miniature Particle Collider");

        tech("frame_material", "框架材料", "Frame Material");
        tech("quantum_printing", "量子打印技术", "Quantum Printing Technology");
        tech("solar_sail_orbit", "太阳帆轨道系统", "Solar Sail Orbit System");
        tech("ray_receiver", "射线接收站", "Ray Receiver");
        tech("miniature_collider", "微型粒子对撞机", "Miniature Particle Collider");
        tech("gravitational_wave_refraction", "引力波折射", "Gravitational Wave Refraction");
        tech("gravity_matrix", "引力矩阵", "Gravity Matrix");
        tech("vertical_launching_silo", "垂直发射井", "Vertical Launching Silo");
        tech("dyson_stress_1", "戴森球应力系统 I", "Dyson Sphere Stress System I");
        tech("dyson_stress_2", "戴森球应力系统 II", "Dyson Sphere Stress System II");
        both("tech.universe.dyson_stress_1.effect", "戴森球能建的纬度从 ±30° 放宽到 ±60°", "Dyson shells can be built up to ±60° latitude (was ±30°)");
        both("tech.universe.dyson_stress_2.effect", "戴森球能建满整个球面（±90°）", "Dyson shells can cover the whole sphere (±90°)");
        both("tech.universe.interstellar_survey.effect", "解锁跃迁到别的恒星系", "Unlocks warping to other star systems");

        // 弹射器
        both("gui.universe.ejector.orbit", "戴森云轨道 %s", "Swarm orbit %s");
        both("gui.universe.ejector.orbit_info", "半径 %s AU · 倾角 %s°", "Radius %s AU · inclination %s°");
        both("gui.universe.ejector.swarm", "戴森云：%s 张帆，发电 %s", "Swarm: %s sails, %s");
        both("gui.universe.ejector.progress", "下一张 %s%%", "Next sail %s%%");
        both("gui.universe.ejector.status.0", "正在弹射太阳帆", "Launching solar sails");
        both("gui.universe.ejector.status.1", "放入太阳帆", "Insert solar sails");
        both("gui.universe.ejector.status.2", "轨道不存在（在戴森球编辑器中新建）", "No such orbit (create one in the Dyson editor)");
        both("gui.universe.ejector.status.3", "恒星高度低于 5°", "Star below 5° elevation");
        both("gui.universe.ejector.status.4", "上方有遮挡", "Sky blocked");
        both("gui.universe.ejector.status.5", "这里没有恒星", "There is no star here");
        both("gui.universe.ejector.status.6", "电力不足", "Not enough power");
        // 发射井
        both("gui.universe.silo.progress", "下一枚 %s%%", "Next rocket %s%%");
        both("gui.universe.silo.structure", "结构点 %s / %s", "Structure points %s / %s");
        both("gui.universe.silo.target", "下一枚去第 %s 层", "Next rocket: layer %s");
        both("gui.universe.silo.status.0", "正在发射运载火箭", "Launching carrier rockets");
        both("gui.universe.silo.status.1", "放入小型运载火箭", "Insert small carrier rockets");
        both("gui.universe.silo.status.2", "戴森球没有壳层（在编辑器中添加）", "The Dyson sphere has no layers (add one in the editor)");
        both("gui.universe.silo.status.3", "可建结构已全部完成", "All buildable structures complete");
        both("gui.universe.silo.status.4", "井口上方被挡住了", "The silo mouth is blocked");
        both("gui.universe.silo.status.5", "这里没有恒星", "There is no star here");
        both("gui.universe.silo.status.6", "电力不足", "Not enough power");
        // 射线接收站
        both("gui.universe.receiver.mode.0", "发电模式", "Power mode");
        both("gui.universe.receiver.mode.1", "光子模式", "Photon mode");
        both("gui.universe.receiver.switch", "切换", "Switch");
        both("gui.universe.receiver.continuity", "连续接收 %s%% · 效率 %s%%", "Continuity %s%% · efficiency %s%%");
        both("gui.universe.receiver.max_output", "最大输出 %s · 分到 %s%%", "Max output %s · share %s%%");
        both("gui.universe.receiver.photon_draw", "从戴森球取 %s · 分到 %s%%", "Drawing %s · share %s%%");
        both("gui.universe.receiver.photon", "下一个临界光子 %s%%", "Next critical photon %s%%");
        both("gui.universe.receiver.lens", "引力透镜 %s%%（功率×2）", "Graviton lens %s%% (power ×2)");
        both("gui.universe.receiver.no_lens", "没有引力透镜", "No graviton lens");
        both("gui.universe.receiver.lens_hint", "功率翻倍；有大气的星球上不用看见恒星", "Doubles power; no line of sight needed under an atmosphere");
        both("gui.universe.receiver.status.0", "正在接收", "Receiving");
        both("gui.universe.receiver.status.1", "戴森球没有发电", "The Dyson sphere generates no power");
        both("gui.universe.receiver.status.2", "恒星在地平线以下", "The star is below the horizon");
        both("gui.universe.receiver.status.3", "上方有遮挡", "Sky blocked");
        both("gui.universe.receiver.status.4", "这里没有恒星", "There is no star here");
        both("gui.universe.receiver.status.5", "未接入电网（需要电力感应塔）", "No power (needs a tesla tower in range)");
        both("gui.universe.receiver.status.6", "临界光子已满", "Critical photons full");
        both("gui.universe.receiver.sphere", "戴森球 %s · 接收站共取走 %s", "Dyson sphere %s · receivers draw %s");
        // 编辑器
        both("key.universe.dyson_editor", "戴森球编辑器", "Dyson Sphere Editor");
        both("gui.universe.dyson.title", "戴森球编辑器 · %s", "Dyson Sphere Editor · %s");
        both("gui.universe.dyson.open", "戴森球编辑器", "Dyson Editor");
        both("gui.universe.dyson.open_short", "编辑器", "Editor");
        both("gui.universe.dyson.swarm", "戴森云", "Swarm");
        both("gui.universe.dyson.shells", "戴森壳", "Shells");
        both("gui.universe.dyson.orbit", "轨道 %s", "Orbit %s");
        both("gui.universe.dyson.layer", "第 %s 层", "Layer %s");
        both("gui.universe.dyson.add", "新建", "Add");
        both("gui.universe.dyson.remove", "删除", "Delete");
        both("gui.universe.dyson.confirm", "确定删除？", "Really delete?");
        both("gui.universe.dyson.radius", "半径 %s AU", "Radius %s AU");
        both("gui.universe.dyson.inclination", "倾角 %s°", "Inclination %s°");
        both("gui.universe.dyson.node", "升交点 %s°", "Ascending node %s°");
        both("gui.universe.dyson.template", "网格", "Grid");
        both("gui.universe.dyson.template.0", "测地三角网", "Geodesic");
        both("gui.universe.dyson.template.1", "蜂窝网", "Honeycomb");
        both("gui.universe.dyson.template.2", "经纬网", "Lat-long");
        both("gui.universe.dyson.density", "密度 %s", "Density %s");
        both("gui.universe.dyson.locked", "已经开始建造：只能改朝向", "Construction started: only orientation can change");
        both("gui.universe.dyson.power", "发电 %s", "Generating %s");
        both("gui.universe.dyson.power_split", "戴森云 %s · 戴森壳 %s", "Swarm %s · shells %s");
        both("gui.universe.dyson.drawn", "接收站取走 %s", "Receivers draw %s");
        both("gui.universe.dyson.sails", "%s 张帆", "%s sails");
        both("gui.universe.dyson.sail_life", "太阳帆寿命 90 分钟", "Sails last 90 minutes");
        both("gui.universe.dyson.elements", "节点 %s/%s · 框架 %s/%s · 球壳 %s/%s", "Nodes %s/%s · frames %s/%s · shells %s/%s");
        both("gui.universe.dyson.sp", "结构点 %s / %s", "Structure points %s / %s");
        both("gui.universe.dyson.cells", "单元点 %s / %s", "Cell points %s / %s");
        both("gui.universe.dyson.capacity", "建完以后 %s", "When finished %s");
        both("gui.universe.dyson.latitude", "能建的纬度 ±%s°", "Buildable latitude ±%s°");
        both("gui.universe.dyson.rockets", "在飞的火箭 %s", "%s rockets in flight");
        both("gui.universe.dyson.light", "恒星光照系数 %s · 半径上限 %s AU", "Star light factor %s · max radius %s AU");
        both("gui.universe.dyson.empty", "戴森球是空的：新建一条戴森云轨道或一层戴森壳", "This Dyson sphere is empty: add a swarm orbit or a shell layer");
        both("gui.universe.dyson.no_system", "看不到恒星", "No star in sight");
        both("gui.universe.dyson.waiting", "正在接收戴森球的数据……", "Waiting for Dyson sphere data…");
        both("gui.universe.dyson.hint", "拖动旋转 · 滚轮缩放 · Y 关闭", "Drag to rotate · scroll to zoom · Y to close");
        both("gui.universe.dyson.full", "满了：最多 %s 个", "Full: at most %s");
    }

    private void tech(String id, String zh, String en) {
        both("tech.universe." + id, zh, en);
    }

    private void item(net.minecraft.world.item.Item item, String zh, String en) {
        this.add(item, this.chinese ? zh : en);
    }

    private void block(net.minecraft.world.level.block.Block block, String zh, String en) {
        this.add(block, this.chinese ? zh : en);
    }
}
