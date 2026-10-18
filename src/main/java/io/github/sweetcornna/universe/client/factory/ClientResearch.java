package io.github.sweetcornna.universe.client.factory;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.research.ResearchState;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipes;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.network.ResearchStatePayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端的研究进度和设备配方：服务端同步过来的副本，给界面和配方判断用。只在客户端主线程上读写。按键（默认 J）
 * 打开科技树。
 */
public final class ClientResearch {
    /** 本模组按键的分类。 */
    public static final KeyMapping.Category KEYS = new KeyMapping.Category(Universe.id("universe"));
    public static final KeyMapping TECH_TREE = new KeyMapping("key.universe.tech_tree", InputConstants.Type.KEYBOARD, InputConstants.KEY_J, KEYS);
    private static ResearchState state = new ResearchState();
    private static boolean received;
    private static RecipeMap recipes = RecipeMap.EMPTY;

    private ClientResearch() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class,
                event -> event.register(ResearchStatePayload.TYPE, ClientResearch::handle));
        gameBus.addListener(RecipesReceivedEvent.class, event -> recipes = event.getRecipeMap());
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            state = new ResearchState();
            received = false;
            recipes = RecipeMap.EMPTY;
        });
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.registerCategory(KEYS);
            event.register(TECH_TREE);
        });
        gameBus.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft minecraft = Minecraft.getInstance();
            while (TECH_TREE.consumeClick()) {
                if (minecraft.gui.screen() == null && minecraft.player != null) {
                    minecraft.gui.setScreen(new TechTreeScreen());
                }
            }
        });
        Research.setClientState(() -> state);
        MachineRecipes.setClientRecipes(() -> recipes);
    }

    public static ResearchState state() {
        return state;
    }

    private static void handle(ResearchStatePayload payload, IPayloadContext context) {
        ResearchState next = payload.toState();
        // 登录时的第一份不算"刚完成"
        if (received && next.done().stream().anyMatch(id -> !state.isDone(id))) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.6F));
        }
        state = next;
        received = true;
    }
}
