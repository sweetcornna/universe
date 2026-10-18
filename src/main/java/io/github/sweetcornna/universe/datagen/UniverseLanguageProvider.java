package io.github.sweetcornna.universe.datagen;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;
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
        both("universe.bed.no_sleep_on_planet", "这颗星球的昼夜和母星不同步，床只能用来设置重生点",
                "This planet's days don't follow the homeworld's; beds here only set your respawn point");
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
        item(UniverseItems.ALIEN_CREATURE_SPAWN_EGG.get(), "异星生物刷怪蛋", "Alien Creature Spawn Egg");
        item(UniverseItems.ALIEN_FLYER_SPAWN_EGG.get(), "异星飞兽刷怪蛋", "Alien Flyer Spawn Egg");
        this.ships();
        this.ores();
        this.suit();
        this.multitool();
        this.factory();
    }

    private void ships() {
        block(UniverseBlocks.SHIP_HELM.get(), "驾驶台", "Ship Helm");
        block(UniverseBlocks.SHIP_THRUSTER.get(), "推进器", "Thruster");
        block(UniverseBlocks.FUEL_TANK.get(), "燃料舱", "Fuel Tank");
        block(UniverseBlocks.IMPULSE_ENGINE.get(), "脉冲引擎", "Impulse Engine");
        block(UniverseBlocks.SHIP_SEAT.get(), "乘客座椅", "Passenger Seat");
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
        both("universe.bed.no_sleep_in_space", "太空里不能睡觉", "You can't sleep in space");

        both("message.universe.fuel_tank.full", "燃料舱已经满了", "The fuel tank is full");
        both("message.universe.fuel_tank.level", "燃料：%s / %s", "Fuel: %s / %s");
        both("message.universe.ship.too_large", "飞船太大了（超过 %s 个方块），或者连到了地面、建筑上",
                "Too big for a ship (over %s blocks), or it is attached to the ground or a building");
        both("message.universe.ship.split_multiblock", "%s 只有一部分在船上", "Only part of the %s is on the ship");
        both("message.universe.ship.two_helms", "一艘船只能有一个驾驶台", "A ship can only have one helm");
        both("message.universe.ship.no_thrusters", "船上没有推进器，飞不起来", "This ship has no thrusters");
        both("message.universe.ship.assembled", "飞船就绪：%s 个方块，%s 吨，推重比 %s，燃料 %s/%s",
                "Ship ready: %s blocks, %s t, thrust-to-weight %s, fuel %s/%s");
        both("message.universe.ship.too_heavy", "推力不够，这艘船在这里飞不起来：多装推进器，或者换轻一些的船体板。",
                "Not enough thrust to lift off here. Add thrusters or switch to lighter hull plating.");
        both("message.universe.ship.blocked", "这里放不下飞船，找块空旷的地方再停", "Not enough room to park here; find an open spot");
        both("message.universe.ship.too_hot", "离恒星太近了！", "Too close to the star!");
        both("message.universe.ship.cannot_land", "%1$s 是气态巨行星，没有可以着陆的地面", "%2$s is a gas giant with no ground to land on");
        both("message.universe.ship.entered_planet", "进入 %1$s 的大气层", "Entering the atmosphere of %2$s");
        both("message.universe.ship.entered_space", "进入太空", "Entering space");
        both("message.universe.ship.boarded_pilot", "空格上升，Shift 下降；着陆后按住 Shift 停船",
                "Space to climb, Shift to descend; hold Shift after landing to park");
        both("message.universe.ship.boarded_passenger", "着陆后按 Shift 下船", "Press Shift after landing to get off");

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
        both("hud.universe.ship.hint.space", "一直往上飞进入太空，按住 Shift 下降着陆", "Climb to reach space, hold Shift to land");
        both("hud.universe.ship.hint.land", "飞进行星的大气层就会开始降落", "Fly into a planet's atmosphere to land");
        both("hud.universe.ship.hint.no_fuel", "燃料耗尽：着陆后给燃料舱加燃料", "Out of fuel: land and refuel the tanks");
        both("hud.universe.ship.target", "%s  %s 格", "%s  %s m");
        both("hud.universe.ship.unlandable", "（不能着陆）", " (no landing)");
        both("hud.universe.ship.above", "  ▲%s 格", "  ▲%s m");
        both("hud.universe.ship.below", "  ▼%s 格", "  ▼%s m");
    }

    private void multitool() {
        item(UniverseItems.MULTITOOL.get(), "多功能工具", "Multitool");
        item(UniverseItems.DAMAGED_MULTITOOL.get(), "损坏的多功能工具", "Damaged Multitool");
        both("message.universe.multitool.overheated", "激光过热！", "Laser overheated!");
        both("message.universe.multitool.charging", "扫描仪充能中", "Scanner recharging");
        both("message.universe.multitool.scanned", "扫描完成：%s 条矿脉，共 %s 块", "Scan complete: %s veins, %s blocks");
        both("hud.universe.multitool.overheated", "过热", "OVERHEATED");
        both("hud.universe.multitool.marker", "%s ×%s  %s 格", "%s ×%s  %sm");
        both("message.universe.crash_site.smoke", "远处%s方升起一道烟柱，大约 %s 格远。好像有什么东西坠落了。",
                "A column of smoke rises to the %s, about %s blocks away. Something has crashed.");
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
        both("hud.universe.suit.warning.no_oxygen", "氧气耗尽！", "Out of oxygen!");
        both("hud.universe.suit.warning.hazard", "%s防护即将耗尽", "%s protection low");
        both("message.universe.suit.oxygen_used", "用掉一个氧气罐：氧气 +50%", "Used an oxygen capsule: oxygen +50%");
        both("message.universe.suit.cell_used", "用掉一个防护电池：防护 +50%", "Used a hazard cell: protection +50%");
        both("death.attack.universe.vacuum", "%1$s耗尽了氧气", "%1$s ran out of oxygen");
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
        block(UniverseBlocks.THERMAL_PLANT.get(), "火力发电厂", "Thermal Power Plant");
        block(UniverseBlocks.ACCUMULATOR.get(), "蓄电器", "Accumulator");
        block(UniverseBlocks.ARC_SMELTER.get(), "电弧熔炉", "Arc Smelter");
        block(UniverseBlocks.ASSEMBLER.get(), "制造台 Mk.I", "Assembling Machine Mk.I");
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

        both("gui.universe.machine.unpowered", "未接入电网：放在电力感应塔的覆盖范围里", "No power: place within a tesla tower's range");
        both("gui.universe.machine.power", "功率 %s · 满足率 %s%%", "Power %s · %s%% satisfied");
        both("gui.universe.machine.output", "输出 %s · 负荷 %s%%", "Output %s · load %s%%");
        both("gui.universe.machine.standby", "待机：已接入电网", "Standby · connected");
        both("gui.universe.tesla_tower.grid", "电网：%s 座感应塔，%s 台设备", "Grid: %s towers, %s devices");
        both("gui.universe.tesla_tower.flow", "发电能力 %s · 用电 %s", "Capacity %s · Demand %s");
        both("gui.universe.tesla_tower.satisfaction", "满足率 %s%%", "Satisfied %s%%");
        both("gui.universe.tesla_tower.storage", "蓄电器 充 %s · 放 %s", "Accumulators: in %s · out %s");
        both("gui.universe.tesla_tower.range", "覆盖半径 %s 格，相距 %s 格内的塔自动相连", "Range %s, links towers within %s");
        both("gui.universe.wind_turbine.wind", "风力系数 %s", "Wind factor %s");
        both("gui.universe.wind_turbine.hint", "越高风越大，雨天、雷雨天更大", "Windier up high and in storms");
        both("gui.universe.thermal_plant.fuel", "炉膛", "Firebox");
        both("gui.universe.thermal_plant.hint", "只按电网需要烧燃料", "Burns only what the grid needs");
        both("gui.universe.accumulator.charging", "充电中 %s", "Charging %s");
        both("gui.universe.accumulator.discharging", "放电中 %s", "Discharging %s");
        both("gui.universe.accumulator.idle", "待机", "Idle");
        both("gui.universe.accumulator.energy", "电量 %s / %s", "Stored %s / %s");
        both("gui.universe.recipe_machine.running", "加工中", "Working");
        both("gui.universe.recipe_machine.no_recipe", "在左边选一个配方", "Select a recipe");
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
        both("research.universe.completed", "研究完成：%s", "Research complete: %s");
        both("research.universe.missing_items", "背包里的物品不够，无法完成这项研究", "Not enough items in your inventory for this research");
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
