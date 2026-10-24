package io.github.sweetcornna.universe.client.settlement;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.client.dev.DevScript;
import io.github.sweetcornna.universe.settlement.SettlementPayloads;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * 聚落的客户端部分（ADR-0034 §二）：管理界面、"我的聚落"的按键（默认不绑），和开发脚本的几个命令。
 *
 * <p>开发脚本（前四个走 {@code /universe settlement ...}，要有权限）：
 *
 * <ul>
 *   <li>{@code settleclaim}：接管最近的聚落（不看声望、不收材料）
 *   <li>{@code settleskip}：施工、升级都不用再等；{@code settlefinish}：动工了的直接盖完
 *   <li>{@code settleraid}：海盗现在就来
 *   <li>{@code settleopen}：打开最近的自己的聚落的管理界面
 *   <li>{@code settlebuild <地基 1–8> <建筑>}、{@code settleday [天数]}：同名的调试命令
 *   <li>{@code settletab <页 0–5> [地基 0–7]}：界面开着时切页、选地基
 *   <li>{@code settlelist}：打开"我的聚落"
 * </ul>
 */
public final class SettlementClient {
    /** "我的聚落"（默认不绑：按键位已经不多了）。 */
    public static final KeyMapping SETTLEMENTS = new KeyMapping("key.universe.settlements", InputConstants.Type.KEYBOARD,
            InputConstants.UNKNOWN.getValue(), UniverseKeys.CATEGORY);

    private SettlementClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(SettlementPayloads.View.TYPE,
                (payload, context) -> context.enqueueWork(() -> show(payload))));
        gameBus.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft minecraft = Minecraft.getInstance();
            while (SETTLEMENTS.consumeClick()) {
                if (minecraft.gui.screen() == null && minecraft.player != null) {
                    list();
                }
            }
        });
        DevScript.EXTRA_VERBS.put("settleclaim", args -> command("universe settlement claim"));
        DevScript.EXTRA_VERBS.put("settleskip", args -> command("universe settlement skip"));
        DevScript.EXTRA_VERBS.put("settlefinish", args -> command("universe settlement finish"));
        DevScript.EXTRA_VERBS.put("settleraid", args -> command("universe settlement raid"));
        DevScript.EXTRA_VERBS.put("settleopen", args -> command("universe settlement open"));
        DevScript.EXTRA_VERBS.put("settlebuild", args -> command("universe settlement build " + String.join(" ", args)));
        DevScript.EXTRA_VERBS.put("settleday", args -> command("universe settlement day " + (args.length > 0 ? args[0] : "1")));
        DevScript.EXTRA_VERBS.put("settlelist", args -> list());
        DevScript.EXTRA_VERBS.put("settletab", args -> {
            if (Minecraft.getInstance().gui.screen() instanceof SettlementScreen screen && args.length > 0) {
                screen.select(Integer.parseInt(args[0]), args.length > 1 ? Integer.parseInt(args[1]) : -1);
            }
        });
    }

    private static void command(String command) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.connection.sendCommand(command);
        }
    }

    private static void list() {
        ClientPacketDistributor.sendToServer(new SettlementPayloads.Action("", SettlementPayloads.Action.LIST, -1, 0));
    }

    /** 服务端发来一份：要打开的打开，开着的换上新的。 */
    private static void show(SettlementPayloads.View payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof SettlementScreen screen) {
            screen.update(payload.data());
        } else if (payload.open()) {
            minecraft.gui.setScreen(new SettlementScreen(payload.data()));
        }
    }
}
