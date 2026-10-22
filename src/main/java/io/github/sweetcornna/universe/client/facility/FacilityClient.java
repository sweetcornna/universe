package io.github.sweetcornna.universe.client.facility;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.darkfog.Facilities;
import io.github.sweetcornna.universe.darkfog.facility.FacilityGhostBlockEntity;
import io.github.sweetcornna.universe.network.FacilityPayloads;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * 设施耐久的客户端（ADR-0028 §四、§五）：记着服务端发来的附近掉血的设施，准星对着时在下面画一条耐久；
 * 对着残影时写上是什么设施。残影的渲染器、战场分析基地的界面也在这里登记。
 */
public final class FacilityClient {
    private static final int BAR_WIDTH = 40;
    private static final int GHOST_COLOR = 0xFF8CC8FF;
    private static final Map<Long, Float> HEALTH = new HashMap<>();
    private static @Nullable ClientLevel owner;

    private FacilityClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(FacilityPayloads.Health.TYPE,
                (payload, context) -> context.enqueueWork(() -> receive(payload))));
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerBlockEntityRenderer(UniverseBlockEntities.FACILITY_GHOST.get(), FacilityGhostRenderer::new));
        modBus.addListener(RegisterMenuScreensEvent.class,
                event -> event.register(UniverseMenus.BATTLEFIELD_ANALYSIS_BASE.menuType(), BattlefieldBaseScreen::new));
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerBelow(VanillaGuiLayers.CROSSHAIR, Universe.id("facility_health"), FacilityClient::renderHud));
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            HEALTH.clear();
            owner = null;
        });
    }

    private static void receive(FacilityPayloads.Health payload) {
        sameLevel();
        if (payload.replace()) {
            HEALTH.clear();
        }
        for (int i = 0; i < payload.positions().size() && i < payload.ratios().size(); i++) {
            float ratio = payload.ratios().get(i);
            if (ratio >= 1) {
                HEALTH.remove(payload.positions().get(i));
            } else {
                HEALTH.put(payload.positions().get(i), ratio);
            }
        }
    }

    /** 换了维度：以前记的作废。 */
    private static void sameLevel() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != owner) {
            HEALTH.clear();
            owner = level;
        }
    }

    /** 设施的耐久比例；没掉血时是空的（两格高的按下半截）。 */
    public static @Nullable Float health(BlockPos pos, BlockState state) {
        BlockPos origin = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        return HEALTH.get(origin.asLong());
    }

    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || minecraft.gui.hud.isHidden() || minecraft.gui.screen() != null
                || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        sameLevel();
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        int x = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2 + 10;
        if (level.getBlockEntity(pos) instanceof FacilityGhostBlockEntity ghost) {
            if (!ghost.original().isAir()) {
                graphics.centeredText(minecraft.font, Component.translatable("hud.universe.facility.ghost", ghost.original().getBlock().getName()),
                        x, y, GHOST_COLOR);
            }
            return;
        }
        if (!Facilities.isFacility(state)) {
            return;
        }
        Float ratio = health(pos, state);
        if (ratio == null) {
            return;
        }
        int left = x - BAR_WIDTH / 2;
        int filled = Math.round(BAR_WIDTH * Math.max(0, Math.min(1, ratio)));
        int color = ratio > 0.6F ? 0xFF5BD47A : ratio > 0.3F ? 0xFFE8C14A : 0xFFE5583F;
        graphics.fill(left - 1, y - 1, left + BAR_WIDTH + 1, y + 3, 0xC0101A21);
        graphics.fill(left, y, left + filled, y + 2, color);
        graphics.centeredText(minecraft.font, Component.translatable("hud.universe.facility.health", Math.round(ratio * 100)), x, y + 5, color);
    }
}
