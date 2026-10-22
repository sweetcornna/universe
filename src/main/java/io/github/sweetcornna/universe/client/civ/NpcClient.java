package io.github.sweetcornna.universe.client.civ;

import io.github.sweetcornna.universe.network.DialoguePayloads;
import io.github.sweetcornna.universe.network.TechShopPayloads;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** 外星 NPC 的客户端部分（ADR-0027）：渲染器和对话界面。 */
public final class NpcClient {
    private NpcClient() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerEntityRenderer(UniverseEntities.ALIEN_NPC.get(), AlienNpcRenderer::new));
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(DialoguePayloads.State.TYPE,
                (payload, context) -> context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof DialogueScreen screen && screen.entity() == payload.entity()) {
                        screen.update(payload);
                    } else if (payload.open()) {
                        minecraft.gui.setScreen(new DialogueScreen(payload));
                    }
                })));
        // 技术商的货（ADR-0027 §六）
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(TechShopPayloads.State.TYPE,
                (payload, context) -> context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof TechShopScreen screen && screen.entity() == payload.entity()) {
                        screen.update(payload);
                    } else if (payload.open()) {
                        minecraft.gui.setScreen(new TechShopScreen(payload));
                    }
                })));
    }
}
