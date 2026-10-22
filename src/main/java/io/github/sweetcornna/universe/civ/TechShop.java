package io.github.sweetcornna.universe.civ;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.civ.NpcRole;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.civ.Standing;
import io.github.sweetcornna.universe.core.civ.TechMerchant;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.tech.ModuleLoot;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.dyson.Dyson;
import io.github.sweetcornna.universe.entity.civ.AlienNpc;
import io.github.sweetcornna.universe.network.TechShopPayloads;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.trade.Credits;
import io.github.sweetcornna.universe.trade.Trading;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 技术商的货（ADR-0027 §六）：对话里按"看货"打开。货从 {@link TechMerchant} 来：种类按 NPC 和日期定，品级看玩家的声望档；
 * 价钱照这个恒星系空间站卖模块的价，再按声望打折。每样每人每天只能买一次。
 */
public final class TechShop {
    /** 离技术商这么远以内才能买。 */
    private static final double REACH = 8;
    /** 恒星系里没有行情时的基准价（不会发生，保底）。 */
    private static final double FALLBACK_PRICE = 400;

    /** 一样货：模块和打完折的价钱。 */
    public record Offer(ItemStack stack, long price) {
    }

    private TechShop() {
    }

    /** 打开这个技术商今天的货。 */
    public static void open(ServerPlayer player, AlienNpc npc) {
        send(player, npc, true, null);
    }

    /** 这个技术商今天卖给这个玩家的货：种族是 NPC 的种族，档是玩家在这个种族的声望档。 */
    public static List<Offer> offers(ServerPlayer player, AlienNpc npc) {
        MinecraftServer server = player.level().getServer();
        Race race = npc.race();
        Standing.Tier tier = Civ.state(player).tier(race);
        OptionalInt system = Dyson.systemOf(npc.level().dimension());
        Optional<StarSystem> star = system.isEmpty() ? Optional.empty() : Planets.galaxy(server).system(system.getAsInt());
        int rarity = star.map(s -> ModuleLoot.rarity(s.star().starClass())).orElse(0);
        long seed = Seeds.derive(Seeds.derive(npc.seed(), "tech_merchant"), Dialogues.day(server));
        double factor = Standing.priceFactor(tier);
        List<Offer> offers = new ArrayList<>();
        for (TechModule module : TechMerchant.stock(race, tier, rarity, seed)) {
            ItemStack stack = new ItemStack(UniverseItems.MODULES.get(module.type()).get());
            stack.set(UniverseDataComponents.TECH_MODULE.get(), module);
            OptionalDouble price = system.isEmpty() ? OptionalDouble.empty() : Trading.modulePrice(server, system.getAsInt(), stack);
            offers.add(new Offer(stack, (long) Math.ceil(price.orElse(FALLBACK_PRICE) * factor - 1e-9)));
        }
        return offers;
    }

    private static String key(AlienNpc npc, int index) {
        return "shop:" + npc.key() + ":" + index;
    }

    /** 今天买过第几样没有。 */
    public static boolean sold(ServerPlayer player, AlienNpc npc, int index) {
        return Civ.usedToday(player, key(npc, index));
    }

    public static void handleBuy(TechShopPayloads.Buy payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Entity entity = player.level().getEntity(payload.entity());
        if (!(entity instanceof AlienNpc npc) || !npc.isAlive() || npc.role() != NpcRole.TECH_MERCHANT || player.distanceToSqr(npc) > REACH * REACH) {
            return;
        }
        Component notice = buy(player, npc, payload.index(), payload.price());
        send(player, npc, false, notice);
    }

    /**
     * 买第 {@code index} 样。{@code expected} 是界面上看到的价钱，和现算的对不上（档变了、换了一天）就不买。返回界面底下写的结果；
     * 买成没有看信用点和背包。
     */
    public static Component buy(ServerPlayer player, AlienNpc npc, int index, long expected) {
        if (!TechMerchant.sells(Civ.state(player).tier(npc.race()))) {
            return Component.translatable("message.universe.npc.refuses", npc.label()).withStyle(ChatFormatting.RED);
        }
        List<Offer> offers = offers(player, npc);
        if (index < 0 || index >= offers.size()) {
            return Component.translatable("gui.universe.tech_shop.changed").withStyle(ChatFormatting.YELLOW);
        }
        if (sold(player, npc, index)) {
            return Component.translatable("gui.universe.tech_shop.sold_today").withStyle(ChatFormatting.GRAY);
        }
        Offer offer = offers.get(index);
        if (offer.price() != expected) {
            return Component.translatable("gui.universe.tech_shop.changed").withStyle(ChatFormatting.YELLOW);
        }
        if (!Credits.spend(player, offer.price())) {
            return Component.translatable("gui.universe.tech_shop.not_enough", Credits.format(offer.price())).withStyle(ChatFormatting.RED);
        }
        Civ.markUsed(player, key(npc, index));
        ItemStack bought = offer.stack().copy();
        Component name = bought.getHoverName();
        // GameTest 的模拟玩家没有连接，不发格子更新
        player.getInventory().placeItemBackInInventory(bought, player.connection != null, Prediction.SERVER_ONLY);
        return Component.translatable("gui.universe.tech_shop.bought", name, Credits.format(offer.price())).withStyle(ChatFormatting.GREEN);
    }

    /** 把货单发给玩家。 */
    static void send(ServerPlayer player, AlienNpc npc, boolean open, @Nullable Component notice) {
        if (player.connection == null) {
            return;
        }
        List<TechShopPayloads.Row> rows = new ArrayList<>();
        List<Offer> offers = offers(player, npc);
        for (int i = 0; i < offers.size(); i++) {
            Offer offer = offers.get(i);
            rows.add(new TechShopPayloads.Row(offer.stack(), offer.price(), sold(player, npc, i)));
        }
        Race race = npc.race();
        PacketDistributor.sendToPlayer(player, new TechShopPayloads.State(open, npc.getId(), npc.npcName().zh(), npc.npcName().en(), race.ordinal(),
                Civ.state(player).tier(race).ordinal(), Credits.get(player), rows, Optional.ofNullable(notice)));
    }
}
