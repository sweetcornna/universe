package io.github.sweetcornna.universe.settlement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.civ.Civ;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.civ.Standing;
import io.github.sweetcornna.universe.core.poi.Landmark;
import io.github.sweetcornna.universe.core.poi.LandmarkLayout;
import io.github.sweetcornna.universe.core.poi.LandmarkType;
import io.github.sweetcornna.universe.core.poi.SettlementLayout;
import io.github.sweetcornna.universe.core.procgen.NameGenerator;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.settlement.BuildingType;
import io.github.sweetcornna.universe.core.settlement.Cost;
import io.github.sweetcornna.universe.core.settlement.Decisions;
import io.github.sweetcornna.universe.core.settlement.Plot;
import io.github.sweetcornna.universe.core.settlement.Product;
import io.github.sweetcornna.universe.core.settlement.Settlement;
import io.github.sweetcornna.universe.core.settlement.SettlementRules;
import io.github.sweetcornna.universe.fishing.Fishing;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.poi.Landmarks;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.sound.SettlementSounds;
import io.github.sweetcornna.universe.registry.sound.WorldSounds;
import io.github.sweetcornna.universe.trade.Credits;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 聚落（ADR-0034 §二）的服务端：接管、施工、每天的结算、决定、生产，和界面来回的包。数值在 {@link Settlement}，存档在
 * {@link SettlementData}，世界里的方块由 {@link SettlementBuilder} 盖，海盗由 {@link SettlementRaids} 管，居民由 {@link Settlers} 放。
 *
 * <p>入口：没有总督的聚落里，蹲下右键交易终端打开接管的界面；接管以后同样蹲下右键交易终端、或者右键任意一块地基前的施工终端打开管理界面。
 * 按键打开"我的聚落"表，人不在聚落里只能看不能管。多人时只有总督能管，别人进得来、看得到。
 */
public final class Settlements {
    /** 离聚落中心多远以内能管。 */
    public static final double ACT_RANGE = 64;
    /** 走进这么近提示一次这是谁的聚落。 */
    private static final double HINT_RANGE = 30;
    /** 储备里一次最多取几批。 */
    private static final int WITHDRAW_BATCHES = 64;
    /** 鱼塘、瞭望塔攒的东西最多留几组。 */
    private static final int STOCK_STACKS = 18;
    /** 每个人最近一次提示的是哪个聚落。 */
    private static final Map<UUID, String> HINTED = new HashMap<>();
    /** 聚落中心多远以内不出局部危害（格，按方形算）。 */
    private static final int HAZARD_FREE = 40;

    private Settlements() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Post.class, event -> ServerPerf.time("settlements", () -> tick(event.getServer())));
        gameBus.addListener(PlayerInteractEvent.RightClickBlock.class, Settlements::onRightClick);
        gameBus.addListener(RegisterCommandsEvent.class, event -> SettlementCommand.register(event.getDispatcher()));
        gameBus.addListener(ServerStoppedEvent.class, event -> {
            HINTED.clear();
            SettlementRaids.clear();
            Settlers.clear();
        });
        // 有总督的聚落里不出局部危害（ADR-0034 §六）：地基最远到 30 格，再留一圈
        io.github.sweetcornna.universe.hazard.Hazards.EXEMPTIONS.add((level, pos) -> {
            for (SettlementData.Entry entry : SettlementData.get(level.getServer()).in(dimension(level))) {
                Settlement s = entry.settlement();
                if (s.owner().isPresent() && Math.abs(pos.getX() - s.x()) <= HAZARD_FREE && Math.abs(pos.getZ() - s.z()) <= HAZARD_FREE) {
                    return true;
                }
            }
            return false;
        });
    }

    static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    static @Nullable ServerLevel level(MinecraftServer server, Settlement s) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(s.dimension())));
    }

    static String dimension(Level level) {
        return level.dimension().identifier().toString();
    }

    // ---- 找聚落

    /** 这里附近的聚落地标（只有星球上有）。 */
    static Optional<Landmark> landmarkNear(ServerLevel level, double x, double z, double radius) {
        return Landmarks.layout(level).flatMap(layout -> layout.nearest(LandmarkType.SETTLEMENT, x, z, radius));
    }

    /** 这处地标对应的聚落：接管过的从存档拿，没接管过的现算一个（还不记进存档）。 */
    static Settlement site(ServerLevel level, Landmark landmark) {
        String id = Settlement.id(dimension(level), landmark.key());
        Optional<SettlementData.Entry> entry = SettlementData.get(level.getServer()).entry(id);
        if (entry.isPresent()) {
            return entry.get().settlement();
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, landmark.x(), landmark.z()) - 1;
        Race race = Civ.dominant(level).orElse(Race.TRADER);
        return new Settlement(dimension(level), landmark.key(), landmark.x(), y, landmark.z(), landmark.rotation(), landmark.seed(), race);
    }

    /** 聚落的名字："中文|English"，按种子用这个种族的语言起。 */
    static String name(MinecraftServer server, Settlement s) {
        NameGenerator.Name name = Civ.tongue(server, s.race()).name(Seeds.derive(s.seed(), "settlement/name"));
        return name.zh() + "|" + name.en();
    }

    /** 这个位置在哪个接管过的聚落里（地基、广场都算）。 */
    static Optional<SettlementData.Entry> at(ServerLevel level, BlockPos pos) {
        for (SettlementData.Entry entry : SettlementData.get(level.getServer()).in(dimension(level))) {
            if (entry.settlement().distance(pos.getX() + 0.5, pos.getZ() + 0.5) <= 40) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /** 世界坐标落在第几块地基上，不在是 -1。 */
    static int plotAt(SettlementData.Entry entry, BlockPos pos) {
        int[] local = SettlementLayout.local(entry.settlement().landmark(), pos.getX(), pos.getZ());
        return SettlementLayout.plotAt(local[0], local[1]);
    }

    // ---- 入口

    /** 蹲下右键聚落里的交易终端：打开接管或管理的界面。 */
    private static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player) || !player.isShiftKeyDown()) {
            return;
        }
        if (!level.getBlockState(event.getPos()).is(UniverseBlocks.OUTPOST_TRADE_TERMINAL.get())) {
            return;
        }
        Optional<Landmark> landmark = landmarkNear(level, event.getPos().getX(), event.getPos().getZ(), LandmarkType.SETTLEMENT.radius() + 2);
        if (landmark.isEmpty()) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        open(player, level, site(level, landmark.get()), 0, -1);
    }

    /**
     * 右键聚落终端：施工终端打开这块地基，总督办公室门口的那台和广场上交易棚边的那台打开概况；没有总督的聚落打开接管的界面。
     */
    static void useTerminal(ServerPlayer player, ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, WorldSounds.TERMINAL_ACCESS.get(), SoundSource.BLOCKS, 0.7F, 1.0F);
        Optional<SettlementData.Entry> entry = at(level, pos);
        if (entry.isEmpty() || !entry.get().settlement().owned()) {
            landmarkNear(level, pos.getX(), pos.getZ(), LandmarkType.SETTLEMENT.radius() + 2)
                    .ifPresent(landmark -> open(player, level, site(level, landmark), 0, -1));
            return;
        }
        int index = plotAt(entry.get(), pos);
        Plot plot = index < 0 ? null : entry.get().settlement().plot(index);
        boolean overview = plot == null || plot.type() == BuildingType.OFFICE && plot.active();
        open(player, level, entry.get().settlement(), overview ? 0 : 2, overview ? -1 : index);
    }

    /** 打开界面。{@code tab} 见 {@code SettlementScreen.Tab} 的序号，{@code focus} 是选中的地基。 */
    public static void open(ServerPlayer player, ServerLevel level, Settlement s, int tab, int focus) {
        send(player, view(player, level.getServer(), s, tab, focus), true);
    }

    private static void send(ServerPlayer player, CompoundTag data, boolean open) {
        if (player.connection != null) {
            PacketDistributor.sendToPlayer(player, new SettlementPayloads.View(data, open));
        }
    }

    static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().placeItemBackInInventory(stack, player.connection != null, net.minecraft.util.Prediction.SERVER_ONLY);
    }

    static void tell(@Nullable ServerPlayer player, Component message) {
        if (player != null && player.connection != null) {
            player.sendOverlayMessage(message);
        }
    }

    static void chat(@Nullable ServerPlayer player, Component message) {
        if (player != null && player.connection != null) {
            player.sendSystemMessage(message);
        }
    }

    static @Nullable ServerPlayer owner(MinecraftServer server, Settlement s) {
        return s.owner().map(uuid -> server.getPlayerList().getPlayer(uuid)).orElse(null);
    }

    static boolean near(ServerPlayer player, Settlement s) {
        return dimension(player.level()).equals(s.dimension()) && s.distance(player.getX(), player.getZ()) <= ACT_RANGE;
    }

    // ---- 界面的内容

    /** 发给界面的一份：聚落的全部数值，加上看的人自己的信用点、声望和"我的聚落"表。 */
    static CompoundTag view(ServerPlayer player, MinecraftServer server, @Nullable Settlement s, int tab, int focus) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("now", now(server));
        tag.putLong("credits", Credits.get(player));
        tag.putInt("tab", tab);
        tag.putInt("focus", focus);
        SettlementData data = SettlementData.get(server);
        if (s != null) {
            Optional<SettlementData.Entry> entry = data.entry(s.id());
            tag.put("settlement", SettlementData.write(s));
            tag.putString("id", s.id());
            tag.putString("name", entry.map(SettlementData.Entry::name).orElse(name(server, s)));
            ServerLevel level = level(server, s);
            if (level != null) {
                Planets.planet(level).ifPresent(planet -> {
                    tag.putString("place_zh", planet.name().zh());
                    tag.putString("place_en", planet.name().en());
                });
            }
            Standing.Tier tier = Civ.state(player).tier(s.race());
            tag.putInt("tier", tier.ordinal());
            tag.putBoolean("near", near(player, s));
            tag.putBoolean("mine", s.ownedBy(player.getUUID()));
            tag.putInt("raid", SettlementRaids.remaining(s.id()));
            ListTag stock = new ListTag();
            if (entry.isPresent()) {
                var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
                for (ItemStack stack : entry.get().stock()) {
                    ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent(stock::add);
                }
            }
            tag.put("stock", stock);
        }
        tag.putInt("owned", data.ownedBy(player.getUUID()).size());
        ListTag mine = new ListTag();
        for (SettlementData.Entry e : data.ownedBy(player.getUUID())) {
            Settlement m = e.settlement();
            CompoundTag row = new CompoundTag();
            row.putString("id", m.id());
            row.putString("name", e.name());
            ServerLevel level = level(server, m);
            if (level != null) {
                Planets.planet(level).ifPresent(planet -> {
                    row.putString("place_zh", planet.name().zh());
                    row.putString("place_en", planet.name().en());
                });
            }
            row.putString("class", io.github.sweetcornna.universe.core.settlement.SettlementRules.classLetter(m.settlementClass()));
            row.putInt("population", m.population());
            row.putInt("housing", m.housing());
            row.putInt("happiness", (int) Math.round(m.happiness()));
            row.putInt("threat", (int) Math.round(m.threat()));
            row.putInt("reserve", (int) Math.floor(m.reserve()));
            row.putInt("cap", m.reserveCap());
            row.putLong("debt", m.debt());
            row.putBoolean("decision", m.decision().isPresent());
            row.putBoolean("raid", m.raidAt() >= 0);
            row.putInt("buildings", m.buildings());
            row.putInt("x", m.x());
            row.putInt("z", m.z());
            mine.add(row);
        }
        tag.put("list", mine);
        return tag;
    }

    /** 操作的结果写进发给界面的那一份，界面底下显示。 */
    private static CompoundTag notice(MinecraftServer server, CompoundTag tag, @Nullable Component notice) {
        if (notice != null) {
            ComponentSerialization.CODEC.encodeStart(server.registryAccess().createSerializationContext(NbtOps.INSTANCE), notice).result()
                    .ifPresent(encoded -> tag.put("notice", encoded));
        }
        return tag;
    }

    // ---- 界面来的操作

    public static void handleAction(SettlementPayloads.Action payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (payload.action() == SettlementPayloads.Action.LIST || payload.settlement().isEmpty()) {
            // "我的聚落"表；人在哪个聚落里，就顺带把那个聚落的几页也给上
            send(player, view(player, server, here(player), 5, -1), payload.action() == SettlementPayloads.Action.LIST);
            return;
        }
        SettlementData data = SettlementData.get(server);
        Optional<SettlementData.Entry> found = data.entry(payload.settlement());
        if (payload.action() == SettlementPayloads.Action.CLAIM) {
            claimHere(player);
            return;
        }
        if (found.isEmpty()) {
            // 还没接管的：只有刷新
            send(player, view(player, server, here(player), -1, -1), false);
            return;
        }
        SettlementData.Entry entry = found.get();
        Settlement s = entry.settlement();
        Component notice = act(player, entry, payload.action(), payload.plot(), payload.value());
        send(player, notice(server, view(player, server, s, -1, payload.plot()), notice), false);
    }

    /** 玩家所在的聚落：接管过的、或者附近的聚落地标；不在聚落里是 null。 */
    private static @Nullable Settlement here(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        Optional<SettlementData.Entry> entry = at(level, player.blockPosition());
        if (entry.isPresent()) {
            return entry.get().settlement();
        }
        return landmarkNear(level, player.getX(), player.getZ(), ACT_RANGE).map(landmark -> site(level, landmark)).orElse(null);
    }

    /** 接管玩家所在的这个聚落（界面上的"接管"）。 */
    private static void claimHere(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Optional<Landmark> landmark = landmarkNear(level, player.getX(), player.getZ(), ACT_RANGE);
        if (landmark.isEmpty()) {
            tell(player, Component.translatable("message.universe.settlement.not_here"));
            return;
        }
        Settlement site = site(level, landmark.get());
        Component problem = claim(player, level, site);
        if (problem != null) {
            level.playSound(null, player.blockPosition(), WorldSounds.TERMINAL_DENY.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        send(player, notice(level.getServer(), view(player, level.getServer(), site(level, landmark.get()), 0, -1), problem), false);
    }

    /**
     * 接管 {@code site}：声望到"相识"、交齐建设物资、管着的不到 4 个、没有别的总督。成了就整平地基、修路。
     *
     * @return 不行的原因；成了返回 null
     */
    public static @Nullable Component claim(ServerPlayer player, ServerLevel level, Settlement site) {
        return claim(player, level, site, false);
    }

    /** {@code force}：调试用，不看声望、不收材料、不限个数。 */
    public static @Nullable Component claim(ServerPlayer player, ServerLevel level, Settlement site, boolean force) {
        MinecraftServer server = level.getServer();
        SettlementData data = SettlementData.get(server);
        Settlement s = data.entry(site.id()).map(SettlementData.Entry::settlement).orElse(site);
        if (s.owned()) {
            return s.ownedBy(player.getUUID()) ? Component.translatable("message.universe.settlement.already_yours")
                    : Component.translatable("message.universe.settlement.taken", s.ownerName());
        }
        if (!force && data.ownedBy(player.getUUID()).size() >= SettlementRules.MAX_OWNED) {
            return Component.translatable("message.universe.settlement.too_many", SettlementRules.MAX_OWNED);
        }
        Standing.Tier tier = Civ.state(player).tier(s.race());
        if (!force && tier.ordinal() < Standing.Tier.ACQUAINTED.ordinal()) {
            return Component.translatable("message.universe.settlement.standing", Civ.raceName(s.race()), Civ.tierName(Standing.Tier.ACQUAINTED));
        }
        List<Cost> cost = SettlementRules.claimCost();
        if (!force && !Materials.enough(player, cost)) {
            return Component.translatable("message.universe.settlement.claim_materials");
        }
        for (Cost c : force ? List.<Cost>of() : cost) {
            Materials.take(player, c, c.count());
        }
        SettlementData.Entry entry = data.add(s, name(server, s));
        s.claim(player.getUUID(), player.getGameProfile().name(), now(server));
        SettlementBuilder.prepare(level, entry);
        for (int i = 0; i < s.plotCount(); i++) {
            SettlementBuilder.sync(level, entry, i, now(server), true);
        }
        data.changed();
        level.playSound(null, s.x(), s.y() + 1, s.z(), SettlementSounds.CLAIM.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
        chat(player, Component.translatable("message.universe.settlement.claimed", displayName(entry)).withStyle(ChatFormatting.GOLD));
        return null;
    }

    static Component displayName(SettlementData.Entry entry) {
        String[] parts = entry.name().split("\\|", 2);
        return Component.translatable("message.universe.settlement.name", parts[0], parts.length > 1 ? parts[1] : parts[0]);
    }

    /** 名字里取当前语言的那一半：服务端不知道客户端的语言，聊天里用可翻译的键（两半都给，语言文件里挑）。 */
    private static Component buildingName(@Nullable BuildingType type) {
        return Component.translatable("settlement.universe.building." + (type == null ? "none" : type.id()));
    }

    /** 做一个操作，返回给玩家的提示（没有是 null）。 */
    static @Nullable Component act(ServerPlayer player, SettlementData.Entry entry, int action, int index, int value) {
        Settlement s = entry.settlement();
        if (!s.ownedBy(player.getUUID())) {
            return Component.translatable("message.universe.settlement.not_owner");
        }
        if (action == SettlementPayloads.Action.REFRESH) {
            return null;
        }
        if (!near(player, s)) {
            return Component.translatable("message.universe.settlement.too_far");
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        MinecraftServer server = level.getServer();
        long now = now(server);
        SettlementData data = SettlementData.get(server);
        boolean plotAction = action >= SettlementPayloads.Action.START && action <= SettlementPayloads.Action.CANCEL;
        if (plotAction && (index < 0 || index >= s.plotCount())) {
            return null;
        }
        Component notice = switch (action) {
            case SettlementPayloads.Action.START -> start(level, entry, index, value);
            case SettlementPayloads.Action.DELIVER -> deliver(player, level, entry, index, now);
            case SettlementPayloads.Action.OPEN -> open(level, entry, index);
            case SettlementPayloads.Action.UPGRADE -> s.plot(index).startUpgrade()
                    ? Component.translatable("message.universe.settlement.upgrade_started", buildingName(s.plot(index).type()))
                    : Component.translatable("message.universe.settlement.cannot_upgrade");
            case SettlementPayloads.Action.CANCEL -> {
                BuildingType type = s.plot(index).type();
                yield s.plot(index).cancel() ? Component.translatable("message.universe.settlement.cancelled", buildingName(type)) : null;
            }
            case SettlementPayloads.Action.DECIDE -> decide(player, s, value);
            case SettlementPayloads.Action.PRODUCT -> value >= 0 && value < Product.values().length && s.setProduct(Product.values()[value])
                    ? Component.translatable("message.universe.settlement.product_set", Component.translatable("settlement.universe.product."
                            + Product.values()[value].id()))
                    : null;
            case SettlementPayloads.Action.WITHDRAW -> withdraw(player, s);
            case SettlementPayloads.Action.SELL -> {
                long credits = s.sell();
                if (credits <= 0) {
                    yield Component.translatable("message.universe.settlement.reserve_empty");
                }
                Credits.add(player, credits);
                level.playSound(null, player.blockPosition(), WorldSounds.CREDITS.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
                yield Component.translatable("message.universe.settlement.sold", Credits.format(credits));
            }
            case SettlementPayloads.Action.TREASURY -> {
                long credits = s.takeTreasury();
                if (credits <= 0) {
                    yield Component.translatable("message.universe.settlement.treasury_empty");
                }
                Credits.add(player, credits);
                level.playSound(null, player.blockPosition(), WorldSounds.CREDITS.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
                yield Component.translatable("message.universe.settlement.treasury_taken", Credits.format(credits));
            }
            case SettlementPayloads.Action.STOCK -> {
                if (entry.stock().isEmpty()) {
                    yield Component.translatable("message.universe.settlement.stock_empty");
                }
                int count = 0;
                for (ItemStack stack : entry.stock()) {
                    count += stack.getCount();
                    give(player, stack.copy());
                }
                entry.stock().clear();
                yield Component.translatable("message.universe.settlement.stock_taken", count);
            }
            case SettlementPayloads.Action.PAY_DEBT -> {
                long amount = Math.min(s.debt(), Credits.get(player));
                if (amount <= 0 || !Credits.spend(player, amount)) {
                    yield Component.translatable("message.universe.settlement.no_credits");
                }
                s.payDebt(amount);
                yield Component.translatable("message.universe.settlement.debt_paid", Credits.format(amount));
            }
            case SettlementPayloads.Action.RESIGN -> {
                s.resign();
                SettlementRaids.cancel(s.id());
                yield Component.translatable("message.universe.settlement.resigned", displayName(entry));
            }
            default -> null;
        };
        if (plotAction) {
            SettlementBuilder.sync(level, entry, index, now, true);
        }
        data.changed();
        return notice;
    }

    private static Component start(ServerLevel level, SettlementData.Entry entry, int index, int value) {
        Settlement s = entry.settlement();
        if (value < 0 || value >= BuildingType.values().length) {
            return Component.translatable("message.universe.settlement.cannot_start");
        }
        BuildingType type = BuildingType.values()[value];
        if (s.plot(index).type() != null) {
            return Component.translatable("message.universe.settlement.plot_taken");
        }
        if (!s.canStart(type)) {
            return Component.translatable(type.unique() ? "message.universe.settlement.only_one" : "message.universe.settlement.office_first");
        }
        s.start(index, type);
        level.playSound(null, SettlementBuilder.terminalPos(entry, index), SettlementSounds.BUILD.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
        return Component.translatable("message.universe.settlement.started", buildingName(type), index + 1);
    }

    private static Component deliver(ServerPlayer player, ServerLevel level, SettlementData.Entry entry, int index, long now) {
        Settlement s = entry.settlement();
        Plot plot = s.plot(index);
        List<Cost> costs = plot.costs();
        if (costs.isEmpty()) {
            return Component.translatable("message.universe.settlement.nothing_to_deliver");
        }
        boolean damaged = plot.damaged();
        int given = 0;
        for (Cost cost : costs) {
            int n = Materials.take(player, cost, plot.missing(cost));
            given += plot.deliver(cost, n, now);
        }
        if (given == 0) {
            return Component.translatable("message.universe.settlement.no_materials");
        }
        BlockPos at = SettlementBuilder.terminalPos(entry, index);
        level.playSound(null, at, SettlementSounds.DELIVER.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
        if (damaged && !plot.damaged()) {
            s.repaired(index);
            level.playSound(null, at, WorldSounds.REPAIR.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
            return Component.translatable("message.universe.settlement.repaired", buildingName(plot.type()));
        }
        if (plot.readyAt() > 0) {
            return Component.translatable("message.universe.settlement.delivered_all", SettlementRules.STAGE_WAIT / 1200);
        }
        return Component.translatable("message.universe.settlement.delivered", given, plot.missing());
    }

    private static @Nullable Component open(ServerLevel level, SettlementData.Entry entry, int index) {
        Settlement s = entry.settlement();
        if (!s.open(index)) {
            return null;
        }
        SettlementBuilder.Frame frame = SettlementBuilder.frame(entry, index);
        SettlementBuilder.sync(level, entry, index, now(level.getServer()), true);
        SettlementBuilder.fireworks(level, frame, Palette.of(s.race()));
        level.playSound(null, frame.at(0, 2, -SettlementLayout.PLOT_HALF), SettlementSounds.OPEN.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
        return Component.translatable("message.universe.settlement.opened", buildingName(s.plot(index).type()));
    }

    private static @Nullable Component decide(ServerPlayer player, Settlement s, int option) {
        Optional<Decisions.Decision> pending = s.decision();
        if (pending.isEmpty()) {
            return Component.translatable("message.universe.settlement.no_decision");
        }
        Optional<Decisions.Effect> applied = s.decide(option, player.hasInfiniteMaterials() ? Long.MAX_VALUE : Credits.get(player));
        if (applied.isEmpty()) {
            return Component.translatable("message.universe.settlement.cannot_afford");
        }
        Decisions.Effect effect = applied.get();
        if (effect.credits() < 0) {
            Credits.spend(player, -effect.credits());
        } else if (effect.credits() > 0) {
            Credits.add(player, effect.credits());
        }
        if (effect.standing() != 0) {
            Civ.changeStanding(player, s.race(), effect.standing());
        }
        if (!effect.item().isEmpty() && effect.count() > 0) {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(effect.item()));
            give(player, new ItemStack(item, effect.count()));
        }
        if (effect.word()) {
            Civ.teachWord(player, s.race(), -1, Seeds.derive(s.seed(), s.day()));
        }
        player.level().playSound(null, player.blockPosition(), WorldSounds.TERMINAL_CONFIRM.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        return Component.translatable("message.universe.settlement.decided");
    }

    private static Component withdraw(ServerPlayer player, Settlement s) {
        Product product = s.effectiveProduct();
        int batches = s.withdraw(WITHDRAW_BATCHES);
        if (batches <= 0) {
            return Component.translatable("message.universe.settlement.reserve_empty");
        }
        int amount = batches * product.items();
        if (product.credits()) {
            Credits.add(player, amount);
            player.level().playSound(null, player.blockPosition(), WorldSounds.CREDITS.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
            return Component.translatable("message.universe.settlement.withdrew_credits", Credits.format(amount));
        }
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(product.item()));
        int left = amount;
        while (left > 0) {
            int n = Math.min(left, item.getDefaultMaxStackSize());
            give(player, new ItemStack(item, n));
            left -= n;
        }
        return Component.translatable("message.universe.settlement.withdrew", amount, new ItemStack(item).getHoverName());
    }

    // ---- 每刻

    private static void tick(MinecraftServer server) {
        SettlementData data = SettlementData.get(server);
        long now = now(server);
        long today = SettlementRules.day(now);
        int ticks = server.getTickCount();
        for (SettlementData.Entry entry : List.copyOf(data.all())) {
            Settlement s = entry.settlement();
            if (!s.owned()) {
                continue;
            }
            ServerLevel level = level(server, s);
            // 每天的结算：服务器关着时游戏刻不走，最多补 30 天
            if (s.day() < today - 30) {
                s.advanceDay(today - 31, Rng.of(Seeds.derive(s.seed(), today - 31)));
            }
            while (s.day() < today) {
                long day = s.day() + 1;
                Settlement.DayReport report = s.advanceDay(day, Rng.of(Seeds.derive(s.seed(), day)));
                daily(server, level, entry, report);
                data.changed();
            }
            for (int[] done : s.tickPlots(now)) {
                finished(server, level, entry, done[0], Plot.Finished.values()[done[1]]);
                data.changed();
            }
            if (s.raidAt() >= 0 && now >= s.raidAt() && !SettlementRaids.active(s.id())) {
                SettlementRaids.begin(server, level, entry, now);
                data.changed();
            }
            if (level != null && (ticks + (int) (s.key() & 15)) % 20 == 0) {
                for (int i = 0; i < s.plotCount(); i++) {
                    Plot plot = s.plot(i);
                    if (SettlementBuilder.sync(level, entry, i, now, false) && plot.readyAt() > 0) {
                        SettlementBuilder.Frame frame = SettlementBuilder.frame(entry, i);
                        SettlementBuilder.dust(level, frame, 6);
                        level.playSound(null, frame.at(0, 2, 0), SettlementSounds.BUILD.get(), SoundSource.BLOCKS, 0.8F,
                                0.9F + level.getRandom().nextFloat() * 0.2F);
                    }
                }
            }
        }
        SettlementRaids.tick(server, now);
        if (ticks % 40 == 0) {
            Settlers.tick(server);
            hints(server);
        }
    }

    /** 一天的结算以后：鱼塘、瞭望塔出东西，告诉总督今天的决定和晚上的海盗。 */
    private static void daily(MinecraftServer server, @Nullable ServerLevel level, SettlementData.Entry entry, Settlement.DayReport report) {
        Settlement s = entry.settlement();
        if (level != null && report.fish() > 0) {
            for (int i = 0; i < s.plotCount(); i++) {
                Plot plot = s.plot(i);
                if (plot.active() && plot.type() == BuildingType.FISHING_POND) {
                    for (int k = 0; k < SettlementRules.fishPerDay(plot.grade()); k++) {
                        stock(entry, Fishing.pondCatch(level, SettlementBuilder.pond(entry, i), level.getRandom(), plot.grade()));
                    }
                }
            }
        }
        if (report.navData() > 0) {
            stock(entry, new ItemStack(UniverseItems.NAVIGATION_DATA.get(), report.navData()));
        }
        ServerPlayer owner = owner(server, s);
        if (owner == null) {
            return;
        }
        if (report.decision() != null) {
            chat(owner, Component.translatable("message.universe.settlement.new_decision", displayName(entry)).withStyle(ChatFormatting.AQUA));
            notify(owner, SettlementSounds.DECISION.get());
        }
        if (report.raid()) {
            chat(owner, Component.translatable("message.universe.settlement.raid_warning", displayName(entry)).withStyle(ChatFormatting.RED));
        }
        if (s.debt() > 0 && report.upkeep() > 0) {
            chat(owner, Component.translatable("message.universe.settlement.in_debt", displayName(entry), Credits.format(s.debt()))
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    /** 调试：提前结算下一天（真到那天就不再结算）。 */
    static Settlement.DayReport advance(MinecraftServer server, SettlementData.Entry entry) {
        Settlement s = entry.settlement();
        long day = s.day() + 1;
        Settlement.DayReport report = s.advanceDay(day, Rng.of(Seeds.derive(s.seed(), day)));
        daily(server, level(server, s), entry, report);
        SettlementData.get(server).changed();
        return report;
    }

    private static void notify(ServerPlayer player, SoundEvent sound) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 0.7F, 1.0F);
    }

    /** 攒进鱼塘、瞭望塔的那一堆：同样的合在一起，最多 {@value #STOCK_STACKS} 组。 */
    static void stock(SettlementData.Entry entry, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        for (ItemStack existing : entry.stock()) {
            if (ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
                int n = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(n);
                stack.shrink(n);
                if (stack.isEmpty()) {
                    return;
                }
            }
        }
        if (entry.stock().size() < STOCK_STACKS) {
            entry.stock().add(stack.copy());
        }
    }

    /** 施工、升级完成一段。 */
    private static void finished(MinecraftServer server, @Nullable ServerLevel level, SettlementData.Entry entry, int index, Plot.Finished done) {
        Settlement s = entry.settlement();
        Plot plot = s.plot(index);
        if (level != null) {
            SettlementBuilder.sync(level, entry, index, now(server), true);
            level.playSound(null, SettlementBuilder.terminalPos(entry, index), SettlementSounds.STAGE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        ServerPlayer owner = owner(server, s);
        Component name = buildingName(plot.type());
        Component message = switch (done) {
            case STAGE -> Component.translatable("message.universe.settlement.stage_done", name, plot.stage(), BuildingType.STAGES);
            case BUILT -> Component.translatable("message.universe.settlement.built", name);
            case UPGRADED -> Component.translatable("message.universe.settlement.upgraded", name, BuildingType.letter(plot.grade()));
        };
        if (owner != null && near(owner, s)) {
            tell(owner, message);
        } else if (owner != null && done != Plot.Finished.STAGE) {
            chat(owner, Component.translatable("message.universe.settlement.remote", displayName(entry), message));
        }
    }

    /** 走进聚落提示一次：没有总督的告诉怎么接管，有总督的说是谁的。 */
    private static void hints(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level) || player.isSpectator()) {
                continue;
            }
            Optional<Landmark> landmark = landmarkNear(level, player.getX(), player.getZ(), HINT_RANGE);
            if (landmark.isEmpty()) {
                HINTED.remove(player.getUUID());
                continue;
            }
            Settlement s = site(level, landmark.get());
            if (s.id().equals(HINTED.get(player.getUUID()))) {
                continue;
            }
            HINTED.put(player.getUUID(), s.id());
            if (!s.owned()) {
                ensureTerminal(level, landmark.get());
                tell(player, Component.translatable("message.universe.settlement.hint_unclaimed"));
            } else if (!s.ownedBy(player.getUUID())) {
                tell(player, Component.translatable("message.universe.settlement.hint_owned", s.ownerName()));
            }
        }
    }

    /**
     * 交易终端右边那台聚落终端：M19 以前生成的聚落没有，有人走进来时补上（交易终端在、那一格不是聚落终端才放）。
     */
    static void ensureTerminal(ServerLevel level, Landmark landmark) {
        int[] trade = SettlementLayout.world(landmark, SettlementLayout.terminal()[0], SettlementLayout.terminal()[1]);
        int[] at = SettlementLayout.world(landmark, SettlementLayout.terminal()[0] + 1, SettlementLayout.terminal()[1]);
        if (!level.isLoaded(new BlockPos(trade[0], 0, trade[1])) || !level.isLoaded(new BlockPos(at[0], 0, at[1]))) {
            return;
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, trade[0], trade[1]);
        for (int y = top; y > top - 8; y--) {
            if (!level.getBlockState(new BlockPos(trade[0], y, trade[1])).is(UniverseBlocks.OUTPOST_TRADE_TERMINAL.get())) {
                continue;
            }
            BlockPos pos = new BlockPos(at[0], y, at[1]);
            if (level.getBlockState(pos).is(UniverseBlocks.SETTLEMENT_TERMINAL.get())) {
                return;
            }
            // 朝着广场
            Direction facing = Direction.getApproximateNearest(landmark.x() - at[0], 0, landmark.z() - at[1]);
            level.setBlock(pos, UniverseBlocks.SETTLEMENT_TERMINAL.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,
                    facing.getAxis().isHorizontal() ? facing : Direction.NORTH), 3);
            return;
        }
    }

    /** 读档以后、开发脚本：把所有加载着的地基照数值对一遍。 */
    static int resync(MinecraftServer server) {
        int count = 0;
        long now = now(server);
        for (SettlementData.Entry entry : SettlementData.get(server).all()) {
            ServerLevel level = level(server, entry.settlement());
            if (level == null) {
                continue;
            }
            for (int i = 0; i < entry.settlement().plotCount(); i++) {
                if (SettlementBuilder.sync(level, entry, i, now, true)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** 一个人离他最近的、自己管的聚落。 */
    static Optional<SettlementData.Entry> nearestOwned(ServerPlayer player) {
        SettlementData.Entry best = null;
        double bestDistance = Double.MAX_VALUE;
        for (SettlementData.Entry entry : SettlementData.get(player.level().getServer()).ownedBy(player.getUUID())) {
            Settlement s = entry.settlement();
            if (!dimension(player.level()).equals(s.dimension())) {
                continue;
            }
            double d = s.distance(player.getX(), player.getZ());
            if (d < bestDistance) {
                bestDistance = d;
                best = entry;
            }
        }
        return Optional.ofNullable(best);
    }

    /** 给游戏内测试用：做一个界面上的操作（{@link SettlementPayloads.Action} 的那几个），返回给玩家的提示。 */
    public static @Nullable Component testAct(ServerPlayer player, SettlementData.Entry entry, int action, int plot, int value) {
        return act(player, entry, action, plot, value);
    }

    /** 给游戏内测试用：地基上方块的样子（第 {@code y} 层，地基的本地坐标）。 */
    public static BlockPos testPlotPos(SettlementData.Entry entry, int plot, int bx, int y, int bz) {
        return SettlementBuilder.frame(entry, plot).at(bx, y, bz);
    }

    /** 给游戏内测试用：施工终端在哪。 */
    public static BlockPos testTerminal(SettlementData.Entry entry, int plot) {
        return SettlementBuilder.terminalPos(entry, plot);
    }

    /** 给游戏内测试用：不经过地标，直接在 {@code center} 立一个聚落。 */
    public static Settlement testSite(ServerLevel level, BlockPos center, long key, Race race) {
        return new Settlement(dimension(level), key, center.getX(), center.getY(), center.getZ(), 0, Seeds.derive(key, "test"), race);
    }

    /** 给游戏内测试、调试命令用：登记一个聚落（不整平）。 */
    public static SettlementData.Entry register(MinecraftServer server, Settlement s) {
        return SettlementData.get(server).add(s, name(server, s));
    }

    /** 给游戏内测试用：删掉一个聚落。 */
    public static void forget(MinecraftServer server, String id) {
        SettlementRaids.cancel(id);
        Settlers.forget(id);
        SettlementBuilder.forget(id);
        SettlementData.get(server).remove(id);
    }

    static List<SettlementData.Entry> owned(MinecraftServer server, UUID player) {
        return new ArrayList<>(SettlementData.get(server).ownedBy(player));
    }

    static @Nullable LandmarkLayout layout(ServerLevel level) {
        return Landmarks.layout(level).orElse(null);
    }
}
