package io.github.sweetcornna.universe.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.item.CompositeModel;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.EmptyModel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 一帧里同一种物品共用一份解析好的模型（ADR-0038）。传送带、无人机一帧要画成百上千个同样的东西，原来每个都新建渲染状态、
 * 解析一次。
 *
 * <p>只有模型树里全是固定的模型（不按种子、时间、组件挑子模型），也不带附魔光效、动画贴图时才共用：比如没有目标的指南针按种子
 * 乱转，每个都要用自己的种子。别的照旧一个个解析，画出来和不共用时一样。换一帧（{@link RenderFrames}）就清空。
 */
public final class SharedItemModels {
    private final Map<Object, ItemStackRenderState> shared = new HashMap<>();
    /** 这一帧查过、不能共用的。 */
    private final Set<Object> unique = new HashSet<>();
    private long frame = -1;

    /** 一个物品的默认堆（没有组件）的模型，{@code seed} 是这一个自己的种子。物品堆只在要解析时才做。 */
    public ItemStackRenderState get(Item item, ItemModelResolver resolver, @Nullable Level level, int seed) {
        ItemStackRenderState cached = this.cached(item);
        return cached != null ? cached : this.resolve(item, new ItemStack(item), resolver, level, seed);
    }

    /** {@code key} 相同的物品堆画出来一样（调用方保证），{@code seed} 是这一个自己的种子。 */
    public ItemStackRenderState get(Object key, ItemStack stack, ItemModelResolver resolver, @Nullable Level level, int seed) {
        ItemStackRenderState cached = this.cached(key);
        return cached != null ? cached : this.resolve(key, stack, resolver, level, seed);
    }

    /** 这一帧共用的那份；换了一帧先清空。 */
    private @Nullable ItemStackRenderState cached(Object key) {
        long now = RenderFrames.current();
        if (now != this.frame) {
            this.frame = now;
            this.shared.clear();
            this.unique.clear();
        }
        return this.shared.get(key);
    }

    private ItemStackRenderState resolve(Object key, ItemStack stack, ItemModelResolver resolver, @Nullable Level level, int seed) {
        if (this.unique.contains(key)) {
            ItemStackRenderState state = new ItemStackRenderState();
            resolver.updateForTopItem(state, stack, ItemDisplayContext.FIXED, level, null, seed);
            return state;
        }
        TrackingItemStackRenderState state = new TrackingItemStackRenderState();
        resolver.updateForTopItem(state, stack, ItemDisplayContext.FIXED, level, null, seed);
        if (!state.isAnimated() && fixed(state.getModelIdentity())) {
            this.shared.put(key, state);
        } else {
            this.unique.add(key);
        }
        return state;
    }

    /** 模型树里用到的都是固定的模型：方块、物品的普通模型和它们的组合。什么都没记下的（不认识的模型）不算。 */
    private static boolean fixed(Object identity) {
        if (!(identity instanceof List<?> elements) || elements.isEmpty()) {
            return false;
        }
        for (Object element : elements) {
            if (element instanceof ItemModel && !(element instanceof CuboidItemModelWrapper || element instanceof CompositeModel
                    || element instanceof MissingItemModel || element instanceof EmptyModel)) {
                return false;
            }
        }
        return true;
    }
}
