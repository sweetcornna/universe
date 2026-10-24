package io.github.sweetcornna.universe.fossil;

import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseWorldgen;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * 化石（ADR-0034 §三）的事件：石哨兵的属性、骨头的战利品函数、挖化石土吵醒石哨兵、调试命令。
 */
public final class Fossils {
    private Fossils() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        UniverseWorldgen.LOOT_FUNCTION_TYPES.register("fossil_bone", () -> BoneLootFunction.MAP_CODEC);
        modBus.addListener(EntityAttributeCreationEvent.class,
                event -> event.put(UniverseEntities.STONE_SENTRY.get(), StoneSentry.createAttributes().build()));
        gameBus.addListener(BlockDropsEvent.class, Fossils::onDrops);
        gameBus.addListener(RegisterCommandsEvent.class, event -> FossilCommand.register(event.getDispatcher()));
    }

    /** 玩家在生存模式挖开一块化石土：附近睡着的石哨兵躁动一级。 */
    private static void onDrops(BlockDropsEvent event) {
        if (!event.getState().is(UniverseBlocks.FOSSIL_DIRT.get()) || !(event.getBreaker() instanceof Player player)
                || player.isCreative() || player.isSpectator()) {
            return;
        }
        DigSites.disturb(event.getLevel(), event.getPos(), player);
    }

    /** 东西放进背包，放不下的掉在脚下。 */
    public static void give(Player player, ItemStack stack) {
        boolean sync = player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null;
        player.getInventory().placeItemBackInInventory(stack, sync, Prediction.SERVER_ONLY);
    }

    /** 这个世界是不是开着和平难度（石哨兵不醒）。 */
    static boolean peaceful(ServerLevel level) {
        return level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL;
    }
}
