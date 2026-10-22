package io.github.sweetcornna.universe.datagen;

import io.github.sweetcornna.universe.core.ecology.ResourcePlant;
import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.core.fleet.Area;
import io.github.sweetcornna.universe.core.fleet.FrigateTrait;
import io.github.sweetcornna.universe.core.fleet.FrigateType;
import io.github.sweetcornna.universe.core.guide.Guide;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;
import io.github.sweetcornna.universe.core.poi.PoiType;
import io.github.sweetcornna.universe.core.ship.ShipType;
import io.github.sweetcornna.universe.core.space.DriveTier;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.data.LanguageProvider;
import org.jspecify.annotations.Nullable;

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
        this.resourcePlants();
        this.multitool();
        this.factory();
        this.trade();
        this.teleport();
        this.missions();
        this.endgame();
        this.voyages();
        this.fleet();
        this.portal();
        this.encounters();
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
        story();
        blueprint();
        advancements();
        guide();
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
        block(UniverseBlocks.STATION_SOLAR_PANEL.get(), "空间站太阳能板", "Station Solar Array");
        both("hud.universe.station", "空间站 · %s 格", "Station · %s m");
        both("gui.universe.star_map.economy", "空间站：%s · %s", "Station: %s · %s");
        both("gui.universe.star_map.black_hole", "有黑洞：飞进视界会被甩到星团另一头", "Black hole: fly in to be thrown across the cluster");
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
        // JEI 分类和说明页（ADR-0019）
        both("jei.universe.smelting", "冶炼", "Smelting");
        both("jei.universe.requires", "需要研究：%s", "Research: %s");
        both("jei.universe.assembling", "制造", "Assembling");
        both("jei.universe.refining", "精炼", "Refining");
        both("jei.universe.chemical", "化工", "Chemical");
        both("jei.universe.particle", "粒子对撞", "Particle Collision");
        both("jei.universe.info.water", "抽水站放在水源方块上抽水，每分钟 50 个。酸海里的不行。",
                "A water pump on top of a water source block pumps 50 water a minute. Acid seas don't count.");
        both("jei.universe.info.crude_oil", "原油萃取站要压在原油涌泉上。涌泉越抽越少，降到一个下限为止。",
                "An oil extractor has to sit on a crude oil seep. Seeps slow down as you pump them, down to a floor.");
        both("jei.universe.info.deuterium", "分馏塔把流过的氢分出重氢，每个氢有 1% 的机会变成重氢。氢从正面出，重氢从顶部出。",
                "A fractionator turns 1% of the hydrogen passing through into deuterium. Hydrogen leaves the front, deuterium the top.");
        both("jei.universe.info.critical_photon", "射线接收站切到光子模式，用戴森球的能量做临界光子，每个 1.2 GJ。装上引力透镜功率翻倍。",
                "A ray receiver in photon mode turns Dyson sphere energy into critical photons, 1.2 GJ each. A graviton lens doubles the power.");
        // Jade 提示（ADR-0019）
        both("config.jade.plugin_universe.machine", "寰宇：设备", "Universe: Machines");
        both("jade.universe.power.none", "没接上电网", "Not connected to a power grid");
        both("jade.universe.power.generating", "发电 %s · 负荷 %s%%", "Generating %s · %s%% load");
        both("jade.universe.power.using", "用电 %s · 满足率 %s%%", "Using %s · %s%% satisfied");
        both("jade.universe.making", "在做 %s · %s%%", "Making %s · %s%%");
        both("jade.universe.station.slot", "%s %s/%s · %s", "%s %s/%s · %s");
        both("jade.universe.station.slot_remote", "%s %s/%s · 本地%s · 远程%s", "%s %s/%s · local %s · remote %s");
        // 客户端设置（模组列表里的"配置"）
        both("universe.configuration.title", "寰宇设置", "Universe Settings");
        both("universe.configuration.section.universe.client.toml", "客户端设置", "Client Settings");
        both("universe.configuration.section.universe.client.toml.title", "寰宇：客户端设置", "Universe: Client Settings");
        both("universe.configuration.dysonSky", "天空中的戴森球", "Dyson Sphere in the Sky");
        both("universe.configuration.dysonSky.tooltip", "简化：球壳和框架分段少，不画背面的框架和节点，帆点少一些。帧率低时调低。",
                "Low: fewer shell and frame segments, no far-side frames or nodes, fewer sail dots. Lower it if the frame rate drops.");
        both("universe.configuration.dysonSky.high", "完整", "High");
        both("universe.configuration.dysonSky.low", "简化", "Low");
        both("universe.configuration.dysonSky.off", "不显示", "Off");
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
        both("message.universe.warp.needs_drive", "要装%1$s才去得了这颗%2$s", "You need a %1$s to reach this %3$s");
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
        both("hud.universe.ship.status.hangar", "货船机库", "Freighter hangar");
        both("hud.universe.ship.speed", "速度 %s 格/秒", "Speed %s m/s");
        both("hud.universe.ship.altitude", "高度 %s（%s 进入太空）", "Altitude %s (space at %s)");
        both("hud.universe.ship.thrust", "推重比 %s  重力 %s g", "Thrust/weight %s  Gravity %s g");
        both("hud.universe.ship.mass", "%s 个方块  %s 吨", "%s blocks  %s t");
        both("hud.universe.ship.fuel", "燃料 %s / %s", "Fuel %s / %s");
        both("hud.universe.ship.keys", "WASD 推进转向  空格 上升  Shift 下降", "WASD move  Space up  Shift down");
        both("hud.universe.ship.keys.space", "WASD 推进转向  空格/Shift 升降  疾跑键 脉冲引擎  N 自动驾驶",
                "WASD move  Space/Shift up/down  Sprint: impulse  N: autopilot");
        both("hud.universe.ship.parking", "停船中", "Parking");
        both("hud.universe.ship.hint.park", "按住 Shift 停船", "Hold Shift to park");
        both("hud.universe.ship.hint.space", "向上飞进入太空，按住 Shift 降落", "Climb to reach space, hold Shift to land");
        both("hud.universe.ship.hint.land", "飞入行星大气层即可降落", "Fly into a planet's atmosphere to land");
        both("hud.universe.ship.hint.hangar", "从机库门飞出去回到太空，按住 Shift 停船", "Fly out the hangar door to leave. Hold Shift to park");
        both("hud.universe.ship.hint.no_fuel", "燃料耗尽", "Out of fuel");
        both("hud.universe.ship.target", "%s  %s 格", "%s  %s m");
        both("commands.universe.space.unknown", "没有这个天体：%s", "Unknown body: %s");
        both("commands.universe.space.view", "到了 %s 上空 %s 格", "Now %2$s m above %1$s");
        both("hud.universe.ship.target_far", "%s  %s 千格", "%s  %s km");
        both("screen.universe.transition.entry", "进入大气层", "Entering atmosphere");
        both("screen.universe.transition.exit", "离开大气层", "Leaving atmosphere");
        both("screen.universe.transition.warp", "脱离跃迁", "Dropping out of warp");
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
        both("hud.universe.suit.border", "离星球边界 %s 格", "%s blocks to the planet's edge");
        both("message.universe.planet_border.moved", "你在星球边界外，已挪回 %s", "You were outside the planet's edge and were moved to %s");
        both("hud.universe.suit.warning.oxygen", "氧气即将耗尽", "Oxygen low");
        both("hud.universe.suit.warning.no_oxygen", "氧气耗尽", "Out of oxygen");
        both("hud.universe.suit.warning.hazard", "%s防护即将耗尽", "%s protection low");
        both("message.universe.suit.oxygen_used", "氧气 +%2$s%%（%1$s）", "Oxygen +%2$s%% (%1$s)");
        both("message.universe.suit.cell_used", "防护 +%2$s%%（%1$s）", "Protection +%2$s%% (%1$s)");
        both("message.universe.suit.gel_used", "%2$s防护 +%3$s%%（%1$s）", "%2$s protection +%3$s%% (%1$s)");
        both("death.attack.universe.vacuum", "%1$s耗尽了氧气", "%1$s ran out of oxygen");
        both("death.attack.universe.plasma", "%1$s被%2$s的等离子弹击中", "%1$s was shot down by %2$s's plasma");
        both("death.attack.universe.plasma.item", "%1$s被%2$s用%3$s打出的等离子弹击中", "%1$s was shot down by %2$s's plasma using %3$s");
        both("death.attack.universe.cold", "%1$s冻僵了", "%1$s froze to death");
        both("death.attack.universe.heat", "%1$s被高温烤熟了", "%1$s was cooked by the heat");
        both("death.attack.universe.toxic", "%1$s吸进了太多毒气", "%1$s breathed too much toxic air");
        both("death.attack.universe.radiation", "%1$s死于辐射", "%1$s died of radiation");
    }

    /** 资源植物和水培槽（ADR-0026）。 */
    private void resourcePlants() {
        for (ResourcePlant plant : ResourcePlant.values()) {
            block(UniverseBlocks.RESOURCE_PLANTS.get(plant).get(), plant.nameZh(), plant.nameEn());
        }
        resource("oxygen", "氧气", "Oxygen");
        resource("sodium", "钠", "Sodium");
        resource("star_bulb", "星莓", "Star Bulb");
        resource("frost_crystal", "霜晶", "Frost Crystal");
        resource("solanium", "日焰果", "Solanium");
        resource("gamma_root", "伽马根", "Gamma Root");
        resource("fungal_mould", "霉菌", "Fungal Mould");
        resource("cactus_flesh", "仙人掌肉", "Cactus Flesh");
        resource("gravitino_ball", "引力果", "Gravitino Ball");
        resource("marrow_bulb", "髓球", "Marrow Bulb");
        both("message.universe.resource_plant.wrong_planet", "这颗星球上种不活", "It won't grow on this planet");
        block(UniverseBlocks.HYDROPONIC_TRAY.get(), "水培槽", "Hydroponic Tray");
        both("message.universe.hydroponic_tray.empty", "拿植物的产物右键种下", "Use a plant product on it to sow");
        both("message.universe.hydroponic_tray.no_power", "没有通电", "No power");
        both("message.universe.hydroponic_tray.growing", "%s：长了 %s%%", "%s: %s%% grown");
        both("commands.universe.resources.scan", "%s 格内资源植物 %s 株（长熟 %s）：%s", "%s blocks around: %s resource plants (%s ripe): %s");
        both("commands.universe.resources.goto", "到了 %s 旁边（%s）", "Next to %s (%s)");
        both("commands.universe.resources.not_found", "%s 格内没有 %s", "No %2$s within %1$s blocks");
        both("commands.universe.resources.unknown", "没有叫 %s 的资源植物", "No resource plant called %s");
    }

    private void resource(String product, String zh, String en) {
        item(UniverseItems.RESOURCES.get(product).get(), zh, en);
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
        this.factoryDepth();
        this.mecha();

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

    /** 工厂的补全（ADR-0022）：增产剂、传送带上的设备、电力和高级设备。 */
    private void factoryDepth() {
        item(UniverseItems.PROLIFERATOR_MK1.get(), "增产剂 Mk.I", "Proliferator Mk.I");
        item(UniverseItems.PROLIFERATOR_MK2.get(), "增产剂 Mk.II", "Proliferator Mk.II");
        item(UniverseItems.PROLIFERATOR_MK3.get(), "增产剂 Mk.III", "Proliferator Mk.III");
        item(UniverseItems.COMBUSTIBLE_UNIT.get(), "燃烧单元", "Combustible Unit");
        item(UniverseItems.HYDROGEN_FUEL_ROD.get(), "液氢燃料棒", "Hydrogen Fuel Rod");
        block(UniverseBlocks.SPRAY_COATER.get(), "喷涂机", "Spray Coater");
        block(UniverseBlocks.AUTOMATIC_PILER.get(), "自动集装机", "Automatic Piler");
        block(UniverseBlocks.TRAFFIC_MONITOR.get(), "流速监测器", "Traffic Monitor");
        block(UniverseBlocks.PILE_SORTER.get(), "集装分拣器", "Pile Sorter");
        block(UniverseBlocks.WIRELESS_POWER_TOWER.get(), "无线输电塔", "Wireless Power Tower");
        block(UniverseBlocks.SATELLITE_SUBSTATION.get(), "卫星配电站", "Satellite Substation");
        block(UniverseBlocks.GEOTHERMAL_POWER_STATION.get(), "地热发电站", "Geothermal Power Station");
        block(UniverseBlocks.ENERGY_EXCHANGER.get(), "能量枢纽", "Energy Exchanger");
        block(UniverseBlocks.NEGENTROPY_SMELTER.get(), "负熵熔炉", "Negentropy Smelter");
        block(UniverseBlocks.RECOMPOSING_ASSEMBLER.get(), "重组式制造台", "Re-composing Assembler");
        block(UniverseBlocks.QUANTUM_CHEMICAL_PLANT.get(), "量子化工厂", "Quantum Chemical Plant");
        block(UniverseBlocks.SELF_EVOLUTION_LAB.get(), "自演化研究站", "Self-evolution Lab");
        block(UniverseBlocks.FOUNDATION.get(), "地基", "Foundation");

        // 物品说明（按住 Shift）
        for (int tier = 1; tier <= 3; tier++) {
            int points = tier == 3 ? 4 : tier;
            int sprays = tier == 1 ? 12 : tier == 2 ? 24 : 60;
            both("tooltip.universe.proliferator_mk" + tier + ".summary",
                    "装进喷涂机，给传送带上的货物喷 " + points + " 点\n一个能喷 " + sprays + " 个货物\n设备用喷过的原料：多出产物或者做得更快",
                    "Load it into a spray coater to give belt cargo " + points + " point" + (points > 1 ? "s" : "")
                            + "\nOne covers " + sprays + " items\nMachines fed sprayed ingredients make extra products or work faster");
        }
        both("tooltip.universe.combustible_unit.summary", "燃料，比高能石墨耐烧\n火力发电厂和熔炉都能烧",
                "Fuel that outlasts energetic graphite\nBurns in thermal plants and furnaces");
        both("tooltip.universe.hydrogen_fuel_rod.summary", "高能燃料，一根顶 8 块多高能石墨\n火力发电厂和熔炉都能烧",
                "Dense fuel, worth over 8 energetic graphite\nBurns in thermal plants and furnaces");
        both("tooltip.universe.spray_coater.summary", "串在传送带上，给经过的货物喷增产剂\n拿着增产剂右键装填，空手右键看还剩多少\n用电 100 W",
                "Sits in a belt line and sprays passing cargo\nRight-click with a proliferator to load, empty-handed to check\nUses 100 W");
        both("tooltip.universe.automatic_piler.summary", "串在传送带上，把同一种货物叠成一摞再送走\n一摞几个看「货物集装」研究到几级\n用电 160 W",
                "Sits in a belt line and stacks matching cargo into piles\nPile size depends on Cargo Stacking research\nUses 160 W");
        both("tooltip.universe.traffic_monitor.summary", "串在传送带上，数最近一分钟流过多少个\n右键设报警下限，拿着物品右键只数这一种\n流量低于下限时亮红灯、发出红石信号",
                "Sits in a belt line and counts items over the last minute\nRight-click to set an alarm floor; right-click with an item to count only that\nBelow the floor it lights red and emits redstone");
        both("tooltip.universe.pile_sorter.summary", "一次搬一整摞，比分拣器 Mk.III 快一倍\n一摞几个看「货物集装」研究到几级",
                "Moves a whole pile at once, twice as fast as Sorter Mk.III\nPile size depends on Cargo Stacking research");
        both("tooltip.universe.wireless_power_tower.summary", "覆盖半径 4 格，相距 10 格内的塔自动相连\n给 5 格内穿着外骨骼的玩家补氧、给危害防护充能",
                "Range 4, links towers within 10\nRefills oxygen and hazard protection for exosuits within 5 blocks");
        both("tooltip.universe.satellite_substation.summary", "覆盖半径 20 格，相距 40 格内的塔自动相连",
                "Range 20, links towers within 40");
        both("tooltip.universe.geothermal_power_station.summary", "正下方一格是熔岩才发电，最大 4.8 kW\n灼热星球和下界多五成\n不烧燃料",
                "Needs lava directly below; up to 4.8 kW\n50% more on scorched planets and in the Nether\nNo fuel needed");
        both("tooltip.universe.energy_exchanger.summary", "空的蓄电器放进去充满，满的放进去把电放回电网\n在界面里切换充电和放电，功率 45 kW",
                "Charges empty accumulators, or drains full ones back into the grid\nSwitch modes in the screen; 45 kW");
        both("tooltip.universe.negentropy_smelter.summary", "熔炉配方，速度 3 倍\n用电 2.88 kW", "Smelting recipes at 3× speed\nUses 2.88 kW");
        both("tooltip.universe.recomposing_assembler.summary", "制造台配方，速度 3 倍\n用电 2.7 kW", "Assembling recipes at 3× speed\nUses 2.7 kW");
        both("tooltip.universe.quantum_chemical_plant.summary", "化工配方，速度 2 倍\n用电 2.16 kW", "Chemical recipes at 2× speed\nUses 2.16 kW");
        both("tooltip.universe.self_evolution_lab.summary", "研究速度是矩阵研究站的 3 倍\n可以和矩阵研究站叠在一起，上面的从下面拿矩阵",
                "Researches 3× as fast as a matrix lab\nStacks with matrix labs; upper labs draw matrices from below");
        both("tooltip.universe.matrix_lab.summary", "研究全服当前的科技，几台一起研究就快几倍\n可以竖着叠，上面的从下面拿矩阵，只给最下面一台送料",
                "Researches the server's current tech; more labs, faster research\nStack them: upper labs draw matrices from below, so feed only the bottom one");
        both("tooltip.universe.storage_depot.summary", "30 格\n可以竖着叠：下面放满了往上放，从最下面一层就能取整叠",
                "30 slots\nStack them: overflow goes up, and the bottom one reaches the whole stack");
        both("tooltip.universe.foundation.summary", "铺地面用的建筑方块", "A building block for floors");
        both("tooltip.universe.proliferation", "增产点数 %s", "Proliferator points %s");

        // 界面
        both("gui.universe.proliferation.extra", "额外产出", "Extra products");
        both("gui.universe.proliferation.speedup", "生产加速", "Speedup");
        both("gui.universe.proliferation.speedup_only", "这个配方只能加速", "Speedup only");
        both("gui.universe.proliferation.bonus", "%s +%s", "%s +%s");
        both("gui.universe.tesla_tower.range_charge", "%s；给 %s 格内的外骨骼充能", "%s; charges exosuits within %s");
        both("gui.universe.geothermal.heat", "地热 %s%%", "Heat %s%%");
        both("gui.universe.geothermal.hint", "灼热星球和下界的地热更强", "Hotter on scorched planets and in the Nether");
        both("gui.universe.geothermal.no_lava", "正下方一格要是熔岩", "Needs lava directly below");
        both("gui.universe.energy_exchanger.energy", "当前蓄电器 %s / %s", "Current accumulator %s / %s");
        both("gui.universe.energy_exchanger.empty", "放入蓄电器", "Insert an accumulator");
        both("gui.universe.energy_exchanger.charge", "模式：充电", "Mode: charge");
        both("gui.universe.energy_exchanger.discharge", "模式：放电", "Mode: discharge");
        both("gui.universe.traffic_monitor.count", "最近一分钟 %s 个", "Last minute: %s");
        both("gui.universe.traffic_monitor.all", "统计所有物品", "Counting all items");
        both("gui.universe.traffic_monitor.only", "只统计 %s", "Counting only %s");
        both("gui.universe.traffic_monitor.no_threshold", "没设报警下限", "No alarm floor set");
        both("gui.universe.traffic_monitor.alarm", "低于下限 %s，报警中", "Below %s, alarm on");
        both("gui.universe.traffic_monitor.warming", "下限 %s，开机满一分钟后开始判断", "Floor %s, checking after one minute");
        both("gui.universe.traffic_monitor.ok", "下限 %s，流量正常", "Floor %s, flow OK");
        both("gui.universe.star_map.surveyed", "远程测绘：没去过，资料来自宇宙探索", "Remote survey: not visited, data from Universe Exploration");
        both("gui.universe.star_map.resources", "资源：%s", "Resources: %s");

        // 右键的提示
        both("message.universe.spray_coater.empty", "没有增产剂，拿着增产剂右键装填", "Empty. Right-click with a proliferator to load it");
        both("message.universe.spray_coater.status", "%s，还能喷 %s 个", "%s, %s sprays left");
        both("message.universe.traffic_monitor.filter", "只统计 %s", "Counting only %s");
        both("message.universe.traffic_monitor.no_filter", "统计所有物品", "Counting all items");
        both("message.universe.splitter.filter", "过滤 %s：只走优先出口，别的走其他出口", "Filter %s: priority output only, everything else elsewhere");
        both("message.universe.splitter.no_filter", "已清除过滤", "Filter cleared");
        both("message.universe.splitter.priority", "优先出口：%s", "Priority output: %s");
        both("message.universe.splitter.no_priority", "已取消优先出口", "Priority output cleared");
        both("message.universe.direction.north", "北", "north");
        both("message.universe.direction.east", "东", "east");
        both("message.universe.direction.south", "南", "south");
        both("message.universe.direction.west", "西", "west");

        // 科技和升级
        tech("proliferator_mk1", "增产剂 Mk.I", "Proliferator Mk.I");
        tech("proliferator_mk2", "增产剂 Mk.II", "Proliferator Mk.II");
        tech("proliferator_mk3", "增产剂 Mk.III", "Proliferator Mk.III");
        tech("integrated_logistics", "集装物流系统", "Integrated Logistics System");
        tech("pile_sorting", "集装分拣", "Pile Sorting");
        tech("wireless_power", "无线输电", "Wireless Power Transmission");
        tech("geothermal_power", "地热开采", "Geothermal Extraction");
        tech("satellite_power", "卫星配电系统", "Satellite Power Distribution");
        tech("energy_exchange", "能量枢纽", "Energy Exchanger");
        tech("negentropy_smelting", "负熵熔炼", "Negentropy Smelting");
        tech("recomposing_assembly", "重组式制造", "Re-composing Assembly");
        tech("quantum_chemistry", "量子化工", "Quantum Chemical Engineering");
        tech("self_evolution", "自演化研究", "Self-evolution Research");
        both("tech.universe.belt_stacking", "货物集装", "Cargo Stacking");
        both("upgrade.universe.belt_stacking.effect", "一摞最多 %s 个", "Piles up to %s");
        both("tech.universe.universe_exploration", "宇宙探索", "Universe Exploration");
        both("upgrade.universe.universe_exploration.effect.0", "星图只看得到去过的星系", "The star map shows only visited systems");
        both("upgrade.universe.universe_exploration.effect.1", "星图上看得到去过的星系里每颗行星的资源", "The star map shows resources in visited systems");
        both("upgrade.universe.universe_exploration.effect.2", "看得到去过的星系周围 %s 光年内的行星", "Shows planets within %s ly of visited systems");
        both("upgrade.universe.universe_exploration.effect.3", "看得到去过的星系周围 %2$s 光年内的行星和资源", "Shows planets and resources within %2$s ly of visited systems");
        both("upgrade.universe.universe_exploration.effect.4", "看得到整个银河的行星和资源", "Shows planets and resources across the galaxy");
    }

    /** 科技名称和研究的提示。 */
    /** 机甲、建造无人机、物流配送器（ADR-0023）。 */
    private void mecha() {
        item(UniverseItems.LOGISTICS_BOT.get(), "配送机器人", "Logistics Bot");
        item(UniverseItems.CONSTRUCTION_DRONE.get(), "建造无人机", "Construction Drone");
        block(UniverseBlocks.LOGISTICS_DISTRIBUTOR.get(), "物流配送器", "Logistics Distributor");
        both("tooltip.universe.logistics_distributor.summary", "放在箱子或储物仓顶上，管一种物品\n给附近机甲的物流栏位送货、收货，也和别的配送器互通\n范围 64 格，充电最多 9 kW",
                "Sits on a chest or storage depot and handles one item\nFills and empties nearby mechas' logistics slots, and trades with other distributors\nRange 64; charges at up to 9 kW");
        both("tooltip.universe.logistics_bot.summary", "放进物流配送器，一台最多 10 个", "Goes in a logistics distributor, up to 10 each");
        both("tooltip.universe.construction_drone.summary", "机甲自带，研究「通讯控制」增加数量", "Built into the mecha; Communication Control adds more");
        both("key.universe.mecha", "机甲面板", "Mecha Panel");

        // 科技
        both("tech.universe.distribution_logistics", "配送物流系统", "Distribution Logistics");
        both("tech.universe.mecha_core", "机甲核心", "Mecha Core");
        both("tech.universe.energy_circuit", "能量回路", "Energy Circuit");
        both("tech.universe.drive_engine", "驱动引擎", "Drive Engine");
        both("tech.universe.mechanical_frame", "机械骨骼", "Mechanical Frame");
        both("tech.universe.communication_control", "通讯控制", "Communication Control");
        both("tech.universe.drone_engine", "无人机引擎", "Drone Engine");
        both("tech.universe.mass_construction", "批量建造", "Mass Construction");
        both("upgrade.universe.mecha_core.effect", "核心容量 %s", "Core capacity %s");
        both("upgrade.universe.energy_circuit.effect", "燃烧室功率 %s", "Fuel chamber %s");
        both("upgrade.universe.drive_engine.effect", "能持续飞行，速度 ×%s", "Sustained flight at ×%s");
        both("upgrade.universe.mechanical_frame.effect", "行走速度 +%s%%", "Walk speed +%s%%");
        both("upgrade.universe.communication_control.effect", "%s 架建造无人机，每趟 %s 格", "%s construction drones, %s blocks a trip");
        both("upgrade.universe.drone_engine.effect", "无人机 %s m/s", "Drones fly at %s m/s");
        both("upgrade.universe.mass_construction.effect", "一张蓝图最多 %s 个方块", "Blueprints up to %s blocks");

        // 机甲面板
        both("gui.universe.mecha.title", "机甲", "Mecha");
        both("gui.universe.mecha.core", "能量核心", "Energy Core");
        both("gui.universe.mecha.energy", "%s / %s", "%s / %s");
        both("gui.universe.mecha.flow", "充 %s · 耗 %s", "In %s · out %s");
        both("gui.universe.mecha.core.hint", "飞行、加快行走、建造无人机都用这里的电\n燃烧室烧燃料充电，无线输电塔旁边也能充",
                "Flight, faster walking and drones run on this\nCharged by the fuel chamber, or near a wireless power tower");
        both("gui.universe.mecha.chamber", "燃烧室 %s", "Fuel %s");
        both("gui.universe.mecha.refuel.on", "自动加燃料：开", "Refuel: on");
        both("gui.universe.mecha.refuel.off", "自动加燃料：关", "Refuel: off");
        both("gui.universe.mecha.refuel.hint", "烧完了从背包里拿同一种燃料", "Takes the same fuel from your inventory when it runs out");
        both("gui.universe.mecha.no_chest", "没穿外骨骼", "No exosuit");
        both("gui.universe.mecha.speed", "行走 +%s%%", "Walk +%s%%");
        both("gui.universe.mecha.flight", "飞行 ×%s", "Flight ×%s");
        both("gui.universe.mecha.no_flight", "还不能飞", "Can't fly yet");
        both("gui.universe.mecha.drones", "无人机 %s 架 · %s m/s", "Drones %s · %s m/s");
        both("gui.universe.mecha.tasks", "每趟 %s 格", "%s blocks a trip");
        both("gui.universe.mecha.sites", "工地还差 %s 格", "Sites: %s left");
        both("gui.universe.mecha.no_sites", "没有工地", "No sites");
        both("gui.universe.mecha.missing", "缺 %s", "Missing %s");
        both("gui.universe.mecha.logistics", "物流栏位", "Logistics Slots");
        both("gui.universe.mecha.logistics.locked", "研究「配送物流系统」后可用", "Research Distribution Logistics to use");
        both("gui.universe.mecha.logistics.hint", "拿着物品点格子设定，空手点选中，右键清掉", "Click with an item to set; empty hand selects; right-click clears");
        both("gui.universe.mecha.carried", "身上 %s 个", "Carrying %s");
        both("gui.universe.mecha.bounds", "少于 %s 送来，多于 %s 收走", "Delivered below %s, collected above %s");
        both("gui.universe.mecha.min", "下限", "Min");
        both("gui.universe.mecha.max", "上限", "Max");
        both("gui.universe.mecha.limit_hint", "点一下 ±16，Shift ±64，滚轮 ±1", "Click ±16, Shift ±64, scroll ±1");
        both("gui.universe.mecha.cancel", "撤掉工地", "Clear Sites");
        both("gui.universe.mecha.cancel.hint", "撤掉你在这个维度里还没建完的工地", "Removes your unfinished sites in this dimension");

        // 状态栏和提示
        both("hud.universe.suit.energy", "能量", "Energy");
        both("hud.universe.suit.flying", "飞行中", "Flying");
        both("hud.universe.suit.build", "工地还差 %s 格", "%s blocks to build");
        both("hud.universe.suit.build_missing", "缺 %s", "Missing %s");
        both("hud.universe.suit.warning.energy", "能量不足", "Low energy");
        both("hud.universe.mecha.cutout", "机甲没电了", "Mecha out of energy");
        both("message.universe.blueprint.site", "工地开工，%s 个方块", "Site started: %s blocks");
        both("message.universe.blueprint.need_chest", "穿上外骨骼，无人机才会出来", "Wear an exosuit to send out drones");
        both("message.universe.blueprint.already_built", "这里已经建好了", "Already built");
        both("message.universe.blueprint.over_limit", "这张蓝图 %s 个方块，批量建造现在最多 %s 个", "%s blocks; Mass Construction allows %s");
        both("message.universe.blueprint.cancelled", "撤掉了 %s 处工地", "Cleared %s sites");

        // 物流配送器
        both("gui.universe.distributor.no_item", "没设物品", "No item set");
        both("gui.universe.distributor.item_hint", "拿着物品点这里设定，右键清掉", "Click with an item to set; right-click clears");
        both("gui.universe.distributor.battery", "电池 %s / %s", "Battery %s / %s");
        both("gui.universe.distributor.bots", "机器人 %s 架空闲，共 %s 架", "Bots: %s idle of %s");
        both("gui.universe.distributor.bot_hint", "放配送机器人，最多 %s 个", "Logistics bots, up to %s");
        both("gui.universe.distributor.icarus.none", "机甲：不管", "Mecha: off");
        both("gui.universe.distributor.icarus.supply", "机甲：供应", "Mecha: supply");
        both("gui.universe.distributor.icarus.collect", "机甲：回收", "Mecha: collect");
        both("gui.universe.distributor.icarus.both", "机甲：供应和回收", "Mecha: both");
        both("gui.universe.distributor.icarus_hint.none", "不给机甲送货，也不收", "Ignores mecha logistics slots");
        both("gui.universe.distributor.icarus_hint.supply", "机甲身上少于下限时送过去", "Delivers when a mecha drops below its minimum");
        both("gui.universe.distributor.icarus_hint.collect", "机甲身上多于上限时收回来", "Collects when a mecha goes over its maximum");
        both("gui.universe.distributor.icarus_hint.both", "少了送去，多了收回", "Delivers below the minimum, collects above the maximum");
        both("gui.universe.distributor.peer.none", "配送器：不管", "Peers: off");
        both("gui.universe.distributor.peer.supply", "配送器：供应", "Peers: supply");
        both("gui.universe.distributor.peer.demand", "配送器：需求", "Peers: demand");
        both("gui.universe.distributor.peer_hint.none", "不和别的配送器来往", "Ignores other distributors");
        both("gui.universe.distributor.peer_hint.supply", "需求同一种物品的配送器会派机器人来取", "Distributors demanding this item send bots to fetch it");
        both("gui.universe.distributor.peer_hint.demand", "派机器人去供应的配送器取货，放进下面", "Sends bots to supplying distributors and stores the haul below");
        both("gui.universe.distributor.storage", "下面有 %s 个，还放得下 %s 个", "%s stored, room for %s");
        both("gui.universe.distributor.no_storage", "下面没有容器", "No container below");

        // 手册
        both("guide.universe.survival.core.title", "机甲的能量核心", "Mecha Energy Core");
        both("guide.universe.survival.core",
                "穿上外骨骼就有了机甲的能量核心。飞行、加快行走、建造无人机都用核心的电，生命维持另算。按 B 打开机甲面板。\n\n核心：120 kJ，研究「机甲核心」加大\n燃烧室：800 W，研究「能量回路」加大\n燃烧室里放燃料，自动加燃料打开时烧完了从背包里拿同一种\n无线输电塔旁边也能充，最多 15 kW",
                "An exosuit carries the mecha's energy core. Flight, faster walking and drones draw on it; life support does not. Press B for the mecha panel.\n\nCore: 120 kJ, more with Mecha Core\nFuel chamber: 800 W, more with Energy Circuit\nPut fuel in the chamber; with auto refuel on it takes more of the same from your inventory\nWireless power towers also charge it, up to 15 kW");
        both("guide.universe.survival.flight.title", "机甲飞行", "Mecha Flight");
        both("guide.universe.survival.flight",
                "研究「驱动引擎」后，跳起来按两下空格就能一直飞，和创造模式一样。没电时会慢慢落下来，不摔伤。太空里不能这样飞。\n\n悬停：1.2 kW，每 1 m/s 再加 100 W\n驱动引擎 1–4 级：速度 ×1、×1.3、×1.6、×2\n机械骨骼：前 6 级每级行走 +10%，后 2 级每级 +20%，多出来的速度才耗电",
                "With Drive Engine you can fly like in creative: double-tap jump. Run out of energy and you drift down without fall damage. This flight does not work in space.\n\nHover: 1.2 kW, plus 100 W per m/s\nDrive Engine 1-4: speed x1, x1.3, x1.6, x2\nMechanical Frame: walking +10% per level for 6 levels, then +20% for 2; only the extra speed costs energy");
        both("guide.universe.logistics.drones.title", "建造无人机", "Construction Drones");
        both("guide.universe.logistics.drones",
                "生存模式放下蓝图是一片蓝色的工地。机甲的建造无人机从低处、近处开始，一格格建起来，材料从背包和货舱里扣。自己放对了方块也算建好。\n\n范围：64 格\n无人机：3 架，6 m/s，每趟 4 格；研究「通讯控制」「无人机引擎」加多加快\n一张蓝图的上限：300 格，研究「批量建造」放宽\n缺材料时状态栏会写缺什么；机甲面板里可以撤掉工地",
                "In survival a blueprint places a blue site. Your mecha's drones build it from the bottom up, nearest first, using materials from inventory and cargo. Placing the right block yourself counts too.\n\nRange: 64 blocks\nDrones: 3 at 6 m/s, 4 blocks a trip; more and faster with Communication Control and Drone Engine\nBlueprint size: 300 blocks, more with Mass Construction\nThe HUD shows what is missing; the mecha panel can clear sites");
        both("guide.universe.logistics.distributor.title", "物流配送器", "Logistics Distributor");
        both("guide.universe.logistics.distributor",
                "配送器放在箱子或储物仓顶上，只管一种物品，派机器人给附近的机甲送货、收货，也在配送器之间搬。\n\n范围：水平 64 格，一叠储物仓算一个容器\n机器人：最多 10 个，每趟 8 个，6 m/s\n物流载具的升级对机器人一样有效\n电池：60 kJ，最多 9 kW 充电，每飞一格 20 J\n研究：配送物流系统",
                "Sits on a chest or depot and handles one item. Bots supply and collect for nearby mechas and other distributors.\n\nRange: 64 blocks\nBots: 10 max, 8 items a trip at 6 m/s\nBattery: 60 kJ, 20 J per block flown\nResearch: Distribution Logistics");
        both("guide.universe.logistics.slots.title", "物流栏位", "Logistics Slots");
        both("guide.universe.logistics.slots",
                "机甲面板右边有 12 个物流栏位，每格设一种物品和上下限。背包加货舱少于下限时，供应的配送器派机器人送来；多于上限时，回收的配送器来收走（带附加数据的不收）。\n\n上下限：0–9999\n机甲模式：不管、供应、回收、供应和回收\n配送器之间：需求的从供应的那里取货",
                "The 12 slots on the right of the mecha panel each hold an item with a min and max. Below the min (inventory plus cargo), a supplying distributor sends bots; above the max, a collecting one takes the surplus, plain items only.\n\nLimits: 0-9999\nMecha modes: off, supply, collect, both\nBetween distributors: demand fetches from supply");
    }

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

    /** 手册（ADR-0018）：章名、每页的标题和正文。页里嵌的物品在这里查一遍注册名。 */
    private void guide() {
        both("guide.universe.start", "开局", "Getting Started");
        both("guide.universe.power", "电力", "Power");
        both("guide.universe.gathering", "采集和冶炼", "Mining");
        both("guide.universe.research", "研究", "Research");
        both("guide.universe.logistics", "传送带和物流", "Logistics");
        both("guide.universe.ship", "飞船", "Ships");
        both("guide.universe.survival", "星球生存", "Planet Survival");
        both("guide.universe.exploration", "探索", "Exploration");
        both("guide.universe.trade", "太空和贸易", "Space & Trade");
        both("guide.universe.husbandry", "驯养和探索车", "Creatures");
        both("guide.universe.dyson", "戴森球", "Dyson Sphere");
        both("guide.universe.endgame", "终局", "Endgame");
        both("guide.universe.log", "日志", "Logs");
        both("guide.universe.ending", "结局", "Ending");
        both("guide.universe.start.crash.title", "坠毁点", "The Crash Site");
        both("guide.universe.start.crash",
                "坠毁点在出生点 60 到 110 格外，第一次进入世界时生成，聊天栏会提示烟柱的方向。\n\n箱子里有：\n损坏的多功能工具\n探测器日志和这本手册\n铜锭、铁锭、红石\n氧气罐、防护电池各 2 个",
                "The crash site is 60 to 110 blocks from spawn and appears the first time the world loads. Chat tells you which way the smoke is.\n\nThe chest holds:\nDamaged multitool\nProbe log and this guide\nCopper, iron and redstone\n2 oxygen capsules, 2 hazard cells");
        both("guide.universe.start.multitool.title", "多功能工具", "The Multitool");
        both("guide.universe.start.multitool",
                "多功能工具是开局的主要工具，能当镐、锹、斧用。损坏的那把加铜锭、红石和铁锭就能修好，箱子里的材料正好够。\n没有坠毁点的玩家可以用钻石从头做一把。\n\n挖掘等级：铁\n挖掘速度：6",
                "The multitool is your main early tool: pickaxe, shovel and axe in one. Repair the damaged one with copper, redstone and iron; the crash chest has exactly enough.\nWithout a crash site, craft one from scratch with a diamond.\n\nMining tier: iron\nDig speed: 6");
        both("guide.universe.start.laser.title", "激光和扫描", "Laser and Scanner");
        both("guide.universe.start.laser",
                "多功能工具还带采矿激光、扫描器和等离子枪。\n\n按住右键：采矿激光\n潜行右键：扫描\nG：切换采矿和战斗模式\n\n激光：射程 10 格，比手挖快一半\n过热：连续 4 秒，冷却 2 秒\n扫描：矿脉 32 格，兴趣点 200 格，冷却 5 秒\n等离子弹：每发 5 点伤害\n\n在星球上开激光会引来哨兵。",
                "The multitool also carries a mining laser, a scanner and a plasma gun.\n\nHold right-click: mining laser\nSneak + right-click: scan\nG: switch mining and combat mode\n\nLaser: 10 blocks, 1.5x dig speed\nOverheat: after 4 s, cools for 2 s\nScan: veins 32, points of interest 200, 5 s cooldown\nPlasma bolt: 5 damage\n\nLasering on planets draws Sentinels.");
        both("guide.universe.start.suit.title", "外骨骼", "The Exosuit");
        both("guide.universe.start.suit",
                "外骨骼四件都用铁和铜做。头盔加胸甲才算密封。\n件数越少防护掉得越快：三件 ×2，两件 ×3.5，一件 ×6。\n\n氧气：密封 10 分钟，不密封 15 秒\n危害防护：全套 5 分钟\n主世界、空间站、飞船里不消耗",
                "All four pieces use iron and copper. Helmet plus chestplate seals the suit.\nFewer pieces drain faster: x2 with three, x3.5 with two, x6 with one.\n\nOxygen: 10 min sealed, 15 s unsealed\nHazard protection: 5 min, full set\nNo drain in the Overworld, stations or ships");
        both("guide.universe.power.grid.title", "电网", "The Grid");
        both("guide.universe.power.grid",
                "电力感应塔给周围的设备供电，塔和塔之间自动连成电网，不用拉线。\n发电不够时先用蓄电器里的电，还不够就所有设备一起按比例减速。\n\n供电半径：6 格\n连接距离：12 格\n右键电塔看发电、用电和满足率，O 键的生产统计里也有电力一栏。",
                "Tesla towers power nearby devices and link to each other into a grid. No wires.\nWhen power runs short, accumulators discharge first, then all machines slow down evenly.\n\nPower radius: 6 blocks\nLink distance: 12 blocks\nRight-click a tower to see its numbers. The stats screen (O) has a power panel.");
        both("guide.universe.power.generators.title", "发电", "Generators");
        both("guide.universe.power.generators",
                "风力涡轮机\n300 W × 风力系数。越高风越大，下雨 ×1.2，打雷 ×1.5，没有大气不转。\n\n太阳能板\n360 W，要露天。按太阳高度和恒星亮度算，晚上为 0。\n\n火力发电厂\n最多 2.16 kW，烧原版燃料，用多少烧多少。一块煤满负荷烧 11 秒。",
                "Wind Turbine\n300 W x wind factor. Higher is windier; rain x1.2, thunder x1.5. Nothing on airless planets.\n\nSolar Panel\n360 W with open sky, scaled by sun height and star brightness. Zero at night.\n\nThermal Power Plant\nUp to 2.16 kW from vanilla fuel, burning only what the grid draws. Coal lasts 11 s at full load.");
        both("guide.universe.power.storage.title", "蓄电器", "Accumulators");
        both("guide.universe.power.storage",
                "蓄电器在电网有富余时充电，发电不够时放电。拆下来电量还在。\n\n容量：180 kJ\n充放电：最多 2.7 kW\n研究：能量储存",
                "Accumulators charge from surplus and discharge when generation falls short. They keep their charge when broken.\n\nCapacity: 180 kJ\nRate: up to 2.7 kW\nResearch: Energy Storage");
        both("guide.universe.power.towers.title", "输电塔", "Power Towers");
        both("guide.universe.power.towers",
                "三种塔都能连成同一个电网，只是范围不同。\n\n电力感应塔：供电 6 格，连接 12 格\n无线输电塔：供电 4 格，连接 10 格；给 5 格内穿外骨骼的玩家补氧、给危害防护充能，充能时用电 1.5 kW\n卫星配电站：供电 20 格，连接 40 格\n\n研究：无线输电、卫星配电系统",
                "All three towers join the same grid; only their reach differs.\n\nTesla tower: power 6, link 12\nWireless power tower: power 4, link 10. Refills oxygen and hazard protection for exosuits within 5 blocks, drawing 1.5 kW while charging\nSatellite substation: power 20, link 40\n\nResearch: Wireless Power, Satellite Power Distribution");
        both("guide.universe.power.geothermal.title", "地热发电站", "Geothermal Power");
        both("guide.universe.power.geothermal",
                "地热发电站放在熔岩上，正下方一格是熔岩才发电。不烧燃料，电网用多少发多少。\n\n最大：4.8 kW\n灼热星球和下界：7.2 kW\n研究：地热开采",
                "Place it over lava; the block directly below must be lava. No fuel, and it only produces what the grid draws.\n\nMax: 4.8 kW\nScorched planets and the Nether: 7.2 kW\nResearch: Geothermal Extraction");
        both("guide.universe.power.exchanger.title", "能量枢纽", "Energy Exchanger");
        both("guide.universe.power.exchanger",
                "能量枢纽给蓄电器（物品）充电，或者把满的蓄电器里的电放回电网，用来把电带去没有电网的地方。\n界面里切换充电和放电。和模式对不上的蓄电器直接挪到右边。\n\n功率：45 kW\n充满一个：4 秒\n研究：能量枢纽",
                "The exchanger charges accumulator items, or drains full ones back into the grid, so you can carry power to a site with no generators.\nSwitch modes in its screen. Accumulators that don't match the mode go straight to the output.\n\nRate: 45 kW\nOne accumulator: 4 s\nResearch: Energy Exchanger");
        both("guide.universe.research.stacking.title", "叠研究站", "Stacking Labs");
        both("guide.universe.research.stacking",
                "研究站可以竖着叠。上面那台每秒从正下方那台拿矩阵，两台的数量拉平，只给最下面一台送料就行。喷过增产剂的矩阵点数跟着走。\n\n自演化研究站：研究速度 3 倍，1.92 kW，能和矩阵研究站叠在一起\n研究：自演化研究",
                "Labs stack. Each second a lab takes matrices from the one below until both hold the same, so feed only the bottom one. Sprayed points carry over.\n\nSelf-evolution Lab: 3x speed, 1.92 kW, stacks with matrix labs\nResearch: Self-evolution Research");
        both("guide.universe.logistics.splitter.title", "分流器和叠仓", "Splitters and Stacked Storage");
        both("guide.universe.logistics.splitter",
                "空手右键分流器的一边，把它设成优先出口，再点一次取消。货物先走优先出口，堵了才分给别的出口。\n设了优先出口以后，拿着物品右键设过滤：这种物品只走优先出口，别的物品走其他出口。潜行空手右键清除过滤。\n\n储物仓可以竖着叠。传送带送进来的东西放满了往上一层放；分拣器从最下面一层就能存取整叠。",
                "Right-click a side of a splitter empty-handed to make it the priority output; click again to clear. Cargo takes the priority side first and spills to the others when it backs up.\nWith a priority set, right-click with an item to filter: that item goes only to the priority side, everything else to the rest. Sneak + empty hand clears the filter.\n\nStorage depots stack. Belt input fills upward, and a sorter on the bottom depot reaches the whole stack.");
        both("guide.universe.logistics.proliferator.title", "增产剂", "Proliferators");
        both("guide.universe.logistics.proliferator",
                "喷涂机串在传送带上，给经过的货物喷增产剂。拿着增产剂右键装填，空手右键看还剩多少。\n设备用喷过的原料时，在界面里选：额外产出（多出产物）或者生产加速。两种都更费电。原料点数不齐时按最少的算。\n\nMk.I：1 点，一个喷 12 个，产物 +12.5% 或速度 +25%，耗电 +30%\nMk.II：2 点，喷 24 个，+20% 或 +50%，耗电 +70%\nMk.III：4 点，喷 60 个，+25% 或 +100%，耗电 +150%\n\n喷过的燃料多发电，喷过的矩阵多出研究点数。",
                "Spray coaters sit in a belt line and spray passing cargo. Right-click with a proliferator to load, empty-handed to check.\nMachines fed sprayed ingredients offer extra products or speedup in their screen, at a power cost. Mixed batches count the lowest level.\n\nPoints, sprays, extra / speed, power:\nMk.I: 1, 12, +12.5% / +25%, +30%\nMk.II: 2, 24, +20% / +50%, +70%\nMk.III: 4, 60, +25% / +100%, +150%\nSprayed fuel burns longer; sprayed matrices give extra research.");
        both("guide.universe.logistics.piling.title", "集装", "Piling");
        both("guide.universe.logistics.piling",
                "自动集装机把传送带上同一种货物叠成一摞，一格传送带能运好几个。凑不齐时最多等 1 秒。集装分拣器一次搬一整摞，每秒 12 次。\n一摞几个看升级科技「货物集装」：开始 2 个，最多 4 个。\n\n流速监测器数最近一分钟流过多少个。右键设报警下限，拿着物品右键只数这一种。流量低于下限时亮红灯，四面发出红石信号；开机满一分钟才开始判断。\n\n研究：集装物流系统、集装分拣",
                "The automatic piler stacks matching cargo so one belt slot carries several. It waits up to 1 s for a full pile. The pile sorter moves whole piles, 12 a second.\nPile size comes from the Cargo Stacking upgrade: 2 at first, up to 4.\n\nThe traffic monitor counts items over the last minute. Right-click to set an alarm floor, or right-click with an item to count only that. Below the floor it lights red and emits redstone; it starts checking after a minute.\n\nResearch: Integrated Logistics, Pile Sorting");
        both("guide.universe.endgame.advanced.title", "高级设备", "Advanced Machines");
        both("guide.universe.endgame.advanced",
                "有了绿矩阵就能研究四台高级设备，配方和低一级的一样，只是快得多。\n\n负熵熔炉：速度 3，2.88 kW\n重组式制造台：速度 3，2.7 kW\n量子化工厂：速度 2，2.16 kW\n自演化研究站：研究速度 3 倍，1.92 kW",
                "Green matrices unlock four advanced machines. They run the same recipes as the machines below them, only faster.\n\nNegentropy Smelter: speed 3, 2.88 kW\nRe-composing Assembler: speed 3, 2.7 kW\nQuantum Chemical Plant: speed 2, 2.16 kW\nSelf-evolution Lab: 3x research, 1.92 kW");
        both("guide.universe.gathering.mining.title", "采矿机", "Mining Machine");
        both("guide.universe.gathering.mining",
                "采矿机开采下方的矿，从正面出货。范围里有几种矿时，只挖数量最多的那种。\n\n范围：5×5，深 3 层\n产量：每块矿 40 秒 1 个\n耗电：420 W\n研究：电磁学",
                "The mining machine mines ore below it and outputs from its front. With several ores in range it mines only the most common one.\n\nArea: 5x5, 3 layers deep\nRate: 1 item per ore block every 40 s\nPower: 420 W\nResearch: Electromagnetism");
        both("guide.universe.gathering.veins.title", "矿脉", "Ore Veins");
        both("guide.universe.gathering.veins",
                "每块矿有一定储量，挖完变回石头。\n\n储量：400 到 1200，再乘星球丰度 0.5 到 1.5\n主世界：地表有铁、铜、煤的露头，少量金矿和原油涌泉\n其他星球：钛、硅、硫、可燃冰、光学晶体、铀等，看星球类型\n\n用多功能工具扫描找矿最快。",
                "Each ore block holds a set amount and turns back into stone when empty.\n\nReserve: 400 to 1200, times planet abundance 0.5 to 1.5\nOverworld: surface outcrops of iron, copper and coal, some gold and crude oil seeps\nOther planets: titanium, silicon, sulfur, fire ice, optical crystal, uranium and more, by planet type\n\nA multitool scan finds veins fastest.");
        both("guide.universe.gathering.smelting.title", "电弧熔炉", "Arc Smelter");
        both("guide.universe.gathering.smelting",
                "电弧熔炉用电炼东西，有三个原料格。粗矿炼成锭，铁锭炼成钢，石墨、高纯硅、钛锭和玻璃也在这里炼。\n用分拣器送料时只会送配方要的东西。\n\n耗电：360 W\n粗矿 → 锭：1 秒\n3 铁锭 → 钢：3 秒（研究钢材冶炼）",
                "The arc smelter smelts with electricity and has three input slots: ingots, steel, graphite, silicon, titanium and glass.\nSorters feeding it only insert what the recipe needs.\n\nPower: 360 W\nRaw ore to ingot: 1 s\n3 iron ingots to steel: 3 s (Steel Smelting)");
        both("guide.universe.gathering.fluids.title", "原油和水", "Oil and Water");
        both("guide.universe.gathering.fluids",
                "原油萃取站\n两格高，下半截放在原油涌泉上。每秒 0.5 到 1.5 原油，越抽越慢，最低三成。耗电 840 W。\n\n抽水站\n放在水源上，每分钟 50 水，耗电 300 W。酸海星球上不能用。",
                "Oil Extractor\nTwo blocks tall, lower half on a crude oil seep. 0.5 to 1.5 crude oil per second, slowing as it drains, down to 30%. Power 840 W.\n\nWater Pump\nSits on a water source. 50 water a minute for 300 W. Not on acid-ocean planets.");
        both("guide.universe.research.hand_in.title", "上交科技", "Hand-in Research");
        both("guide.universe.research.hand_in",
                "J 键打开科技树。开头几项不用研究站，在科技树里直接上交材料。研究是全服共用的。\n\n电磁学：10 磁线圈、10 电路板，解锁电力感应塔、风力涡轮机、采矿机\n自动化冶金：20 铁锭、10 磁线圈，解锁电弧熔炉\n基础制造工艺：20 电路板、20 齿轮，解锁制造台\n电磁矩阵：10 电路板、10 磁线圈，解锁研究站",
                "Press J for the tech tree. Early techs take a hand-in instead of a lab. Research is shared server-wide.\n\nElectromagnetism: 10 coils, 10 boards. Tesla tower, wind turbine, mining machine\nAutomatic Metallurgy: 20 iron, 10 coils. Arc smelter\nBasic Assembling: 20 boards, 20 gears. Assembler\nElectromagnetic Matrix: 10 boards, 10 coils. Matrix lab");
        both("guide.universe.research.lab.title", "研究站", "Matrix Lab");
        both("guide.universe.research.lab",
                "矩阵研究站消耗矩阵产生研究点数。在科技树里选当前研究，研究站每 5 秒消耗所需的每种矩阵各一个，算一点。几台研究站的进度叠加。\n\n耗电：480 W\n自动送料：每格最多 10 个\n研究：电磁矩阵",
                "Matrix labs turn matrices into research points. Pick a current research in the tech tree; every 5 s a lab uses one of each required matrix for one point. Labs add up.\n\nPower: 480 W\nAutomation: up to 10 per slot\nResearch: Electromagnetic Matrix");
        both("guide.universe.research.matrices.title", "六种矩阵", "The Six Matrices");
        both("guide.universe.research.matrices",
                "六种矩阵都在制造台里做，越往后的科技要的种类越多。\n\n蓝：磁线圈 + 电路板\n红：2 高能石墨 + 2 氢\n黄：金刚石 + 钛晶石\n紫：2 处理器 + 粒子宽带\n绿：引力透镜 + 量子芯片，出 2 个\n白：复合矩阵 + 紫 + 绿 + 反物质",
                "All six matrices are made in assemblers; later techs need more colors.\n\nBlue: coil + circuit board\nRed: 2 energetic graphite + 2 hydrogen\nYellow: diamond + titanium crystal\nPurple: 2 processors + particle broadband\nGreen: graviton lens + quantum chip, makes 2\nWhite: composite + purple + green + antimatter");
        both("guide.universe.research.upgrades.title", "升级科技", "Upgrades");
        both("guide.universe.research.upgrades",
                "科技树最右边一列是升级科技。前几级用低档矩阵；下一级要的矩阵和这一级相同时，研究会自动接着做。\n\n矿脉利用：采矿 +10%，矿耗 ×0.94\n研究速度：+25%\n物流载具引擎：+25%\n射线传输效率：损耗 ×0.85\n物流载具容量：+10%\n太阳帆寿命：+15 分钟\n（都是每级）\n\n前四条最后用白矩阵研究，没有上限；容量和寿命各 6 级。",
                "The rightmost column of the tech tree holds the upgrades. Early levels use lower matrices; research continues by itself while the next level needs the same ones.\n\nVein Utilization: mining +10%, drain x0.94\nResearch Speed: +25%\nCarrier Engine: +25%\nRay Transmission: loss x0.85\nCarrier Capacity: +10%\nSolar Sail Life: +15 min\n(all per level)\n\nThe first four end in white-matrix levels with no cap; capacity and sail life have 6 levels each.");
        both("guide.universe.logistics.belts.title", "传送带", "Conveyor Belts");
        both("guide.universe.logistics.belts",
                "传送带沿你放置时面对的方向运东西。站在上面的生物和掉落物也会被带走。\n\n空手潜行右键：平、上坡、下坡\n空手右键：拿下物品\n速度：每秒 6 / 12 / 30 个",
                "Belts carry items the way you faced when placing them. Mobs and dropped items ride along.\n\nSneak + empty hand: flat, up, down\nEmpty hand: take items off\nSpeed: 6, 12 or 30 items/s by tier");
        both("guide.universe.logistics.sorters.title", "分拣器", "Sorters");
        both("guide.universe.logistics.sorters",
                "分拣器从背后的方块拿东西，放进前面的方块，只拿前面装得下的。\n\n拿着物品右键：设过滤\n空手右键：清除过滤\n速度：每秒 1.5 / 3 / 6 次\n四向分流器轮流分给各个出口，储物仓有 30 格。",
                "Sorters move items from the block behind to the block in front, only what fits.\n\nRight-click with item: set filter\nEmpty hand: clear filter\nSpeed: 1.5, 3 or 6 moves/s\nSplitters alternate between outputs. Storage Mk.I has 30 slots.");
        both("guide.universe.logistics.assembler.title", "制造台", "Assemblers");
        both("guide.universe.logistics.assembler",
                "制造台按选好的配方做东西。在界面里选配方；每个原料格只收对应的原料，换配方会退回原料。自动送料最多装 4 次的量。\n\nMk.I：速度 0.75，260 W\nMk.II：速度 1.0，540 W\nMk.III：速度 1.5，1.08 kW",
                "Assemblers craft the recipe picked in their screen. Each slot takes only its own ingredient, and automation fills up to 4 crafts.\n\nMk.I: speed 0.75, 260 W\nMk.II: speed 1.0, 540 W\nMk.III: speed 1.5, 1.08 kW");
        both("guide.universe.logistics.stations.title", "物流站", "Logistics Stations");
        both("guide.universe.logistics.stations",
                "物流站按每格的设置自动收发货。本地用无人机，在同一个维度里送；远程用运输船，在星球之间送。\n在界面里给每格设物品和上限，本地、远程各选仓储、供应或需求。站只用电网富余的电充电。\n\n行星物流站：4 格 × 5000，无人机范围默认 1000 格\n星际物流站：5 格 × 10000，加运输船；去别的星系每趟 2 个空间翘曲器",
                "Stations send and receive by each slot's settings. Drones serve one dimension; vessels travel between planets.\nSet each slot's item and limit, then storage, supply or demand for local and remote. Stations charge only from grid surplus.\n\nPlanetary: 4 slots x 5,000, drone range 1,000 by default\nInterstellar: 5 slots x 10,000 plus vessels; other systems cost 2 space warpers a trip");
        both("guide.universe.logistics.blueprint.title", "蓝图", "Blueprints");
        both("guide.universe.logistics.blueprint",
                "蓝图把一片建筑存下来，在别处照着放。配方、分拣器过滤和物流站设置一起复制，库存不复制。\n\n空白：右键两个方块框选（每边最多 32），潜行右键保存\n画好的：右键放下，R 转 90°，潜行右键清空\n放置距离：64 格\n放下的是工地：蓝色的虚影由机甲的建造无人机一格格建起来，材料从背包和外骨骼货舱里扣。创造模式直接放好。",
                "Blueprints copy builds with recipes, filters and station settings.\n\nBlank: mark two corners (32 max per side), sneak + right-click to save\nSaved: right-click to place, R rotates, sneak + right-click clears\nRange: 64 blocks\nSurvival: your drones build it\nCreative: placed at once");
        both("guide.universe.ship.build.title", "造船", "Building a Ship");
        both("guide.universe.ship.build",
                "飞船由方块拼成。右键驾驶台组装：和它连在一起的方块都算船上的，地形不算。\n\n必需：一个驾驶台（朝向是船头）、至少一个推进器\n可选：燃料舱、脉冲引擎、跃迁引擎、护盾、武器、座椅、船体板\n上限：4096 个方块\n起飞条件：推重比大于 1；一个推进器在 1 倍重力下推 12 吨",
                "Ships are built from blocks. Right-click the helm to assemble everything connected to it; terrain is left out.\n\nRequired: one helm (its facing is the bow), at least one thruster\nOptional: fuel tanks, impulse engines, warp drives, shields, weapons, seats, hull plating\nLimit: 4,096 blocks\nLift-off: thrust-to-weight above 1; one thruster lifts 12 t at 1 g");
        both("guide.universe.ship.fly.title", "驾驶", "Flying");
        both("guide.universe.ship.fly",
                "拿着燃料右键燃料舱加油，再右键驾驶台组装，组装好你就在驾驶位上。\n\nWASD：推进和转向\n空格 / 潜行：上升 / 下降\n疾跑：脉冲引擎（只在太空）\n左键：开火，G 换武器\n落地后按住潜行 1 秒：停船，变回方块\n\n进入太空：飞出世界顶部 48 格\n降落：飞进星球的大气层\n燃料：煤 200，煤块 1800，发射燃料 1000\n驾驶舱有氧气，防护会充满。",
                "Right-click a fuel tank with fuel, then assemble at the helm; you start in the pilot seat.\n\nWASD: thrust and turn\nSpace / Sneak: up / down\nSprint: impulse engine (space only)\nLeft-click: fire, G: switch weapon\nHold Sneak 1 s after landing: park as blocks\n\nTo space: 48 blocks above the world's top\nTo land: fly into a planet's atmosphere\nFuel: coal 200, coal block 1,800, launch fuel 1,000\nThe cabin has air and refills protection.");
        both("guide.universe.ship.warp.title", "跃迁", "Warping");
        both("guide.universe.ship.warp",
                "M 键打开星图，选一个星系，点跃迁。\n\n条件：在太空里开船，船上有跃迁引擎，背包里有空间翘曲器\n位置：离恒星 1200 格以外，离星球大气 150 格以外\n充能：3.5 秒，用掉 1 个空间翘曲器\n航程：5 光年，每台跃迁引擎 +5（Mk.II +10），最多算 4 台\n\n空间翘曲器在制造台里做，空间站也卖。",
                "Press M for the star map, pick a system and press Warp.\n\nNeeds: piloting in space, a warp drive aboard, a space warper in your inventory\nPosition: 1,200 blocks from the star, 150 from any atmosphere\nCharge: 3.5 s, uses 1 warper\nRange: 5 ly, +5 per warp drive (+10 for Mk.II), up to 4 drives\n\nSpace warpers come from assemblers or station shops.");
        both("guide.universe.ship.rating.title", "评级和技术", "Rating and Tech");
        both("guide.universe.ship.rating",
                "组装时按部件给船评级。开船时按 B，或者潜行右键停着的驾驶台，打开飞船面板。\n\n类型：战斗机、运输船、探索船、穿梭机，看炮、货舱、跃迁引擎各有多少\n档次：C、B、A、S，看 Mk.II 部件和合金船体板占多少\n专长加成：B +10%，A +20%，S +35%\n技术槽：C 3 格，B 4，A 5，S 6\n\n飞船模块：推进效率、脉冲调校、超空间增幅、偏导护盾、船体加固、火控校准",
                "Ships are rated when assembled. Press B while flying, or sneak + right-click a parked helm, for the ship panel.\n\nClass: fighter, hauler, explorer or shuttle, from guns, cargo and warp drives\nGrade: C, B, A, S, from Mk.II parts and alloy plating\nSpecialty bonus: B +10%, A +20%, S +35%\nTech slots: C 3, B 4, A 5, S 6\n\nShip modules: thrust efficiency, pulse tuning, hyperdrive booster, deflector, hull reinforcement, fire control");
        both("guide.universe.ship.drives.title", "驱动档次", "Drive Tiers");
        both("guide.universe.ship.drives",
                "有些恒星光靠跃迁引擎去不了，要在技术槽里装一台驱动，星图上写着缺哪种。高一档的驱动也能去下面几档。\n\n跃迁引擎：M、K、G、F 型\n镉驱动：红巨星\n翠金驱动：A 型白星、白矮星\n铟驱动：B、O 型蓝星、中子星、黑洞\n\n驱动研究以后在制造台里做。",
                "Some stars are out of reach with a plain warp drive. Fit a drive in a tech slot; the star map says which one. Each drive also covers the tiers below it.\n\nWarp drive: M, K, G, F stars\nCadmium drive: red giants\nEmeril drive: A-type white stars, white dwarfs\nIndium drive: B and O blue stars, neutron stars, black holes\n\nResearch a drive, then make it in an assembler.");
        both("guide.universe.ship.black_hole.title", "黑洞", "Black Holes");
        both("guide.universe.ship.black_hole",
                "六分之一的星系里有一个黑洞，离恒星 3 到 4.5 万格。自动驾驶列表里有它，太空 HUD 上写着方位。\n\n飞进视界 150 格内：不用翘曲器、不看驱动，被甩到星团里很远的一个星系\n代价：船体掉 30%，护盾清空\n\n星图上有黑洞的星系会写出来。",
                "One system in six has a black hole, 30,000 to 45,000 blocks from the star. It's in the autopilot list and on the space HUD.\n\nFly within 150 blocks of the horizon: no warper, no drive tier, and you're thrown to a far-off system\nCost: 30% hull, shields emptied\n\nThe star map marks systems with a black hole.");
        both("guide.universe.ship.dealer.title", "买船和召唤", "Buying and Summoning");
        both("guide.universe.ship.dealer",
                "空间站机库里的飞船交易终端每天挂 4 艘船，价钱按方块价值和档次算。买下的船停在起降台上，燃料是满的。自己的船停进机库能卖，给六成。\n\n召唤（N）：在地面或空间站里叫主力船过来，64 格内有着陆台就停在台上\n自动驾驶（太空里按 N）：选星球、空间站、货船或信号，动方向键退出\n着陆台：接电，给台上 4 格内的船加燃料\n坠毁的飞船：拿着 4 个钛锭和 2 个电路板右键损坏的驾驶台",
                "Each station hangar has a ship dealer with 4 ships a day, priced by blocks and grade. Bought ships wait fueled on the pad. Ships you park in the hangar sell for 60%.\n\nSummon (N): call your main ship on the ground or in a station; it lands on a pad within 64 blocks\nAutopilot (N in space): pick a planet, station, freighter or signal; steer to cancel\nLanding pad: powered; refuels ships up to 4 blocks above it\nCrashed ships: right-click the damaged helm with 4 titanium ingots and 2 circuit boards");
        both("guide.universe.ship.freighter.title", "货船", "Freighters");
        both("guide.universe.ship.freighter",
                "货船一人一艘：救下遇险的货船，或者在飞船交易终端买（12 万信用点起）。飞进左舷的机库口就到了船里，里面是一大片随你建的甲板。\n\n舰桥控制台：带着整艘船跃迁，用 3 个翘曲器，航程 C 40、B 45、A 50、S 60 光年\n召唤货船：太空里按 N 选它，停到船头前 220 格，冷却 5 分钟\n\n从甲板边上掉下去会被拉回机库。床能设重生点。",
                "One freighter per player: rescue one in distress or buy one from a ship dealer (from 120,000 credits). Fly into the port-side hangar to go aboard; inside is a big deck to build on.\n\nBridge console: warp the whole ship for 3 warpers; range C 40, B 45, A 50, S 60 ly\nSummon freighter: press N in space and pick it; it arrives 220 blocks ahead, 5 min cooldown\n\nFall off the deck and you're pulled back to the hangar. Beds set your spawn.");
        both("guide.universe.ship.fleet.title", "护卫舰和远征", "Frigates and Expeditions");
        both("guide.universe.ship.fleet",
                "有货船以后能在飞船交易终端雇护卫舰，最多 12 艘。舰队指挥室的终端每天给 3 个远征。\n\n种类：战斗、探索、工业、贸易、支援\n远征：1–5 星，10–40 分钟，最多派 5 艘，付发射燃料\n每段按队伍那一项属性判定，失败让一艘受损，再损就沉\n奖励：信用点、材料、模块，偶尔有传送门符文\n\n每出 4 次远征升一级。",
                "With a freighter you can hire frigates at a ship dealer, up to 12. The fleet terminal offers 3 expeditions a day.\n\nTypes: combat, exploration, industry, trade, support\nExpeditions: 1–5 stars, 10–40 min, up to 5 frigates, paid in launch fuel\nEach leg checks the team's stat; a failure damages a frigate, a second one sinks it\nRewards: credits, materials, modules, sometimes a portal glyph\n\nFrigates level up every 4 expeditions.");
        both("guide.universe.ship.encounters.title", "太空遭遇", "Space Encounters");
        both("guide.universe.ship.encounters",
                "开船在离空间站 600 格以外时，每 6 到 10 分钟可能收到信号。HUD 上标出来，自动驾驶列表里叫\"信号\"。\n\n废弃货船：从机库口飞进去搜货箱，保险箱在最前面那间。船里失重、没空气，别站到冒火花的线管底下。30 分钟后消失\n遇险信号：3 分钟内打光围攻货船的海盗。还没有货船的，船长把这艘送你；有的给 15000 信用点。别打货船",
                "Piloting more than 600 blocks from a station, you may pick up a signal every 6 to 10 minutes. It's marked on the HUD and listed as Signal in the autopilot.\n\nDerelict freighter: fly into the hangar and search the crates; the captain's safe is at the front. No gravity or air inside. Keep out from under sparking conduits. Gone after 30 min\nDistress call: clear the pirates within 3 min. No freighter yet? The captain gives you this one; otherwise 15,000 credits. Don't hit the freighter");
        both("guide.universe.ship.space_sentinels.title", "太空守卫机", "Space Sentinels");
        both("guide.universe.ship.space_sentinels",
                "在星球上被通缉到三星还飞上太空，或者朝遇险的货船开火打中 8 下，守卫机的拦截机就从身后追来，脉冲引擎被干扰。打光它们、甩开 1200 格，或者躲到空间站 600 格内就脱身。打下的拦截机掉守卫机残骸，偶尔有导航数据和飞船模块。\n\n应急信号接收器：科技商人卖，在太空里开船时用，找一艘废弃货船\n信用点芯片：右键兑换，一枚 1000",
                "Leave a planet at 3 wanted stars, or hit a freighter in distress 8 times, and Sentinel interceptors chase you, jamming your pulse drive. Destroy them, get 1,200 blocks away or reach a station. Wrecks drop Sentinel scrap, sometimes nav data or a ship module.\n\nEmergency broadcast receiver: sold by tech merchants. Use it while flying in space to find a derelict\nCredit chips: right-click to cash in, 1,000 each");
        both("guide.universe.survival.hazards.title", "危害", "Hazards");
        both("guide.universe.survival.hazards",
                "星球最多两种危害：寒冷、高温、毒性、辐射。HUD 上每种一条防护。\n\n全套外骨骼：撑 5 分钟，离开 25 秒充满\n用完：防护每 2 秒掉 1 血，氧气每秒掉 2 血\n有顶有墙的地方：不掉，20 秒充满\n风暴 ×2，火或冰挡相反的危害\n氧气罐、防护电池：各回 50%，低于 20% 自动用",
                "Up to two hazards per planet: cold, heat, toxicity, radiation.\n\nFull suit: 5 min, refills 25 s after\nEmpty: -1 HP/2 s; no oxygen -2 HP/s\nUnder a roof with walls: no drain, refills in 20 s\nStorms x2\nFire or ice blocks the opposite hazard\nCapsules and cells: +50%, auto below 20%");
        both("guide.universe.survival.resources.title", "资源植物", "Resource Plants");
        both("guide.universe.survival.resources",
                "星球上一丛丛长着能采的植物，空手右键收获，15 分钟左右长回来。\n\n氧囊草：氧气，右键补 10% 氧气\n盐角草、盐晶簇：钠，右键补 8% 防护\n每类星球还有一种特产，洞穴里有髓球\n产物右键土地能种回去，只能种在本来长它的星球上\n采引力果会惊动守卫机",
                "Patches of harvestable plants grow on planets. Right-click with an empty hand to harvest; they grow back in about 15 minutes.\n\nOxygen Bladderwort: oxygen, right-click for +10% oxygen\nSaltwort, salt crystals: sodium, +8% protection\nEach planet type has its own plant; caves have marrow\nPlant a product on soil where it grows wild\nPicking gravitino balls angers the Sentinels");
        both("guide.universe.survival.hydroponics.title", "水培槽", "Hydroponic Tray");
        both("guide.universe.survival.hydroponics",
                "研究「水培」后能做。接电网（20 kW），拿产物右键种下，熟了空手右键收，潜行右键拔掉，分拣器也能取。哪颗星球都能种。\n\n氧气 3 分钟一熟，钠 5 分钟，特产 10–30 分钟\n一次 4–8 个",
                "Unlocked by Hydroponics. Connect power (20 kW), use a plant product on it to sow, right-click to harvest, sneak to uproot. Sorters can take the harvest. Works on any planet.\n\nOxygen: 3 min, sodium: 5 min, others: 10-30 min\n4-8 per harvest");
        both("guide.universe.survival.border.title", "星球边界", "Planet Edge");
        both("guide.universe.survival.border",
                "星球的地面有边界，以 (0, 0) 为中心：小卫星两千多格见方，大行星三万格。离边界 64 格以内外骨骼面板会提示，屏幕边缘发红。边界走不出去，去别处要开飞船。母星没有边界。",
                "A planet's surface has an edge centered on (0, 0): about 2000 blocks across on small moons, up to 30000 on large planets. Within 64 blocks the suit panel warns you and the screen edges turn red. You can't walk past it; take your ship. The home planet has no edge.");
        both("guide.universe.survival.modules.title", "技术模块", "Tech Modules");
        both("guide.universe.survival.modules",
                "U 键打开技术配置，在这里装模块、整理货舱。\n\n外骨骼：开始 4 个技术槽、9 格货舱，每修好一个掉落舱 +1 槽、+9 格，最多 12 槽、54 格\n多功能工具：6 个技术槽\n品质：C、B、A、S，同一种最多三个生效\n来源：制造台做 C 档，更好的从兴趣点和空间站拿\n\n喷气背包：空中再按一次跳跃，喷 2.5 秒，落地 2 秒充满",
                "Press U for tech configuration to fit modules and sort cargo.\n\nExosuit: starts with 4 tech slots and 9 cargo; each repaired drop pod adds 1 slot and 9 cargo, up to 12 and 54\nMultitool: 6 tech slots\nGrades: C, B, A, S; up to three of one kind count\nSources: assemblers make C, better ones come from points of interest and stations\n\nJetpack: jump again in mid-air for 2.5 s of thrust, recharges in 2 s on the ground");
        both("guide.universe.exploration.visor.title", "分析目镜", "Analysis Visor");
        both("guide.universe.exploration.visor",
                "分析目镜用来鉴定动物、植物、矿石、恒星和星球。拿着多功能工具按 V 打开，对准目标按住右键 1.2 秒。\n对着动物还会显示它吃什么、产什么、能不能骑。\n\n第一个发现的人得勘探数据：\n动物：2\n植物、树、矿物：1\n一颗星球的动植物全部发现：+5",
                "The analysis visor identifies creatures, plants, ores, stars and planets. Hold the multitool, press V, aim and hold right-click for 1.2 s.\nOn creatures it also shows diet, produce and whether you can ride them.\n\nFirst discoverer gets survey data:\nCreature: 2\nPlant, tree, mineral: 1\nWhole planet's wildlife: +5");
        both("guide.universe.exploration.discoveries.title", "发现日志", "Discovery Log");
        both("guide.universe.exploration.discoveries",
                "K 键打开发现日志。进入太空时记下星系，降落时记下星球。\n发现者可以给星系和星球改名，最多 24 个字，留空恢复原名。\n\n第一次发现星系：5 到 20 个勘探数据（黑洞最多）\n第一次发现星球：3 到 4 个\n\n勘探数据用来上交科技（星际测绘、技术模块、跃迁校准）、做 C 档模块，也能卖。",
                "Press K for the discovery log. Systems are logged when you enter space, planets when you land.\nDiscoverers can rename them, up to 24 characters.\n\nNew system: 5 to 20 survey data (black holes the most)\nNew planet: 3 or 4\n\nSurvey data goes into hand-in research (Interstellar Survey, Technology Modules, Warp Calibration) and grade C modules, and it sells.");
        both("guide.universe.exploration.poi.title", "兴趣点", "Points of Interest");
        both("guide.universe.exploration.poi",
                "扫描能标出附近的兴趣点。\n\n掉落舱：右键修理，外骨骼扩容\n先驱者石碑：第一次碰给 3 个勘探数据和一个传送门符文\n科技储藏箱：挖开掉模块\n信号增强器：1 份导航数据找 2000 格内的兴趣点",
                "Scans mark nearby points of interest.\n\nDrop pod: repair to expand your exosuit\nMonolith: 3 survey data and a portal glyph the first time\nTech cache: break it for a module\nSignal booster: finds one within 2,000 blocks");
        both("guide.universe.exploration.bases.title", "基地和传送", "Bases and Teleporters");
        both("guide.universe.exploration.bases",
                "基地信标放下就登记成你的基地，右键改名，不用研究。\n\n传送器能去你的基地和进去过的空间站。\n每次传送：12 kJ，10 秒充满\n使用距离：6 格内\n宠物：24 格内的一起过去\n研究：传送网络",
                "Place a base beacon to register a base; right-click to rename. No research.\n\nTeleporters reach your bases and stations you've visited.\nCost: 12 kJ per jump, 10 s to recharge\nRange: stand within 6 blocks\nPets within 24 blocks come along\nResearch: Teleport Network");
        both("guide.universe.exploration.portals.title", "先驱者传送门", "Precursor Portals");
        both("guide.universe.exploration.portals",
                "每颗能降落的星球都有一座先驱者传送门，门上刻着这颗星球的地址：6 个符文，一共 16 种。船不跟着过去。\n\n学符文：每座先驱者石碑第一次碰、每座传送门第一次走近，各学一个；探索远征偶尔带回一个\n拨号：右键拨号台，按 6 个认识的符文，门开 30 秒，走进光里就到了\n星图上去过的星球写着地址",
                "Every landable planet has a Precursor portal carved with its address: 6 glyphs out of 16. Your ship can't follow you through.\n\nLearning glyphs: each monolith's first touch and each portal's first visit teach one; exploration expeditions may bring one back\nDialing: right-click the dial and enter 6 known glyphs; the portal opens for 30 s, walk into the light\nThe star map shows the address of planets you've visited");
        both("guide.universe.trade.stations.title", "空间站", "Space Stations");
        both("guide.universe.trade.stations",
                "每个星系有一座空间站，在第一颗能降落的星球外 700 格。从机库口飞进去，停在起降台上。\n\n交易终端：买卖\n任务板：接任务\n飞船维修台：修船，每点船体 2 信用点\n空间站传送器：不用电\n\n站内有重力和空气。站外失重：跳跃上升，潜行下降。",
                "Every system has one space station, 700 blocks outside the first landable planet. Fly in through the hangar and set down on the pad.\n\nTrade terminal: buy and sell\nMission board: take missions\nShip repair console: 2 credits per hull point\nStation teleporter: no power needed\n\nInside there is gravity and air. Outside you float: jump to rise, sneak to sink.");
        both("guide.universe.trade.trading.title", "买卖", "Trading");
        both("guide.universe.trade.trading",
                "交易终端在空间站里。信用点死了不掉。\n\n卖：点击 1 个，Shift 一组，Ctrl 全部\n买：点击 1 个，Shift 一组\n\n同一样东西卖得越多价格越低，每 2 天回升一半。\n星系缺的那类东西：多给 30%\n本地产的东西：少给 25%\n\n每个站都卖翘曲器、燃料、推进器和武器；富裕的站有 Mk.II 部件。",
                "Trade terminals are in space stations. Credits are kept on death.\n\nSell: click 1, Shift a stack, Ctrl all\nBuy: click 1, Shift a stack\n\nPrices drop as you sell the same thing and recover by half every 2 days.\nCategories the system lacks: +30%\nLocal products: -25%\n\nEvery station sells warpers, fuel, thrusters and weapons; wealthy ones stock Mk.II parts.");
        both("guide.universe.trade.missions.title", "任务板", "Missions");
        both("guide.universe.trade.missions",
                "任务板每天有 3 个任务，同时只能接一个，回到接任务的站交。\n\n送货：报酬是货值的 1.6 倍\n赏金：每架海盗 220\n采矿：每颗小行星 70\n勘测：每个物种 150\n\n报酬按站的富裕程度浮动，有时附带导航数据或模块。",
                "Mission boards post 3 missions a day. Hold one at a time and hand it in where you took it.\n\nDelivery: 1.6x the cargo value\nBounty: 220 per pirate\nMining: 70 per asteroid\nSurvey: 150 per species\n\nRewards scale with the station's wealth and sometimes include navigation data or a module.");
        both("guide.universe.trade.pirates.title", "海盗", "Pirates");
        both("guide.universe.trade.pirates",
                "在太空里开船、离空间站 600 格以外时可能遇上海盗。每 6 到 10 分钟判定一次，货越值钱越容易遇上。警告后 5 秒，2 到 4 架战机出现在身后。\n\n左键：开火\nG：光子炮 / 采矿光束\n脱身：跃迁、降落、靠近空间站或跑出 400 格\n奖励：每架给赏金，残骸进货舱\n\n船被打爆会拖回空间站，每组货丢 30%。",
                "Pirates can appear while you pilot in space more than 600 blocks from a station. A roll every 6 to 10 minutes, likelier with valuable cargo. 5 s after the warning, 2 to 4 fighters show up behind you.\n\nLeft-click: fire\nG: photon cannon / mining beam\nEscape: warp, land, reach a station or get 400 blocks away\nReward: a bounty per kill, scrap in your hold\n\nA destroyed ship is towed to the station and each cargo stack loses 30%.");
        both("guide.universe.husbandry.taming.title", "驯养", "Taming");
        both("guide.universe.husbandry.taming",
                "用分析目镜看动物吃什么，拿对的食物右键喂。每秒能喂一口，每口有概率驯服。\n\n草食：小麦、胡萝卜、苹果、草食饲料\n肉食：生肉、肉食饲料\n概率：温顺 35%，易受惊 25%，有领地意识 15%，捕食者 8%",
                "The visor shows what a creature eats. Feed it once a second; each bite may tame it.\n\nHerbivores: wheat, carrots, apples, feed\nCarnivores: raw meat, carnivore feed\nChance: docile 35%, skittish 25%, territorial 15%, predator 8%");
        both("guide.universe.husbandry.produce.title", "产物和骑乘", "Produce and Riding");
        both("guide.universe.husbandry.produce",
                "驯服的动物会产东西、跟着你，体型大的能骑。\n\n产物：饿了喂一口，30 秒后产出，5 分钟后再饿\n跟随：10 格外跟上，超过 12 格直接传送过来\n空手潜行右键：跟随 / 待命\n骑乘：体型大的四足、六足、两足动物，空手右键上去，按住跳跃蓄力",
                "Tamed creatures produce items, follow you, and the big ones can be ridden.\n\nProduce: feed when hungry, drops after 30 s, hungry again after 5 min\nFollow: from 10 blocks, teleports past 12\nSneak + empty hand: follow / stay\nRiding: large quadrupeds, hexapods and bipeds; right-click with an empty hand, hold jump to charge");
        both("guide.universe.husbandry.exocraft.title", "探索车", "Exocraft");
        both("guide.universe.husbandry.exocraft",
                "探索车是能开的采矿车。对着地面使用物品放下，拿镐敲三下收回，燃料和货物留在物品里。\n\n右键上车，潜行下车\nW / S：油门，A / D：转向\n按住左键：采矿激光，射程 12 格\n车上拿多功能工具右键：扫描 64 格内的矿脉\n\n油箱 1000（和飞船燃料通用），货箱 27 格",
                "A drivable mining vehicle. Use it on the ground to deploy; three pickaxe hits pack it up with its fuel and cargo.\n\nRight-click to get in, sneak to get out\nW / S: throttle, A / D: steer\nHold left-click: mining laser, 12 blocks\nVein scan: multitool right-click, 64 blocks\n\nTank: 1,000 (ship fuel), hold: 27 slots");
        both("guide.universe.dyson.sails.title", "太阳帆", "Solar Sails");
        both("guide.universe.dyson.sails",
                "电磁轨道弹射器把太阳帆射进戴森云，在界面里选轨道。\n\n射速：每 3 秒一张\n条件：露天，恒星在地平线 5° 以上；太空里随时能射\n寿命：90 分钟\n每张帆：36 W × 恒星光度系数\n\n射线接收站把电送到地面。",
                "The EM-Rail Ejector fires solar sails into the swarm; pick the orbit in its screen.\n\nRate: one sail every 3 s\nNeeds: star 5° above horizon, or in space\nLifetime: 90 min\nPer sail: 36 W x star luminosity factor");
        both("guide.universe.dyson.editor.title", "戴森球编辑器", "Dyson Sphere Editor");
        both("guide.universe.dyson.editor",
                "Y 键打开戴森球编辑器，也能从弹射器、发射井和接收站的界面打开。\n\n戴森云：最多 20 条轨道\n戴森壳：最多 10 层，每层选网格（测地、蜂窝、经纬），先建节点和框架，再铺球壳\n纬度：开始只能在 ±30° 内建，应力系统 I、II 放宽到 ±60°、±90°\n材料：建好的节点每分钟从戴森云吸 10 张帆",
                "Press Y for the Dyson sphere editor, or open it from an ejector, silo or receiver.\n\nSwarm: up to 20 orbits\nShell: up to 10 layers; pick a grid (geodesic, honeycomb, lat-long), build nodes and frames, then shells\nLatitude: within 30 degrees at first; Dyson Stress I and II raise it to 60 and 90\nMaterial: each built node pulls 10 sails a minute from the swarm");
        both("guide.universe.dyson.rockets.title", "火箭", "Rockets");
        both("guide.universe.dyson.rockets",
                "节点和框架靠火箭送上去，垂直发射井负责发射。\n\n射速：每 12 秒一枚\n条件：露天\n耗电：18 kW\n火箭：飞 20 秒到达，加 1 个结构点\n每个结构点：96 W",
                "Nodes and frames are built with rockets from a vertical launching silo.\n\nRate: one rocket every 12 s\nNeeds: open sky\nPower: 18 kW\nRocket: lands in 20 s, +1 structure point\nPer structure point: 96 W");
        both("guide.universe.dyson.receiver.title", "射线接收站", "Ray Receiver");
        both("guide.universe.dyson.receiver",
                "射线接收站把戴森球的电送到地面。连续接收 20 分钟达到满效率，中断 2 分钟清零。\n\n发电模式：6 到 15 kW\n光子模式：改出临界光子，满功率每分钟 6 个\n引力透镜：功率翻倍，每 10 分钟耗一个",
                "Ray receivers bring the sphere's power down. Full efficiency after 20 min of steady reception; 2 min off resets it.\n\nPower mode: 6 to 15 kW\nPhoton mode: critical photons, 6/min max\nGraviton lens: doubles power, one per 10 min");
        both("guide.universe.endgame.antimatter.title", "反物质", "Antimatter");
        both("guide.universe.endgame.antimatter",
                "微型粒子对撞机用临界光子造反物质。\n\n耗电：12 kW\n研究：狄拉克逆变机制\n\n反物质用来做宇宙矩阵和反物质燃料棒。12 反物质、12 氢、1 个湮灭约束球、1 个钛合金做 2 根燃料棒。",
                "The miniature particle collider turns critical photons into antimatter.\n\nPower: 12 kW\nResearch: Dirac Inversion Mechanism\n\nAntimatter goes into universe matrices and fuel rods. 12 antimatter, 12 hydrogen, a constraint sphere and a titanium alloy make 2 rods.");
        both("guide.universe.endgame.power.title", "聚变和人造恒星", "Fusion and Artificial Stars");
        both("guide.universe.endgame.power",
                "微型聚变发电站\n15 kW，烧氘核燃料棒，一根满负荷 6 分钟\n\n人造恒星\n72 kW，烧反物质燃料棒，一根满负荷 15 分钟",
                "Mini Fusion Power Plant\n15 kW from deuteron fuel rods, 6 min per rod at full load\n\nArtificial Star\n72 kW from antimatter fuel rods, 15 min per rod at full load");
        both("guide.universe.endgame.white.title", "宇宙矩阵", "Universe Matrix");
        both("guide.universe.endgame.white",
                "宇宙矩阵（白）用来研究「任务完成」和四条没有上限的升级线。\n\n宇宙矩阵：复合矩阵 + 信息矩阵 + 引力矩阵 + 反物质\n复合矩阵：蓝 + 红 + 黄",
                "Universe matrices (white) fund Mission Complete and the four uncapped upgrade lines.\n\nUniverse matrix: composite + information + gravity + antimatter\nComposite matrix: blue + red + yellow");
        both("guide.universe.endgame.finale.title", "任务完成", "Mission Complete");
        both("guide.universe.endgame.finale",
                "「任务完成」是科技树的最后一项：3000 点，只用白矩阵。\n研究完会放结局，之后可以接着玩，升级科技还能继续研究。\n结局可以在「结局」一章重看。",
                "Mission Complete is the last tech: 3,000 points of white matrices only.\nFinishing it plays the ending. The game carries on afterwards and upgrades stay open.\nReplay the ending from the Ending chapter.");
        both("item.universe.guide", "手册", "Guide");
        both("tooltip.universe.guide", "右键翻开", "Right-click to read");
        both("gui.universe.guide.title", "手册", "Guide");
        both("gui.universe.guide.page", "%s/%s", "%s/%s");
        both("gui.universe.guide.replay", "重看结局", "Replay Ending");
        both("gui.universe.guide.crafting", "工作台", "Crafting Table");
        both("gui.universe.guide.machine", "%s · %s 秒", "%s · %s s");
        both("gui.universe.guide.machine.smelting", "熔炉", "Smelter");
        both("gui.universe.guide.machine.assembling", "制造台", "Assembler");
        both("gui.universe.guide.machine.refining", "原油精炼厂", "Oil Refinery");
        both("gui.universe.guide.machine.chemical", "化工厂", "Chemical Plant");
        both("gui.universe.guide.machine.particle", "微型粒子对撞机", "Miniature Particle Collider");
        both("gui.universe.guide.log.day", "第 %s 天", "Day %s");
        both("guide.universe.log.empty", "还没有收到探测器的日志。", "No probe logs yet.");
        both("guide.universe.ending.locked", "研究完「任务完成」以后，可以在这里重看结局。", "Finish Mission Complete to replay the ending here.");
        both("guide.universe.ending.done", "点下面的按钮重看结局和这个世界的统计。", "Press the button below to replay the ending and this world's statistics.");
        for (Guide.Chapter chapter : Guide.CHAPTERS) {
            for (Guide.Page page : chapter.pages()) {
                for (String item : page.items()) {
                    if (!BuiltInRegistries.ITEM.containsKey(Identifier.parse(item))) {
                        throw new IllegalStateException("手册 " + chapter.id() + "." + page.id() + " 里的物品不存在：" + item);
                    }
                }
            }
        }
    }

    /** "寰宇"进度树（ADR-0018），和 UniverseAdvancements.NAMES 一一对应。 */
    private void advancements() {
        Set<String> written = new HashSet<>();
        this.advancementNames = written;
        advancement("root", "寰宇", "工业、星海和戴森球", "Universe", "Industry, the stars and a Dyson sphere");
        advancement("repair_multitool", "还能用", "修好损坏的多功能工具", "Good as New", "Repair the damaged multitool");
        advancement("first_power", "通电", "放下风力涡轮机、太阳能板或火力发电厂", "Power On", "Place a wind turbine, solar panel or thermal power plant");
        advancement("tesla", "拉电线", "放下电力感应塔", "Grid Expansion", "Place a tesla tower");
        advancement("mining", "自动开采", "放下采矿机", "Automated Mining", "Place a mining machine");
        advancement("smelting", "高温", "放下电弧熔炉", "Hot Stuff", "Place an arc smelter");
        advancement("assembling", "流水线", "放下制造台", "Assembly Line", "Place an assembler");
        advancement("sorting", "抓取", "放下分拣器", "Pick and Place", "Place a sorter");
        advancement("blue_matrix", "蓝", "做出电磁矩阵", "Blue", "Make an electromagnetic matrix");
        advancement("red_matrix", "红", "做出能量矩阵", "Red", "Make an energy matrix");
        advancement("yellow_matrix", "黄", "做出结构矩阵", "Yellow", "Make a structure matrix");
        advancement("purple_matrix", "紫", "做出信息矩阵", "Purple", "Make an information matrix");
        advancement("green_matrix", "绿", "做出引力矩阵", "Green", "Make a gravity matrix");
        advancement("logistics", "无人机送货", "放下行星物流站", "Drone Delivery", "Place a planetary logistics station");
        advancement("interstellar", "跨星送货", "放下星际物流站", "Interstellar Shipping", "Place an interstellar logistics station");
        advancement("blueprint", "照图施工", "保存一张蓝图", "Copy That", "Save a blueprint");
        advancement("species", "新物种", "用分析目镜发现一个新物种", "New Species", "Discover a new species with the analysis visor");
        advancement("survey", "全部登记", "发现一颗星球上的全部动植物", "Catalogued", "Discover every creature and plant on a planet");
        advancement("monolith", "有人来过", "碰一下先驱者的石碑", "Someone Was Here", "Touch a Precursor monolith");
        advancement("tame", "新朋友", "驯服一只外星生物", "New Friend", "Tame an alien creature");
        advancement("exocraft", "上车", "放下一辆探索车", "Hop In", "Deploy an exocraft");
        advancement("teleport", "一步到位", "用传送器传送一次", "Blink", "Use a teleporter");
        advancement("ship", "造船", "在舵前组装一艘飞船", "Shipwright", "Assemble a ship at its helm");
        advancement("space", "离开大气层", "飞进太空", "Liftoff", "Fly into space");
        advancement("warp", "跃迁", "跃迁到另一个星系", "Jump", "Warp to another star system");
        advancement("trade", "第一笔生意", "在空间站买或卖一次东西", "First Deal", "Buy or sell something at a space station");
        advancement("mission", "接活", "完成任务板上的一项任务", "Odd Jobs", "Complete a mission from a mission board");
        advancement("pirates", "不速之客", "打退一次海盗袭击", "Uninvited Guests", "Fight off a pirate raid");
        advancement("ejector", "发射", "放下电磁轨道弹射器", "Launch", "Place an EM-Rail Ejector");
        advancement("silo", "垂直发射", "放下垂直发射井", "Vertical Launch", "Place a vertical launching silo");
        advancement("first_shell", "第一片球壳", "戴森球铺满一片球壳", "First Shell", "Complete a shell of a Dyson sphere");
        advancement("ray", "接收", "放下射线接收站", "Receiving", "Place a ray receiver");
        advancement("antimatter", "反物质", "做出反物质", "Antimatter", "Make antimatter");
        advancement("universe_matrix", "白", "做出宇宙矩阵", "White", "Make a universe matrix");
        advancement("artificial_star", "造一颗星", "放下人造恒星", "Star Maker", "Place an artificial star");
        advancement("mission_complete", "任务完成", "研究完最后一项科技", "Mission Complete", "Finish the final research");
        if (!written.equals(Set.of(UniverseAdvancements.NAMES))) {
            throw new IllegalStateException("进度的语言条目和 UniverseAdvancements.NAMES 对不上");
        }
    }

    private @Nullable Set<String> advancementNames;

    private void advancement(String name, String zhTitle, String zhDescription, String enTitle, String enDescription) {
        both("advancements.universe." + name + ".title", zhTitle, enTitle);
        both("advancements.universe." + name + ".description", zhDescription, enDescription);
        if (this.advancementNames != null) {
            this.advancementNames.add(name);
        }
    }

    /** 蓝图（ADR-0018）。 */
    private void blueprint() {
        item(UniverseItems.BLUEPRINT.get(), "蓝图", "Blueprint");
        both("item.universe.blueprint.named", "蓝图：%s", "Blueprint: %s");
        both("item.universe.blueprint.unnamed", "未命名", "Untitled");
        both("tech.universe.blueprint", "蓝图", "Blueprints");
        both("key.universe.rotate_blueprint", "旋转蓝图", "Rotate Blueprint");
        both("tooltip.universe.blueprint.summary",
                "空白：右键两个方块框出选区，每边最多 32 格\n空白：潜行右键起名保存\n画好的：右键放下，R 键转向\n画好的：潜行右键清空\n材料从背包和外骨骼货舱里扣",
                "Blank: right-click two blocks to mark a region, up to 32 per side\nBlank: sneak + right-click to name and save\nSaved: right-click to build, R to rotate\nSaved: sneak + right-click to clear\nMaterials come from your inventory and exosuit cargo");
        both("tooltip.universe.blueprint.blank", "空白", "Blank");
        both("tooltip.universe.blueprint.first", "第一个角：%s, %s, %s", "First corner: %s, %s, %s");
        both("tooltip.universe.blueprint.selection", "选区 %s×%s×%s，潜行右键保存", "Region %s×%s×%s, sneak + right-click to save");
        both("tooltip.universe.blueprint.size", "%s×%s×%s，%s 个方块", "%s×%s×%s, %s blocks");
        both("tooltip.universe.blueprint.material", "  %s ×%s", "  %s ×%s");
        both("tooltip.universe.blueprint.more", "  还有 %s 种", "  and %s more");
        both("message.universe.blueprint.first", "第一个角：%s, %s, %s", "First corner: %s, %s, %s");
        both("message.universe.blueprint.second", "选区 %s×%s×%s，潜行右键保存", "Region %s×%s×%s. Sneak + right-click to save");
        both("message.universe.blueprint.too_big", "选区太大，每边最多 %s 格", "Region too large: %s blocks per side at most");
        both("message.universe.blueprint.need_selection", "先右键两个方块框出选区", "Right-click two blocks to mark a region first");
        both("message.universe.blueprint.other_dimension", "选区不在这个维度", "The region is in another dimension");
        both("message.universe.blueprint.empty_selection", "选区里没有能存的方块", "Nothing in the region to save");
        both("message.universe.blueprint.saved", "蓝图「%s」已保存，%s 个方块", "Saved blueprint \"%s\" with %s blocks");
        both("message.universe.blueprint.rotated", "转到 %s°", "Rotated to %s°");
        both("message.universe.blueprint.cleared", "蓝图已清空", "Blueprint cleared");
        both("message.universe.blueprint.busy", "上一张蓝图还没放完", "Still building the last blueprint");
        both("message.universe.blueprint.missing_data", "找不到这张蓝图的内容", "This blueprint's contents are missing");
        both("message.universe.blueprint.done", "蓝图放好了，放了 %s 个方块", "Blueprint built: %s blocks placed");
        both("message.universe.blueprint.missing", "缺材料：%s", "Missing materials: %s");
        both("message.universe.blueprint.count", "%s ×%s", "%s ×%s");
        both("message.universe.blueprint.more", "，还有 %s 种", ", and %s more");
        both("message.universe.blueprint.blocked", "%s 个位置被占着，跳过了", "%s positions were occupied and skipped");
        both("gui.universe.blueprint.save.title", "保存蓝图", "Save Blueprint");
        both("gui.universe.blueprint.save.size", "选区 %s×%s×%s", "Region %s×%s×%s");
        both("gui.universe.blueprint.save.name", "名字", "Name");
        both("gui.universe.blueprint.save.save", "保存", "Save");
        both("gui.universe.blueprint.save.cancel", "取消", "Cancel");
    }

    /** 剧情（ADR-0018）：探测器的日志、先驱者的回应、结局。 */
    private void story() {
        both("story.universe.log.chat", "[%s] %s", "[%s] %s");
        both("story.universe.log.first_space.title", "UNV-7 · 轨道", "UNV-7 · Orbit");
        both("story.universe.log.first_space", "你出了大气层，信号清楚多了。勘测计划一共发射了十二台探测器，现在还在发信号的只有我。",
                "You're out of the atmosphere and the signal is much clearer. The survey launched twelve probes. I'm the only one still transmitting.");
        both("story.universe.log.first_warp.title", "UNV-7 · 跃迁", "UNV-7 · Jump");
        both("story.universe.log.first_warp", "跃迁那一下，我丢了你的信号四秒。其他探测器最后一次报告位置，也都是在跃迁之后。",
                "During the jump I lost your signal for four seconds. The other probes all sent their last position report right after a jump.");
        both("story.universe.log.first_shell.title", "UNV-7 · 球壳", "UNV-7 · Shell");
        both("story.universe.log.first_shell", "第一片球壳铺满了，从这里看那颗恒星暗了一点。石碑上说过：星空变暗的时候，回去把手放在石头上。",
                "The first shell panel is complete. From here that star looks a little dimmer. The monoliths said it: when your sky begins to dim, go back and lay your hand on the stone.");
        both("story.universe.log.first_universe_matrix.title", "UNV-7 · 宇宙矩阵", "UNV-7 · Universe Matrix");
        both("story.universe.log.first_universe_matrix", "第一个宇宙矩阵出来了。它的读数和石碑里的信号在同一个频率上，我查了三遍。",
                "The first universe matrix is done. Its readings sit on the same frequency as the signal in the monoliths. I checked three times.");

        both("gui.universe.monolith.response_heading", "先驱者的回应 · 第 %s/%s 段", "PRECURSOR RESPONSE · %s of %s");
        both("gui.universe.monolith.response_footer", "（石头是温的）", "(The stone is warm.)");
        both("poi.universe.response.1.title", "你回来了", "You Came Back");
        both("poi.universe.response.1.text", "你回来了。你的恒星暗了一点，我们感觉到了。我们说过会听见，这些年一直在听。",
                "You came back. Your star has dimmed a little; we felt it. We said we would hear you, and we have been listening all this time.");
        both("poi.universe.response.2.title", "寂静的由来", "Where the Silence Came From");
        both("poi.universe.response.2.text", "我们包住的恒星太多了。星团里的光开始朝一个地方流，流向中心。所有的频段都被它吞了进去，这就是寂静。",
                "We enclosed too many stars. The light of the cluster began to flow toward one place, the center. It swallowed every channel. That was the Silence.");
        both("poi.universe.response.3.title", "中心", "The Center");
        both("poi.universe.response.3.text", "中心吞的是我们收集起来的能量。我们收集得越多，它长得越快。那些暗下去的星系，有一半是被它吃掉的。",
                "The center feeds on the energy we gathered. The more we gathered, the faster it grew. Half the systems that went dark were eaten by it.");
        both("poi.universe.response.4.title", "舰队", "The Fleet");
        both("poi.universe.response.4.text", "最后一支舰队带着我们存下的所有能量飞了进去，想把它还回去。舰队没有回来，但从那天起，中心不再长大。",
                "Our last fleet flew in carrying every joule we had stored, to give it back. The fleet never returned, but from that day on the center stopped growing.");
        both("poi.universe.response.5.title", "钥匙", "The Key");
        both("poi.universe.response.5.text", "你们叫它宇宙矩阵，我们叫它钥匙。它把物质、能量和信息编在一起，中心读得懂。有了它，能量可以收，也可以还。",
                "You call it the universe matrix. We called it the key. It weaves matter, energy and information together in a form the center can read. With it, energy can be taken, and it can be returned.");
        both("poi.universe.response.6.title", "最后的请求", "A Last Request");
        both("poi.universe.response.6.text", "把你的研究做完。到那时你就明白，球壳只是开始。收集的时候，记得留一份还回去。",
                "Finish your research. Then you will see that the shells were only a beginning. When you gather, remember to keep a share to give back.");

        both("story.universe.ending.title", "UNV-7 · 最后一条日志", "UNV-7 · Final Log");
        both("story.universe.ending.1", "任务完成。", "Mission complete.");
        both("story.universe.ending.2", "寰宇勘测计划要做两件事：测绘这个星团，找到一种能让文明一直走下去的能源。",
                "The Universe Survey had two goals: map this cluster, and find a source of energy a civilization could live on indefinitely.");
        both("story.universe.ending.3", "两件你都做到了。你走过的星系、命名的物种都存进了我的记录。你点亮了一颗恒星，又用球壳把另一颗包了起来。",
                "You did both. Every system you crossed and every species you named is in my records. You lit a star of your own and wrapped another in shells.");
        both("story.universe.ending.4", "先驱者也走过这条路。他们包住了一千颗恒星，然后寂静来了。他们的最后一支舰队把能量还给中心，它才停下。",
                "The Precursors walked this road before you. They enclosed a thousand stars, and then the Silence came. It only stopped when their last fleet gave the energy back to the center.");
        both("story.universe.ending.5", "你手里的宇宙矩阵就是他们说的钥匙。中心现在读得懂我们了。",
                "The universe matrix in your hands is the key they spoke of. The center can read us now.");
        both("story.universe.ending.6", "接下来收多少、还多少，由你决定。石碑会一直在那里。",
                "How much you take from here, and how much you give back, is up to you. The monoliths will still be there.");
        both("story.universe.ending.7", "其余十一台探测器，我再也没有收到它们的信号。我会继续往外发我的，直到电池用完。",
                "I never heard from the other eleven probes again. I will keep sending my own signal until the battery runs out.");
        both("story.universe.ending.8", "如果以后有人在哪颗星球上捡到我，告诉他们：有人走完了这条路。",
                "If someone finds me on some planet one day, tell them someone made it to the end.");
        both("story.universe.ending.end", "—— 日志结束 ——", "— END OF LOG —");
        both("story.universe.ending.stats", "这个世界", "This World");
        both("story.universe.stat.time", "游戏时间", "Time played");
        both("story.universe.stat.time.value", "%s 小时 %s 分，第 %s 天", "%sh %sm, day %s");
        both("story.universe.stat.systems", "发现的恒星系", "Star systems discovered");
        both("story.universe.stat.bodies", "发现的天体", "Bodies discovered");
        both("story.universe.stat.species", "发现的物种", "Species discovered");
        both("story.universe.stat.dyson", "戴森球", "Dyson spheres");
        both("story.universe.stat.dyson.value", "%s 层，%s 片球壳", "%s layers, %s shell panels");
        both("story.universe.stat.power", "戴森球发电", "Dyson power");
        both("story.universe.stat.techs", "研究完的科技", "Techs researched");
        both("story.universe.stat.techs.value", "%s 个", "%s");
        both("story.universe.stat.upgrades", "升级科技", "Upgrades");
        both("story.universe.stat.upgrades.value", "%s 级", "%s levels");
        both("story.universe.ending.continue", "升级科技可以一直研究下去。", "Upgrades can be researched forever.");
        both("story.universe.ending.thanks", "感谢游玩", "Thanks for playing");
        both("story.universe.ending.skip", "按住空格加速，Esc 关闭", "Hold Space to speed up, Esc to close");
    }

    /** M13 的飞船与航行（ADR-0024）：评级、飞船技术、驱动、着陆台、召唤和自动驾驶。 */
    /** 货船、护卫舰和远征（ADR-0024）。 */
    private void fleet() {
        block(UniverseBlocks.FREIGHTER_PLATE.get(), "货船装甲板", "Freighter Armour Plate");
        block(UniverseBlocks.FREIGHTER_ENGINE.get(), "货船引擎喷口", "Freighter Engine Nozzle");
        block(UniverseBlocks.FREIGHTER_CONSOLE.get(), "货船控制台", "Freighter Console");
        block(UniverseBlocks.FLEET_TERMINAL.get(), "舰队终端", "Fleet Terminal");
        both("universe.bed.no_sleep_on_freighter", "重生点设在货船上了。船上没有昼夜，睡不了觉",
                "Respawn point set on the freighter. There's no night aboard, so no sleeping");
        for (FrigateType type : FrigateType.values()) {
            both("gui.universe.fleet.type." + type.key(), type.nameZh() + "舰", type.nameEn());
        }
        both("gui.universe.fleet.type_short.combat", "战", "CMB");
        both("gui.universe.fleet.type_short.exploration", "探", "EXP");
        both("gui.universe.fleet.type_short.industrial", "工", "IND");
        both("gui.universe.fleet.type_short.trade", "贸", "TRD");
        both("gui.universe.fleet.type_short.support", "援", "SUP");
        for (Area area : Area.values()) {
            both("gui.universe.fleet.area." + area.key(), area.nameZh(), area.nameEn());
        }
        both("gui.universe.fleet.stat.combat", "战 %s", "CMB %s");
        both("gui.universe.fleet.stat.exploration", "探 %s", "EXP %s");
        both("gui.universe.fleet.stat.industry", "工 %s", "IND %s");
        both("gui.universe.fleet.stat.trade", "贸 %s", "TRD %s");
        for (FrigateTrait trait : FrigateTrait.values()) {
            both("gui.universe.fleet.trait." + trait.key(), trait.nameZh(), trait.nameEn());
        }
        both("gui.universe.fleet.kind.combat", "战斗", "Combat");
        both("gui.universe.fleet.kind.exploration", "探索", "Exploration");
        both("gui.universe.fleet.kind.industrial", "工业", "Industrial");
        both("gui.universe.fleet.kind.trade", "贸易", "Trade");
        both("gui.universe.fleet.kind.balanced", "均衡", "Balanced");

        // 舰队终端
        both("gui.universe.fleet.title", "舰队终端", "Fleet Terminal");
        both("gui.universe.fleet.tab.frigates", "护卫舰", "Frigates");
        both("gui.universe.fleet.tab.offers", "远征任务", "Missions");
        both("gui.universe.fleet.tab.voyages", "在外面", "Out on Missions");
        both("gui.universe.fleet.count", "%s / %s 艘", "%s / %s ships");
        both("gui.universe.fleet.no_frigates", "还没有护卫舰。在空间站的飞船交易终端雇", "No frigates yet. Hire them at a station's starship dealer");
        both("gui.universe.fleet.frigate", "%1$s · %2$s · %3$s 级", "%1$s · %2$s · Rank %3$s");
        both("gui.universe.fleet.busy", "出征中", "On mission");
        both("gui.universe.fleet.damaged", "受损", "Damaged");
        both("gui.universe.fleet.fuel", "燃料 %s 吨", "Fuel %s t");
        both("gui.universe.fleet.traits", "✦%s", "✦%s");
        both("gui.universe.fleet.trait", "%1$s：%2$s", "%1$s: %2$s");
        both("gui.universe.fleet.trait_fuel", "燃料 %s 吨", "%s t fuel");
        both("gui.universe.fleet.trait_stat", "%2$s %1$s", "%1$s %2$s");
        both("gui.universe.fleet.repair", "修理 %s", "Repair %s");
        both("gui.universe.fleet.dismiss", "解散", "Dismiss");
        both("gui.universe.fleet.dismiss_confirm", "确认解散", "Confirm");
        both("gui.universe.fleet.offer", "%1$s %2$s · %3$s 分钟", "%1$s %2$s · %3$s min");
        both("gui.universe.fleet.offer_taken", "已接", "Taken");
        both("gui.universe.fleet.no_offers", "要先有一艘货船", "You need a freighter first");
        both("gui.universe.fleet.pick_offer", "左边选一个任务", "Pick a mission on the left");
        both("gui.universe.fleet.team", "出征的护卫舰 %s/%s", "Team %s/%s");
        both("gui.universe.fleet.pick", "□ %1$s %2$s", "□ %1$s %2$s");
        both("gui.universe.fleet.picked", "■ %1$s %2$s", "■ %1$s %2$s");
        both("gui.universe.fleet.none_ready", "没有能出发的护卫舰，受损的先修好", "No frigates ready. Repair the damaged ones first");
        both("gui.universe.fleet.segments", "%s 段遭遇，每段要 %s 点", "%s encounters, %s points each");
        both("gui.universe.fleet.chance", "%1$s %2$s（%3$s%%）", "%1$s %2$s (%3$s%%)");
        both("gui.universe.fleet.fuel_cost", "发射燃料：要 %s 个，有 %s 个", "Launch fuel: %s needed, %s carried");
        both("gui.universe.fleet.launch", "出发", "Launch");
        both("gui.universe.fleet.launch_confirm", "胜算很低，确定？", "Low odds. Sure?");
        both("gui.universe.fleet.expected", "平均每段的胜算 %s%%", "Average odds per leg: %s%%");
        both("gui.universe.fleet.expected_low", "平均每段的胜算 %s%%，失败的段会让护卫舰受损，伤两次就沉",
                "Average odds per leg: %s%%. Failed legs damage frigates; a second hit sinks one");
        both("gui.universe.fleet.no_voyages", "没有在外面的远征", "No missions under way");
        both("gui.universe.fleet.voyage", "%1$s %2$s", "%1$s %2$s");
        both("gui.universe.fleet.voyage_back", "%1$s %2$s · 回来了", "%1$s %2$s · Back");
        both("gui.universe.fleet.voyage_team", "出征：%s", "Team: %s");
        both("gui.universe.fleet.back", "回来了，可以领取", "Back. Ready to claim");
        both("gui.universe.fleet.remaining", "还有 %s 分 %s 秒", "%s min %s s left");
        both("gui.universe.fleet.event_pending", "第 %s 段：……", "Encounter %s: ...");
        both("gui.universe.fleet.event_won", "第 %1$s 段：%2$s，成功", "Encounter %1$s: %2$s, success");
        both("gui.universe.fleet.event_lost", "第 %1$s 段：%2$s，失败", "Encounter %1$s: %2$s, failed");
        both("gui.universe.fleet.event_hurt", "第 %1$s 段：%2$s，失败，%3$s 受损", "Encounter %1$s: %2$s, failed. %3$s damaged");
        both("gui.universe.fleet.event_sunk", "第 %1$s 段：%2$s，失败，%3$s 沉没", "Encounter %1$s: %2$s, failed. %3$s lost");
        both("gui.universe.fleet.claim", "领取", "Claim");
        both("message.universe.fleet.returned", "%s远征回来了，到舰队终端领取", "A %s mission is back. Claim it at the fleet terminal");
        both("message.universe.fleet.problem.no_freighter", "要先有一艘货船", "You need a freighter first");
        both("message.universe.fleet.problem.fleet_full", "舰队满了", "Your fleet is full");
        both("message.universe.fleet.problem.no_team", "至少派一艘", "Pick at least one frigate");
        both("message.universe.fleet.problem.team_too_big", "一次最多派 5 艘", "Five frigates at most");
        both("message.universe.fleet.problem.unknown_frigate", "舰队里没有这艘", "That frigate isn't in your fleet");
        both("message.universe.fleet.problem.busy", "有护卫舰还在外面", "A frigate is still out");
        both("message.universe.fleet.problem.damaged", "受损的护卫舰要先修好", "Repair the damaged frigates first");
        both("message.universe.fleet.problem.too_many_voyages", "同时最多 3 支远征", "Three missions at a time at most");
        both("message.universe.fleet.problem.taken", "这个任务已经接过了", "That mission's already taken");
        both("message.universe.fleet.no_fuel", "要 %s 个发射燃料", "Needs %s launch fuel");
        both("message.universe.fleet.launched", "%s 艘护卫舰出发了，%s 分钟后回来", "%s frigates launched. Back in %s minutes");
        both("message.universe.fleet.not_back", "还没回来", "Not back yet");
        both("message.universe.fleet.claimed", "成功 %1$s/%2$s 段，带回 %3$s 信用点和 %4$s 件东西。沉没 %5$s，受损 %6$s，升级 %7$s",
                "%1$s/%2$s encounters won. Brought back %3$s credits and %4$s items. Lost %5$s, damaged %6$s, promoted %7$s");
        both("message.universe.fleet.cannot_repair", "这艘不用修", "Nothing to repair");
        both("message.universe.fleet.repaired", "%s 修好了", "%s repaired");
        both("message.universe.fleet.cannot_dismiss", "出征中的护卫舰不能解散", "Can't dismiss a frigate on a mission");
        both("message.universe.fleet.dismissed", "%s 解散了", "%s dismissed");
        both("commands.universe.fleet.not_in_space", "先到太空里去", "Go to space first");
        both("commands.universe.fleet.bad_grade", "没有这个等级：%s（C、B、A、S）", "Unknown grade: %s (C, B, A or S)");
        both("commands.universe.fleet.granted", "给了你一艘 %2$s 级货船（#%1$s）", "Granted you a %2$s-class freighter (#%1$s)");
        both("commands.universe.fleet.hired", "雇了 %s 艘护卫舰，现在一共 %s 艘", "Hired %s frigates, %s in the fleet now");
        both("commands.universe.fleet.hurried", "%s 支远征都回来了", "%s expeditions are back");
        both("commands.universe.blackhole.none", "%s 号恒星系没有黑洞", "System %s has no black hole");
        both("commands.universe.blackhole.list", "%2$s 个恒星系里有 %1$s 个有黑洞：", "%1$s of %2$s systems have a black hole:");
        both("commands.universe.blackhole.here", "黑洞在 %1$s，视界半径 %2$s 格，离视界还有 %3$s 格",
                "Black hole at %1$s, horizon radius %2$s, %3$s blocks to the horizon");
        both("commands.universe.blackhole.went", "到了 %1$s 号恒星系，离黑洞的视界 %2$s 格", "In system %1$s, %2$s blocks from the horizon");

        // 货船控制台
        both("gui.universe.freighter_console.title", "货船控制台", "Freighter Console");
        both("gui.universe.freighter_console.here", "停在：%s", "Parked at: %s");
        both("gui.universe.freighter_console.info", "%1$s 级 · 航程 %2$s 光年 · 空间翘曲器 %3$s（每次 %4$s 个）",
                "Class %1$s · Range %2$s ly · Space Warpers %3$s (%4$s per jump)");
        both("gui.universe.freighter_console.hint", "选一个恒星系，整艘货船跃迁过去", "Pick a system. The whole freighter jumps there");
        both("gui.universe.freighter_console.spooling", "正在充能", "Charging");
        both("gui.universe.freighter_console.no_warpers", "空间翘曲器不够 %s 个", "Needs %s Space Warpers");
        both("gui.universe.freighter_console.empty", "航程内没有别的恒星系", "No other systems in range");
        both("gui.universe.freighter_console.target", "%1$s · %2$s 光年", "%1$s · %2$s ly");
        both("gui.universe.freighter_console.target_new", "%1$s · %2$s 光年 · 没去过", "%1$s · %2$s ly · Unvisited");
        both("message.universe.freighter.none", "你还没有货船", "You don't own a freighter");
        both("message.universe.freighter.cooldown", "货船还要 %s 秒才能再叫", "Freighter can be called again in %s s");
        both("message.universe.freighter.not_in_space", "在太空里才能叫货船", "You can only call the freighter in space");
        both("message.universe.freighter.no_room", "这里放不下货船，离星球和空间站远一点", "No room here. Get clear of planets and stations");
        both("message.universe.freighter.summoned", "货船正跃迁过来", "Freighter inbound");
        both("message.universe.freighter.vacuum", "机库门外是真空，开船才能出去", "Vacuum past the hangar door. Fly out in a ship");
        both("message.universe.freighter.not_yours", "这不是你的货船", "This isn't your freighter");
        both("message.universe.freighter.no_warpers", "货船跃迁要 %s 个空间翘曲器", "A freighter jump takes %s Space Warpers");
        both("message.universe.freighter.spooling", "货船开始充能，目标%1$s", "Freighter charging for %2$s");
        both("message.universe.freighter.arrived", "货船到了%1$s", "Freighter arrived at %2$s");
        both("message.universe.freighter.boarded", "进了货船的机库", "Landed in the freighter hangar");
        both("message.universe.freighter.left", "离开货船", "Left the freighter");
        both("gui.universe.autopilot.summon_freighter", "召唤货船", "Summon freighter");
        both("gui.universe.autopilot.freighter", "货船", "Freighter");

        // 飞船交易终端的护卫舰、货船
        both("gui.universe.dealer.tab.frigates", "护卫舰", "Frigates");
        both("gui.universe.dealer.tab.freighter", "货船", "Freighter");
        both("gui.universe.dealer.hire", "雇用 %s", "Hire %s");
        both("gui.universe.dealer.hired_out", "今天这艘雇走了", "Already hired today");
        both("gui.universe.dealer.no_traits", "没有特性", "No traits");
        both("gui.universe.dealer.need_freighter", "要先有一艘货船才能雇护卫舰", "You need a freighter to hire frigates");
        both("gui.universe.dealer.fleet_full", "舰队满了（%s 艘）", "Fleet full (%s ships)");
        both("gui.universe.dealer.fleet", "舰队 %s / %s 艘", "Fleet %s / %s ships");
        both("gui.universe.dealer.no_freighter", "今天没有货船卖，过几天再来看", "No freighter for sale today. Check back in a few days");
        both("gui.universe.dealer.freighter", "%s 级货船", "Class %s Freighter");
        both("gui.universe.dealer.freighter_size", "长 %s 宽 %s 高 %s", "%s long, %s wide, %s tall");
        both("gui.universe.dealer.freighter_range", "跃迁 %s 光年，每次 %s 个翘曲器", "Jumps %s ly, %s Space Warpers each");
        both("gui.universe.dealer.freighter_fleet", "能带 %s 艘护卫舰", "Carries up to %s frigates");
        both("gui.universe.dealer.freighter_inside", "里面有机库、舰桥和大甲板", "Hangar, bridge and a big open deck");
        both("gui.universe.dealer.freighter_hint", "停在离空间站 3000 格的地方", "Parks 3000 blocks off the station");
        both("gui.universe.dealer.have_freighter", "你已经有一艘货船了", "You already own a freighter");
        both("message.universe.dealer.hired", "雇下了 %1$s（%2$s），%3$s 信用点", "Hired %1$s (%2$s) for %3$s credits");
        both("message.universe.dealer.have_freighter", "你已经有一艘货船了", "You already own a freighter");
        both("message.universe.dealer.bought_freighter", "买下了 %1$s 级货船，%2$s 信用点。开船时按 N 选\"货船\"飞过去",
                "Bought a class %1$s freighter for %2$s credits. Press N while flying and pick Freighter");

        both("message.universe.glyph.learned", "学会了一个先驱者符文（%s/%s）", "Learned a Precursor glyph (%s/%s)");
    }

    /** 太空遭遇（ADR-0024）：废弃货船、遇险信号、太空守卫机。 */
    private void encounters() {
        item(UniverseItems.CREDIT_CHIP.get(), "信用点芯片", "Credit Chip");
        item(UniverseItems.EMERGENCY_RECEIVER.get(), "应急信号接收器", "Emergency Broadcast Receiver");
        block(UniverseBlocks.LIVE_CONDUIT.get(), "漏电的线管", "Live Conduit");
        both("entity.universe.sentinel_interceptor", "守卫机拦截机", "Sentinel Interceptor");
        both("stat.universe.freighters_rescued", "救下的遇险货船", "Freighters Rescued");
        both("stat.universe.interceptors_destroyed", "击落的守卫机拦截机", "Sentinel Interceptors Destroyed");
        both("death.attack.universe.shock", "%1$s触电了", "%1$s was electrocuted");
        both("death.attack.universe.shock.player", "%1$s躲%2$s的时候触电了", "%1$s was electrocuted while fleeing %2$s");
        both("gui.universe.autopilot.derelict", "废弃货船", "Derelict Freighter");
        both("gui.universe.autopilot.distress", "遇险货船", "Freighter in Distress");
        both("hud.universe.encounter.derelict", "废弃货船 · %s 格", "Derelict · %s m");
        both("hud.universe.encounter.distress", "遇险货船 · %s 格", "Distress call · %s m");
        both("hud.universe.encounter.distress_hull", "遇险货船 · 船体 %1$s%% · %2$s 格", "Distress call · hull %1$s%% · %2$s m");
        both("hud.universe.encounter.interceptors", "守卫机拦截机 ×%s", "Sentinel interceptors ×%s");
        both("hud.universe.encounter.interceptor_marker", "拦截机 · %s 格", "Interceptor · %s m");
        both("message.universe.encounter.derelict", "收到一艘废弃货船的信标，在前方 %s 格", "Derelict freighter beacon, %s m ahead");
        both("message.universe.encounter.distress", "收到遇险信号：前方 %s 格有艘货船遭海盗围攻",
                "Distress call: a freighter is under pirate attack %s m ahead");
        both("message.universe.encounter.under_attack", "%s 架海盗正在打货船", "%s pirates are attacking the freighter");
        both("message.universe.encounter.signal_lost", "遇险信号断了", "The distress signal went dead");
        both("message.universe.encounter.abandoned", "你走了，货船自己跳走了", "You left. The freighter jumped away on its own");
        both("message.universe.encounter.freighter_fled", "货船撑不住，跳走了", "The freighter couldn't hold out and jumped away");
        both("message.universe.encounter.rescued", "海盗打光了，货船得救了", "Pirates cleared. The freighter is safe");
        both("message.universe.encounter.offer", "船长要把这艘 %1$s 级货船送给你。%2$s  %3$s",
                "The captain offers you this class %1$s freighter. %2$s  %3$s");
        both("message.universe.encounter.accept", "[收下货船]", "[Take the freighter]");
        both("message.universe.encounter.accept_hint", "货船停在原地，飞进左舷的机库口就能上船",
                "It stays where it is. Fly into the port-side hangar to board");
        both("message.universe.encounter.decline", "[要 %s 信用点]", "[Take %s credits]");
        both("message.universe.encounter.reward", "船长转来 %s 信用点", "The captain sent you %s credits");
        both("message.universe.encounter.offer_expired", "船长没等到回话，转来 %s 信用点", "No answer. The captain sent %s credits instead");
        both("message.universe.encounter.offer_gone", "这份报酬已经没了", "That offer is gone");
        both("message.universe.encounter.freighter_yours", "货船归你了（%s 级）。飞进左舷的机库口就能上船",
                "The freighter is yours (class %s). Fly into the port-side hangar to board");
        both("message.universe.encounter.friendly_fire", "别打货船！", "Cease fire on the freighter!");
        both("message.universe.encounter.betrayed", "你朝货船开了火，货船跳走了", "You fired on the freighter. It jumped away");
        both("message.universe.encounter.sentinels_locked", "太空守卫机锁定了你的飞船", "Space Sentinels have locked onto your ship");
        both("message.universe.encounter.sentinels_incoming", "%s 架守卫机拦截机追上来了", "%s Sentinel interceptors incoming");
        both("message.universe.encounter.sentinels_escaped", "离开了这片太空，守卫机没追上来", "You left this sector. The Sentinels broke off");
        both("message.universe.encounter.sentinels_station", "进了空间站的防区，守卫机撤了", "Inside station space. The Sentinels pulled back");
        both("message.universe.encounter.sentinels_lost", "甩掉了守卫机", "You shook off the Sentinels");
        both("message.universe.encounter.sentinels_gone", "守卫机撤了", "The Sentinels pulled back");
        both("message.universe.encounter.sentinels_destroyed", "打下 %s 架拦截机，守卫机撤了", "Shot down %s interceptors. The Sentinels pulled back");
        both("message.universe.encounter.interceptor_down", "打下一架拦截机", "Interceptor down");
        both("message.universe.encounter.interceptor_down_stowed", "打下一架拦截机，残骸收进了货舱", "Interceptor down. Salvage stowed in the hold");
        both("message.universe.encounter.chips_cashed", "兑换了 %s 信用点", "Cashed in %s credits");
        both("message.universe.encounter.receiver_space", "要在太空里开着飞船才能用", "Use it while flying a ship in space");
        both("message.universe.encounter.receiver_busy", "已经在追一个信号了", "You're already following a signal");
        both("message.universe.encounter.receiver_nothing", "这里收不到信号，换个地方再试", "No signal here. Try somewhere else");
        both("commands.universe.encounter.cleared", "收掉了 %s 处遭遇", "Removed %s encounters");
        both("commands.universe.encounter.not_piloting", "要在太空里开着飞船", "You need to be flying a ship in space");
        both("commands.universe.encounter.no_room", "前方找不到地方放", "No room ahead");
        both("commands.universe.encounter.not_in_space", "要在太空里", "You need to be in space");
        both("commands.universe.encounter.none", "你在这个星系里没有遭遇", "You have no encounter in this system");
        both("commands.universe.encounter.status", "计时 %1$s · 遭遇 %2$s · 遇险 %3$s · 守卫机 %4$s",
                "Timer %1$s · encounter %2$s · distress %3$s · sentinels %4$s");
    }

    /** 先驱者传送门（ADR-0024）。 */
    private void portal() {
        block(UniverseBlocks.PORTAL_STONE.get(), "先驱者石", "Precursor Stone");
        block(UniverseBlocks.PORTAL_GLYPH.get(), "符文石", "Glyph Stone");
        block(UniverseBlocks.PORTAL_DIAL.get(), "传送门拨号台", "Portal Dial");
        block(UniverseBlocks.PORTAL_FIELD.get(), "传送门光幕", "Portal Field");
        both("gui.universe.portal.title", "先驱者传送门", "Precursor Portal");
        both("gui.universe.portal.here", "这座门：", "This portal:");
        both("gui.universe.portal.back", "退格", "Back");
        both("gui.universe.portal.dial", "拨号", "Dial");
        both("gui.universe.portal.glyph", "符文 %s", "Glyph %s");
        both("gui.universe.portal.known", "认识 %s/%s 个符文", "%s/%s glyphs known");
        both("gui.universe.star_map.portal", "传送门：", "Portal:");
        both("message.universe.portal.dormant", "传送门没有反应", "The portal doesn't respond");
        both("message.universe.portal.unknown_glyph", "有的符文你还不认识", "You don't know all of those glyphs");
        both("message.universe.portal.no_answer", "没有回应。这个地址没有传送门", "No answer. Nothing at that address");
        both("message.universe.portal.same", "这就是这座门自己的地址", "That's this portal's own address");
        both("message.universe.portal.opened", "门开了，通往 %1$s。%3$s 秒后关闭", "Portal open to %2$s. Closes in %3$s s");
        both("message.universe.portal.arrived", "到了 %1$s", "Arrived at %2$s");
        both("commands.universe.portal.no_portal", "这里没有传送门：不是能着陆的星球", "No portal here: not a landable planet");
        both("commands.universe.portal.unknown_body", "没有这颗能着陆的星球：%s", "No landable body: %s");
        both("commands.universe.portal.glyphs", "现在认识 %s 个符文", "You now know %s glyphs");
        both("commands.universe.portal.went", "到了传送门前（%s）", "At the portal (%s)");
        both("commands.universe.portal.dialed", "拨到了 %s", "Dialed %s");
        both("commands.universe.portal.address", "%1$s 的地址：%2$s", "Address of %1$s: %2$s");
    }

    private void voyages() {
        for (ShipType type : ShipType.values()) {
            both("ship.universe.type." + type.key(), type.nameZh(), type.nameEn());
        }
        for (DriveTier tier : DriveTier.values()) {
            both("ship.universe.drive." + tier.name().toLowerCase(java.util.Locale.ROOT), tier.nameZh(), tier.nameEn());
            if (tier != DriveTier.BASIC) {
                item(UniverseItems.DRIVES.get(tier).get(), tier.nameZh(), tier.nameEn());
            }
        }
        both("tooltip.universe.drive.cadmium", "装上以后能跃迁到红巨星", "Lets the warp drive reach red giants");
        both("tooltip.universe.drive.emeril", "装上以后能跃迁到红巨星、白星和白矮星", "Reaches red giants, white stars and white dwarfs");
        both("tooltip.universe.drive.indium", "装上以后能跃迁到所有恒星，包括蓝星、中子星和黑洞",
                "Reaches every star, blue stars, neutron stars and black holes included");
        both("tooltip.universe.module.host.ship", "装在驾驶台上（开船时 B 键）", "Installs in the ship helm (B while flying)");
        both("tooltip.universe.ship_helm.summary", "右键：组装飞船并坐上去\n空手潜行右键：飞船面板（评级、技术槽）",
                "Right-click: assemble the ship and board\nSneak + right-click empty-handed: ship panel (class, tech slots)");
        both("tech.universe.stat.fuel_use", "%s 燃料效率", "%s fuel efficiency");
        both("tech.universe.stat.pulse_speed", "%s 脉冲速度", "%s pulse speed");
        both("tech.universe.stat.warp_range", "%s 光年跃迁航程", "%s ly warp range");
        both("tech.universe.stat.ship_shield", "%s 飞船护盾", "%s ship shield");
        both("tech.universe.stat.hull", "%s 船体", "%s hull");

        // 飞船面板
        both("gui.universe.ship_panel.title", "飞船", "Starship");
        both("gui.universe.ship_panel.rating", "%s · %s 级", "%s · Class %s");
        both("gui.universe.ship_panel.quality", "用料 %s / 100", "Build quality %s / 100");
        both("gui.universe.ship_panel.specialty.fighter", "专长：武器伤害 +%s%%", "Specialty: weapon damage +%s%%");
        both("gui.universe.ship_panel.specialty.hauler", "专长：船体和护盾 +%s%%", "Specialty: hull and shield +%s%%");
        both("gui.universe.ship_panel.specialty.explorer", "专长：跃迁航程 +%s%%", "Specialty: warp range +%s%%");
        both("gui.universe.ship_panel.specialty.shuttle", "专长：燃料消耗 -%s%%", "Specialty: fuel use -%s%%");
        both("gui.universe.ship_panel.specialty_none", "C 级没有专长加成", "No specialty bonus at class C");
        both("gui.universe.ship_panel.stat.hull", "船体 %s", "Hull %s");
        both("gui.universe.ship_panel.stat.shield", "护盾 %s", "Shield %s");
        both("gui.universe.ship_panel.stat.fuel", "燃料舱 %s", "Fuel capacity %s");
        both("gui.universe.ship_panel.stat.lift", "推重比 %s（1 g）", "Thrust/weight %s (1 g)");
        both("gui.universe.ship_panel.stat.impulse", "脉冲 %s 格/秒", "Impulse %s blocks/s");
        both("gui.universe.ship_panel.stat.warp", "跃迁 %s 光年", "Warp %s ly");
        both("gui.universe.ship_panel.stat.damage", "武器伤害 %s", "Weapon damage %s");
        both("gui.universe.ship_panel.stat.fire_rate", "射速 %s", "Fire rate %s");
        both("gui.universe.ship_panel.stat.cargo", "货舱 %s 格", "Cargo %s slots");
        both("gui.universe.ship_panel.drive", "驱动：%s", "Drive: %s");
        both("gui.universe.ship_panel.slots", "技术槽 %s/%s", "Tech %s/%s");
        both("gui.universe.ship_panel.hint_flying", "改了马上生效", "Changes apply at once");
        both("gui.universe.ship_panel.hint_parked", "组装起飞时生效", "Applies when you assemble the ship");
        both("hud.universe.ship.rating", "%s · %s 级", "%s · Class %s");
        both("hud.universe.ship.autopilot", "自动驾驶中，按方向键接管", "Autopilot on. Steer to take over");

        // 自动驾驶
        both("gui.universe.autopilot.title", "自动驾驶", "Autopilot");
        both("gui.universe.autopilot.hint", "选一个目的地，按方向键随时接管", "Pick a destination. Steer to take over");
        both("gui.universe.autopilot.empty", "这个恒星系里没有能去的地方", "Nowhere to go in this system");
        both("gui.universe.autopilot.entry", "%1$s · %2$s · %3$s 格", "%1$s · %2$s · %3$s blocks");
        both("gui.universe.autopilot.entry_unnamed", "%1$s · %2$s 格", "%1$s · %2$s blocks");
        both("gui.universe.autopilot.kind.planet", "星球", "Planet");
        both("gui.universe.autopilot.kind.moon", "卫星", "Moon");
        both("gui.universe.autopilot.kind.station", "空间站", "Station");
        both("gui.universe.autopilot.kind.freighter", "货船", "Freighter");
        both("gui.universe.autopilot.kind.signal", "信号", "Signal");
        both("gui.universe.autopilot.kind.black_hole", "黑洞", "Black hole");
        both("gui.universe.autopilot.black_hole", "黑洞", "Black Hole");
        both("message.universe.black_hole.flung", "被黑洞甩到了 %1$s。船体受损，护盾耗尽", "The black hole threw you out at %2$s. Hull damaged, shields down");
        both("hud.universe.ship.black_hole", "黑洞", "Black hole");
        both("gui.universe.autopilot.station", "空间站", "Space Station");
        both("gui.universe.autopilot.name", "%1$s", "%2$s");
        both("message.universe.autopilot.engaged", "自动驾驶：前往 %s", "Autopilot: heading for %s");
        both("message.universe.autopilot.off", "已退出自动驾驶", "Autopilot off");
        both("message.universe.autopilot.arrived", "已到达 %s", "Arrived at %s");
        both("message.universe.autopilot.blocked", "前面被挡住了，自动驾驶已退出", "Path blocked. Autopilot off");

        // 召唤
        both("key.universe.summon", "召唤飞船 / 自动驾驶", "Summon Ship / Autopilot");
        both("message.universe.summon.cooldown", "还要 %s 秒才能再召唤", "Summon ready in %ss");
        both("message.universe.summon.in_space", "在开阔的太空里叫不来飞船", "Can't summon a ship in open space");
        both("message.universe.summon.none", "你还没有自己的飞船", "You don't have a ship yet");
        both("message.universe.summon.parked", "你的船停成了方块，得走过去开", "Your ship is parked as blocks. Walk over and fly it");
        both("message.universe.summon.other_system", "你的船在别的恒星系", "Your ship is in another system");
        both("message.universe.summon.lost", "找不到你的船", "Couldn't find your ship");
        both("message.universe.summon.calling", "正在呼叫飞船……", "Calling your ship...");
        both("message.universe.summon.occupied", "有人在开你的船", "Someone is flying your ship");
        both("message.universe.summon.no_room", "附近没有能停船的地方", "No room to land nearby");
        both("message.universe.summon.arrived", "飞船到了", "Your ship has arrived");
        both("message.universe.summon.aboard", "你已经在船上了", "You're already aboard");

        // 着陆台
        block(UniverseBlocks.LANDING_PAD.get(), "着陆台", "Landing Pad");
        both("tooltip.universe.landing_pad.summary", "接入电网后给停在上面的船加燃料（600 W）\n召唤飞船时，船停到 64 格内你的着陆台上",
                "Refuels ships parked on it when powered (600 W)\nSummoned ships land on your pad within 64 blocks");
        both("message.universe.landing_pad.no_power", "着陆台没接电网", "The landing pad has no power");
        both("message.universe.landing_pad.empty", "着陆台空着", "The landing pad is empty");
        both("message.universe.landing_pad.fuel", "加燃料：%s / %s", "Refueling: %s / %s");

        // 飞船交易终端
        block(UniverseBlocks.SHIP_DEALER.get(), "飞船交易终端", "Starship Dealer");
        both("gui.universe.dealer.title", "飞船交易终端", "Starship Dealer");
        both("gui.universe.dealer.credits", "信用点 %s", "%s credits");
        both("gui.universe.dealer.tab.ships", "在售", "For Sale");
        both("gui.universe.dealer.tab.sell", "卖船", "Sell");
        both("gui.universe.dealer.ship", "%1$s 级%2$s", "Class %1$s %2$s");
        both("gui.universe.dealer.sold_out", "已售出", "Sold");
        both("gui.universe.dealer.buy", "买下 · %s", "Buy · %s");
        both("gui.universe.dealer.sell", "卖出 · %s", "Sell · %s");
        both("gui.universe.dealer.none", "今天没有船卖", "No ships today");
        both("gui.universe.dealer.slots", "技术槽 %s 格", "Tech slots: %s");
        both("gui.universe.dealer.tech", "自带技术", "Installed");
        both("gui.universe.dealer.no_tech", "没有自带技术", "No installed tech");
        both("gui.universe.dealer.pad_busy", "起降台上有船", "Landing pad occupied");
        both("gui.universe.dealer.poor", "信用点不够", "Not enough credits");
        both("gui.universe.dealer.no_ships", "机库里没有你的船。把船开进来停好，下船以后再看", "None of your ships are in the hangar. Land one and step out");
        both("gui.universe.dealer.has_cargo", "船上还有货", "Cargo still aboard");
        both("gui.universe.dealer.blocks", "%s 个方块", "%s blocks");
        both("gui.universe.dealer.sell_hint", "按方块价值的六成收购，驾驶台里的技术还给你", "Pays 60% of block value. Helm tech comes back to you");
        both("message.universe.dealer.bought", "买下了 %2$s 级%1$s（%3$s 信用点），停在起降台上",
                "Bought a class %2$s %1$s for %3$s credits. It's on the landing pad");
        both("message.universe.dealer.sold", "卖掉了 %2$s 级%1$s，得到 %3$s 信用点", "Sold a class %2$s %1$s for %3$s credits");
        both("message.universe.dealer.sold_out", "这艘已经卖掉了", "That ship is already sold");
        both("message.universe.dealer.poor", "还差 %s 信用点", "%s credits short");
        both("message.universe.dealer.pad_busy", "起降台上有东西，先清出来", "Clear the landing pad first");
        both("message.universe.dealer.failed", "这艘船交付不了", "Couldn't deliver the ship");
        both("message.universe.dealer.not_here", "那艘船不在机库里", "That ship isn't in the hangar");
        both("message.universe.dealer.cargo", "先把船上的货卸了", "Unload the cargo first");

        // 坠毁的飞船
        block(UniverseBlocks.DAMAGED_HELM.get(), "损坏的驾驶台", "Damaged Ship Helm");
        both("tooltip.universe.damaged_ship_helm.summary", "背包里有 4 个钛锭和 2 个电路板时右键修好", "Right-click with 4 titanium ingots and 2 circuit boards to repair");
        both("message.universe.damaged_helm.needs", "修驾驶台要 %s", "Repairing the helm takes %s");
        both("message.universe.damaged_helm.repaired", "驾驶台修好了，右键组装起飞", "Helm repaired. Right-click to assemble and fly");

        // 研究
        tech("landing_pad", "着陆台", "Landing Pad");
        tech("cadmium_drive", "镉驱动", "Cadmium Drive");
        tech("emeril_drive", "翠金驱动", "Emeril Drive");
        tech("indium_drive", "铟驱动", "Indium Drive");
        tech("hydroponics", "水培", "Hydroponics");
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
