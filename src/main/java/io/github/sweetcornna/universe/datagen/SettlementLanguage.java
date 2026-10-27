package io.github.sweetcornna.universe.datagen;

/** M19 的文字：聚落（子代理 B，ADR-0034 §二）。 */
final class SettlementLanguage {
    /** 同时写中文和英文的一条。 */
    interface Both {
        void add(String key, String zh, String en);
    }

    private SettlementLanguage() {
    }

    static void add(Both both) {
        names(both);
        decisions(both);
        events(both);
        messages(both);
        screen(both);
        commands(both);
        guide(both);
    }

    private static void names(Both both) {
        both.add("block.universe.settlement_terminal", "聚落终端", "Settlement Terminal");
        both.add("block.universe.settlement_turret", "聚落炮台", "Settlement Turret");
        both.add("key.universe.settlements", "我的聚落", "My Settlements");
        both.add("bossbar.universe.settlement_raid", "海盗来袭：%s", "Pirate Raid: %s");
        both.add("tag.item.universe.settlement.stone", "石料", "Stone");
        both.add("tag.item.universe.settlement.timber", "木料", "Timber");
        both.add("tag.item.universe.settlement.glass", "玻璃", "Glass");
        String[][] buildings = {
                {"none", "空地", "Empty"},
                {"office", "总督办公室", "Governor's Office"},
                {"habitation", "住宅", "Habitation"},
                {"farm", "农场", "Farm"},
                {"workshop", "工坊", "Workshop"},
                {"saloon", "酒馆", "Saloon"},
                {"market", "市场", "Market"},
                {"watchtower", "瞭望塔", "Watchtower"},
                {"turret", "防御炮台", "Defense Turret"},
                {"landing_pad", "着陆坪", "Landing Pad"},
                {"fishing_pond", "鱼塘", "Fishing Pond"},
        };
        for (String[] b : buildings) {
            both.add("settlement.universe.building." + b[0], b[1], b[2]);
        }
        String[][] products = {
                {"credits", "信用点", "Credits"},
                {"trade_maps", "贸易图", "Trade Maps"},
                {"survey_data", "勘测数据", "Survey Data"},
                {"nav_data", "导航数据", "Navigation Data"},
                {"ammo", "弹药", "Ammo"},
                {"steel", "钢", "Steel"},
                {"food", "食物", "Food"},
                {"gears", "齿轮", "Gears"},
                {"circuits", "电路板", "Circuit Boards"},
        };
        for (String[] p : products) {
            both.add("settlement.universe.product." + p[0], p[1], p[2]);
        }
    }

    /** 一个决定：编号、标题、问题，再是一个个选项（中文、英文交替）。 */
    private static void decision(Both both, String id, String titleZh, String titleEn, String zh, String en, String... options) {
        String key = "settlement.universe.decision." + id;
        both.add(key + ".title", titleZh, titleEn);
        both.add(key, zh, en);
        for (int i = 0; i * 2 + 1 < options.length; i++) {
            both.add(key + "." + i, options[i * 2], options[i * 2 + 1]);
        }
    }

    private static void decisions(Both both) {
        // 请愿
        decision(both, "well", "打井", "New Well",
                "居民想在广场边上打一口井，问总督出不出钱。", "Residents want a well by the plaza and are asking you to pay for it.",
                "出钱打井", "Pay for it",
                "让他们自己挖", "Let them dig it themselves",
                "不打", "No well");
        decision(both, "festival", "过节", "Festival",
                "居民想办一场节日，东西从储备里出。", "Residents want to hold a festival, paid for out of the reserve.",
                "大办一场", "Go all out",
                "小办一下", "Keep it small",
                "今年不办", "Not this year");
        decision(both, "medkit", "急救包", "Med Kits",
                "工坊里有人受了伤，大家想备一批急救包。", "Someone got hurt in the workshop. People want med kits on hand.",
                "买一批", "Buy a batch",
                "工坊停两天自己做", "Have the workshop make them",
                "先不管", "Not now");
        decision(both, "lights", "路灯", "Street Lights",
                "晚上路上太黑，居民想沿路装一排灯。", "The roads are pitch black at night. Residents want lights along them.",
                "装灯", "Put them up",
                "不装", "Leave it dark");
        decision(both, "classroom", "学堂", "Classroom",
                "孩子们没地方上课。可以盖一间学堂，也可以请老人教本族的话。",
                "The children have nowhere to learn. Build a classroom, or have the elders teach the old tongue.",
                "盖学堂", "Build a classroom",
                "请老人教", "Ask the elders",
                "都不用", "Neither");
        decision(both, "monument", "纪念碑", "Monument",
                "居民想在广场上立一块碑，问碑上刻什么。", "Residents want a monument on the plaza. What should it say?",
                "刻建聚落的人", "Honor the founders",
                "刻全体居民的名字", "List every resident",
                "先把广场铺好", "Pave the plaza instead");
        // 纠纷
        decision(both, "irrigation", "水渠", "Irrigation",
                "农场想改水渠，要从住户门口挖过去。", "The farm wants to reroute its ditch right past people's doors.",
                "照旧不动", "Leave it as is",
                "让农场挖", "Let the farm dig",
                "出钱绕开走", "Pay to route it around");
        decision(both, "noise", "酒馆太吵", "Noisy Saloon",
                "住在酒馆旁边的人说，晚上吵得睡不着。", "The saloon's neighbors say they can't sleep at night.",
                "生意照做", "Business as usual",
                "出钱装隔音", "Pay for soundproofing",
                "早点打烊", "Close early");
        decision(both, "missing_crate", "少了一箱货", "Missing Crate",
                "仓库少了一箱货，有人说看见一个新来的半夜搬东西。", "A crate went missing. Someone saw a newcomer hauling things at night.",
                "不追究", "Drop it",
                "让那人走", "Make them leave",
                "用储备补上", "Cover it from the reserve");
        decision(both, "empty_lot", "空地", "Empty Lot",
                "两户人家都说广场后面那块空地是自己的。", "Two families both claim the lot behind the plaza.",
                "一家一半", "Split it",
                "收归聚落堆料", "Use it for storage",
                "改成菜园", "Turn it into a garden");
        decision(both, "unpaid_trader", "欠账", "Unpaid Bill",
                "一个过路的商人说，聚落还欠着 500 信用点的货款。", "A passing trader says the settlement still owes 500 credits for goods.",
                "照付", "Pay up",
                "先欠着，货照用", "Keep the goods, pay later",
                "不认账", "Refuse");
        decision(both, "stray_creature", "野兽", "Stray Creature",
                "一头野兽天天来翻垃圾，孩子们还给它喂吃的。", "A wild creature keeps raiding the trash, and the kids have been feeding it.",
                "随它去", "Leave it be",
                "赶走", "Drive it off",
                "养起来", "Keep it");
        // 新来的人
        decision(both, "engineer", "工程师", "Engineer",
                "一个工程师想在这里住下，说能帮着干活。", "An engineer wants to settle here and offers to help out.",
                "欢迎", "Welcome",
                "不收", "Turn them away");
        decision(both, "refugees", "难民", "Refugees",
                "一群逃难的人到了门口，后面可能有人追。", "A group of refugees is at the gate. Someone may be after them.",
                "收留", "Take them in",
                "给些补给送走", "Give supplies, send them on",
                "关门", "Close the gate");
        decision(both, "peddler", "货郎", "Peddler",
                "一个货郎想在广场上摆两天摊，愿意交 400 信用点。", "A peddler wants a stall on the plaza for two days and offers 400 credits.",
                "收钱", "Take the money",
                "让对方免费摆", "Let them set up for free",
                "不让摆", "No stall");
        decision(both, "archivist", "档案员", "Archivist",
                "一个档案员带着几份星图路过，可以卖给你，也可以留下来。", "An archivist passing through has star charts to sell, or could stay.",
                "买星图", "Buy the charts",
                "请对方留下", "Ask them to stay",
                "都不要", "No thanks");
        decision(both, "veteran", "老兵", "Veteran",
                "一个老兵来找活干，可以雇来巡逻。", "A veteran is looking for work. You could hire them to patrol.",
                "雇来巡逻", "Hire a patrol",
                "让对方住下", "Let them settle",
                "不用", "No");
        decision(both, "family", "一家人", "A Family",
                "一家人想搬进空着的住宅。", "A family wants to move into an empty house.",
                "让他们搬进来", "Let them in",
                "不行", "No");
        // 政策
        decision(both, "long_shifts", "加班", "Long Shifts",
                "工坊的活干不完。可以加班，也可以放假。", "The workshop is behind on orders.",
                "加班三天", "Three days of overtime",
                "照常", "Carry on",
                "放三天假", "Three days off");
        decision(both, "market_tax", "摊位费", "Stall Fees",
                "市场上的摊子多了，可以收一笔摊位费。", "The market is busy. You could charge stall fees.",
                "收", "Charge them",
                "不收", "Don't");
        decision(both, "night_watch", "守夜", "Night Watch",
                "最近夜里不太平，要不要抽人守夜？", "Nights have been rough lately. Pull people off work to keep watch?",
                "守夜", "Post a watch",
                "不用", "No need");
        decision(both, "rationing", "配给", "Rationing",
                "储备见底了，可以先配给一阵。", "The reserve is running low. You could ration for a while.",
                "配给", "Ration",
                "不配给", "Don't");
        decision(both, "caravan", "商队", "Caravan",
                "一支商队想把这里当歇脚的地方。会给钱，也会招来海盗。",
                "A caravan wants to rest here on its route. They'll pay, but they draw pirates.",
                "答应", "Agree",
                "不答应", "Refuse");
        decision(both, "rest_day", "休息日", "Rest Day",
                "居民问能不能放几天假。", "Residents are asking for some time off.",
                "放三天", "Three days off",
                "不放", "No",
                "放一天", "One day off",
                "再干两天就放", "Two more days, then rest");
    }

    private static void events(Both both) {
        String[][] events = {
                {"claimed", "%s 接管了聚落", "%s took charge"},
                {"resigned", "%s 辞去了总督", "%s resigned"},
                {"built", "%s盖好了", "%s built"},
                {"upgraded", "%s升到 %s 级", "%s upgraded to %s"},
                {"opened", "%s开张", "%s opened"},
                {"debt", "付不起维护费，欠下 %s", "Missed upkeep, %s owed"},
                {"left", "走了一个居民", "A resident left"},
                {"arrived", "来了一个新居民", "A new resident arrived"},
                {"decided", "定了：%s", "Decided: %s"},
                {"raid_won", "打退了 %s 架海盗战机", "Drove off %s pirate fighters"},
                {"raid_lost", "海盗来过，%s 座建筑受损", "Pirate raid, %s buildings damaged"},
                {"repaired", "%s修好了", "%s repaired"},
        };
        for (String[] e : events) {
            both.add("settlement.universe.event." + e[0], e[1], e[2]);
        }
    }

    private static void messages(Both both) {
        String p = "message.universe.settlement.";
        both.add(p + "name", "%1$s", "%2$s");
        both.add(p + "not_here", "附近没有聚落", "No settlement nearby");
        both.add(p + "already_yours", "这里已经归你管了", "You already run this settlement");
        both.add(p + "taken", "这个聚落的总督是 %s", "This settlement is run by %s");
        both.add(p + "too_many", "最多管 %s 个聚落", "You can run at most %s settlements");
        both.add(p + "standing", "和%s的关系要到「%s」", "Needs %2$s standing with %1$s");
        both.add(p + "claim_materials", "建设物资不够", "Not enough construction materials");
        both.add(p + "claimed", "你接管了%s", "You now run %s");
        both.add(p + "not_owner", "只有总督能管这个聚落", "Only the governor can manage this settlement");
        both.add(p + "too_far", "离聚落太远了", "Too far from the settlement");
        both.add(p + "upgrade_started", "%s开始升级，交齐材料就动工", "Upgrading %s: deliver the materials to begin");
        both.add(p + "cannot_upgrade", "现在不能升级", "Can't upgrade right now");
        both.add(p + "cancelled", "%s停工了", "%s cancelled");
        both.add(p + "product_set", "改为生产%s", "Now producing %s");
        both.add(p + "reserve_empty", "储备是空的", "The reserve is empty");
        both.add(p + "sold", "卖掉储备，得到 %s", "Sold the reserve for %s");
        both.add(p + "treasury_empty", "账上没有钱", "The treasury is empty");
        both.add(p + "treasury_taken", "从账上取走 %s", "Took %s from the treasury");
        both.add(p + "stock_empty", "没有攒下的东西", "Nothing stored");
        both.add(p + "stock_taken", "取出 %s 件", "Took %s items");
        both.add(p + "no_credits", "信用点不够", "Not enough credits");
        both.add(p + "debt_paid", "还了 %s 欠款", "Paid %s of debt");
        both.add(p + "resigned", "你不再是%s的总督", "You resigned as governor of %s");
        both.add(p + "cannot_start", "这里盖不了", "Can't build that here");
        both.add(p + "plot_taken", "这块地基已经在用了", "That plot is already in use");
        both.add(p + "only_one", "只能有一座", "Only one allowed");
        both.add(p + "office_first", "先盖好总督办公室", "Build the governor's office first");
        both.add(p + "started", "%s开工了（%s 号地基）", "Started %s on plot %s");
        both.add(p + "nothing_to_deliver", "现在不用交材料", "No materials needed right now");
        both.add(p + "no_materials", "背包里没有要交的材料", "You don't have any of the materials");
        both.add(p + "repaired", "%s修好了", "%s repaired");
        both.add(p + "delivered_all", "材料交齐了，%s 分钟后完成这一段", "All delivered. This stage is done in %s min");
        both.add(p + "delivered", "交了 %s 件，还差 %s 件", "Delivered %s, %s still needed");
        both.add(p + "opened", "%s开张了", "%s is open");
        both.add(p + "no_decision", "今天没有要定的事", "Nothing to decide today");
        both.add(p + "cannot_afford", "付不起这个选项", "You can't afford that");
        both.add(p + "decided", "定了", "Decision made");
        both.add(p + "withdrew_credits", "取出 %s", "Withdrew %s");
        both.add(p + "withdrew", "取出 %s 个%s", "Withdrew %s %s");
        both.add(p + "new_decision", "%s：今天有件事要你定", "%s: a decision is waiting");
        both.add(p + "raid_warning", "%s：今晚有海盗", "%s: pirates expected tonight");
        both.add(p + "in_debt", "%s付不起维护费，欠款 %s", "%s can't cover upkeep. Debt: %s");
        both.add(p + "stage_done", "%s：第 %s/%s 段完成", "%s: stage %s/%s done");
        both.add(p + "built", "%s盖好了，去施工终端开张", "%s is built. Open it at its terminal");
        both.add(p + "upgraded", "%s升到 %s 级", "%s upgraded to %s");
        both.add(p + "remote", "%s：%s", "%s: %s");
        both.add(p + "hint_unclaimed", "这个聚落没有总督，到广场上的聚落终端接管", "No governor here. Claim it at the settlement terminal on the plaza");
        both.add(p + "hint_owned", "%s 的聚落", "%s's settlement");
        both.add(p + "raid_start", "%s：%s 架海盗战机来袭，%s 分钟内打下来", "%s: %s pirate fighters incoming. Shoot them down within %s min");
        both.add(p + "raid_won", "%s守住了，得到 %s 信用点", "%s held. Reward: %s credits");
        both.add(p + "raid_won_away", "%s的防御打退了海盗，%s 信用点记在账上", "%s fought off the pirates. %s credits added to its treasury");
        both.add(p + "raid_lost", "%s没守住：%s 座建筑受损，储备被抢走 %s 份", "%s fell: %s buildings damaged, %s reserve stolen");
        both.add(p + "raid_lost_away", "海盗洗劫了%s：%s 座建筑受损，储备被抢走 %s 份", "Pirates raided %s: %s buildings damaged, %s reserve stolen");
    }

    private static void screen(Both both) {
        String g = "gui.universe.settlement.";
        both.add(g + "title", "聚落", "Settlement");
        both.add(g + "tab.overview", "概况", "Overview");
        both.add(g + "tab.buildings", "建筑", "Buildings");
        both.add(g + "tab.construction", "施工", "Build");
        both.add(g + "tab.decisions", "决定", "Decisions");
        both.add(g + "tab.production", "生产", "Output");
        both.add(g + "tab.mine", "我的聚落", "My Towns");
        both.add(g + "tab.claim", "接管", "Claim");
        // 接管
        both.add(g + "unclaimed", "这个聚落还没有总督。", "This settlement has no governor.");
        both.add(g + "claim.needs", "接管要：", "To claim it:");
        both.add(g + "claim.standing", "和%s的关系到「%s」（现在「%s」）", "%2$s standing with %1$s (now %3$s)");
        both.add(g + "claim.owned", "管着的聚落 %s/%s", "Settlements run: %s/%s");
        both.add(g + "claim.materials", "交一份建设物资：", "One batch of construction materials:");
        both.add(g + "claim.after", "接管以后，周围的 8 块地基会整平，路也会修好。第一座先盖总督办公室。",
                "Claiming levels the 8 plots around the settlement and lays roads to them. Start with the governor's office.");
        both.add(g + "claim", "接管", "Claim");
        both.add(g + "too_far", "离得太远，只能看", "Too far away: view only");
        // 概况
        both.add(g + "class", "%s 级聚落 · %s", "Class %s settlement · %s");
        both.add(g + "class_short", "%s 级", "Class %s");
        both.add(g + "population", "人口 %s/%s", "Population %s/%s");
        both.add(g + "happiness", "幸福", "Happiness");
        both.add(g + "productivity", "生产力", "Output");
        both.add(g + "threat", "威胁", "Threat");
        both.add(g + "reserve", "储备", "Reserve");
        both.add(g + "daily", "每天：收入 %s · 维护费 %s", "Daily: income %s · upkeep %s");
        both.add(g + "treasury", "市场账上 %s", "Treasury %s");
        both.add(g + "debt", "欠款 %s", "Debt %s");
        both.add(g + "defense", "防御 %s", "Defense %s");
        both.add(g + "day", "第 %s 天 · 离下一天 %s", "Day %s · next day in %s");
        both.add(g + "raid_now", "海盗来袭，还剩 %s", "Pirates attacking: %s left");
        both.add(g + "raid_tonight", "今晚有海盗，%s 后到", "Pirates tonight, in %s");
        both.add(g + "governor", "总督：%s", "Governor: %s");
        both.add(g + "events", "最近的事", "Recent events");
        both.add(g + "no_events", "还没有", "Nothing yet");
        both.add(g + "event_day", "第 %s 天", "Day %s");
        both.add(g + "pay_debt", "还欠款 %s", "Pay debt %s");
        both.add(g + "resign", "辞去总督", "Resign");
        both.add(g + "confirm", "再点一次确认", "Click again");
        // 建筑
        both.add(g + "empty_plot", "空地", "Empty plot");
        both.add(g + "build_here", "开工", "Build");
        both.add(g + "manage", "管理", "Manage");
        both.add(g + "buildings_total", "建筑 %s 座 · 维护费每天 %s", "%s buildings · upkeep %s/day");
        both.add(g + "status.empty", "空地", "Empty");
        both.add(g + "status.deliver", "交材料 %s/%s", "Materials %s/%s");
        both.add(g + "status.wait", "施工 %s/%s", "Building %s/%s");
        both.add(g + "status.opening", "等开张", "Ready to open");
        both.add(g + "status.active", "开着", "Open");
        both.add(g + "status.upgrading", "升级中", "Upgrading");
        both.add(g + "status.damaged", "受损", "Damaged");
        both.add(g + "effect.housing", "住房 +%s", "Housing +%s");
        both.add(g + "effect.productivity", "生产力 +%s", "Output +%s");
        both.add(g + "effect.happiness", "幸福 +%s", "Happiness +%s");
        both.add(g + "effect.income", "每天收入 %s", "Income %s/day");
        both.add(g + "effect.relief", "威胁每天 −%s", "Threat −%s/day");
        both.add(g + "effect.defense", "防御 +%s", "Defense +%s");
        both.add(g + "effect.office", "有了它才有每天的决定", "Unlocks daily decisions");
        both.add(g + "effect.food", "能生产食物", "Can produce food");
        both.add(g + "effect.parts", "能生产齿轮、电路板", "Can produce gears and circuit boards");
        both.add(g + "effect.nav_data", "每天导航数据 ×%s", "Navigation data ×%s/day");
        both.add(g + "effect.fish", "每天出鱼 ×%s", "Fish ×%s/day");
        both.add(g + "effect.landing", "能停飞船", "Ships can land here");
        both.add(g + "effect.turret", "海盗来时开火", "Fires on raiding pirates");
        // 施工
        both.add(g + "plaza", "广场", "Plaza");
        both.add(g + "pick_plot", "在左边的图上选一块地基。", "Pick a plot on the map.");
        both.add(g + "plot_empty", "%s 号地基：空地", "Plot %s: empty");
        both.add(g + "office_hint", "第一座得是总督办公室", "The first building must be the governor's office");
        both.add(g + "size", "占地 %s×%s", "Footprint %s×%s");
        both.add(g + "first_stage", "第一段要：", "Stage 1 needs:");
        both.add(g + "only_one", "只能有一座", "Only one allowed");
        both.add(g + "office_first", "先盖总督办公室", "Build the governor's office first");
        both.add(g + "stage", "第 %s/%s 段：%s", "Stage %s/%s: %s");
        both.add(g + "stage.foundation", "打地基、搭脚手架", "Foundation and scaffolding");
        both.add(g + "stage.frame", "起框架", "Frame");
        both.add(g + "stage.finish", "完工", "Finishing");
        both.add(g + "cost", "%s ×%s", "%s ×%s");
        both.add(g + "cost.delivered", "%s %s/%s", "%s %s/%s");
        both.add(g + "cost.have", "（有 %s）", "(have %s)");
        both.add(g + "deliver", "交材料", "Deliver");
        both.add(g + "cancel", "取消施工", "Cancel");
        both.add(g + "cancel_upgrade", "取消升级", "Cancel Upgrade");
        both.add(g + "building_now", "施工中，还要 %s", "Under construction: %s left");
        both.add(g + "ready_to_open", "盖好了。开张以后起作用。", "Built. It takes effect once it opens.");
        both.add(g + "open", "开张", "Open");
        both.add(g + "repair", "被海盗打坏了，交材料修：", "Damaged by pirates. Deliver to repair:");
        both.add(g + "upgrade", "升级到 %s", "Upgrade to %s");
        both.add(g + "upgrading_to", "在升级到 %s 级", "Upgrading to %s");
        both.add(g + "next_grade", "升到 %s 级：作用 ×%s", "Upgrade to %s: effects ×%s");
        both.add(g + "max_grade", "已经是最高档", "Fully upgraded");
        // 决定
        both.add(g + "kind.request", "请愿", "Request");
        both.add(g + "kind.dispute", "纠纷", "Dispute");
        both.add(g + "kind.arrival", "新来的人", "Arrival");
        both.add(g + "kind.policy", "政策", "Policy");
        both.add(g + "no_office", "总督办公室开张以后，每天会有一件事要你定。", "Once the governor's office opens, there's a decision to make every day.");
        both.add(g + "decided_today", "今天的事处理完了。", "Nothing left to decide today.");
        both.add(g + "next_day", "离下一天 %s", "Next day in %s");
        both.add(g + "change.credits", "信用点 %s", "Credits %s");
        both.add(g + "change.happy", "幸福 %s", "Happiness %s");
        both.add(g + "change.boost", "生产力 %s（%s 天）", "Output %s (%s days)");
        both.add(g + "change.threat", "威胁 %s", "Threat %s");
        both.add(g + "change.reserve", "储备 %s", "Reserve %s");
        both.add(g + "change.people", "人口 %s", "Population %s");
        both.add(g + "change.standing", "声望 %s", "Standing %s");
        both.add(g + "change.item", "得到 %s 个%s", "Get %s %s");
        both.add(g + "change.word", "学一个词", "Learn a word");
        both.add(g + "change.none", "没有变化", "No change");
        // 生产
        both.add(g + "products", "产品", "Products");
        both.add(g + "output", "每天出 %s 个%s", "Makes %s %s a day");
        both.add(g + "output_credits", "每天出 %s 信用点", "Makes %s credits a day");
        both.add(g + "unit_value", "每份值 %s", "Each unit is worth %s");
        both.add(g + "withdraw", "取出 %s 个", "Withdraw %s");
        both.add(g + "withdraw_credits", "取出 %s", "Withdraw %s");
        both.add(g + "sell", "卖掉 %s", "Sell for %s");
        both.add(g + "take_treasury", "取走", "Take");
        both.add(g + "stock", "鱼塘、瞭望塔攒下的", "Stored by pond and watchtower");
        both.add(g + "stock_empty", "还没有", "Nothing yet");
        both.add(g + "take_stock", "全部取出", "Take all");
        // 我的聚落
        both.add(g + "mine_count", "管着 %s/%s 个聚落", "Running %s/%s settlements");
        both.add(g + "mine_none", "你还没有管着的聚落。在星球上找一个聚落，到广场上的聚落终端接管。",
                "You don't run any settlements yet. Find one on a planet and claim it at the terminal on its plaza.");
        both.add(g + "row", "人口 %s/%s · 幸福 %s · 威胁 %s · 储备 %s/%s · 建筑 %s",
                "Pop %s/%s · Happiness %s · Threat %s · Reserve %s/%s · Buildings %s");
        both.add(g + "flag.decision", "有事要定", "Decision waiting");
        both.add(g + "flag.raid", "海盗", "Raid");
    }

    private static void commands(Both both) {
        String c = "commands.universe.settlement.";
        both.add(c + "none", "附近没有你管的聚落", "You don't run a settlement nearby");
        both.add(c + "no_landmark", "512 格内没有聚落", "No settlement within 512 blocks");
        both.add(c + "none_within", "%s 格内没有聚落", "No settlement within %s blocks");
        both.add(c + "went", "到了 %s, %s 的聚落", "Arrived at the settlement at %s, %s");
        both.add(c + "claimed", "接管了 %s, %s 的聚落", "Claimed the settlement at %s, %s");
        both.add(c + "skipped", "%s 块地基不用再等", "Skipped the wait on %s plots");
        both.add(c + "finished", "盖完了 %s 块地基", "Finished %s plots");
        both.add(c + "built", "%s 号地基盖好了", "Built on plot %s");
        both.add(c + "days", "结算了 %s 天，收入 %s", "Advanced %s days, income %s");
        both.add(c + "raid", "海盗马上就到", "Pirates are on their way");
        both.add(c + "raid_busy", "海盗已经来了", "A raid is already underway");
        both.add(c + "decision", "今天的决定换成 %s", "Today's decision is now %s");
        both.add(c + "no_such_decision", "没有这个决定：%s", "Unknown decision: %s");
        both.add(c + "resynced", "对了 %s 块地基", "Resynced %s plots");
        both.add(c + "info", "%s：%s 级，人口 %s/%s，幸福 %s，威胁 %s，生产力 %s，防御 %s，储备 %s/%s，账上 %s，欠款 %s，第 %s 天",
                "%s: class %s, pop %s/%s, happiness %s, threat %s, output %s, defense %s, reserve %s/%s, treasury %s, debt %s, day %s");
    }

    /** 手册的「管理聚落」一章。 */
    private static void guide(Both both) {
        String g = "guide.universe.settlement";
        both.add(g, "管理聚落", "Governorship");
        both.add(g + ".claim.title", "接管", "Claiming");
        both.add(g + ".claim",
                "没有总督的聚落能接管。广场上交易终端右边是聚落终端，打开「接管」页：\n\n"
                        + "和这个种族的关系到「认识」\n"
                        + "交建设物资：钢 30、碳纳米管 8、电路板 8\n"
                        + "一个人最多管 4 个聚落\n\n"
                        + "接管以后周围 8 块地基整平、修好路，头两天没有海盗。要在聚落 64 格内才能管；按键设置里给「我的聚落」绑个键，远处也能看。",
                "A settlement with no governor can be claimed from the Claim tab of its settlement terminal, right of the trade "
                        + "terminal:\n\n"
                        + "Acquainted standing with its race\n"
                        + "Construction materials: 30 steel, 8 carbon nanotubes, 8 circuit boards\n"
                        + "Up to 4 settlements per player\n\n"
                        + "Claiming levels 8 plots and lays roads to them, and pirates leave you alone for two days. "
                        + "You manage it from within 64 m. Bind a key to My Settlements to check on them from anywhere.");
        both.add(g + ".construction.title", "施工", "Construction");
        both.add(g + ".construction",
                "第一座得是总督办公室，开张以后才能盖别的。在「施工」页选一块地基、选建筑，地基上立起施工终端。\n\n"
                        + "分三段：打地基、起框架、完工\n"
                        + "每段交齐材料，再等 5 分钟\n"
                        + "石料、木料、玻璃，什么石头、木板、玻璃都算\n"
                        + "盖完去施工终端点「开张」\n"
                        + "每座能升三档，作用 ×1.5、×2、×3\n"
                        + "被海盗打坏的交材料修，修好前不起作用",
                "The governor's office comes first. Nothing else can go up until it opens. Pick a plot and a building on the Build tab, "
                        + "and a construction terminal appears on the plot.\n\n"
                        + "Three stages: foundation, frame, finishing\n"
                        + "Deliver each stage's materials, then wait 5 min\n"
                        + "Any stone, planks or glass will do for stone, timber and glass\n"
                        + "When it's built, press Open at the terminal\n"
                        + "Each building upgrades three times: effects ×1.5, ×2, ×3\n"
                        + "Pirate damage takes materials to repair, and the building does nothing until then");
        both.add(g + ".buildings.title", "建筑", "Buildings");
        both.add(g + ".buildings",
                "盖好的建筑每座每天维护费 40 信用点。\n\n"
                        + "住宅：住房 +3\n"
                        + "农场：生产力 +10，能出食物\n"
                        + "工坊：生产力 +15，能出齿轮、电路板\n"
                        + "酒馆：幸福 +12\n"
                        + "市场：每天 150 信用点，门口有公会使节\n"
                        + "瞭望塔：每天一份导航数据，威胁每天 −5\n"
                        + "防御炮台：海盗来时开火，威胁每天 −3\n"
                        + "着陆坪：能停飞船，生产力 +5\n"
                        + "鱼塘：每天 2 条鱼",
                "Every finished building costs 40 credits a day in upkeep.\n\n"
                        + "Habitation: housing +3\n"
                        + "Farm: output +10, can produce food\n"
                        + "Workshop: output +15, can produce gears and circuit boards\n"
                        + "Saloon: happiness +12\n"
                        + "Market: 150 credits a day, and a guild envoy at the door\n"
                        + "Watchtower: navigation data daily, threat −5 a day\n"
                        + "Defense turret: fires on raiders, threat −3 a day\n"
                        + "Landing pad: ships can land, output +5\n"
                        + "Fishing pond: 2 fish a day");
        both.add(g + ".days.title", "每一天", "Each Day");
        both.add(g + ".days",
                "聚落的一天是 24000 刻，不管星球自己的昼夜。每天开始时结算：\n\n"
                        + "按生产力往储备里攒产品，攒满就停\n"
                        + "维护费先从市场账上扣，再折算储备，还不够记欠款\n"
                        + "有欠款，幸福每天 −5\n"
                        + "幸福低于 25 走一个人，高于 70 又有空房来一个人\n"
                        + "威胁每天涨 生产力 / 10 + 人口 / 4\n"
                        + "等级 C、B、A、S 看建筑、人口和幸福，等级越高储备越多",
                "A settlement day is 24000 ticks, whatever the planet's own day. At the start of each:\n\n"
                        + "Output adds product to the reserve, up to its cap\n"
                        + "Upkeep comes out of the market treasury, then the reserve, and the rest becomes debt\n"
                        + "While in debt, happiness −5 a day\n"
                        + "Under 25 happiness someone leaves; over 70 with a free house someone moves in\n"
                        + "Threat rises by output / 10 + population / 4\n"
                        + "Class C to S follows buildings, population and happiness. Higher class, bigger reserve");
        both.add(g + ".products.title", "生产", "Products");
        both.add(g + ".products",
                "「生产」页选一种产品，按种族和建筑开放：\n\n"
                        + "商旅族：信用点、贸易图\n"
                        + "学者族：勘测数据、导航数据\n"
                        + "戍卫族：弹药、钢\n"
                        + "有农场：食物\n"
                        + "有工坊：齿轮、电路板\n\n"
                        + "储备能按批取出，也能整个卖掉。鱼塘的鱼、瞭望塔的导航数据另外攒着，最多 18 组。",
                "Pick a product on the Output tab. What's on offer depends on race and buildings:\n\n"
                        + "Traders: credits, trade maps\n"
                        + "Scholars: survey data, navigation data\n"
                        + "Wardens: ammo, steel\n"
                        + "With a farm: food\n"
                        + "With a workshop: gears, circuit boards\n\n"
                        + "Take the reserve out in batches or sell all of it. Fish from the pond and data from the watchtower pile up separately, up to 18 stacks.");
        both.add(g + ".decisions.title", "决定", "Decisions");
        both.add(g + ".decisions",
                "总督办公室开张以后，每天有一件事要你定：请愿、纠纷、新来的人或者政策。\n\n"
                        + "选项会改信用点、幸福、生产力、威胁、储备、人口、声望，有的给东西\n"
                        + "每个选项的后果写在旁边\n"
                        + "钱不够的选项选不了，事情一直留着\n"
                        + "有事要定时会收到提示",
                "Once the governor's office opens, there's one matter a day: a request, a dispute, an arrival or a policy.\n\n"
                        + "Choices shift credits, happiness, output, threat, reserve, population or standing, and some hand you items\n"
                        + "Each choice lists what it does\n"
                        + "Choices you can't afford are locked, and the matter waits\n"
                        + "You get a notice when one comes up");
        both.add(g + ".raids.title", "海盗来袭", "Pirate Raids");
        both.add(g + ".raids",
                "威胁过 40 以后每天都可能来海盗，到 100 一定来，入夜时到。3–6 架战机扫射建筑，2 分钟内全部打下来算守住。\n\n"
                        + "守住：威胁清零，幸福 +10，给信用点\n"
                        + "没守住：两座建筑受损，幸福 −15，储备被抢走三成\n"
                        + "防御炮台自己朝战机开火\n"
                        + "总督不在 256 格内，或者聚落不在模拟距离内：不真打，按防御值算输赢，炮台、瞭望塔、人口、幸福都算\n"
                        + "打到一半走开，或者有战机飞出模拟距离停住，剩下的也按防御值算",
                "Past 40 threat, pirates may come any night; at 100 they always do. 3–6 fighters strafe the buildings. "
                        + "Down them all within 2 min to hold.\n\n"
                        + "Held: threat 0, happiness +10, credits\n"
                        + "Lost: two buildings damaged, happiness −15, a third of the reserve stolen\n"
                        + "Defense turrets fire on their own\n"
                        + "Governor beyond 256 m, or settlement outside simulation distance: no fight; "
                        + "the defense rating decides (turrets, watchtowers, population, happiness)\n"
                        + "Leave mid-raid, or a fighter strays out of simulation distance and freezes: the defense rating settles the rest");
    }
}
