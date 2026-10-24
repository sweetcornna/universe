package io.github.sweetcornna.universe.datagen;

/** M18 的文字：机甲的舰队和战斗（子代理 A，ADR-0033 §一、§二）。 */
final class FleetLanguage {
    /** 同时写中文和英文的一条。 */
    interface Both {
        void add(String key, String zh, String en);
    }

    private FleetLanguage() {
    }

    static void add(Both both) {
        units(both);
        research(both);
        panel(both);
        combat(both);
        messages(both);
        guide(both);
    }

    private static void units(Both both) {
        String[][] units = {
                {"prototype", "原型机", "Prototype"},
                {"precision_drone", "精准无人机", "Precision Drone"},
                {"attack_drone", "攻击无人机", "Attack Drone"},
                {"corvette", "炮艇", "Corvette"},
                {"destroyer", "驱逐舰", "Destroyer"},
        };
        for (String[] unit : units) {
            both.add("item.universe." + unit[0], unit[1], unit[2]);
            both.add("entity.universe.fleet_" + unit[0], unit[1], unit[2]);
        }
        both.add("entity.universe.plasma_shot", "等离子团", "Plasma Bolt");
        both.add("entity.universe.mecha_missile", "机甲导弹", "Mecha Missile");
        both.add("tooltip.universe.fleet_unit.ground", "地面编队用", "For ground squadrons");
        both.add("tooltip.universe.fleet_unit.space", "太空舰队用", "For space fleets");
        both.add("tooltip.universe.fleet_unit.health", "生命 %s/%s", "Health %s/%s");
        both.add("tooltip.universe.fleet_unit.weapon.laser", "激光：%s 点，每秒 %s 发，射程 %s", "Laser: %s dmg, %s/s, range %s");
        both.add("tooltip.universe.fleet_unit.weapon.twin_laser", "双联激光：%s 点，每秒 %s 发，射程 %s", "Twin laser: %s dmg, %s/s, range %s");
        both.add("tooltip.universe.fleet_unit.weapon.plasma", "等离子：%s 点，每秒 %s 发，射程 %s", "Plasma: %s dmg, %s/s, range %s");
        both.add("tooltip.universe.fleet_unit.point_defense", "近防激光：打枪骑", "Point defense: shoots Lancers");
        both.add("tooltip.universe.fleet_unit.power", "待命 %s，每发 %s", "Idle %s, %s per shot");
        String ground = "放进机甲面板「舰队」页的地面编队\n按 Z 出动或召回\n停着时用核心的电修";
        String groundEn = "Goes in a ground squadron on the mecha panel's Fleet page\nPress Z to deploy or recall\nRepairs from the core while docked";
        String space = "放进机甲面板「舰队」页的太空舰队\n在太空里按 Z 出动或召回\n停着时用核心的电修";
        String spaceEn = "Goes in a space fleet on the mecha panel's Fleet page\nPress Z in space to deploy or recall\nRepairs from the core while docked";
        both.add("tooltip.universe.prototype.summary", ground, groundEn);
        both.add("tooltip.universe.precision_drone.summary", ground, groundEn);
        both.add("tooltip.universe.attack_drone.summary", ground, groundEn);
        both.add("tooltip.universe.corvette.summary", space, spaceEn);
        both.add("tooltip.universe.destroyer.summary", space, spaceEn);
        both.add("tooltip.universe.throw.jamming", "右键扔出去：落点 %s 格内的黑雾慢 %s 秒", "Right-click to throw: slows Dark Fog within %s blocks for %s s");
        both.add("tooltip.universe.throw.water", "右键扔出去：灭掉 %s 格内的火", "Right-click to throw: puts out fire within %s blocks");
    }

    private static void research(Both both) {
        both.add("tech.universe.ground_squadron", "地面编队", "Ground Squadron");
        both.add("tech.universe.precision_drones", "精准无人机", "Precision Drones");
        both.add("tech.universe.attack_drones", "攻击无人机", "Attack Drones");
        both.add("tech.universe.space_fleet", "太空舰队", "Space Fleet");
        both.add("tech.universe.destroyers", "驱逐舰", "Destroyers");
        both.add("tech.universe.energy_shield", "能量护盾", "Energy Shield");
        both.add("tech.universe.squadron_expansion", "编队扩充", "Squadron Expansion");
        both.add("tech.universe.fleet_expansion", "舰队扩充", "Fleet Expansion");
        both.add("upgrade.universe.energy_shield.effect", "护盾容量 +%s", "Shield +%s");
        both.add("upgrade.universe.squadron_expansion.effect", "%s 个地面编队", "%s ground squadrons");
        both.add("upgrade.universe.fleet_expansion.effect", "%s 支太空舰队", "%s space fleets");
        both.add("key.universe.fleet", "出动 / 召回舰队", "Deploy / Recall Fleet");
        both.add("key.universe.shield_burst", "护盾爆发", "Shield Burst");
    }

    private static void panel(Both both) {
        both.add("gui.universe.mecha.tab.core", "核心", "Core");
        both.add("gui.universe.mecha.tab.fleet", "舰队", "Fleet");
        both.add("gui.universe.mecha.tab.combat", "战斗", "Combat");
        both.add("gui.universe.squadron.ground", "地面编队 %s", "Ground Squadron %s");
        both.add("gui.universe.squadron.space", "太空舰队 %s", "Space Fleet %s");
        both.add("gui.universe.squadron.squadrons", "编队", "Squadrons");
        both.add("gui.universe.squadron.locked", "未解锁", "Locked");
        both.add("gui.universe.squadron.out", "出动中", "Deployed");
        both.add("gui.universe.squadron.docked", "待命", "Docked");
        both.add("gui.universe.squadron.deploy", "出动", "Deploy");
        both.add("gui.universe.squadron.recall", "召回", "Recall");
        both.add("gui.universe.squadron.replenish.on", "自动补员：开", "Auto-replenish: On");
        both.add("gui.universe.squadron.replenish.off", "自动补员：关", "Auto-replenish: Off");
        both.add("gui.universe.squadron.buildings.on", "打建筑：开", "Hit structures: On");
        both.add("gui.universe.squadron.buildings.off", "打建筑：关", "Hit structures: Off");
        both.add("gui.universe.squadron.auto_deploy.on", "遇袭出动：开", "Deploy when hit: On");
        both.add("gui.universe.squadron.auto_deploy.off", "遇袭出动：关", "Deploy when hit: Off");
        both.add("gui.universe.squadron.replenish.hint", "被打掉的单位从背包和货舱里补同一种", "Refills lost units of the same kind from inventory and cargo");
        both.add("gui.universe.squadron.buildings.hint", "关着时只打黑雾的单位", "When off, only Dark Fog units are targeted");
        both.add("gui.universe.squadron.auto_deploy.hint", "被黑雾打到时自动出动", "Deploys when Dark Fog hits you");
        both.add("gui.universe.squadron.deploy.hint_ground", "在星球和主世界出动", "Deploys on planets and in the Overworld");
        both.add("gui.universe.squadron.deploy.hint_space", "只在太空出动", "Deploys in space only");
        both.add("gui.universe.squadron.busy", "召回以后才能换单位", "Recall to change units");
        both.add("gui.universe.squadron.empty.ground", "放原型机、精准无人机或攻击无人机", "Holds prototypes and drones");
        both.add("gui.universe.squadron.empty.space", "放炮艇或驱逐舰", "Holds corvettes and destroyers");
        both.add("gui.universe.squadron.stats", "待命 %s · 每秒 %s 伤害", "Idle %s · %s DPS");
        both.add("gui.universe.squadron.key", "%s：出动或召回", "%s: deploy or recall");
        both.add("gui.universe.squadron.locked.ground", "研究「地面编队」后可用", "Research Ground Squadron to unlock");
        both.add("gui.universe.squadron.locked.more_ground", "升级「编队扩充」多开一个", "Upgrade Squadron Expansion to unlock");
        both.add("gui.universe.squadron.locked.space", "研究「太空舰队」后可用", "Research Space Fleet to unlock");
        both.add("gui.universe.squadron.locked.more_space", "升级「舰队扩充」多开一个", "Upgrade Fleet Expansion to unlock");
        both.add("gui.universe.squadron.pending", "被打掉了，等着补：%s", "Lost, waiting for a %s");
    }

    private static void combat(Both both) {
        both.add("gui.universe.combat.shield", "能量护盾", "Energy Shield");
        both.add("gui.universe.combat.shield_value", "%s / %s", "%s / %s");
        both.add("gui.universe.combat.shield_bonus", "升级 +%s", "Upgrades +%s");
        both.add("gui.universe.combat.shield.hint", "护盾模块加容量，升级「能量护盾」再加", "Shield modules add capacity; Energy Shield adds more");
        both.add("gui.universe.combat.recharge", "不满时用机甲核心的电回充：每点 %s J，每秒最多 %s 点", "Recharges from the mecha core when not full: %s J per point, up to %s per second");
        both.add("gui.universe.combat.burst", "护盾爆发", "Shield Burst");
        both.add("gui.universe.combat.burst_hint", "按住 %s 蓄力，松开放出", "Hold %s, release to fire");
        both.add("gui.universe.combat.burst_cost", "蓄满 %s 秒用掉一半护盾", "%ss full charge: half the shield");
        both.add("gui.universe.combat.burst_damage", "伤害 ×%s，半径 %s 格", "×%s damage, %s-block radius");
        both.add("gui.universe.combat.burst_cooldown", "冷却 %s 秒", "%s s cooldown");
        both.add("gui.universe.combat.weapons", "武器", "Weapons");
        both.add("gui.universe.combat.weapon.kinetic", "动能炮", "Kinetic Gun");
        both.add("gui.universe.combat.weapon.missile", "导弹舱", "Missile Pod");
        both.add("gui.universe.combat.weapon.plasma", "等离子炮", "Plasma Gun");
        both.add("gui.universe.combat.mode.auto", "自动", "Auto");
        both.add("gui.universe.combat.mode.aim", "瞄准", "Aim");
        both.add("gui.universe.combat.mode.off", "关", "Off");
        both.add("gui.universe.combat.mode.hint.auto", "自动：打射程里最近的敌人，打你的优先", "Auto: nearest enemy in range, attackers first");
        both.add("gui.universe.combat.mode.hint.aim", "瞄准：只打准星对着的", "Aim: only what's under your crosshair");
        both.add("gui.universe.combat.mode.hint.off", "关：不开火", "Off: holds fire");
        both.add("gui.universe.combat.ammo", "装着 %s 发 · 备弹 %s", "%s loaded · %s spare");
        both.add("gui.universe.combat.no_ammo", "没有弹药", "No ammo");
        both.add("gui.universe.combat.stats", "射程 %s · 每秒 %s 发", "Range %s · %s/s");
        both.add("gui.universe.combat.uses.kinetic", "用弹箱", "Uses ammo boxes");
        both.add("gui.universe.combat.uses.missile", "用导弹组，会追目标", "Uses missile sets; homing");
        both.add("gui.universe.combat.uses.plasma", "用等离子、反物质胶囊；星球上要飞着或打高空目标", "Uses plasma and antimatter capsules; on planets only "
                + "while flying or at high targets");
        both.add("gui.universe.combat.ammo_damage", "%s：%s 点", "%s: %s dmg");
        both.add("gui.universe.combat.shot_energy", "每发 %s", "%s per shot");
        both.add("gui.universe.combat.sneak", "潜行时不开火", "Sneak to hold fire");
        both.add("hud.universe.burst.weak", "护盾不够", "Shield too low");
    }

    private static void messages(Both both) {
        both.add("message.universe.squadron.none", "编队里没有单位", "No units in your squadrons");
        both.add("message.universe.squadron.locked", "还没研究编队", "Squadrons not researched yet");
        both.add("message.universe.squadron.deployed_ground", "地面编队出动", "Ground squadrons deployed");
        both.add("message.universe.squadron.deployed_space", "太空舰队出动", "Space fleet deployed");
        both.add("message.universe.squadron.no_chest", "要穿外骨骼胸甲", "Needs the exosuit chestplate");
        both.add("message.universe.squadron.space_only", "太空舰队只在太空出动", "Space fleets deploy in space only");
        both.add("message.universe.squadron.ground_only", "地面编队只在星球上出动", "Ground squadrons deploy on planets only");
        both.add("message.universe.squadron.no_power", "机甲核心没电", "Mecha core is empty");
        both.add("message.universe.squadron.empty", "这个编队是空的", "This squadron is empty");
        both.add("message.universe.squadron.deployed", "%s 出动：%s 架", "%s deployed: %s units");
        both.add("message.universe.squadron.recalled", "%s 已召回", "%s recalled");
        both.add("message.universe.squadron.lost", "%s 被击毁", "%s destroyed");
        both.add("message.universe.squadron.starved", "核心没电，舰队召回了", "Out of energy. Fleet recalled");
    }

    private static void guide(Both both) {
        both.add("guide.universe.survival.fleet.title", "地面编队", "Ground Squadrons");
        both.add("guide.universe.survival.fleet",
                "研究「地面编队」以后，机甲面板「舰队」页的编队格能放单位：一个编队 6 格，放原型机、精准无人机、攻击无人机。按 Z 出动或召回，"
                        + "面板里也能一个个编队地出动。\n\n"
                        + "跟在身后，打 24 格内的黑雾，你正在打的先打\n"
                        + "离你 48 格以外不追\n"
                        + "待命和开火都用核心的电，没电 10 秒自动召回\n"
                        + "被打掉就没了；开着自动补员时从背包和货舱里补同一种\n"
                        + "停着时用核心的电修，每点生命 50 J\n"
                        + "升级「编队扩充」最多 3 个编队",
                "With Ground Squadron researched, the Fleet page of the mecha panel takes units: 6 slots a squadron, for prototypes and drones. "
                        + "Press Z to deploy or recall, or deploy each squadron from the panel.\n\n"
                        + "They follow you and attack Dark Fog within 24 blocks, your current target first\n"
                        + "They give up past 48 blocks from you\n"
                        + "Idling and firing draw on the core; 10 s without energy recalls them\n"
                        + "Lost units are gone; auto-replenish refills from inventory and cargo\n"
                        + "Docked units repair from the core, 50 J per health\n"
                        + "Squadron Expansion: up to 3 squadrons");
        both.add("guide.universe.survival.space_fleet.title", "太空舰队", "Space Fleet");
        both.add("guide.universe.survival.space_fleet",
                "研究「太空舰队」以后能编炮艇和驱逐舰，一支舰队 3 格，只在太空出动。开着飞船时跟在船后面。\n\n"
                        + "炮艇：双联激光，射程 64\n"
                        + "驱逐舰：等离子主炮，射程 96；近防激光打枪骑\n"
                        + "打 96 格内的黑雾，160 格以外不追\n"
                        + "打开「打建筑」去拆巢穴\n"
                        + "升级「舰队扩充」开第二支",
                "Space Fleet lets you field corvettes and destroyers, 3 slots a fleet, in space only. They trail your ship while you fly it.\n\n"
                        + "Corvette: twin lasers, range 64\n"
                        + "Destroyer: plasma main gun, range 96; point defense shoots Lancers\n"
                        + "They attack Dark Fog within 96 blocks and give up past 160\n"
                        + "Turn on Hit structures to take down hives\n"
                        + "Fleet Expansion: a second fleet");
        both.add("guide.universe.survival.mecha_weapons.title", "机甲武器", "Mecha Weapons");
        both.add("guide.universe.survival.mecha_weapons",
                "外骨骼胸甲自带三门武器，用背包和货舱里的炮塔弹药，每发 40 J。\n\n"
                        + "动能炮：弹箱，射程 24，每秒 4 发\n"
                        + "导弹舱：导弹组，射程 40，每 2 秒 1 发，会追\n"
                        + "等离子炮：等离子、反物质胶囊，射程 48，每秒 1 发；星球上要飞着或打高空目标\n"
                        + "伤害是炮塔的六成\n"
                        + "在机甲面板「战斗」页设自动、瞄准或关；潜行时不开火",
                "The exosuit chestplate has three guns that use turret ammo from your inventory and cargo, 40 J a shot.\n\n"
                        + "Kinetic gun: ammo boxes, range 24, 4 shots/s\n"
                        + "Missile pod: missile sets, range 40, one every 2 s, homing\n"
                        + "Plasma cannon: plasma or antimatter capsules, range 48, 1 shot/s; on planets only while flying or at high targets\n"
                        + "Damage is 60% of the turret's\n"
                        + "Set each to Auto, Aim or Off on the Combat page; sneaking holds fire");
        both.add("guide.universe.survival.shield_burst.title", "护盾和爆发", "Shield and Burst");
        both.add("guide.universe.survival.shield_burst",
                "护盾不满时，机甲核心有电就帮着回充：每点 200 J，每秒最多 4 点，挨打后照样等 5 秒。升级「能量护盾」每级 +20。\n\n"
                        + "按住左 Alt 蓄力，最多 2 秒，松开放出\n"
                        + "用掉一半护盾，6 格内的黑雾和怪物挨用掉的 ×2，被推开\n"
                        + "太空里半径 ×3，伤害多五成\n"
                        + "冷却 5 秒，不伤玩家和中立生物\n"
                        + "干扰胶囊右键扔，6 格内的黑雾慢 8 秒\n"
                        + "穿着胸甲潜行右键扔水瓶，灭掉 3 格内的火",
                "When the shield is down, the mecha core tops it up: 200 J a point, up to 4 a second, after the usual 5 s delay. "
                        + "Energy Shield adds 20 a level.\n\n"
                        + "Hold Left Alt to charge (2 s max), release to fire\n"
                        + "Spends half the shield; Dark Fog and monsters within 6 blocks take twice that and are knocked back\n"
                        + "In space: radius ×3, damage +50%\n"
                        + "5 s cooldown; players and neutral mobs are safe\n"
                        + "Right-click to throw a jamming capsule: Dark Fog within 6 blocks slows for 8 s\n"
                        + "Sneak and right-click a water bottle (chestplate on) to throw it: puts out fire within 3 blocks");
    }
}
