package io.github.sweetcornna.universe.civ;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import io.github.sweetcornna.universe.core.civ.CivState;
import io.github.sweetcornna.universe.core.civ.Guild;
import io.github.sweetcornna.universe.core.civ.GuildMissions;
import io.github.sweetcornna.universe.core.civ.NpcRole;
import io.github.sweetcornna.universe.core.civ.Standing;
import io.github.sweetcornna.universe.core.mission.Mission;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.entity.civ.AlienNpc;
import io.github.sweetcornna.universe.mission.Missions;
import io.github.sweetcornna.universe.network.GuildEnvoyPayloads;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.trade.Credits;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 公会使者（ADR-0027 §六）：对话里按"公会任务"打开，每天 2 个这个公会的任务（和任务板共用"手上只能有一个"），
 * 档越高报酬越高；每升一档能来领一次礼物。使者是哪个公会的按 NPC 的种子定，空间站的使者不换人。
 *
 * <p>只有空间站上有使者，任务回这座空间站交付（在使者这儿或者任务板上都行）。除名的人使者不给任务、不给礼物。
 */
public final class GuildEnvoy {
    private static final double REACH = 8;

    private GuildEnvoy() {
    }

    /** 这个使者是哪个公会的。 */
    public static Guild guild(AlienNpc npc) {
        return GuildMissions.envoyGuild(npc.seed());
    }

    /** 打开这个使者今天的任务。 */
    public static void open(ServerPlayer player, AlienNpc npc) {
        if (SpaceDimensions.system(player.level().dimension()).isEmpty()) {
            overlay(player, Component.translatable("message.universe.guild.no_missions"));
            return;
        }
        send(player, npc, true);
    }

    public static void handleAction(GuildEnvoyPayloads.Action payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        Entity entity = player.level().getEntity(payload.entity());
        OptionalInt system = SpaceDimensions.system(player.level().dimension());
        if (!(entity instanceof AlienNpc npc) || !npc.isAlive() || npc.role() != NpcRole.GUILD_ENVOY || system.isEmpty()
                || player.distanceToSqr(npc) > REACH * REACH || !Standing.canTalk(Civ.state(player).tier(npc.race()))) {
            return;
        }
        Guild guild = guild(npc);
        Component message = switch (payload.action()) {
            case GuildEnvoyPayloads.Action.ACCEPT -> accept(player, system.getAsInt(), guild, payload.slot());
            case GuildEnvoyPayloads.Action.ABANDON -> Missions.abandon(player);
            case GuildEnvoyPayloads.Action.HAND_IN -> Missions.handIn(player, system.getAsInt());
            case GuildEnvoyPayloads.Action.GIFT -> claimGift(player, guild) ? null : Component.translatable("message.universe.guild.no_gift");
            default -> null;
        };
        if (message != null) {
            overlay(player, message);
        }
        send(player, npc, false);
    }

    private static Component accept(ServerPlayer player, int system, Guild guild, int slot) {
        Standing.Tier tier = Civ.state(player).tier(guild);
        if (!GuildMissions.serves(tier)) {
            return Component.translatable("message.universe.guild.expelled", Civ.guildName(guild));
        }
        List<Mission> offers = Missions.envoyOffers(player.level().getServer(), system, guild, tier);
        if (slot < 0 || slot >= offers.size()) {
            return null;
        }
        return Missions.accept(player, offers.get(slot));
    }

    /**
     * 领一档的礼物：信用点、导航数据，资深以后加一个这个公会擅长的模块。跳了几档就能连着领几次。没有可领的返回 false。
     */
    public static boolean claimGift(ServerPlayer player, Guild guild) {
        CivState state = Civ.state(player);
        if (!GuildMissions.serves(state.tier(guild))) {
            return false;
        }
        Optional<Standing.Tier> tier = state.claimGift(guild);
        if (tier.isEmpty()) {
            return false;
        }
        Civ.sync(player);
        Optional<GuildMissions.Gift> gift = GuildMissions.gift(guild, tier.get());
        if (gift.isEmpty()) {
            return true;
        }
        Credits.add(player, gift.get().credits());
        if (gift.get().navigationData() > 0) {
            give(player, new ItemStack(UniverseItems.NAVIGATION_DATA.get(), gift.get().navigationData()));
        }
        if (!gift.get().modules().isEmpty()) {
            Rng rng = Rng.of(Seeds.derive(Seeds.derive(player.getUUID().getLeastSignificantBits(), "guild_gift/" + guild.id()), tier.get().ordinal()));
            ModuleType type = rng.pick(gift.get().modules());
            TechModule module = TechModule.roll(type, gift.get().grade(), rng);
            ItemStack stack = new ItemStack(UniverseItems.MODULES.get(type).get());
            stack.set(UniverseDataComponents.TECH_MODULE.get(), module);
            give(player, stack);
        }
        if (player.connection != null) {
            player.sendSystemMessage(Component.translatable("message.universe.guild.gift", Civ.guildName(guild), Civ.guildTierName(tier.get()),
                    Credits.format(gift.get().credits())).withStyle(ChatFormatting.GOLD));
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.2F);
        return true;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        // GameTest 的模拟玩家没有连接，不发格子更新
        player.getInventory().placeItemBackInInventory(stack, player.connection != null, Prediction.SERVER_ONLY);
    }

    private static void send(ServerPlayer player, AlienNpc npc, boolean open) {
        OptionalInt system = SpaceDimensions.system(player.level().dimension());
        if (system.isEmpty() || player.connection == null) {
            return;
        }
        Guild guild = guild(npc);
        CivState state = Civ.state(player);
        Standing.Tier tier = state.tier(guild);
        List<Mission> offers = GuildMissions.serves(tier) ? Missions.envoyOffers(player.level().getServer(), system.getAsInt(), guild, tier)
                : List.of();
        List<Integer> states = Missions.states(player, offers);
        int gift = GuildMissions.serves(tier) && state.giftClaimable(guild) ? state.guildGifts()[guild.ordinal()] + 1 : -1;
        PacketDistributor.sendToPlayer(player, new GuildEnvoyPayloads.Open(open, npc.getId(), npc.npcName().zh(), npc.npcName().en(),
                guild.ordinal(), state.standing(guild), system.getAsInt(), offers, states, Missions.state(player).active(),
                Missions.have(player), gift));
    }

    private static void overlay(ServerPlayer player, Component message) {
        if (player.connection != null) {
            player.sendOverlayMessage(message);
        }
    }
}
