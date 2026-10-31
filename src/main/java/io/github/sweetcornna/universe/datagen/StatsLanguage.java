package io.github.sweetcornna.universe.datagen;

/** M18 的文字：统计面板、仪表盘和研究队列（子代理 C，ADR-0033 §四、§五）。 */
final class StatsLanguage {
    /** 同时写中文和英文的一条。 */
    interface Both {
        void add(String key, String zh, String en);
    }

    private StatsLanguage() {
    }

    static void add(Both both) {
        stats(both);
        dashboard(both);
        queue(both);
        guide(both);
    }

    private static void stats(Both both) {
        both.add("gui.universe.stats.search", "搜索物品", "Search items");
        both.add("gui.universe.stats.tab.production", "生产", "Production");
        both.add("gui.universe.stats.tab.power", "电力", "Power");
        both.add("gui.universe.stats.tab.dyson", "戴森球", "Dyson");
        both.add("gui.universe.stats.system", "%s 星系", "%s system");
        both.add("gui.universe.stats.no_match", "没找到这种物品", "No matching items");
        both.add("gui.universe.stats.pick_item", "点左边的一种物品，看它的曲线、参考产量、进出口和上下游配方", "Pick an item to see its chart, "
                + "reference rate, trade and recipes");
        both.add("gui.universe.stats.per_minute_short", "分", "min");
        both.add("gui.universe.stats.detail.produced", "产出 %s/分", "Made %s/min");
        both.add("gui.universe.stats.detail.consumed", "消耗 %s/分", "Used %s/min");
        both.add("gui.universe.stats.detail.reference", "参考 %s/分", "Ref %s/min");
        both.add("gui.universe.stats.detail.reference_tip", "参考产量：在用这些配方、正在运转的 %s 台设备满速时每分钟的量",
                "Reference: what the %s running machines on these recipes do per minute at full speed");
        both.add("gui.universe.stats.detail.trade", "运进 %s/分 · 运出 %s/分", "In %s/min · Out %s/min");
        both.add("gui.universe.stats.detail.upstream", "上游：%s 个配方", "Made by: %s recipes");
        both.add("gui.universe.stats.detail.downstream", "下游：%s 个配方", "Used in: %s recipes");
        both.add("gui.universe.stats.detail.none", "没有", "None");
        both.add("gui.universe.stats.detail.time", "%s 秒一次（速度 1）", "%s s per craft at speed 1");
        both.add("gui.universe.stats.power.satisfaction_short", "满足率", "satisfied");
        both.add("gui.universe.stats.power.spare", "空余 %s", "Spare %s");
        both.add("gui.universe.stats.power.generators", "发电设备", "Generators");
        both.add("gui.universe.stats.power.consumers", "用电设备", "Consumers");
        both.add("gui.universe.stats.power.other", "其他 %s", "Other %s");
        both.add("gui.universe.stats.dyson.none_short", "没有戴森球", "No Dyson sphere");
        both.add("gui.universe.stats.dyson.none", "还没有戴森球。发射了太阳帆或者建起第一层，这一页才有数",
                "No Dyson sphere yet. Launch sails or build a layer to see numbers here");
        both.add("gui.universe.stats.dyson.generation", "总功率 %s", "Total %s");
        both.add("gui.universe.stats.dyson.swarm", "戴森云 %s（%s 张太阳帆）", "Swarm %s (%s sails)");
        both.add("gui.universe.stats.dyson.shells", "球壳 %s", "Shells %s");
        both.add("gui.universe.stats.dyson.drawn", "接收站取走 %s", "Receivers draw %s");
        both.add("gui.universe.stats.dyson.siphon", "黑雾偷走 %s", "Dark Fog siphons %s");
        both.add("gui.universe.stats.dyson.no_layers", "还没有建球壳", "No layers yet");
        both.add("gui.universe.stats.dyson.layer", "第 %s 层", "Layer %s");
        both.add("gui.universe.stats.dyson.col.layer", "层", "Layer");
        both.add("gui.universe.stats.dyson.col.radius", "半径", "Radius");
        both.add("gui.universe.stats.dyson.col.nodes", "节点", "Nodes");
        both.add("gui.universe.stats.dyson.col.frames", "框架", "Frames");
        both.add("gui.universe.stats.dyson.col.shells", "壳面", "Shells");
        both.add("gui.universe.stats.dyson.col.power", "发电", "Power");
    }

    private static void dashboard(Both both) {
        both.add("key.universe.dashboard", "编辑仪表盘", "Edit Dashboard");
        both.add("gui.universe.dashboard.edit", "仪表盘", "Dashboard");
        both.add("gui.universe.dashboard.title", "编辑仪表盘", "Edit Dashboard");
        both.add("gui.universe.dashboard.help", "拖标题栏挪位置，– 收起，× 关掉", "Drag a title bar to move it, – collapses, × removes");
        both.add("gui.universe.dashboard.empty", "还没有钉住的图。在统计面板里选一种物品，点“钉”", "Nothing pinned. Pick an item in Statistics "
                + "and press Pin");
        both.add("gui.universe.dashboard.reset", "全部靠右", "Restack");
        both.add("gui.universe.dashboard.done", "完成", "Done");
        both.add("gui.universe.dashboard.pin", "钉到仪表盘", "Pin to Dashboard");
        both.add("gui.universe.dashboard.pin_short", "钉", "Pin");
        both.add("gui.universe.dashboard.pinned", "钉到仪表盘了", "Pinned to the dashboard");
        both.add("gui.universe.dashboard.already", "仪表盘上已经有这张了", "Already on the dashboard");
        both.add("gui.universe.dashboard.full", "仪表盘满了，最多 %s 张", "Dashboard full (%s max)");
        both.add("gui.universe.dashboard.scoped", "%s · %s", "%s · %s");
        both.add("gui.universe.dashboard.power", "电力 · %s", "Power · %s");
        both.add("gui.universe.dashboard.research", "研究", "Research");
        both.add("gui.universe.dashboard.dyson", "戴森球 · %s", "Dyson · %s");
        both.add("gui.universe.dashboard.station", "物流站", "Station");
        both.add("gui.universe.dashboard.produced", "产出 %s", "Made %s");
        both.add("gui.universe.dashboard.consumed", "消耗 %s", "Used %s");
        both.add("gui.universe.dashboard.reference", "参考 %s/分", "Ref %s/min");
        both.add("gui.universe.dashboard.generation", "发电 %s", "Gen %s");
        both.add("gui.universe.dashboard.demand", "需求 %s", "Demand %s");
        both.add("gui.universe.dashboard.research_idle", "没有在研究", "Not researching");
        both.add("gui.universe.dashboard.queue", "队里 %s 项", "%s queued");
        both.add("gui.universe.dashboard.dyson_power", "功率 %s", "Power %s");
        both.add("gui.universe.dashboard.dyson_drawn", "接收站取走 %s", "Drawn %s");
        both.add("gui.universe.dashboard.dyson_sails", "太阳帆 %s 张", "%s sails");
        both.add("gui.universe.dashboard.station_empty", "空的", "Empty");
    }

    private static void queue(Both both) {
        both.add("gui.universe.tech_tree.queue", "研究队列 %s/%s", "Queue %s/%s");
        both.add("gui.universe.tech_tree.queue_empty", "选一个科技点“加入队列”，或者按住 Shift 点它。当前的研究完成或停下后，接着研究队里第一个能研究的",
                "Select a tech and press Queue, or Shift-click it. When the current research ends, the first ready entry starts");
        both.add("gui.universe.tech_tree.queue_waiting", "等前置", "Waiting");
        both.add("gui.universe.tech_tree.queue_ready", "可以研究", "Ready");
        both.add("gui.universe.tech_tree.queue_clear", "清空", "Clear");
        both.add("gui.universe.tech_tree.enqueue", "加入队列", "Queue");
        both.add("gui.universe.tech_tree.queue_full", "队列满了", "Queue Full");
        both.add("gui.universe.tech_tree.shift_click", "Shift + 点击：加入研究队列", "Shift-click to queue");
        both.add("research.universe.queue_full", "研究队列满了", "The research queue is full");
        both.add("research.universe.queue_blocked", "排不进队：前置要已经完成、正在研究，或者已经在队里", "Can't queue that: its prerequisites "
                + "must be done, in progress or queued");
    }

    private static void guide(Both both) {
        both.add("guide.universe.research.queue.title", "研究队列", "Research Queue");
        both.add("guide.universe.research.queue",
                "科技树右边是研究队列，通关前最多排 8 项，通关后 32 项。\n"
                        + "选中一个要研究站的科技，点“加入队列”，或者按住 Shift 点它。前置要已经完成、正在研究，或者排在队里。"
                        + "升级科技可以排好几次，一次一级。\n\n"
                        + "当前的研究完成或停下时，自动开始队里第一个能研究的；前置还没好的跳过，留在队里。\n"
                        + "队列全服共用，谁都能改。",
                "The queue sits to the right of the tech tree: 8 entries, 32 after the ending.\n"
                        + "Select a lab tech and press Queue, or Shift-click it. Its prerequisites must be done, in progress or already queued. "
                        + "Upgrades can be queued several times, one level each.\n\n"
                        + "When the current research finishes or stops, the first entry that can start does; entries still waiting on "
                        + "prerequisites stay put.\nThe queue is shared by the whole server.");
        both.add("guide.universe.research.stats.title", "统计面板", "Statistics");
        both.add("guide.universe.research.stats",
                "O 键打开统计面板。左右箭头换地方：全部、一个星系、一颗星球。\n\n"
                        + "生产：选一种物品，看它的曲线和上下游配方，点配方里的物品跳过去。"
                        + "“参考”是正在运转、用着这些配方的设备满速时每分钟的量，比实际的高很多，说明原料或电跟不上。"
                        + "星际运输船运进运出的单独记，标着箭头。\n\n"
                        + "电力：发电、用电、充电的环形图，按设备分开。\n"
                        + "戴森球：每一层的节点、框架、壳面和发电。",
                "O opens Statistics. Arrows switch between everything, a system or a planet.\n\n"
                        + "Production: pick an item for its chart and recipes; click an item in a recipe to jump to it. "
                        + "Ref is the full-speed output of the running machines on those recipes. "
                        + "Well above the real rate means inputs or power are short. "
                        + "Interstellar trade is counted separately, marked with an arrow.\n\n"
                        + "Power: a ring of generation, use and charging, split by device.\n"
                        + "Dyson: nodes, frames, shells and output for each layer.");
        both.add("guide.universe.research.dashboard.title", "仪表盘", "Dashboard");
        both.add("guide.universe.research.dashboard",
                "统计面板、科技树上的“钉”把图钉到屏幕上，最多 6 张：物品的产出和消耗、电力、研究进度、戴森球的功率、一座物流站的存货。\n\n"
                        + "钉住的图叠在屏幕右边，每 5 秒更新。统计面板标题旁的“仪表盘”打开编辑：拖标题栏挪位置，– 收成一行，× 关掉。"
                        + "也可以在按键设置里给“编辑仪表盘”绑一个键。\n每个存档各存一份。",
                "Pin buttons in Statistics and the tech tree put a chart on screen, up to 6: an item's production, power, research, "
                        + "Dyson output or a logistics station's stock.\n\n"
                        + "Pinned charts stack on the right and update every 5 seconds. Dashboard next to the Statistics title opens the editor: "
                        + "drag a title bar to move it, – collapses it, × removes it. You can also bind Edit Dashboard in Controls.\n"
                        + "Each world keeps its own.");
    }
}
