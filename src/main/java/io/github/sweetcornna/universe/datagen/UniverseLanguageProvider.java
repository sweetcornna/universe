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
        block(UniverseBlocks.WARP_DRIVE.get(), "跃迁引擎", "Warp Drive");
        item(UniverseItems.SPACE_WARPER.get(), "空间翘曲器", "Space Warper");
        item(UniverseItems.SURVEY_DATA.get(), "勘探数据", "Survey Data");
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
        both("entity.universe.plasma_bolt", "等离子弹", "Plasma Bolt");
        both("entity.universe.sentinel", "守卫机", "Sentinel");
        both("entity.universe.heavy_sentinel", "重型守卫机", "Heavy Sentinel");
        item(UniverseItems.SENTINEL_SCRAP.get(), "守卫机残骸", "Sentinel Scrap");
        item(UniverseItems.SENTINEL_SPAWN_EGG.get(), "守卫机刷怪蛋", "Sentinel Spawn Egg");
        item(UniverseItems.HEAVY_SENTINEL_SPAWN_EGG.get(), "重型守卫机刷怪蛋", "Heavy Sentinel Spawn Egg");
        both("message.universe.sentinel.wanted", "守卫机警报！通缉等级 %s", "Sentinel alert! Wanted level %s");
        both("message.universe.sentinel.heavy", "通缉等级 %s：重型守卫机正在赶来！", "Wanted level %s: a heavy sentinel is inbound!");
        both("message.universe.sentinel.escaped", "你甩掉了守卫机", "You shook off the sentinels");
        both("message.universe.sentinel.left_planet", "离开了星球，守卫机不再追捕你", "You left the planet; the sentinels lost your trail");
        both("hud.universe.sentinel.wanted", "守卫机通缉", "SENTINELS");
        both("hud.universe.sentinel.seen", "被守卫机盯上了", "You have been spotted");
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
        both("gui.universe.matrix_lab.idle", "没有在研究。按 %s 打开科技树，选一个要研究站研究的科技。", "Not researching. Press %s to open the tech tree and pick a lab tech.");
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
        both("gui.universe.stats.waiting", "正在向服务器要数据……", "Fetching from the server…");
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
        both("gui.universe.star_map.warp.here", "已经在这个恒星系里了", "You are already in this system");
        both("gui.universe.star_map.warp.ready", "准备就绪：消耗 1 个空间翘曲器（还有 %s 个）", "Ready: uses 1 space warper (%s left)");
        both("gui.universe.star_map.warp.ready_creative", "准备就绪：创造模式不消耗空间翘曲器", "Ready: no warper needed in creative mode");
        both("message.universe.warp.not_piloting", "驾驶装有跃迁引擎的飞船进入太空后才能跃迁", "Pilot a ship with a warp drive in space to warp");
        both("message.universe.warp.not_in_space", "要先飞出大气层进入太空", "Leave the atmosphere first");
        both("message.universe.warp.busy", "跃迁引擎正在充能", "The warp drive is already spooling");
        both("message.universe.warp.no_drive", "飞船上没有跃迁引擎", "This ship has no warp drive");
        both("message.universe.warp.bad_target", "无效的跃迁目标", "Invalid warp destination");
        both("message.universe.warp.out_of_range", "超出跃迁射程：%s / %s 光年，多装几台跃迁引擎或者分几次跳",
                "Out of range: %s / %s ly. Add warp drives or jump in stages");
        both("message.universe.warp.near_star", "离恒星太近，引力井太深，飞远一点再跃迁", "Too deep in the star's gravity well, fly farther out");
        both("message.universe.warp.near_body", "离%1$s太近，飞远一点再跃迁", "Too close to %2$s, fly farther out");
        both("message.universe.warp.no_warper", "背包里没有空间翘曲器", "No space warper in your inventory");
        both("message.universe.warp.cancelled", "跃迁中断", "Warp aborted");
        both("message.universe.warp.arrived", "已抵达 %1$s 恒星系", "Arrived at the %2$s system");
        both("hud.universe.warp.spooling", "跃迁引擎充能中，目标：%s", "Warp drive spooling: %s");
        both("hud.universe.warp.spooling_unknown", "跃迁引擎充能中", "Warp drive spooling");
        both("hud.universe.ship.warp", "跃迁射程 %s 光年 · 翘曲器 %s · %s 打开星图", "Warp %s ly · warpers %s · %s for star map");
        both("message.universe.sorter.filter", "分拣器只搬：%s", "Sorter filter: %s");
        both("message.universe.sorter.no_filter", "分拣器不过滤，什么都搬", "Sorter filter cleared: moves anything");
        both("message.universe.storage_tank.empty", "储液罐是空的（能装 %s 个）", "The tank is empty (holds %s)");
        both("message.universe.storage_tank.contents", "%s：%s / %s", "%s: %s / %s");
        both("tooltip.universe.storage_tank.empty", "空的：装一种流体，最多 %s 个", "Empty: holds up to %s of one fluid");
        both("tooltip.universe.storage_tank.contents", "装着 %s %s 个（最多 %s）", "Contains %2$s %1$s (max %3$s)");
        both("message.universe.factory_anchor.placed", "工厂锚已启动：周围 %1$s×%1$s 个区块会一直加载", "Factory anchor online: %1$s×%1$s chunks stay loaded");
        both("message.universe.factory_anchor.info", "周围 %1$s×%1$s 个区块（%2$s 格见方）一直加载，这颗星球不会卸载",
                "Keeps %1$s×%1$s chunks (%2$s blocks across) loaded; this world stays loaded");
        // 物流站（ADR-0015）
        both("message.universe.station.full", "装不下了", "The station is full");
        both("message.universe.station.drones", "无人机 %s / %s", "Drones %s / %s");
        both("message.universe.station.vessels", "运输船 %s / %s", "Vessels %s / %s");
        both("message.universe.station.warpers", "空间翘曲器 %s / %s", "Space warpers %s / %s");
        both("message.universe.orbital_collector.not_in_space", "轨道采集器只能放在太空里，挨着气态巨行星",
                "Orbital collectors go in space, next to a gas giant");
        both("message.universe.orbital_collector.no_giant", "这个星系没有气态巨行星", "This system has no gas giant");
        both("message.universe.orbital_collector.too_far", "离气态巨行星%1$s还差 %3$s 格（在%4$s方向；要在大气层外 %5$s 格以内）",
                "%2$s is %3$s blocks too far (to the %4$s; must be within %5$s blocks of its atmosphere)");
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
        both("gui.universe.fractionator.idle", "没有氢：用传送带送进来", "No hydrogen: feed it by belt");
        both("gui.universe.fractionator.rate", "每秒处理 %s 个（最多 %s）", "%s per second (max %s)");
        both("gui.universe.fractionator.produced", "已分出重氢 %s 个", "Deuterium produced: %s");
        both("gui.universe.fractionator.hint", "氢从正面出去，重氢往上推", "Hydrogen exits the front, deuterium goes up");
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
        both("message.universe.multitool.overheated", "多功能工具过热！", "Multitool overheated!");
        both("message.universe.multitool.mode.mining", "多功能工具：采矿模式", "Multitool: Mining mode");
        both("message.universe.multitool.mode.combat", "多功能工具：战斗模式", "Multitool: Combat mode");
        both("hud.universe.multitool.mode.mining", "采矿", "Mining");
        both("hud.universe.multitool.mode.combat", "战斗", "Combat");
        both("item.universe.multitool.mode", "模式：%s（%s 切换）", "Mode: %s (%s to switch)");
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

        both("gui.universe.machine.unpowered", "未接入电网：放在电力感应塔的覆盖范围里", "No power: needs a tesla tower nearby");
        both("gui.universe.machine.power", "功率 %s · 满足率 %s%%", "Power %s · %s%% satisfied");
        both("gui.universe.machine.output", "输出 %s · 负荷 %s%%", "Output %s · load %s%%");
        both("gui.universe.machine.standby", "待机：已接入电网", "Standby · connected");
        both("gui.universe.machine.output_slot", "产物", "Output");
        both("gui.universe.machine.output_full", "产物格满了，接上传送带或箱子", "Output full: add a belt or chest");
        both("gui.universe.mining_machine.vein", "矿脉：%s · %s 个节点", "Vein: %s · %s nodes");
        both("gui.universe.mining_machine.remaining", "剩余储量 %s", "Reserve %s");
        both("gui.universe.mining_machine.rate", "满电产量 %s 个/分钟", "%s per minute at full power");
        both("gui.universe.mining_machine.none", "下方 %1$s×%1$s、深 3 格内没有矿", "No ore in the %1$s×%1$s×3 area below");
        both("gui.universe.mining_machine.hint", "放在地表的矿脉露头上", "Place it on a surface vein");
        both("gui.universe.water_pump.ok", "水源正常", "Water source OK");
        both("gui.universe.water_pump.no_water", "要放在水源方块上面", "Must sit on a water source");
        both("gui.universe.water_pump.acid", "这里的液体是酸液，抽不了", "This liquid is acid");
        both("gui.universe.water_pump.rate", "满电产量 %s 个/分钟", "%s per minute at full power");
        both("gui.universe.oil_extractor.rate", "涌泉产量 %s 个/秒", "Seep yield %s/s");
        both("gui.universe.oil_extractor.none", "要放在原油涌泉上面", "Must sit on a crude oil seep");
        both("gui.universe.oil_extractor.hint", "越抽越少，最低降到三成", "Drops as you pump, down to 30%");
        both("gui.universe.tesla_tower.grid", "电网：%s 座感应塔，%s 台设备", "Grid: %s towers, %s devices");
        both("gui.universe.tesla_tower.flow", "发电能力 %s · 用电 %s", "Capacity %s · Demand %s");
        both("gui.universe.tesla_tower.satisfaction", "满足率 %s%%", "Satisfied %s%%");
        both("gui.universe.tesla_tower.storage", "蓄电器 充 %s · 放 %s", "Accumulators: in %s · out %s");
        both("gui.universe.tesla_tower.range", "覆盖半径 %s 格，相距 %s 格内的塔自动相连", "Range %s, links towers within %s");
        both("gui.universe.wind_turbine.wind", "风力系数 %s", "Wind factor %s");
        both("gui.universe.wind_turbine.hint", "越高风越大，雨天、雷雨天更大", "Windier up high and in storms");
        both("gui.universe.solar_panel.ratio", "光照比例 %s", "Solar ratio %s");
        both("gui.universe.solar_panel.sun", "太阳高度 %s%%", "Sun height %s%%");
        both("gui.universe.solar_panel.no_sky", "头顶被挡住了，看不见天空", "Blocked: needs a clear view of the sky");
        both("gui.universe.solar_panel.no_sun", "这里没有阳光", "No sunlight here");
        both("gui.universe.solar_panel.rain", "下雨了，发电量只有 %s%%", "Rain cuts output to %s%%");
        both("gui.universe.solar_panel.night", "夜里不发电", "No power at night");
        both("gui.universe.solar_panel.space", "太空里没有昼夜，离恒星越近越亮", "No night in space; brighter near the star");
        both("gui.universe.solar_panel.hint", "跟着太阳转，正午发电最多", "Tracks the sun; peaks at noon");
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
        both("research.universe.missing_items", "背包里的物品不够，无法完成这项研究", "Not enough items in your inventory for this research");
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
        both("container.universe.supply_crate", "补给箱", "Supply Crate");
        both("message.universe.poi.found", "发现兴趣点：%s", "Point of interest discovered: %s");
        both("message.universe.poi.arrived", "到达航点：%s", "Waypoint reached: %s");
        both("message.universe.poi.no_signal", "这里收不到信号（信号增强器只在能登陆的星球上工作）", "No signal here (Signal Boosters only work on landable planets)");
        both("message.universe.poi.need_navigation_data", "需要一份导航数据", "Requires one Navigation Data");
        both("message.universe.poi.none_found", "%2$s 格内没有你没去过的%1$s", "No unvisited %1$s within %2$s blocks");
        both("message.universe.poi.located", "信号增强器定位到%1$s：在%2$s方约 %3$s 格处，航点已标在 HUD 上", "Signal Booster located a %1$s about %3$s blocks to the %2$s. Waypoint marked on your HUD");
        both("message.universe.drop_pod.repaired_already", "这个掉落舱已经修好了", "This drop pod has already been repaired");
        both("message.universe.drop_pod.maxed", "外骨骼的技术槽和货舱都已经到上限了", "Your exosuit's tech slots and cargo are already at maximum");
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
        both("gui.universe.terminal.read_before", "（这台终端你读过了）", "(You have read this terminal before)");
        both("gui.universe.poi.reward", "获得：%s ×%s", "Received: %s ×%s");
        both("gui.universe.poi.close", "Esc 关闭", "Esc to close");
        both("gui.universe.monolith.heading", "先驱者石碑 · 第 %s/%s 段", "PRECURSOR MONOLITH · %s of %s");
        both("gui.universe.monolith.touched_before", "（符文的光暗了下去，这段记忆已经交给你了）", "(The glyphs dim. This memory has already been given to you.)");
        both("gui.universe.signal_booster.hint", "放进一份导航数据，选一种地点，定位 %s 格内你还没去过的最近一处。", "Feed in one Navigation Data and pick a kind of site to locate the nearest one you haven't visited within %s blocks.");
        both("gui.universe.signal_booster.data", "导航数据：%s", "Navigation Data: %s");
        both("gui.universe.signal_booster.data_creative", "导航数据：创造模式不消耗", "Navigation Data: not consumed in Creative");
        both("gui.universe.signal_booster.no_data", "没有导航数据。坠毁飞船和据点的补给箱里有，读数据终端也会给，还能用电路板和纸合成。", "No Navigation Data. Find it in crashed ship and outpost crates or from data terminals, or craft it from circuit boards and paper.");
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
        both("poi.universe.lore.7.text", "最远的星系开始一个接一个地暗下去。不是熄灭，是被包了起来。我们之中有人在暗中建造，我们始终不知道是谁。",
                "The farthest systems began to dim, one after another. Not dying — being enclosed. Someone among us was building in secret, and we never learned who.");
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
        both("gui.universe.tech.no_tool", "主手拿着多功能工具时可以配置它的模块", "Hold a multitool in your main hand to configure it");
        both("gui.universe.tech.bonuses", "当前加成", "Bonuses");
        both("gui.universe.tech.no_bonuses", "还没有装模块。同一种模块最多叠三个；外骨骼模块要穿着胸甲才生效",
                "No modules installed. Up to three of a kind stack; exosuit modules need the chestplate");
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
        both("message.universe.discovery.unknown", "目镜识别不了这个目标", "The visor can't identify that");
        both("message.universe.discovery.known", "%1$s 已在发现日志里（%2$s）", "%1$s is already in the log (%2$s)");
        both("message.universe.discovery.renamed", "已改名为 %s", "Renamed to %s");
        both("message.universe.discovery.rename_unknown", "发现日志里还没有它", "That is not in the discovery log yet");
        both("message.universe.discovery.rename_not_discoverer", "只有发现者能改名", "Only the discoverer can rename it");
        both("message.universe.discovery.rename_invalid_name", "名字太长或含有不能用的字符", "That name is too long or has invalid characters");

        both("hud.universe.visor.title", "分析目镜", "Analysis Visor");
        both("hud.universe.visor.need_tool", "手持多功能工具才能打开分析目镜", "Hold a multitool to open the analysis visor");
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
        both("gui.universe.discovery.undiscovered_hint", "降落到这颗星球，或者在太空里用分析目镜看它",
                "Land on it, or analyze it with the visor from space");
        both("gui.universe.discovery.footer", "点选查看 · 发现者可以改名 · K 关闭", "Click to inspect · discoverers can rename · K to close");
        both("gui.universe.star_map.unexplored", "未探测：跃迁过去才能看到行星", "Unexplored: warp there to chart its planets");
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
        both("gui.universe.ejector.status.2", "没有这条轨道：在戴森球编辑器里新建，或换一条", "No such orbit: create one in the Dyson editor or pick another");
        both("gui.universe.ejector.status.3", "恒星不在地平线 5° 以上，等它升起来", "The star is below 5° elevation; wait for it to rise");
        both("gui.universe.ejector.status.4", "头顶被挡住了，看不见天空", "Blocked: needs a clear view of the sky");
        both("gui.universe.ejector.status.5", "这里没有恒星", "There is no star here");
        both("gui.universe.ejector.status.6", "电力不足", "Not enough power");
        // 发射井
        both("gui.universe.silo.progress", "下一枚 %s%%", "Next rocket %s%%");
        both("gui.universe.silo.structure", "结构点 %s / %s", "Structure points %s / %s");
        both("gui.universe.silo.target", "下一枚去第 %s 层", "Next rocket: layer %s");
        both("gui.universe.silo.status.0", "正在发射运载火箭", "Launching carrier rockets");
        both("gui.universe.silo.status.1", "放入小型运载火箭", "Insert small carrier rockets");
        both("gui.universe.silo.status.2", "戴森球还没有层：在编辑器里加一层", "The Dyson sphere has no layers: add one in the editor");
        both("gui.universe.silo.status.3", "能建的结构都建完了（研究应力系统能建到更高的纬度）",
                "Everything buildable is built (Stress System research opens higher latitudes)");
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
        both("gui.universe.receiver.status.1", "戴森球还没有发电：先用弹射器发射太阳帆", "The Dyson sphere produces nothing yet: launch solar sails first");
        both("gui.universe.receiver.status.2", "恒星在地平线以下", "The star is below the horizon");
        both("gui.universe.receiver.status.3", "头顶被挡住了，看不见天空", "Blocked: needs a clear view of the sky");
        both("gui.universe.receiver.status.4", "这里没有恒星", "There is no star here");
        both("gui.universe.receiver.status.5", "没有接入电网：放在电力感应塔的覆盖范围里", "No grid: place it within a tesla tower's range");
        both("gui.universe.receiver.status.6", "临界光子满了，取走一些", "Critical photons are full; take some out");
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
        both("gui.universe.dyson.empty", "这个恒星系的戴森球还是空的：新建一条戴森云轨道或一层戴森壳",
                "This system's Dyson sphere is empty: add a swarm orbit or a shell layer");
        both("gui.universe.dyson.no_system", "这里看不到恒星", "No star in sight here");
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
