package io.github.sweetcornna.universe.datagen;

/** M19 的文字：深空和引力抓取器（子代理 D，ADR-0034 §四、§七）。 */
final class DeepSpaceLanguage {
    /** 同时写中文和英文的一条。 */
    interface Both {
        void add(String key, String zh, String en);
    }

    private DeepSpaceLanguage() {
    }

    static void add(Both both) {
        blocks(both);
        items(both);
        hulks(both);
        ship(both);
        gravity(both);
        commands(both);
        guide(both);
    }

    private static void blocks(Both both) {
        both.add("block.universe.derelict_plating", "残骸舱板", "Derelict Hull Plating");
        both.add("block.universe.derelict_frame", "残骸桁架", "Derelict Truss");
        both.add("block.universe.derelict_radiator", "残骸散热板", "Derelict Radiator Panel");
        both.add("block.universe.derelict_tank", "残骸推进剂罐", "Derelict Propellant Tank");
        both.add("block.universe.derelict_conduit", "残骸导管", "Derelict Conduit");
        both.add("block.universe.derelict_cargo", "残骸货舱", "Derelict Cargo Pod");
        both.add("block.universe.reactor_shielding", "反应堆屏蔽层", "Reactor Shielding");
        both.add("block.universe.reactor_core", "反应堆芯", "Reactor Core");
        both.add("block.universe.derelict_window", "残骸舷窗", "Derelict Viewport");
        both.add("block.universe.derelict_light", "残骸舱灯", "Derelict Cabin Light");
        both.add("block.universe.derelict_deck", "残骸甲板", "Derelict Deck Plating");
        both.add("block.universe.derelict_engine", "残骸发动机", "Derelict Engine");
        both.add("block.universe.outpost_solar", "前哨站太阳能板", "Outpost Solar Panel");
        both.add("block.universe.outpost_antenna", "前哨站天线", "Outpost Antenna");
        both.add("block.universe.outpost_device", "前哨站设备", "Outpost Equipment");
        both.add("entity.universe.debris_chunk", "残骸碎片", "Debris");
    }

    private static void items(Both both) {
        both.add("item.universe.scrap_metal", "废金属", "Scrap Metal");
        both.add("item.universe.alloy_plate", "合金板", "Alloy Plate");
        both.add("item.universe.salvaged_electronics", "打捞的电子元件", "Salvaged Electronics");
        both.add("item.universe.salvaged_reactor_core", "打捞的反应堆芯", "Salvaged Reactor Core");
        both.add("tech.universe.stat.tractor_range", "%s 牵引范围", "%s tractor range");
        both.add("tech.universe.tractor_beam", "牵引光束", "Tractor Beam");
        both.add("tech.universe.gravity_coil", "引力抓取器", "Gravity Coil");
    }

    /** 残骸、前哨站、碎片带：HUD、拆残骸的消息。 */
    private static void hulks(Both both) {
        both.add("hud.universe.deep_space.debris", "碎片带", "Debris field");
        both.add("hud.universe.deep_space.outpost", "废弃前哨站", "Abandoned outpost");
        both.add("hud.universe.deep_space.hulk", "太空残骸", "Derelict hulk");
        both.add("hud.universe.deep_space.marker", "%s · %s 格", "%s · %s m");
        both.add("hud.universe.deep_space.hulk_marker", "太空残骸 · 完整度 %s%% · %s 格", "Derelict hulk · %s%% intact · %s m");
        both.add("hud.universe.deep_space.integrity", "结构完整度 %s%%", "Structural integrity %s%%");
        both.add("hud.universe.deep_space.meltdown", "反应堆熔毁 · %s 秒", "Reactor meltdown · %s s");
        both.add("hud.universe.deep_space.salvaged", "堆芯已拆下", "Core removed");
        both.add("hud.universe.deep_space.own_ship", "你的飞船 · %s 格", "Your ship · %s m");

        both.add("message.universe.hulk.alarm", "结构失稳，残骸在报警", "Structure failing. The hulk's alarms are going off");
        both.add("message.universe.hulk.meltdown", "结构垮了，反应堆要熔毁了，20 秒内离开", "Structure collapsed. Reactor meltdown in 20 seconds. Get out");
        both.add("message.universe.hulk.reactor_taken", "反应堆芯拆下来了，反应堆要熔毁了，20 秒内离开",
                "Reactor core removed. Meltdown in 20 seconds. Get out");
        both.add("message.universe.hulk.countdown", "熔毁倒计时：%s 秒", "Meltdown in %s s");
        both.add("message.universe.hulk.exploded", "反应堆熔毁，残骸炸碎了", "The reactor went critical. The hulk is gone");
        both.add("message.universe.debris.salvaged", "打捞到 %s", "Salvaged %s");
        both.add("message.universe.debris.salvaged_stowed", "打捞到 %s，收进了货舱", "Salvaged %s into the hold");
    }

    /** 牵引光束、出舱、抛货。 */
    private static void ship(Both both) {
        both.add("key.universe.eva", "出舱", "Spacewalk");
        both.add("key.universe.tractor", "牵引光束", "Tractor Beam");
        both.add("message.universe.tractor.on", "牵引光束已开启，范围 %s 格", "Tractor beam on, %s m range");
        both.add("message.universe.tractor.off", "牵引光束已关闭", "Tractor beam off");
        both.add("message.universe.tractor.missing", "驾驶台上没装牵引光束模块", "No tractor beam module in the helm");
        both.add("message.universe.tractor.full", "货舱满了，牵引光束吸不进来", "Hold is full. The tractor beam can't take more");
        both.add("hud.universe.deep_space.tractor_on", "牵引光束 %s 格（%s 关闭）", "Tractor beam %s m (%s to stop)");

        both.add("message.universe.eva.out", "已出舱。对着你的船右键回到座位上", "You're outside. Right-click your ship to get back in");
        both.add("message.universe.eva.unavailable", "只能在太空里、空间站外面出舱", "You can only spacewalk in open space");
        both.add("message.universe.eva.blocked", "船边没有地方出去", "No room to get out");
        both.add("message.universe.eva.not_aboard", "得先坐在飞船上", "You need to be aboard a ship");
        both.add("message.universe.eva.not_yours", "这艘船的主人出舱了，上不去", "The owner is out on a spacewalk. You can't board");
        both.add("message.universe.eva.not_yours_pilot", "这艘船的主人不在船上，开不动", "The owner isn't aboard. The ship won't respond");

        both.add("gui.universe.ship_cargo.jettison", "抛出", "Jettison");
        both.add("gui.universe.ship_cargo.jettison_armed", "选一格", "Pick a slot");
        both.add("gui.universe.ship_cargo.jettison_all", "全部抛出", "Jettison all");
        both.add("gui.universe.ship_cargo.jettison_confirm", "再点一下", "Click again");
    }

    private static void gravity(Both both) {
        both.add("item.universe.gravity_coil", "引力抓取器", "Gravity Coil");
        both.add("item.universe.gravity_coil.desc", "按住右键抓起 16 格内的东西，左键扔出去，潜行左键往前推",
                "Hold right-click to lift things within 16 m. Left-click throws, sneak + left-click pushes");
        both.add("message.universe.multitool.mode.gravity", "多功能工具：引力抓取器", "Multitool: Gravity Coil");
        both.add("hud.universe.multitool.mode.gravity", "引力抓取器", "Gravity Coil");
    }

    private static void commands(Both both) {
        both.add("commands.universe.deepspace.not_in_space", "要在太空维度里用", "Only works in space");
        both.add("commands.universe.deepspace.no_wreck", "附近没有这种兴趣点", "Nothing like that nearby");
        both.add("commands.universe.deepspace.hulk", "放了一艘 %s 格长的残骸（%s 格方块，%s）", "Placed a %s m hulk (%s blocks, %s)");
        both.add("commands.universe.deepspace.outpost", "放了一座前哨站（%s 格方块，%s）", "Placed an outpost (%s blocks, %s)");
        both.add("commands.universe.deepspace.debris", "放了 %s 块碎片", "Spawned %s debris chunks");
        both.add("commands.universe.deepspace.status_line", "%s %s：%s, %s, %s，%s 格，%s", "%s %s: %s, %s, %s, %s m, %s");
        both.add("commands.universe.deepspace.goto", "飞到了%s外面（%s, %s, %s）", "Moved outside the %s (%s, %s, %s)");
        both.add("commands.universe.deepspace.detonated", "引爆了 %s", "Detonated %s");
        both.add("commands.universe.deepspace.cleared", "清掉了 %s 个方块和碎片", "Cleared %s blocks and chunks");
    }

    /** 手册的「深空」一章。 */
    private static void guide(Both both) {
        String g = "guide.universe.deep_space";
        both.add(g, "深空", "Deep Space");
        both.add(g + ".sites.title", "深空兴趣点", "Deep Space Sites");
        both.add(g + ".sites",
                "每个星系的太空里有几处兴趣点，2500 格内 HUD 上标出来。\n\n"
                        + "碎片带：三五十块漂着的碎片，用飞船的采矿光束打碎，掉废金属、合金板、打捞的电子元件，偶尔有技术模块\n"
                        + "废弃前哨站：没电的小站，有补给箱、数据终端和能拆的设备\n"
                        + "太空残骸：60–120 格长的废船\n\n"
                        + "废金属在电弧熔炉里炼成铁或钢，合金板炼成钛锭，电子元件在装配机里做成电路板。",
                "Every system has a few sites out in space, marked on the HUD within 2500 m.\n\n"
                        + "Debris fields: thirty to fifty drifting chunks. Break them with the ship's mining beam for scrap metal, "
                        + "alloy plates and salvaged electronics, sometimes a tech module\n"
                        + "Abandoned outposts: small dead stations with supply crates, a data terminal and equipment to strip\n"
                        + "Derelict hulks: wrecks 60–120 m long\n\n"
                        + "An arc smelter turns scrap into iron or steel and alloy plates into titanium. Assemblers make circuit boards from salvaged electronics.");
        both.add(g + ".hulks.title", "拆残骸", "Salvaging Hulks");
        both.add(g + ".hulks",
                "靠近残骸时 HUD 上显示结构完整度，从 100% 开始往下掉：\n\n"
                        + "拆导管 −8（出电子元件），屏蔽层 −5，反应堆芯 −30，别的 −1\n"
                        + "低于 30% 开始报警，灯变红\n"
                        + "掉到 0 或者拆下反应堆芯，20 秒后熔毁\n"
                        + "熔毁时 24 格内的人受重伤，赶紧走\n"
                        + "反应堆芯最值钱，只能卖",
                "Near a hulk, the HUD shows its structural integrity, starting at 100%:\n\n"
                        + "Conduits −8 (they give electronics), shielding −5, the reactor core −30, anything else −1\n"
                        + "Below 30% the alarms start and the lights go red\n"
                        + "At 0, or once the reactor core is out, it melts down 20 s later\n"
                        + "The blast badly hurts anyone within 24 m. Leave\n"
                        + "The reactor core is the prize. Sell-only");
        both.add(g + ".tractor.title", "牵引光束和抛货", "Tractor Beam and Jettison");
        both.add(g + ".tractor",
                "研究「牵引光束」做牵引光束模块，装进驾驶台。开着飞船按 C 开关。\n\n"
                        + "把 40 格内的掉落物和小碎片吸过来，收进货舱\n"
                        + "货舱满了就不吸\n"
                        + "货舱界面点「抛出」再点一格，那一格的东西从船尾飘出去\n"
                        + "「全部抛出」要在 3 秒内点两次",
                "Research Tractor Beam, make the module and fit it in the helm. Press C while flying to toggle it.\n\n"
                        + "Pulls in drops and small debris within 40 m and stows them in the hold\n"
                        + "It stops when the hold is full\n"
                        + "In the cargo screen, press Jettison, then a slot: it floats out behind the ship\n"
                        + "Jettison all needs two clicks within 3 s");
        both.add(g + ".eva.title", "出舱", "Spacewalks");
        both.add(g + ".eva",
                "在太空里开着飞船时按 X 出舱，船停在原地，不会漂走。\n\n"
                        + "外面失重：跳往上，潜行往下\n"
                        + "按住疾跑再走，喷气背包往六个方向推\n"
                        + "真空和寒冷照常消耗防护\n"
                        + "你在外面时，别人上不了你的船\n"
                        + "对着自己的船右键回到座位上",
                "In space, press X while flying to step outside. The ship holds still and won't drift.\n\n"
                        + "No gravity out there: jump to rise, sneak to sink\n"
                        + "Hold sprint while moving and the jetpack pushes you in any of six directions\n"
                        + "Vacuum and cold drain protection as usual\n"
                        + "While you're out, nobody else can board your ship\n"
                        + "Right-click your ship to get back in the seat");
        both.add(g + ".gravity_coil.title", "引力抓取器", "Gravity Coil");
        both.add(g + ".gravity_coil",
                "研究「引力抓取器」后在装配机里做，装进多功能工具，按 G 切过去。\n\n"
                        + "按住右键，抓起 16 格内准星对着的东西：掉落物、碎片、小生物、无人机、黑雾突袭者\n"
                        + "抓着的东西悬在面前，松开右键放下\n"
                        + "左键扔出去，砸到生物会伤它\n"
                        + "潜行左键把前方 8 格内的东西推开\n"
                        + "往开着牵引光束的自家飞船扔，直接收进货舱",
                "Research Gravity Coil and make it in an assembler. Fit it in the multitool and press G to switch to it.\n\n"
                        + "Hold right-click to lift what you're aiming at within 16 m: drops, debris, small creatures, drones, Dark Fog raiders\n"
                        + "It hangs in front of you. Let go to set it down\n"
                        + "Left-click throws it, and it hurts whatever it hits\n"
                        + "Sneak + left-click shoves everything in an 8 m cone ahead\n"
                        + "Throw salvage at your own ship with the tractor beam on and it goes straight into the hold");
    }
}
