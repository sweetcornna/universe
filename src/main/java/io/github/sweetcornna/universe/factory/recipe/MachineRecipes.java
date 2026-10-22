package io.github.sweetcornna.universe.factory.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.research.Tech;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.registry.UniverseRecipeTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/**
 * 设备配方的清单。26.x 的配方默认不发给客户端，这里登记要发的三种类型，客户端收到后由客户端代码接上。
 *
 * <p>设备界面上先按解锁的科技、再按产物注册的先后、最后按配方 ID 排序列出，服务端和客户端排出来的顺序一样，所以选配方时只传序号。
 */
public final class MachineRecipes {
    private static Supplier<RecipeMap> clientRecipes = () -> RecipeMap.EMPTY;
    /** 按配方表缓存排好序的清单：服务端和客户端各一份，各自只在自己的线程上用。 */
    private static final Cache SERVER = new Cache();
    private static final Cache CLIENT = new Cache();

    private MachineRecipes() {
    }

    public static void registerEvents(IEventBus gameBus) {
        // 每种设备配方都要发给客户端，界面左边的配方清单读的是客户端的副本；工作台配方给手册画配方用
        gameBus.addListener(OnDatapackSyncEvent.class, event -> event.sendRecipes(
                UniverseRecipeTypes.SMELTING.get(), UniverseRecipeTypes.ASSEMBLING.get(), UniverseRecipeTypes.REFINING.get(),
                UniverseRecipeTypes.CHEMICAL.get(), UniverseRecipeTypes.PARTICLE.get(), UniverseRecipeTypes.NUTRIENT.get(),
                UniverseRecipeTypes.PORTABLE_REFINING.get(), RecipeType.CRAFTING));
    }

    public static void setClientRecipes(Supplier<RecipeMap> recipes) {
        clientRecipes = recipes;
    }

    /** 一种设备的全部配方，按 ID 排序。 */
    public static List<RecipeHolder<MachineRecipe>> list(Level level, MachineRecipe.Kind kind) {
        if (level instanceof ServerLevel server) {
            return SERVER.get(server.getServer().getRecipeManager().recipeMap(), kind);
        }
        return CLIENT.get(clientRecipes.get(), kind);
    }

    /** 客户端收到的一种设备的全部配方，排序和 {@link #list} 一样（JEI 插件用）。 */
    public static List<RecipeHolder<MachineRecipe>> clientList(MachineRecipe.Kind kind) {
        return CLIENT.get(clientRecipes.get(), kind);
    }

    /** 清单里的第几个，不在清单里时为 -1。 */
    public static int indexOf(Level level, MachineRecipe.Kind kind, @Nullable RecipeHolder<MachineRecipe> recipe) {
        return recipe == null ? -1 : list(level, kind).indexOf(recipe);
    }

    private static final class Cache {
        private @Nullable RecipeMap map;
        private final Map<MachineRecipe.Kind, List<RecipeHolder<MachineRecipe>>> lists = new EnumMap<>(MachineRecipe.Kind.class);

        List<RecipeHolder<MachineRecipe>> get(RecipeMap map, MachineRecipe.Kind kind) {
            if (map != this.map) {
                this.map = map;
                this.lists.clear();
            }
            return this.lists.computeIfAbsent(kind, k -> {
                List<RecipeHolder<MachineRecipe>> list = new ArrayList<>(map.byType(k.type()));
                // 同一科技解锁的按产物注册的先后排（营养处理器先列半成品，再列菜），再按配方 ID。物品的数字 ID 由 NeoForge 同步给客户端，
                // 两边排出来一样
                list.sort(Comparator.<RecipeHolder<MachineRecipe>>comparingInt(holder -> researchOrder(holder.value().research()))
                        .thenComparingInt(holder -> BuiltInRegistries.ITEM.getId(holder.value().results().getFirst().item().value()))
                        .thenComparing(holder -> holder.id().identifier().toString()));
                return List.copyOf(list);
            });
        }
    }

    /** 不用研究的排最前，其余按科技在科技树里的顺序（大致就是解锁的先后）。 */
    private static int researchOrder(String research) {
        if (research.isEmpty()) {
            return -1;
        }
        List<Tech> techs = Research.TREE.techs();
        for (int i = 0; i < techs.size(); i++) {
            if (techs.get(i).id().equals(research)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }
}
