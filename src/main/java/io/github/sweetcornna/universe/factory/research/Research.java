package io.github.sweetcornna.universe.factory.research;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.github.sweetcornna.universe.core.research.ResearchState;
import io.github.sweetcornna.universe.core.research.Tech;
import io.github.sweetcornna.universe.core.research.TechTree;
import io.github.sweetcornna.universe.core.research.Upgrades;
import io.github.sweetcornna.universe.factory.recipe.ResearchShapedRecipe;
import io.github.sweetcornna.universe.network.ResearchActionPayload;
import io.github.sweetcornna.universe.network.ResearchStatePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 研究的入口（ADR-0011）：整个世界共用一份进度，存在 {@link ResearchData}，变化时同步给所有玩家。
 * 配方和设备通过 {@link #isUnlocked} 查科技有没有完成。
 */
public final class Research {
    public static final TechTree TREE = TechTree.DEFAULT;

    /** 客户端的进度，由客户端代码接上（这里不能引用客户端类）。 */
    private static Supplier<ResearchState> clientState = ResearchState::new;

    private Research() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ResearchState state = ResearchData.get(player.level().getServer()).state();
                PacketDistributor.sendToPlayer(player, ResearchStatePayload.of(state));
                awardRecipes(List.of(player), state.done());
            }
        });
    }

    public static void setClientState(Supplier<ResearchState> state) {
        clientState = state;
    }

    /** 这个世界的研究进度：服务端是存档里的那份，客户端是同步过来的副本（只读）。 */
    public static ResearchState state(Level level) {
        return level instanceof ServerLevel server ? ResearchData.get(server.getServer()).state() : clientState.get();
    }

    /** 科技完成了没有；空字符串表示不需要研究。 */
    public static boolean isUnlocked(Level level, String tech) {
        return tech.isEmpty() || state(level).isDone(tech);
    }

    public static MutableComponent name(String tech) {
        return Component.translatable("tech.universe." + tech);
    }

    /** 升级科技的名字带上级数，比如"矿脉利用 3"。 */
    public static MutableComponent name(String upgrade, int level) {
        return Component.translatable("tech.universe.leveled", name(upgrade), level);
    }

    /** 这一级升级科技的效果说明。 */
    public static MutableComponent upgradeEffect(String upgrade, int level) {
        return Component.translatable("upgrade.universe." + upgrade + ".effect", Upgrades.effectArgs(upgrade, level).toArray());
    }

    /** 升级科技现在的级数（服务端和客户端都能用）。 */
    public static int level(Level level, String upgrade) {
        return state(level).level(upgrade);
    }

    // ---- 服务端改进度

    /** 完成一个科技：存盘、通知所有玩家、把它解锁的手工配方加进配方书。 */
    public static void complete(MinecraftServer server, String tech) {
        ResearchData data = ResearchData.get(server);
        if (data.state().isDone(tech)) {
            return;
        }
        data.state().complete(tech);
        data.setDirty();
        announce(server, tech);
        sync(server);
    }

    /** 研究站攒了一点。 */
    public static void addPoint(MinecraftServer server) {
        ResearchData data = ResearchData.get(server);
        if (data.state().current() == null) {
            return;
        }
        String finished = data.state().addPoint(TREE);
        data.setDirty();
        if (finished != null) {
            announce(server, finished);
        }
        sync(server);
    }

    private static void announce(MinecraftServer server, String tech) {
        MutableComponent name = TREE.upgrade(tech).isPresent() ? name(tech, ResearchData.get(server).state().level(tech)) : name(tech);
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("research.universe.completed", name.withStyle(ChatFormatting.AQUA)), false);
        awardRecipes(server.getPlayerList().getPlayers(), List.of(tech));
    }

    /** 收回科技或清空进度之后调用（命令用）。 */
    public static void changed(MinecraftServer server) {
        ResearchData.get(server).setDirty();
        sync(server);
    }

    private static void sync(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(ResearchStatePayload.of(ResearchData.get(server).state()));
    }

    /** 把这些科技解锁的手工配方加进玩家的配方书。 */
    private static void awardRecipes(Collection<ServerPlayer> players, Collection<String> techs) {
        if (players.isEmpty() || techs.isEmpty()) {
            return;
        }
        MinecraftServer server = players.iterator().next().level().getServer();
        List<RecipeHolder<?>> recipes = new ArrayList<>();
        for (RecipeHolder<?> holder : server.getRecipeManager().recipeMap().byType(RecipeType.CRAFTING)) {
            if (holder.value() instanceof ResearchShapedRecipe recipe && techs.contains(recipe.research())) {
                recipes.add(holder);
            }
        }
        if (!recipes.isEmpty()) {
            players.forEach(player -> player.awardRecipes(recipes));
        }
    }

    // ---- 客户端的操作

    public static void handleAction(ResearchActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        ResearchData data = ResearchData.get(server);
        ResearchState state = data.state();
        String id = payload.tech();
        switch (payload.action()) {
            case START -> {
                if (state.start(TREE, id)) {
                    data.setDirty();
                    sync(server);
                }
            }
            case STOP -> {
                state.stop();
                data.setDirty();
                sync(server);
            }
            case HAND_IN -> {
                Tech tech = TREE.get(id).orElse(null);
                if (tech == null || !tech.isHandIn() || !state.available(TREE, id)) {
                    return;
                }
                if (!player.isCreative()) {
                    if (!hasItems(player, tech)) {
                        player.sendSystemMessage(Component.translatable("research.universe.missing_items").withStyle(ChatFormatting.RED));
                        return;
                    }
                    for (Tech.ItemCost cost : tech.handIn()) {
                        ContainerHelper.clearOrCountMatchingItems(player.getInventory(), matcher(cost), cost.count(), false);
                    }
                }
                complete(server, id);
            }
        }
    }

    /** 玩家背包里有没有上交这个科技要的所有物品。 */
    public static boolean hasItems(ServerPlayer player, Tech tech) {
        for (Tech.ItemCost cost : tech.handIn()) {
            if (ContainerHelper.clearOrCountMatchingItems(player.getInventory(), matcher(cost), 0, true) < cost.count()) {
                return false;
            }
        }
        return true;
    }

    private static Predicate<ItemStack> matcher(Tech.ItemCost cost) {
        Item item = item(cost.item());
        return stack -> stack.is(item);
    }

    /** 物品 ID 对应的物品，不认识时是空气。 */
    public static Item item(String id) {
        Identifier key = Identifier.tryParse(id);
        return key == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(key);
    }
}
