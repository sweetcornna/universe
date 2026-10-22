package io.github.sweetcornna.universe.station;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.survival.MultitoolSpec;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.SuitSlots;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.core.trade.Economy;
import io.github.sweetcornna.universe.core.trade.MultitoolMarket;
import io.github.sweetcornna.universe.entity.ship.ShipDealer;
import io.github.sweetcornna.universe.entity.ship.ShipDealerData;
import io.github.sweetcornna.universe.network.CabinetPayloads;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.survival.MultitoolItem;
import io.github.sweetcornna.universe.tech.TechModuleItem;
import io.github.sweetcornna.universe.tech.ToolWeaponItem;
import io.github.sweetcornna.universe.trade.Credits;
import io.github.sweetcornna.universe.trade.ItemValues;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 空间站的多功能工具柜（ADR-0026 §六）：每天挂 {@link MultitoolMarket#TOOLS_PER_DAY} 把工具，全服共用一份货，卖掉就没了
 * （和飞船交易终端记在同一个存档里）。买的时候主手上的旧多功能工具折价收走，里面装的模块和武器转到新工具上，
 * 装不下的退回背包；手上没拿工具就是直接买一把放进背包。
 */
public final class MultitoolCabinet {
    /** 离柜子多远还能买。 */
    private static final double REACH = 8;
    /** 卖掉的记录里，工具是一类。 */
    static final String TOOLS = "multitool";

    private record Session(ResourceKey<Level> dimension, BlockPos pos, int system) {
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private MultitoolCabinet() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerStoppedEvent.class, event -> SESSIONS.clear());
        gameBus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> SESSIONS.remove(event.getEntity().getUUID()));
    }

    /** 右键柜子：记下会话，发界面内容。 */
    public static void open(ServerPlayer player, ServerLevel level, BlockPos pos) {
        OptionalInt system = SpaceDimensions.system(level.dimension());
        if (system.isEmpty() || Stations.site(level.getServer(), level.dimension()).isEmpty()
                || Planets.galaxy(level.getServer()).system(system.getAsInt()).isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.universe.trade.no_signal"));
            return;
        }
        Session session = new Session(level.dimension(), pos.immutable(), system.getAsInt());
        SESSIONS.put(player.getUUID(), session);
        send(player, session, true, null);
    }

    public static void handleBuy(CabinetPayloads.Buy payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            Session session = session(player);
            if (session != null) {
                send(player, session, false, buy(player, session, payload.slot()));
            }
        }
    }

    private static @Nullable Session session(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !player.isAlive() || player.level().dimension() != session.dimension()
                || player.distanceToSqr(Vec3.atCenterOf(session.pos())) > REACH * REACH
                || !player.level().getBlockState(session.pos()).is(UniverseBlocks.MULTITOOL_CABINET.get())) {
            SESSIONS.remove(player.getUUID());
            return null;
        }
        return session;
    }

    // ---- 货单和价钱

    static List<MultitoolMarket.Offer> offers(MinecraftServer server, int system) {
        StarSystem star = Planets.galaxy(server).system(system).orElseThrow();
        return MultitoolMarket.offers(star.seed(), Economy.of(star).wealth(), ShipDealer.day(server));
    }

    /** 标价：工具本身加上自带模块的价值。 */
    static long price(MinecraftServer server, MultitoolMarket.Offer offer) {
        double modules = 0;
        for (TechModule module : offer.modules()) {
            modules += ItemValues.unitValue(server, ShipDealer.moduleStack(module));
        }
        return MultitoolMarket.price(offer.spec()) + Math.round(modules);
    }

    /** 手上这把旧工具折多少钱；不是多功能工具时 -1。没有类型的旧工具和做出来的 C 档手枪按做它的材料算，免得做了拿来折钱。 */
    public static long tradeIn(MinecraftServer server, ItemStack held) {
        if (!(held.getItem() instanceof MultitoolItem)) {
            return -1;
        }
        MultitoolSpec spec = held.get(UniverseDataComponents.TOOL_SPEC.get());
        if (spec != null && !spec.equals(MultitoolSpec.STARTER)) {
            return MultitoolMarket.tradeIn(spec);
        }
        return MultitoolMarket.legacyTradeIn(ItemValues.value(server, UniverseItems.MULTITOOL.get()).orElse(0));
    }

    private static void send(ServerPlayer player, Session session, boolean open, @Nullable Component notice) {
        MinecraftServer server = player.level().getServer();
        long day = ShipDealer.day(server);
        ShipDealerData data = ShipDealerData.get(server);
        List<CabinetPayloads.Offer> offers = new ArrayList<>();
        for (MultitoolMarket.Offer offer : offers(server, session.system())) {
            offers.add(new CabinetPayloads.Offer(offer.slot(), offer.seed(), offer.type().ordinal(), offer.grade().ordinal(), price(server, offer),
                    data.sold(session.system(), day, TOOLS, offer.slot())));
        }
        PacketDistributor.sendToPlayer(player, new CabinetPayloads.State(open, Credits.get(player), offers,
                tradeIn(server, player.getMainHandItem()), Optional.ofNullable(notice)));
    }

    // ---- 买

    private static Component buy(ServerPlayer player, Session session, int slot) {
        MinecraftServer server = player.level().getServer();
        long day = ShipDealer.day(server);
        ShipDealerData data = ShipDealerData.get(server);
        MultitoolMarket.Offer offer = offers(server, session.system()).stream().filter(o -> o.slot() == slot).findFirst().orElse(null);
        if (offer == null || data.sold(session.system(), day, TOOLS, slot)) {
            return Component.translatable("message.universe.dealer.sold_out");
        }
        long price = price(server, offer);
        ItemStack held = player.getMainHandItem();
        long tradeIn = Math.max(0, tradeIn(server, held));
        long due = MultitoolMarket.due(price, tradeIn);
        if (due > 0 && !Credits.spend(player, due)) {
            return Component.translatable("message.universe.dealer.poor", Credits.format(due - Credits.get(player)));
        }
        if (due < 0) {
            Credits.add(player, -due);
        }
        List<ItemStack> installed = new ArrayList<>();
        for (TechModule module : offer.modules()) {
            installed.add(ShipDealer.moduleStack(module));
        }
        boolean trading = held.getItem() instanceof MultitoolItem;
        if (trading) {
            held.getOrDefault(UniverseDataComponents.TOOL_MODULES.get(), ItemContainerContents.EMPTY).nonEmptyItemCopyStream()
                    .forEach(installed::add);
        }
        MultitoolSpec spec = offer.spec();
        ItemStack tool = MultitoolItem.create(spec);
        List<ItemStack> leftover = install(tool, spec, installed);
        if (trading) {
            player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        } else {
            player.getInventory().placeItemBackInInventory(tool, Prediction.SERVER_ONLY);
        }
        for (ItemStack stack : leftover) {
            player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
        }
        data.markSold(session.system(), day, TOOLS, slot);
        player.level().playSound(null, session.pos(), SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.6F, 1.4F);
        Component name = Component.translatable("multitool.universe.class." + spec.type().key());
        if (!leftover.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.universe.cabinet.returned", leftover.size()));
        }
        return due < 0
                ? Component.translatable("message.universe.cabinet.bought_refund", spec.grade().letter(), name, Credits.format(-due))
                : Component.translatable("message.universe.cabinet.bought", spec.grade().letter(), name, Credits.format(due));
    }

    /**
     * 把模块和武器装进新工具：按顺序往能用的格子里放，同种模块最多 {@value ModuleType#MAX_STACK} 个、同种武器一个。
     * 返回装不下的。
     */
    public static List<ItemStack> install(ItemStack tool, MultitoolSpec spec, List<ItemStack> stacks) {
        NonNullList<ItemStack> slots = NonNullList.withSize(SuitSlots.TOOL_SLOTS, ItemStack.EMPTY);
        List<ItemStack> leftover = new ArrayList<>();
        int used = 0;
        Map<Object, Integer> counts = new HashMap<>();
        for (ItemStack stack : stacks) {
            Object kind = stack.getItem() instanceof TechModuleItem module ? module.type()
                    : stack.getItem() instanceof ToolWeaponItem weapon ? weapon.weapon() : null;
            int limit = kind instanceof ModuleType ? ModuleType.MAX_STACK : 1;
            if (kind == null || used >= spec.working() || counts.getOrDefault(kind, 0) >= limit) {
                leftover.add(stack);
                continue;
            }
            counts.merge(kind, 1, Integer::sum);
            slots.set(used++, stack);
        }
        tool.set(UniverseDataComponents.TOOL_MODULES.get(), ItemContainerContents.fromItems(slots));
        return leftover;
    }
}
