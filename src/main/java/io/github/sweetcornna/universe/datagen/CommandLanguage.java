package io.github.sweetcornna.universe.datagen;

/**
 * {@code /universe} 调试命令的反馈。原来写死在各个命令类里的中文，2026-10-20 改成了语言键，这里补上中英文两份。
 * 键的前缀都是 {@code commands.universe.}；后来加的命令（黑雾、聚落、钓鱼、深空等）的键在各自的语言类里。
 */
final class CommandLanguage {
    /** 同时写中文和英文的一条。 */
    interface Both {
        void add(String key, String zh, String en);
    }

    private static final String C = "commands.universe.";

    private CommandLanguage() {
    }

    static void add(Both both) {
        planet(both);
        worldAndStation(both);
        shipAndDyson(both);
        researchStoryMission(both);
        poiSentinelPerf(both);
    }

    /** 星球：/universe planet。 */
    private static void planet(Both both) {
        both.add(C + "planet.not_planet", "不是可着陆的星球：%s", "Not a landable planet: %s");
        both.add(C + "planet.systems", "星团共 %s 个恒星系、%s 个天体：", "%s star systems and %s bodies in the cluster:");
        both.add(C + "planet.systems.entry", "%1$s %2$s（%3$s）%4$s，天体 %5$s 个，可着陆 %6$s 个", "%1$s %3$s (%2$s): %4$s, %5$s bodies, %6$s landable");
        both.add(C + "planet.no_system", "没有编号为 %s 的恒星系", "No star system #%s");
        both.add(C + "planet.bodies", "%s 恒星系：", "Bodies in the %s system:");
        both.add(C + "planet.bodies.entry", "%1$s %2$s（%3$s）%4$s，%5$s °C，重力 %6$s g%7$s", "%1$s %3$s (%2$s): %4$s, %5$s °C, gravity %6$s g%7$s");
        both.add(C + "planet.bodies.not_landable", "，不可着陆", ", not landable");
        both.add(C + "planet.weather", "星球天气设为 %s，持续 %s 秒", "Set the planet's weather to %s for %s seconds");
        both.add(C + "planet.flora", "%s 的植物：地面植物 %s 种，树 %s 种", "Flora on %s: %s plant species, %s tree species");
        both.add(C + "planet.flora.plant", "%s（%s #%s）%s 生境 %s，多度 %s，颜色 %s / %s", "%s (%s #%s) %sbiotopes %s, abundance %s, colors %s / %s");
        both.add(C + "planet.flora.glowing", "发光，", "glowing, ");
        both.add(C + "planet.flora.tree", "%s（%s #%s）高 %s–%s，冠 %s%s，生境 %s，%s", "%s (%s #%s) height %s–%s, canopy %s%s, biotopes %s, %s");
        both.add(C + "planet.flora.thick", "，粗干", ", thick trunk");
        both.add(C + "planet.flora.bloom.glowing", "开发光的花", "glowing flowers");
        both.add(C + "planet.flora.bloom.plain", "开花", "flowers");
        both.add(C + "planet.flora.bloom.none", "不开花", "no flowers");
        both.add(C + "planet.fauna", "%s 的动物：%s 种；周围 64 格内 %s 只（飞兽 %s），上限 %s", "Fauna on %s: %s species; %s within 64 blocks (%s flying), cap %s");
        both.add(C + "planet.fauna.species", "#%s %s（%s，%s）大小 %s，生命 %s，速度 %s%s，%s–%s 只一群，生境 %s，花纹 %s%s%s",
                "#%s %s (%s, %s) size %s, health %s, speed %s%s, groups of %s–%s, biotopes %s, pattern %s%s%s");
        both.add(C + "planet.fauna.attack", "，攻击 %s", ", attack %s");
        both.add(C + "planet.fauna.glowing_pattern", "（发光）", " (glowing)");
        both.add(C + "planet.fauna.glowing_eyes", "，眼睛发光", ", glowing eyes");
        both.add(C + "planet.fauna.husbandry", "；%s，产%s×%s%s", "; %s, produces %s×%s%s");
        both.add(C + "planet.fauna.rideable", "，驯服后能骑", ", rideable when tamed");
        both.add(C + "planet.fauna.no_species", "这里只有 %s 种动物（编号 0–%s）", "Only %s species here (indices 0–%s)");
        both.add(C + "planet.fauna.spawned", "刷出了 %s 只 %s", "Spawned %s %s");
        both.add(C + "planet.fauna.lineup", "排出了 %s/%s 种动物", "Lined up %s/%s species");
        both.add(C + "planet.sky", "%s 的天空（第 %s 天）：", "Sky over %s (day %s):");
        both.add(C + "planet.sky.body", "%s %s：经度 %s°，纬度 %s°，视直径 %s°，亮面 %s%%", "%s %s: longitude %s°, latitude %s°, apparent diameter %s°, lit %s%%");
        both.add(C + "planet.info.separator", "、", ", ");
        both.add(C + "planet.info.no_hazards", "无", "none");
        both.add(C + "planet.info.resource", "%s %s%%", "%s %s%%");
        both.add(C + "planet.info.title", "%1$s（%2$s）  %3$s  %4$s", "%2$s (%1$s)  %3$s  %4$s");
        both.add(C + "planet.info.climate", "半径 %s 倍母星，重力 %s g，大气 %s %s atm，温度 %s °C（昼夜 ±%s），液体 %s",
                "Radius %s× home planet, gravity %s g, atmosphere %s %s atm, temperature %s °C (day/night ±%s), liquid %s");
        both.add(C + "planet.info.weather", "昼夜 %s，天气 %s（频率 %s，风 %s%s），危害 %s", "Day cycle %s, weather %s (frequency %s, wind %s%s), hazards %s");
        both.add(C + "planet.info.lightning", "，有雷暴", ", thunderstorms");
        both.add(C + "planet.info.terrain", "地形：海平面 %s，基准 %s，大陆尺度 %s，起伏 %s，丘陵 %s，粗糙 %s，山脊 %s，台地 %s，陨坑 %s，洞穴 %s，悬垂 %s，尖塔 %s%s",
                "Terrain: sea level %s, base height %s, continent scale %s, relief %s, hills %s, roughness %s, ridges %s, terraces %s, craters %s, caves %s, overhangs %s, spires %s%s");
        both.add(C + "planet.info.floating_islands", "，有浮空岛", ", floating islands");
        both.add(C + "planet.info.ecology", "生态：植被 %s，树木 %s，动物 %s，植物 %s 种，动物 %s 种",
                "Ecology: vegetation %s, trees %s, fauna %s, %s plant species, %s animal species");
        both.add(C + "planet.info.resources", "资源：%s", "Resources: %s");
        both.add(C + "planet.tp.failed", "传送失败：%s", "Teleport failed: %s");
        both.add(C + "planet.tp", "已传送到 %s (%s, %s, %s)", "Teleported to %s (%s, %s, %s)");
        both.add(C + "planet.created", "创建维度 %s 用时 %s ms", "Created dimension %s in %s ms");
        both.add(C + "planet.bench", "%s：生成 %s 个区块用时 %s ms，平均 %s ms/区块（区块坐标 %s, %s 起）",
                "%s: generated %s chunks in %s ms, %s ms per chunk (from chunk %s, %s)");
        both.add(C + "planet.weather.clear", "晴", "clear");
        both.add(C + "planet.weather.normal", "降水", "precipitation");
        both.add(C + "planet.weather.storm", "风暴", "storm");
        both.add(C + "planet.biotope.meadow", "草原", "meadow");
        both.add(C + "planet.biotope.forest", "林地", "forest");
        both.add(C + "planet.biotope.wetland", "湿地", "wetland");
        both.add(C + "planet.biotope.arid", "旱地", "arid");
        both.add(C + "planet.biotope.cold", "寒地", "cold");
        both.add(C + "planet.biotope.fungal", "菌林", "fungal");
        both.add(C + "planet.biotope.crystal", "晶簇", "crystal");
        both.add(C + "planet.biotope.waste", "荒地", "barren");
        both.add(C + "planet.pattern.none", "无", "none");
        both.add(C + "planet.pattern.stripes", "条纹", "stripes");
        both.add(C + "planet.pattern.spots", "斑点", "spots");
        both.add(C + "planet.pattern.dorsal_band", "背纹", "dorsal band");
        both.add(C + "planet.pattern.saddle", "鞍纹", "saddle");
        both.add(C + "planet.pattern.gradient", "渐变", "gradient");
        both.add(C + "planet.sky.kind.star", "恒星", "star");
        both.add(C + "planet.sky.kind.planet", "行星", "planet");
        both.add(C + "planet.sky.kind.moon", "卫星", "moon");
        both.add(C + "planet.sky.kind.parent", "母行星", "parent planet");
        both.add(C + "planet.sky.kind.sibling", "其他卫星", "other moon");
        both.add(C + "planet.day_cycle.short", "10 分钟", "10 min");
        both.add(C + "planet.day_cycle.standard", "20 分钟", "20 min");
        both.add(C + "planet.day_cycle.long", "40 分钟", "40 min");
        both.add(C + "planet.day_cycle.very_long", "80 分钟", "80 min");
        both.add(C + "planet.day_cycle.locked_day", "潮汐锁定（正午）", "tidally locked (noon)");
        both.add(C + "planet.day_cycle.locked_twilight", "潮汐锁定（黄昏）", "tidally locked (dusk)");
        both.add(C + "planet.day_cycle.locked_night", "潮汐锁定（午夜）", "tidally locked (midnight)");
    }

    /** 维度、坠毁点、空间站、信用点：/universe dim、crash_site、station、credits。 */
    private static void worldAndStation(Both both) {
        both.add(C + "dim.created", "新建 %s 个维度，已有或重新加载 %s 个，用时 %s ms", "Created %s dimensions (%s already existed or were reloaded) in %s ms");
        both.add(C + "dim.invalid_name", "维度名称只能包含小写字母、数字和 _-./：%s", "Invalid dimension name %s: use only lowercase letters, digits and _-./");
        both.add(C + "dim.list", "本模组创建的维度 %s 个：", "Dimensions created by this mod (%s):");
        both.add(C + "dim.list.unloaded", "%s  %s  未加载", "%s  %s  not loaded");
        both.add(C + "dim.list.loaded", "%s  %s  已加载，区块 %s，玩家 %s", "%s  %s  loaded, %s chunks, %s players");
        both.add(C + "dim.list.unloading", "%s  %s  卸载中，区块 %s，玩家 %s", "%s  %s  unloading, %s chunks, %s players");
        both.add(C + "dim.loaded", "已加载 %s，用时 %s ms", "Loaded %s in %s ms");
        both.add(C + "dim.stats", "堆内存已用 %s MB（已分配 %s MB，上限 %s MB）；维度 %s 个，其中运行时维度 %s 个；已加载区块 %s 个",
                "Heap used: %s MB (%s MB committed, %s MB max); %s dimensions, %s of them runtime; %s chunks loaded");
        both.add(C + "dim.stats.gc", "堆内存已用 %s MB（已分配 %s MB，上限 %s MB），已请求 GC；维度 %s 个，其中运行时维度 %s 个；已加载区块 %s 个",
                "Heap used: %s MB (%s MB committed, %s MB max), GC requested; %s dimensions, %s of them runtime; %s chunks loaded");
        both.add(C + "dim.tp", "已传送到 %s", "Teleported to %s");
        both.add(C + "dim.tp.failed", "传送失败：%s", "Could not teleport to %s");
        both.add(C + "dim.unknown", "未知维度：%s", "Unknown dimension: %s");
        both.add(C + "dim.unload", "开始卸载 %s，区块卸完后关闭", "Unloading %s; it will close once its chunks are unloaded");
        both.add(C + "dim.unload.all", "开始卸载 %s 个维度", "Unloading %s dimensions");
        both.add(C + "dim.unload.failed", "无法卸载 %s：不是本模组创建的、没有加载、已在卸载，或者是世界出生点所在的维度",
                "Cannot unload %s: not created by this mod, not loaded, already unloading, or holds the world spawn");
        both.add(C + "crash_site.located", "坠毁点在主世界 %s, %s, %s", "The crash site is at %s, %s, %s in the Overworld");
        both.add(C + "crash_site.none", "还没有放置坠毁点", "No crash site has been placed yet");
        both.add(C + "crash_site.placed", "已在 %s, %s, %s 放置坠毁点", "Placed a crash site at %s, %s, %s");
        both.add(C + "station.info", "恒星系 %s 的空间站：%s, %s, %s，机库朝%s", "Space station in system %s: %s, %s, %s, hangar facing %s");
        both.add(C + "station.no_system", "这里不属于任何恒星系", "Not in a star system");
        both.add(C + "station.none", "这个恒星系没有空间站", "No space station in this system");
        both.add(C + "station.tp", "已传送到恒星系 %s 的空间站", "Teleported to the space station in system %s");
        both.add(C + "station.tp.failed", "传送失败", "Teleport failed");
        both.add(C + "credits", "信用点：%s", "Credits: %s");
    }

    /** 飞船和戴森球：/universe ship、dyson。 */
    private static void shipAndDyson(Both both) {
        both.add(C + "ship.not_helm", "那里不是驾驶台", "That is not a ship helm");
        both.add(C + "ship.not_on_ship", "你不在飞船上", "You are not on a ship");
        both.add(C + "ship.no_system", "没有这个编号的恒星系", "No star system with that number");
        both.add(C + "ship.parked", "已停船", "Parked the ship");
        both.add(C + "ship.park_blocked", "这里放不下飞船", "No room to park the ship here");
        both.add(C + "ship.fuel", "燃料 %s / %s", "Fuel %s / %s");
        both.add(C + "ship.damaged", "船体 %s/%s，护盾 %s/%s", "Hull %s/%s, shield %s/%s");
        both.add(C + "ship.unharmed", "飞船没有受伤", "The ship took no damage");
        both.add(C + "ship.repaired", "已修好飞船", "Repaired the ship");
        both.add(C + "ship.warped", "跃迁到 %s（%s）", "Warped to %s (%s)");
        both.add(C + "ship.info", "%s 个方块，%s 吨，推进器 %s，燃料舱 %s，脉冲引擎 %s（%s 格/刻），跃迁引擎 %s（%s 光年），推重比(1g) %s，燃料 %s/%s",
                "%s blocks, %s t, thrusters %s, fuel tanks %s, impulse engines %s (%s blocks/tick), warp drives %s (%s ly), thrust/weight (1 g) %s, fuel %s/%s");
        both.add(C + "ship.info.combat", "船体 %s/%s，护盾 %s/%s，光子炮 %s，采矿光束 %s", "Hull %s/%s, shield %s/%s, photon cannons %s, mining beams %s");
        both.add(C + "ship.info.landed", "位置 %s %s %s，偏航 %s，速度 %s，着陆", "Position %s %s %s, yaw %s, speed %s, landed");
        both.add(C + "ship.info.flying", "位置 %s %s %s，偏航 %s，速度 %s，飞行", "Position %s %s %s, yaw %s, speed %s, flying");
        both.add(C + "dyson.no_star", "这里没有恒星", "No star here");
        both.add(C + "dyson.failed", "没有改成：编号不存在或超出上限", "Nothing changed: no such ID or over the limit");
        both.add(C + "dyson.info", "恒星系 %s：光照系数 %s，半径上限 %s AU，能建纬度 ±%s°，发电 %s 焦/刻（戴森云 %s，戴森壳 %s），接收站取走 %s，在飞火箭 %s",
                "System %s: light factor %s, max radius %s AU, buildable latitude ±%s°, generating %s J/tick (swarm %s, shells %s), receivers draw %s, rockets in flight %s");
        both.add(C + "dyson.info.orbit", "轨道 %s：半径 %s，倾角 %s°，升交点 %s°，%s 张帆", "Orbit %s: radius %s, inclination %s°, ascending node %s°, %s sails");
        both.add(C + "dyson.info.layer", "第 %s 层：半径 %s，%s×%s，结构点 %s/%s，单元点 %s/%s",
                "Layer %s: radius %s, %s×%s, structure points %s/%s, cell points %s/%s");
        both.add(C + "dyson.orbit_added", "新建了戴森云轨道 %s", "Created swarm orbit %s");
        both.add(C + "dyson.layer_added", "新建了戴森壳第 %s 层", "Created shell layer %s");
        both.add(C + "dyson.completed", "第 %s 层能建的部分已经建满", "Finished the buildable part of layer %s");
        both.add(C + "dyson.sails", "往轨道 %1$s 加了 %2$s 张帆", "Added %2$s sails to orbit %1$s");
        both.add(C + "dyson.cleared", "戴森球已清空", "Cleared the Dyson sphere");
    }

    /** 研究、剧情、任务：/universe research、story、mission。 */
    private static void researchStoryMission(Both both) {
        both.add(C + "none", "（无）", "(none)");
        both.add(C + "research.granted", "已完成科技 %s", "Completed tech %s");
        both.add(C + "research.granted_all", "已完成 %s 个科技", "Completed %s techs");
        both.add(C + "research.level", "%s 设为 %s 级", "Set %s to level %s");
        both.add(C + "research.points", "加了 %s 点", "Added %s research points");
        both.add(C + "research.revoked", "已收回科技 %s", "Revoked tech %s");
        both.add(C + "research.revoked_all", "已清空研究进度", "Cleared all research progress");
        both.add(C + "research.status.current", "正在研究：%s", "Researching: %s");
        both.add(C + "research.status.done", "已完成：%s", "Completed: %s");
        both.add(C + "research.status.levels", "升级：%s", "Upgrades: %s");
        both.add(C + "research.status.paused", "暂停的：%s", "Paused: %s");
        both.add(C + "research.unknown", "未知科技：%s", "Unknown tech: %s");
        both.add(C + "story.cleared", "剧情进度已清空", "Cleared story progress");
        both.add(C + "story.forgot", "忘掉了 %s", "Forgot milestone %s");
        both.add(C + "story.status", "剧情节点：%s", "Story milestones: %s");
        both.add(C + "story.unknown", "未知节点：%s", "Unknown milestone: %s");
        both.add(C + "story.already", "%s 已经到过", "Already reached %s");
        both.add(C + "story.reached", "到达 %s", "Reached %s");
        both.add(C + "mission.active", "手上：%s %s，进度 %s；今天做完 %s 个", "Current mission: %s %s, progress %s; %s completed today");
        both.add(C + "mission.advance", "进度 %s", "Progress: %s");
        both.add(C + "mission.advance.failed", "手上没有能推进度的任务（送货要真的带东西来）", "No mission to advance (deliveries need the actual items)");
        both.add(C + "mission.describe", "%s ×%s，报酬 %s", "%s ×%s, reward %s credits");
        both.add(C + "mission.describe.bonus", "%s ×%s，报酬 %s，另给 %s ×%s", "%s ×%s, reward %s credits, plus %s ×%s");
        both.add(C + "mission.idle", "手上没有任务；今天做完 %s 个", "No current mission; %s completed today");
        both.add(C + "mission.offer", "任务 %s：%s", "Mission %s: %s");
        both.add(C + "mission.reset", "任务记录已清空", "Cleared mission records");
        both.add(C + "mission.bonus.navigation_data", "导航数据", "Navigation Data");
        both.add(C + "mission.bonus.module", "技术模块", "tech module");
    }

    /** 兴趣点、守卫机、计时和参照基地：/universe poi、sentinel、perf、bench。 */
    private static void poiSentinelPerf(Both both) {
        both.add(C + "poi.build.failed", "脚下是液体，没有放置", "Liquid underfoot, nothing placed");
        both.add(C + "poi.built", "已在脚下放置%s", "Placed %s at your feet");
        both.add(C + "poi.list", "%1$s 格内有 %2$s 个兴趣点：", "%2$s points of interest within %1$s blocks:");
        both.add(C + "poi.list.entry", "%s (%s, ~%s, %s) %s 格", "%s (%s, ~%s, %s) %s blocks away");
        both.add(C + "poi.list.visited", "%s (%s, ~%s, %s) %s 格，去过", "%s (%s, ~%s, %s) %s blocks away, visited");
        both.add(C + "poi.not_found", "%2$s 格内没有%1$s", "No %1$s within %2$s blocks");
        both.add(C + "poi.not_planet", "这里不是能登陆的星球，没有兴趣点", "No points of interest here: not a landable planet");
        both.add(C + "poi.tp", "已传送到%s前方 (%s, %s, %s)", "Teleported in front of the %s (%s, %s, %s)");
        both.add(C + "poi.unknown_type", "未知的兴趣点种类：%s", "Unknown point of interest type: %s");
        both.add(C + "poi.waypoint", "航点：%s %s, %s, %s", "Waypoint: %s at %s, %s, %s");
        both.add(C + "sentinel.planet", "%s %s（%s）守卫：%s", "%s %s (%s) sentinels: %s");
        both.add(C + "sentinel.spawn.failed", "附近找不到地方放守卫机", "No room nearby for a sentinel");
        both.add(C + "sentinel.spawned", "在 %s %s %s 放了一架守卫机", "Spawned a sentinel at %s, %s, %s");
        both.add(C + "sentinel.spawned_heavy", "在 %s %s %s 放了一架重型守卫机", "Spawned a heavy sentinel at %s, %s, %s");
        both.add(C + "sentinel.status", "守卫强度：%s（巡逻 %s 架，警戒倍数 %s）；警戒值 %s，通缉 %s 级，脱离 %s%%",
                "Sentinel activity: %s (%s patrols, alert multiplier %s); alert %s, wanted level %s, escape %s%%");
        both.add(C + "sentinel.unknown_offense", "未知的违规行为：%s", "Unknown offense: %s");
        both.add(C + "perf.line", "%s %s ms  %s 次  %s%%", "%s %s ms  %s calls  %s%%");
        both.add(C + "perf.more", "……另有 %s 项", "…and %s more");
        both.add(C + "perf.no_data", "没有记录，先用 /universe perf start", "Nothing recorded: run /universe perf start first");
        both.add(C + "perf.no_tick", "还没记到完整的一刻", "No full tick recorded yet");
        both.add(C + "perf.started", "开始分项计时", "Started per-section timing");
        both.add(C + "perf.stopped", "停止分项计时", "Stopped per-section timing");
        both.add(C + "perf.summary", "%s 刻，服务器每刻 %s ms，本模组合计 %s ms（%s%%）", "%s ticks, server %s ms per tick, this mod %s ms (%s%%)");
        both.add(C + "bench.built", "参照基地：传送带 %s 格，制造台 %s 台，分拣器 %s 个，强制加载 %s 个区块",
                "Benchmark base: %s belts, %s assemblers, %s sorters, %s chunks force-loaded");
        both.add(C + "bench.built_chemistry", "参照基地：传送带 %s 格，制造台 %s 台，分拣器 %s 个，化工设备 %s 台，强制加载 %s 个区块",
                "Benchmark base: %s belts, %s assemblers, %s sorters, %s chemical plants, %s chunks force-loaded");
        both.add(C + "bench.status", "参照基地：%s/%s 台在做，已做出 %s 个，每分钟 %s 个", "Benchmark base: %s/%s machines working, %s made, %s per minute");
        both.add(C + "bench.stopped", "停止了 %s 座参照基地的补给", "Stopped resupplying %s benchmark bases");
    }
}
