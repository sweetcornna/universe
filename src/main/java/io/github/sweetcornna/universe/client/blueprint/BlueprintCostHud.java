package io.github.sweetcornna.universe.client.blueprint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.blueprint.BlueprintMeta;
import io.github.sweetcornna.universe.network.BlueprintPayloads;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * 拿着蓝图预览时 HUD 右上角的材料清单（ADR-0033 §九）：每种要多少、身上（背包和外骨骼货舱）有多少，不够的标红。
 * "只贴设备"时只算设备。身上有多少每秒问服务端一次（外骨骼货舱不同步到客户端）。创造模式不花材料，不画。
 */
final class BlueprintCostHud {
    private static final int STOCK_INTERVAL = 20;
    private static final int RED = 0xFFFF6A5A;
    private static final int OK = 0xFFDCE8EE;
    private static final int DIM = 0xFF8BA3AF;

    private static Map<Item, Integer> need = Map.of();
    private static boolean devicesOnly;
    private static long previewAt = Long.MIN_VALUE;
    private static final Map<Item, Integer> STOCK = new HashMap<>();
    private static long askedAt = Long.MIN_VALUE;
    private static List<Item> asked = List.of();

    private BlueprintCostHud() {
    }

    static void registerEvents(IEventBus modBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(BlueprintPayloads.Stock.TYPE,
                (payload, context) -> context.enqueueWork(() -> {
                    for (int i = 0; i < payload.items().size() && i < payload.counts().size(); i++) {
                        STOCK.put(payload.items().get(i), payload.counts().get(i));
                    }
                })));
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerBelow(VanillaGuiLayers.CROSSHAIR, Universe.id("blueprint_cost"), BlueprintCostHud::render));
    }

    static void clear() {
        need = Map.of();
        STOCK.clear();
        asked = List.of();
        previewAt = Long.MIN_VALUE;
        askedAt = Long.MIN_VALUE;
    }

    /** 这一帧的预览要花的材料。 */
    static void preview(Map<Item, Integer> cost, boolean devices, long gameTime) {
        need = cost;
        devicesOnly = devices;
        previewAt = gameTime;
    }

    private static boolean showing(Minecraft minecraft) {
        return minecraft.level != null && minecraft.player != null && !minecraft.player.isCreative()
                && minecraft.level.getGameTime() - previewAt <= 2;
    }

    /** 每秒问一次身上有多少；要的材料变了马上问。 */
    static void tick(Minecraft minecraft) {
        if (!showing(minecraft) || need.isEmpty()) {
            return;
        }
        List<Item> items = new ArrayList<>(need.keySet());
        long now = minecraft.level.getGameTime();
        boolean changed = !items.equals(asked);
        if (changed || now - askedAt >= STOCK_INTERVAL || now < askedAt) {
            asked = items;
            askedAt = now;
            ClientPacketDistributor.sendToServer(new BlueprintPayloads.StockRequest(
                    items.size() > BlueprintPayloads.StockRequest.MAX_ITEMS ? items.subList(0, BlueprintPayloads.StockRequest.MAX_ITEMS) : items));
        }
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!showing(minecraft) || minecraft.gui.hud.isHidden()) {
            return;
        }
        Font font = minecraft.font;
        List<BlueprintMeta.Cost<Item>> costs = BlueprintMeta.costs(need, STOCK);
        Component title = Component.translatable(devicesOnly ? "hud.universe.blueprint.cost_devices" : "hud.universe.blueprint.cost");
        int shown = Math.min(costs.size(), BlueprintMeta.COST_LINES);
        List<Component> lines = new ArrayList<>();
        int width = font.width(title);
        for (int i = 0; i < shown; i++) {
            BlueprintMeta.Cost<Item> cost = costs.get(i);
            Component line = Component.translatable("hud.universe.blueprint.cost_line", new ItemStack(cost.item()).getHoverName(), cost.need(),
                    cost.have());
            lines.add(line);
            width = Math.max(width, 18 + font.width(line));
        }
        Component more = costs.size() > shown ? Component.translatable("hud.universe.blueprint.cost_more", costs.size() - shown) : null;
        Component empty = costs.isEmpty() ? Component.translatable("hud.universe.blueprint.cost_none") : null;
        if (more != null) {
            width = Math.max(width, font.width(more));
        }
        if (empty != null) {
            width = Math.max(width, font.width(empty));
        }
        int height = 14 + shown * 17 + (more != null || empty != null ? 11 : 0);
        int x = graphics.guiWidth() - width - 10;
        int y = 30;
        graphics.fill(x - 4, y - 4, x + width + 4, y + height, 0x90081016);
        graphics.fill(x - 4, y - 4, x - 3, y + height, 0xFF2E7A92);
        graphics.text(font, title, x, y, 0xFF7FE0F7, true);
        y += 14;
        for (int i = 0; i < shown; i++) {
            BlueprintMeta.Cost<Item> cost = costs.get(i);
            graphics.item(new ItemStack(cost.item()), x, y - 1);
            graphics.text(font, lines.get(i), x + 18, y + 3, cost.shortage() ? RED : OK, true);
            y += 17;
        }
        if (more != null) {
            graphics.text(font, more, x, y, DIM, true);
        } else if (empty != null) {
            graphics.text(font, empty, x, y, DIM, true);
        }
    }
}
