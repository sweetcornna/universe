package io.github.sweetcornna.universe.factory.fluid;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * 流体物品和 NeoForge 流体的对应（ADR-0030 §三）：本模组的流体是物品，对外按一个物品 1000 mB 换成真正的流体，
 * 别的模组的管道、储罐就能接。
 *
 * <p>每种流体物品对一个流体标签 {@code universe:interop/<物品>}，标签里默认放了原版水、本模组的酸液和社区约定的
 * {@code c:} 标签（数据生成见 {@code UniverseFluidTagsProvider}），整合包可以用数据包改。进来时标签里的流体都认；
 * 出去时用标签里排在最前面的源流体，标签是空的（没装产这种流体的模组）就不从流体接口出去。
 */
public final class FluidBridge {
    private static final Map<Item, Optional<Fluid>> OUTBOUND = new HashMap<>();
    private static List<Entry> entries;

    private record Entry(Item item, TagKey<Fluid> tag) {
    }

    private FluidBridge() {
    }

    public static void registerEvents(IEventBus gameBus) {
        // 数据包重载以后标签变了，重新挑
        gameBus.addListener(TagsUpdatedEvent.class, event -> clear());
    }

    /** 这种流体物品对应的标签：{@code universe:interop/<物品名>}。 */
    public static TagKey<Fluid> tag(String item) {
        return TagKey.create(Registries.FLUID, Universe.id("interop/" + item));
    }

    private static List<Entry> entries() {
        if (entries == null) {
            // 水放最后：别的标签对上了就不当水
            List<Supplier<? extends Item>> items = List.of(UniverseItems.SULFURIC_ACID, UniverseItems.CRUDE_OIL, UniverseItems.REFINED_OIL,
                    UniverseItems.HYDROGEN, UniverseItems.DEUTERIUM, UniverseItems.WATER);
            entries = items.stream().map(Supplier::get)
                    .map(item -> new Entry(item, tag(BuiltInRegistries.ITEM.getKey(item).getPath())))
                    .toList();
        }
        return entries;
    }

    /** 流体换成本模组的流体物品；对不上（或者带组件）返回空。 */
    public static Optional<Item> toItem(FluidResource resource) {
        if (resource.isEmpty() || !resource.isComponentsPatchEmpty()) {
            return Optional.empty();
        }
        for (Entry entry : entries()) {
            if (resource.is(entry.tag())) {
                return Optional.of(entry.item());
            }
        }
        return Optional.empty();
    }

    /** 流体物品对外算哪种流体；不是流体物品、或者标签里没有流体时返回 {@link FluidResource#EMPTY}。 */
    public static FluidResource toFluid(Item item) {
        Optional<Fluid> fluid;
        synchronized (OUTBOUND) {
            fluid = OUTBOUND.computeIfAbsent(item, FluidBridge::pick);
        }
        return fluid.map(FluidResource::of).orElse(FluidResource.EMPTY);
    }

    private static Optional<Fluid> pick(Item item) {
        for (Entry entry : entries()) {
            if (entry.item() != item) {
                continue;
            }
            Fluid first = null;
            for (Holder<Fluid> holder : BuiltInRegistries.FLUID.getTagOrEmpty(entry.tag())) {
                Fluid fluid = holder.value();
                if (fluid == Fluids.EMPTY) {
                    continue;
                }
                // 源流体优先：标签里常常源和流动的都放
                if (fluid.defaultFluidState().isSource()) {
                    return Optional.of(fluid);
                }
                if (first == null) {
                    first = fluid;
                }
            }
            return Optional.ofNullable(first);
        }
        return Optional.empty();
    }

    /** 标签变了以后清掉挑好的结果。 */
    public static void clear() {
        synchronized (OUTBOUND) {
            OUTBOUND.clear();
        }
    }
}
