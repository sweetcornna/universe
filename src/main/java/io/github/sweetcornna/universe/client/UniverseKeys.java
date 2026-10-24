package io.github.sweetcornna.universe.client;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** 本模组的按键：都放在"寰宇"分类下。 */
public final class UniverseKeys {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Universe.id("universe"));
    /** 科技树（默认 J）。 */
    public static final KeyMapping TECH_TREE = new KeyMapping("key.universe.tech_tree", InputConstants.Type.KEYBOARD, InputConstants.KEY_J, CATEGORY);
    /** 星图（默认 M）。 */
    public static final KeyMapping STAR_MAP = new KeyMapping("key.universe.star_map", InputConstants.Type.KEYBOARD, InputConstants.KEY_M, CATEGORY);
    /** 分析目镜，手持多功能工具时开关（默认 V）。 */
    public static final KeyMapping ANALYZE = new KeyMapping("key.universe.analyze", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, CATEGORY);
    /** 发现日志（默认 K）。 */
    public static final KeyMapping DISCOVERY_LOG = new KeyMapping("key.universe.discovery_log", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, CATEGORY);
    /** 技术配置：外骨骼和多功能工具的模块、货舱（默认 U）。 */
    public static final KeyMapping TECH = new KeyMapping("key.universe.tech", InputConstants.Type.KEYBOARD, InputConstants.KEY_U, CATEGORY);

    /** 生产统计面板（默认 O）。 */
    public static final KeyMapping STATS = new KeyMapping("key.universe.stats", InputConstants.Type.KEYBOARD, InputConstants.KEY_O, CATEGORY);
    /** 戴森球编辑器（ADR-0016），戴森球计划也是 Y。 */
    public static final KeyMapping DYSON_EDITOR = new KeyMapping("key.universe.dyson_editor", InputConstants.Type.KEYBOARD, InputConstants.KEY_Y,
            CATEGORY);

    /** 多功能工具在采矿和战斗模式之间切换（默认 G）。 */
    public static final KeyMapping TOOL_MODE = new KeyMapping("key.universe.tool_mode", InputConstants.Type.KEYBOARD, InputConstants.KEY_G, CATEGORY);

    /** 拿着画好的蓝图时转 90°（默认 R，ADR-0018）。 */
    public static final KeyMapping ROTATE_BLUEPRINT = new KeyMapping("key.universe.rotate_blueprint", InputConstants.Type.KEYBOARD,
            InputConstants.KEY_R, CATEGORY);

    /** 机甲面板：能量核心、燃烧室、物流栏位（默认 B，ADR-0023）。 */
    public static final KeyMapping MECHA = new KeyMapping("key.universe.mecha", InputConstants.Type.KEYBOARD, InputConstants.KEY_B, CATEGORY);

    /** 召唤飞船；在太空里开船时打开自动驾驶的目的地（默认 N，ADR-0024）。 */
    public static final KeyMapping SUMMON = new KeyMapping("key.universe.summon", InputConstants.Type.KEYBOARD, InputConstants.KEY_N, CATEGORY);

    /** 坐在船上时打开货舱（默认 H，ADR-0030 §六）。 */
    public static final KeyMapping SHIP_CARGO = new KeyMapping("key.universe.ship_cargo", InputConstants.Type.KEYBOARD, InputConstants.KEY_H,
            CATEGORY);

    /** 物流控制面板：全服的物流站、配送器和物品路线（默认 I，ADR-0033 §三）。 */
    public static final KeyMapping CONTROL_PANEL = new KeyMapping("key.universe.control_panel", InputConstants.Type.KEYBOARD,
            InputConstants.KEY_I, CATEGORY);

    private UniverseKeys() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.registerCategory(CATEGORY);
            event.register(TECH_TREE);
            event.register(STAR_MAP);
            event.register(ANALYZE);
            event.register(DISCOVERY_LOG);
            event.register(TECH);
            event.register(STATS);
            event.register(DYSON_EDITOR);
            event.register(TOOL_MODE);
            event.register(ROTATE_BLUEPRINT);
            event.register(MECHA);
            event.register(SUMMON);
            event.register(SHIP_CARGO);
            event.register(io.github.sweetcornna.universe.client.mecha.CombatClient.FLEET);
            event.register(io.github.sweetcornna.universe.client.mecha.CombatClient.SHIELD_BURST);
            // M18 舰队和战斗（A）的按键注册加在这行上面
            event.register(CONTROL_PANEL);
            // M18 物流调度（B）的按键注册加在这行上面
            event.register(io.github.sweetcornna.universe.client.stats.DashboardHud.EDIT_KEY);
            // M18 统计和研究（C）的按键注册加在这行上面
            // M18 信标、透镜、矿脉、蓝图（D）的按键注册加在这行上面
        });
    }
}
