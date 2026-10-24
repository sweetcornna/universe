package io.github.sweetcornna.universe.logistics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.sweetcornna.universe.blueprint.Construction;
import io.github.sweetcornna.universe.core.mecha.Distribution;
import io.github.sweetcornna.universe.core.mecha.LogisticsSlot;
import io.github.sweetcornna.universe.core.research.Upgrades;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.mecha.Mecha;
import io.github.sweetcornna.universe.mecha.MechaSettings;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.tech.ExosuitData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * 物流配送器的规划（ADR-0023）：每个维度每秒把加载着的配送器和玩家的物流栏位放在一起，用 {@link Distribution#plan} 算出派哪些机器人。
 * 另外负责机器人和玩家之间的交货、收货。
 */
public final class Distributors {
    private static final int INTERVAL = 20;
    private static final Map<ResourceKey<Level>, Set<BlockPos>> LOADED = new HashMap<>();

    private Distributors() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(LevelTickEvent.Post.class, event -> {
            if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % INTERVAL == 7) {
                ServerPerf.time("distributors", () -> plan(level));
            }
        });
        gameBus.addListener(ServerStoppedEvent.class, event -> LOADED.clear());
    }

    static void add(ServerLevel level, BlockPos pos) {
        LOADED.computeIfAbsent(level.dimension(), key -> new LinkedHashSet<>()).add(pos.immutable());
    }

    static void remove(ServerLevel level, BlockPos pos) {
        Set<BlockPos> set = LOADED.get(level.dimension());
        if (set != null) {
            set.remove(pos);
        }
    }

    private static void plan(ServerLevel level) {
        Set<BlockPos> loaded = LOADED.get(level.dimension());
        if (loaded == null || loaded.isEmpty()) {
            return;
        }
        List<DistributorBlockEntity> distributors = new ArrayList<>();
        List<Distribution.Node> nodes = new ArrayList<>();
        DistributorRegistry registry = DistributorRegistry.get(level.getServer());
        for (BlockPos pos : loaded) {
            if (level.getBlockEntity(pos) instanceof DistributorBlockEntity distributor) {
                // 机器人数这些只在这里顺手更新到登记表（ADR-0033 §三）
                registry.record(level, distributor);
            }
            if (level.getBlockEntity(pos) instanceof DistributorBlockEntity distributor && distributor.filter() != null) {
                nodes.add(new Distribution.Node(distributors.size(), pos.getX() + 0.5, pos.getZ() + 0.5, id(distributor.filter()),
                        distributor.icarus(), distributor.peer(), distributor.stock(), distributor.space(), distributor.idleBots()));
                distributors.add(distributor);
            }
        }
        if (nodes.isEmpty()) {
            return;
        }
        List<ServerPlayer> players = new ArrayList<>();
        List<Distribution.Request> requests = new ArrayList<>();
        if (Mecha.logisticsUnlocked(level)) {
            for (ServerPlayer player : level.players()) {
                if (!player.isAlive() || player.isSpectator()) {
                    continue;
                }
                MechaSettings settings = player.getData(UniverseAttachments.MECHA_SETTINGS);
                for (LogisticsSlot slot : settings.slots()) {
                    Item item = item(slot);
                    if (item == Items.AIR) {
                        continue;
                    }
                    int count = Construction.available(player, item);
                    int pending = 0;
                    for (DistributorBlockEntity distributor : distributors) {
                        pending += distributor.pending(player.getUUID(), item);
                    }
                    int supply = slot.supply(count + Math.max(0, pending));
                    int collect = slot.collect(count + Math.min(0, pending));
                    if (supply > 0 || collect > 0) {
                        requests.add(new Distribution.Request(players.size(), player.getX(), player.getZ(), slot.item(), supply, collect));
                    }
                }
                players.add(player);
            }
        }
        int capacity = Upgrades.botCapacity(Research.level(level, Upgrades.CARRIER_CAPACITY));
        for (Distribution.Dispatch dispatch : Distribution.plan(nodes, requests, capacity)) {
            DistributorBlockEntity distributor = distributors.get(dispatch.node());
            if (dispatch.kind() == Distribution.Kind.FETCH) {
                distributor.launch(dispatch, null, distributors.get(dispatch.target()));
            } else {
                distributor.launch(dispatch, players.get(dispatch.target()), null);
            }
        }
    }

    private static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static Item item(LogisticsSlot slot) {
        if (slot.isEmpty()) {
            return Items.AIR;
        }
        Identifier id = Identifier.tryParse(slot.item());
        return id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
    }

    // ---- 交货和收货

    /** 机器人送到：先放背包，放不下的放货舱。返回放进去了几个。 */
    static int give(ServerPlayer player, Item item, int amount) {
        int left = amount;
        Inventory inventory = player.getInventory();
        while (left > 0) {
            ItemStack stack = new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize()));
            int before = stack.getCount();
            inventory.add(stack);
            int moved = before - stack.getCount();
            left -= moved;
            if (!stack.isEmpty()) {
                break;
            }
        }
        if (left > 0) {
            ExosuitData data = player.getData(UniverseAttachments.EXOSUIT);
            for (int i = 0; i < data.cargoSlots() && left > 0; i++) {
                ItemStack slot = data.cargo().get(i);
                if (slot.isEmpty()) {
                    int n = Math.min(left, item.getDefaultMaxStackSize());
                    data.cargo().set(i, new ItemStack(item, n));
                    left -= n;
                } else if (slot.is(item) && slot.isComponentsPatchEmpty() && slot.getCount() < slot.getMaxStackSize()) {
                    int n = Math.min(left, slot.getMaxStackSize() - slot.getCount());
                    slot.grow(n);
                    left -= n;
                }
            }
        }
        return amount - left;
    }

    /** 这种物品身上比上限多几个（物流栏位里没设时是 0）。 */
    static int surplus(ServerPlayer player, Item item) {
        String id = id(item);
        int count = Construction.available(player, item);
        int surplus = 0;
        for (LogisticsSlot slot : player.getData(UniverseAttachments.MECHA_SETTINGS).slots()) {
            if (slot.item().equals(id)) {
                surplus = Math.max(surplus, slot.collect(count));
            }
        }
        return surplus;
    }

    /** 机器人收货：先拿货舱的，再拿背包、最后拿快捷栏的；只拿不带组件的。返回拿了几个。 */
    static int collect(ServerPlayer player, Item item, int amount) {
        int left = amount;
        ExosuitData data = player.getData(UniverseAttachments.EXOSUIT);
        for (int i = 0; i < data.cargoSlots() && left > 0; i++) {
            left -= take(data.cargo().get(i), item, left);
        }
        Inventory inventory = player.getInventory();
        for (int i = Inventory.getSelectionSize(); i < Inventory.INVENTORY_SIZE && left > 0; i++) {
            left -= take(inventory.getItem(i), item, left);
        }
        for (int i = 0; i < Inventory.getSelectionSize() && left > 0; i++) {
            left -= take(inventory.getItem(i), item, left);
        }
        if (left < amount) {
            inventory.setChanged();
        }
        return amount - left;
    }

    private static int take(ItemStack stack, Item item, int max) {
        if (!stack.is(item) || !stack.isComponentsPatchEmpty()) {
            return 0;
        }
        int n = Math.min(max, stack.getCount());
        stack.shrink(n);
        return n;
    }
}
