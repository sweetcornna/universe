package io.github.sweetcornna.universe.mecha.fleet;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.mecha.fleet.FleetUnitKind;
import io.github.sweetcornna.universe.core.mecha.fleet.Squadrons;
import io.github.sweetcornna.universe.core.research.Upgrades;
import io.github.sweetcornna.universe.darkfog.DarkFogEnemy;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogDamage;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.mecha.Mecha;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.tech.ExosuitData;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 机甲舰队的服务端（ADR-0033 §一）：出动、召回、补员、停着时修理，和单位找主人要的东西（电、队形、机甲正在打的目标）。
 *
 * <p>编队里的物品一直在 {@link FleetData} 的格子里；出动时每件物品放出一个 {@link FleetUnit}，这里记着哪格对应哪架
 * （不存档：下线、换维度、关服都当召回）。单位被打掉时格子清空，开着自动补员就从背包和货舱里拿同一种补上：
 * 停着的编队马上补，出动着的隔 {@link Squadrons#REPLENISH_INTERVAL} 刻在机甲身边放出一架。
 */
public final class MechaFleet {
    private static final Map<UUID, Live> LIVE = new HashMap<>();

    /** 一个玩家放出去的单位。 */
    private static final class Live {
        final FleetUnit[] units = new FleetUnit[Squadrons.TOTAL_SLOTS];
        final boolean[] deployed = new boolean[Squadrons.COUNT];
        final long[] nextRefill = new long[Squadrons.COUNT];
        long starvedSince = -1;
        long lastStarve = -1;
        int recentTarget = -1;
        long recentAt = Long.MIN_VALUE;

        boolean any() {
            for (boolean d : this.deployed) {
                if (d) {
                    return true;
                }
            }
            return false;
        }
    }

    private MechaFleet() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(EntityAttributeCreationEvent.class, event -> {
            for (FleetUnitKind kind : FleetUnitKind.values()) {
                event.put(type(kind), FleetUnit.createAttributes(kind).build());
            }
        });
        gameBus.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                long start = ServerPerf.begin();
                tick(player);
                ServerPerf.end("player/fleet", start);
            }
        });
        gameBus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> recallAll(event.getEntity(), false));
        gameBus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            recallAll(event.getEntity(), false);
            LIVE.remove(event.getEntity().getUUID());
        });
        gameBus.addListener(LivingDeathEvent.class, event -> {
            if (event.getEntity() instanceof Player player && !player.level().isClientSide()) {
                recallAll(player, false);
            }
        });
        // 被黑雾打了：开着"受到攻击时自动出动"的编队出动
        gameBus.addListener(LivingIncomingDamageEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player && fromDarkFog(event.getSource().getEntity(), event.getSource().getDirectEntity(),
                    event.getSource().is(DarkFogDamage.KEY))) {
                autoDeploy(player);
            }
        });
        // 机甲自己打中的黑雾：舰队优先打它
        gameBus.addListener(LivingDamageEvent.Post.class, event -> {
            if (event.getEntity() instanceof DarkFogEnemy && event.getSource().getEntity() instanceof ServerPlayer player
                    && !(event.getSource().getDirectEntity() instanceof FleetUnit)) {
                Live live = LIVE.get(player.getUUID());
                if (live != null) {
                    live.recentTarget = event.getEntity().getId();
                    live.recentAt = player.level().getGameTime();
                }
            }
        });
        // 玩家这一边的弹打到舰队单位上：穿过去，不挡枪
        gameBus.addListener(ProjectileImpactEvent.class, event -> {
            if (event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof FleetUnit
                    && !(event.getProjectile().getOwner() instanceof DarkFogEnemy)) {
                event.setCanceled(true);
            }
        });
        gameBus.addListener(ServerStoppedEvent.class, event -> LIVE.clear());
    }

    private static boolean fromDarkFog(@Nullable Entity attacker, @Nullable Entity direct, boolean darkFogDamage) {
        return darkFogDamage || attacker instanceof DarkFogEnemy || direct instanceof DarkFogEnemy;
    }

    public static EntityType<FleetUnit> type(FleetUnitKind kind) {
        return switch (kind) {
            case PROTOTYPE -> UniverseEntities.FLEET_PROTOTYPE.get();
            case PRECISION_DRONE -> UniverseEntities.FLEET_PRECISION_DRONE.get();
            case ATTACK_DRONE -> UniverseEntities.FLEET_ATTACK_DRONE.get();
            case CORVETTE -> UniverseEntities.FLEET_CORVETTE.get();
            case DESTROYER -> UniverseEntities.FLEET_DESTROYER.get();
        };
    }

    // ---- 开了几个编队、哪里能出动

    public static int groundOpen(Level level) {
        return Squadrons.groundOpen(Research.isUnlocked(level, Squadrons.GROUND_TECH), Research.level(level, Upgrades.SQUADRON_EXPANSION));
    }

    public static int spaceOpen(Level level) {
        return Squadrons.spaceOpen(Research.isUnlocked(level, Squadrons.SPACE_TECH), Research.level(level, Upgrades.FLEET_EXPANSION));
    }

    public static boolean open(Level level, int squadron) {
        return Squadrons.open(squadron, groundOpen(level), spaceOpen(level));
    }

    /** 这个编队在这里能不能出动：地面编队在星球和主世界，太空舰队在太空。 */
    public static boolean allowedHere(Level level, int squadron) {
        boolean space = SpaceDimensions.isSpace(level.dimension());
        return Squadrons.space(squadron) ? space : !space && Planets.bodyId(level.dimension()).isPresent();
    }

    /** 编队的名字："地面编队 2"。 */
    public static Component name(int squadron) {
        return Component.translatable(Squadrons.space(squadron) ? "gui.universe.squadron.space" : "gui.universe.squadron.ground",
                Squadrons.ordinal(squadron) + 1);
    }

    public static boolean deployed(Player player, int squadron) {
        Live live = LIVE.get(player.getUUID());
        return live != null && live.deployed[squadron];
    }

    /** 服务器上有没有哪个编队出动着（黑雾找目标时先看这个，没有就不用搜舰队单位）。 */
    public static boolean anyOut() {
        for (Live live : LIVE.values()) {
            for (boolean deployed : live.deployed) {
                if (deployed) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 编队里在外面飞着的有几架。 */
    public static int flying(Player player, int squadron) {
        Live live = LIVE.get(player.getUUID());
        if (live == null) {
            return 0;
        }
        int count = 0;
        for (int slot = 0; slot < Squadrons.slots(squadron); slot++) {
            FleetUnit unit = live.units[Squadrons.flatIndex(squadron, slot)];
            if (unit != null && unit.isAlive() && !unit.isRemoved()) {
                count++;
            }
        }
        return count;
    }

    /** 这个编队现在出动着的单位（GameTest 用）。 */
    public static java.util.List<FleetUnit> units(Player player, int squadron) {
        Live live = LIVE.get(player.getUUID());
        java.util.List<FleetUnit> list = new java.util.ArrayList<>();
        if (live != null) {
            for (int slot = 0; slot < Squadrons.slots(squadron); slot++) {
                FleetUnit unit = live.units[Squadrons.flatIndex(squadron, slot)];
                if (unit != null && !unit.isRemoved()) {
                    list.add(unit);
                }
            }
        }
        return list;
    }

    // ---- 出动和召回

    /** 舰队键：在太空里管太空舰队，别的地方管地面编队；有出动着的就全召回，没有就把开了的都放出去。 */
    public static void toggle(ServerPlayer player) {
        boolean space = SpaceDimensions.isSpace(player.level().dimension());
        int from = space ? Squadrons.GROUND_MAX : 0;
        int to = space ? Squadrons.COUNT : Squadrons.GROUND_MAX;
        boolean any = false;
        for (int s = from; s < to; s++) {
            any |= deployed(player, s);
        }
        if (any) {
            for (int s = from; s < to; s++) {
                if (deployed(player, s)) {
                    recall(player, s, true);
                }
            }
            return;
        }
        int sent = 0;
        Component failure = null;
        for (int s = from; s < to; s++) {
            if (!open(player.level(), s) || player.getData(UniverseAttachments.MECHA_FLEET).count(s) == 0) {
                continue;
            }
            Component why = refusal(player, s);
            if (why != null) {
                failure = why;
                continue;
            }
            sent += launch(player, s) > 0 ? 1 : 0;
        }
        if (sent == 0) {
            player.sendOverlayMessage(failure != null ? failure : Component.translatable(open(player.level(), from)
                    ? "message.universe.squadron.none" : "message.universe.squadron.locked").withStyle(ChatFormatting.GRAY));
        } else {
            player.sendOverlayMessage(Component.translatable(space ? "message.universe.squadron.deployed_space" : "message.universe.squadron.deployed_ground"));
        }
    }

    /** 为什么出动不了；能出动是空的。 */
    public static @Nullable Component refusal(ServerPlayer player, int squadron) {
        if (!open(player.level(), squadron)) {
            return Component.translatable("message.universe.squadron.locked");
        }
        if (!Mecha.chest(player) && !player.isCreative()) {
            return Component.translatable("message.universe.squadron.no_chest");
        }
        if (!allowedHere(player.level(), squadron)) {
            return Component.translatable(Squadrons.space(squadron) ? "message.universe.squadron.space_only" : "message.universe.squadron.ground_only");
        }
        if (!Mecha.powered(player)) {
            return Component.translatable("message.universe.squadron.no_power");
        }
        if (player.getData(UniverseAttachments.MECHA_FLEET).count(squadron) == 0) {
            return Component.translatable("message.universe.squadron.empty");
        }
        return null;
    }

    /** 面板上点了出动：检查以后放出去。 */
    public static boolean deploy(ServerPlayer player, int squadron) {
        Component why = refusal(player, squadron);
        if (why != null) {
            player.sendOverlayMessage(why.copy().withStyle(ChatFormatting.GRAY));
            return false;
        }
        if (launch(player, squadron) > 0) {
            player.sendOverlayMessage(Component.translatable("message.universe.squadron.deployed", name(squadron), flying(player, squadron)));
            return true;
        }
        return false;
    }

    /**
     * 把编队放出去，不查研究、维度和电（{@link #deploy} 查过了；GameTest 直接调这个）。
     *
     * @return 放出了几架
     */
    public static int launch(Player player, int squadron) {
        if (!(player.level() instanceof ServerLevel level)) {
            return 0;
        }
        FleetData data = player.getData(UniverseAttachments.MECHA_FLEET);
        Live live = LIVE.computeIfAbsent(player.getUUID(), id -> new Live());
        int sent = 0;
        for (int slot = 0; slot < Squadrons.slots(squadron); slot++) {
            int index = Squadrons.flatIndex(squadron, slot);
            ItemStack stack = data.slots().get(index);
            FleetUnitKind kind = FleetUnitItem.kindOf(stack);
            if (kind == null || kind.space() != Squadrons.space(squadron)) {
                continue;
            }
            FleetUnit existing = live.units[index];
            if (existing != null && !existing.isRemoved()) {
                continue;
            }
            live.units[index] = spawn(level, player, squadron, slot, stack, kind);
            sent++;
        }
        live.deployed[squadron] = true;
        live.nextRefill[squadron] = level.getGameTime() + Squadrons.REPLENISH_INTERVAL;
        if (sent > 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5F, 1.6F);
        }
        return sent;
    }

    private static FleetUnit spawn(ServerLevel level, Player player, int squadron, int slot, ItemStack stack, FleetUnitKind kind) {
        // 从机甲背后飞出来
        Vec3 back = Vec3.directionFromRotation(0, player.getYRot()).scale(-0.6);
        Vec3 at = player.position().add(back.x, player.getBbHeight() * 0.6, back.z);
        FleetUnit unit = FleetUnit.create(level, type(kind), player, squadron, slot, FleetUnitItem.health(stack), at);
        Vec3 home = formationPoint(player, squadron, slot, 0);
        unit.setDeltaMovement(home.subtract(at).normalize().scale(kind.speed() * 0.6));
        level.addFreshEntity(unit);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 3, 0.1, 0.1, 0.1, 0.05);
        return unit;
    }

    /** 收回一个编队：单位的生命写回物品，实体收掉。 */
    public static void recall(Player player, int squadron, boolean announce) {
        Live live = LIVE.get(player.getUUID());
        if (live == null) {
            return;
        }
        FleetData data = player.getData(UniverseAttachments.MECHA_FLEET);
        for (int slot = 0; slot < Squadrons.slots(squadron); slot++) {
            int index = Squadrons.flatIndex(squadron, slot);
            FleetUnit unit = live.units[index];
            live.units[index] = null;
            if (unit == null || unit.isRemoved()) {
                continue;
            }
            if (unit.isAlive()) {
                FleetUnitItem.setHealth(data.slots().get(index), (int) Math.ceil(unit.getHealth()));
            }
            if (unit.level() instanceof ServerLevel level) {
                unit.vanish(level);
            } else {
                unit.discard();
            }
        }
        boolean was = live.deployed[squadron];
        live.deployed[squadron] = false;
        if (announce && was && player instanceof ServerPlayer server) {
            server.sendOverlayMessage(Component.translatable("message.universe.squadron.recalled", name(squadron)));
        }
    }

    public static void recallAll(Player player, boolean announce) {
        if (player.level().isClientSide()) {
            return;
        }
        for (int s = 0; s < Squadrons.COUNT; s++) {
            recall(player, s, announce);
        }
        Live live = LIVE.get(player.getUUID());
        if (live != null) {
            live.starvedSince = -1;
        }
    }

    private static void autoDeploy(ServerPlayer player) {
        FleetData data = player.getData(UniverseAttachments.MECHA_FLEET);
        for (int s = 0; s < Squadrons.COUNT; s++) {
            if (data.autoDeploy(s) && !deployed(player, s) && refusal(player, s) == null) {
                deploy(player, s);
            }
        }
    }

    // ---- 单位找主人要的

    /** 从主人的机甲核心扣电（创造模式不扣）；不是服务端的玩家时不扣。 */
    static boolean draw(Player owner, double joules) {
        return !(owner instanceof ServerPlayer player) || Mecha.draw(player, joules);
    }

    /** 有单位这一刻没电了。 */
    static void starved(Player owner) {
        Live live = LIVE.get(owner.getUUID());
        if (live == null) {
            return;
        }
        long now = owner.level().getGameTime();
        if (live.starvedSince < 0) {
            live.starvedSince = now;
        }
        live.lastStarve = now;
    }

    /** 机甲最近伤过的黑雾（实体 ID）；没有时是 -1。 */
    static int recentTarget(Player owner) {
        Live live = LIVE.get(owner.getUUID());
        if (live == null || owner.level().getGameTime() - live.recentAt > Squadrons.RECENT_HIT) {
            return -1;
        }
        return live.recentTarget;
    }

    static boolean attacksBuildings(Player owner, int squadron) {
        return owner.getData(UniverseAttachments.MECHA_FLEET).buildings(squadron);
    }

    /**
     * 这架单位在队形里的位置：地面编队排在机甲背后，坐着飞船时太空舰队排在飞船后面（按船的大小拉开）。地面的上下轻轻浮动。
     */
    static Vec3 formationPoint(Player owner, int squadron, int slot, double phase) {
        Entity root = owner.getRootVehicle();
        Vec3 origin = owner.position();
        float yaw = owner.yBodyRot;
        double scale = 1;
        if (root instanceof ShipEntity ship && ship.structure() != null) {
            origin = ship.position();
            yaw = ship.getYRot();
            scale = Math.max(1, ship.structure().shape().radius() / 5);
        }
        double[] offset = Squadrons.formation(squadron, slot, yaw, scale);
        double bob = Squadrons.space(squadron) ? 0 : Math.sin(owner.tickCount * 0.08 + phase) * 0.2;
        return origin.add(offset[0], offset[1] + bob, offset[2]);
    }

    /** 单位挨打了：生命马上写回物品（下线、崩服也不会回满）。 */
    static void onUnitHurt(FleetUnit unit) {
        Player owner = unit.owner();
        if (owner == null || unit.squadron() < 0) {
            return;
        }
        ItemStack stack = owner.getData(UniverseAttachments.MECHA_FLEET).slot(unit.squadron(), unit.slot());
        if (FleetUnitItem.kindOf(stack) == unit.kind()) {
            FleetUnitItem.setHealth(stack, (int) Math.ceil(unit.getHealth()));
        }
    }

    /** 单位被打掉了：物品没了，记下是什么，自动补员时补这种。 */
    static void onUnitLost(FleetUnit unit) {
        Player owner = unit.owner();
        if (owner == null || unit.squadron() < 0) {
            return;
        }
        int index = Squadrons.flatIndex(unit.squadron(), unit.slot());
        FleetData data = owner.getData(UniverseAttachments.MECHA_FLEET);
        Live live = LIVE.get(owner.getUUID());
        if (live != null && live.units[index] == unit) {
            live.units[index] = null;
        }
        if (FleetUnitItem.kindOf(data.slots().get(index)) == unit.kind()) {
            data.slots().set(index, ItemStack.EMPTY);
            data.setPending(index, unit.kind().id());
        }
        if (owner instanceof ServerPlayer player) {
            player.sendOverlayMessage(Component.translatable("message.universe.squadron.lost", Component.translatable("item.universe." + unit.kind().id()))
                    .withStyle(ChatFormatting.RED));
        }
    }

    // ---- 每刻

    private static void tick(ServerPlayer player) {
        Live live = LIVE.get(player.getUUID());
        long now = player.level().getGameTime();
        if (live != null && live.any()) {
            if (!Mecha.chest(player) && !player.isCreative() || player.isSpectator()) {
                recallAll(player, true);
                return;
            }
            if (live.lastStarve >= 0 && now - live.lastStarve > 5) {
                live.starvedSince = -1;
                live.lastStarve = -1;
            }
            if (live.starvedSince >= 0 && now - live.starvedSince >= Squadrons.STARVE_RECALL) {
                recallAll(player, false);
                player.sendOverlayMessage(Component.translatable("message.universe.squadron.starved").withStyle(ChatFormatting.RED));
                return;
            }
            for (int s = 0; s < Squadrons.COUNT; s++) {
                if (live.deployed[s] && !allowedHere(player.level(), s)) {
                    recall(player, s, false);
                }
            }
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        FleetData data = player.getData(UniverseAttachments.MECHA_FLEET);
        repair(player, data, live);
        replenish(player, data, live, now);
        if (live != null) {
            rejoin(player, data, live);
        }
    }

    /** 停着的单位用核心的电修，每点生命 50 焦。 */
    private static void repair(ServerPlayer player, FleetData data, @Nullable Live live) {
        if (!Mecha.chest(player) && !player.isCreative()) {
            return;
        }
        for (int index = 0; index < Squadrons.TOTAL_SLOTS; index++) {
            ItemStack stack = data.slots().get(index);
            FleetUnitKind kind = FleetUnitItem.kindOf(stack);
            if (kind == null || live != null && live.units[index] != null && !live.units[index].isRemoved()) {
                continue;
            }
            int health = FleetUnitItem.health(stack);
            int missing = kind.maxHealth() - health;
            if (missing <= 0) {
                continue;
            }
            double energy = player.isCreative() ? Double.MAX_VALUE : player.getData(UniverseAttachments.MECHA).energy().energy();
            int fixed = Squadrons.repair(missing, energy, kind.repairPerSecond());
            if (fixed > 0 && Mecha.draw(player, fixed * FleetUnitKind.REPAIR_JOULES_PER_HP)) {
                FleetUnitItem.setHealth(stack, health + fixed);
            }
        }
    }

    /** 自动补员：被打掉的格子从背包和货舱里拿同一种补上。 */
    private static void replenish(ServerPlayer player, FleetData data, @Nullable Live live, long now) {
        for (int s = 0; s < Squadrons.COUNT; s++) {
            if (!data.replenish(s) || !open(player.level(), s)) {
                continue;
            }
            boolean out = live != null && live.deployed[s];
            for (int slot = 0; slot < Squadrons.slots(s); slot++) {
                int index = Squadrons.flatIndex(s, slot);
                String want = data.pending(index);
                if (want.isEmpty() || !data.slots().get(index).isEmpty()) {
                    continue;
                }
                if (out && now < live.nextRefill[s]) {
                    break;
                }
                FleetUnitKind kind = FleetUnitKind.byId(want).orElse(null);
                ItemStack taken = kind == null ? ItemStack.EMPTY : take(player, kind);
                if (taken.isEmpty()) {
                    continue;
                }
                data.slots().set(index, taken);
                data.setPending(index, "");
                if (out && player.level() instanceof ServerLevel level) {
                    live.units[index] = spawn(level, player, s, slot, taken, kind);
                    live.nextRefill[s] = now + Squadrons.REPLENISH_INTERVAL;
                }
            }
        }
    }

    /** 从背包、货舱里拿一件这种单位：先拿伤得最轻的。 */
    private static ItemStack take(ServerPlayer player, FleetUnitKind kind) {
        Inventory inventory = player.getInventory();
        ExosuitData exosuit = player.getData(UniverseAttachments.EXOSUIT);
        ItemStack best = ItemStack.EMPTY;
        int bestHealth = -1;
        for (int i = 0; i < Inventory.INVENTORY_SIZE + exosuit.cargoSlots(); i++) {
            ItemStack stack = i < Inventory.INVENTORY_SIZE ? inventory.getItem(i) : exosuit.cargo().get(i - Inventory.INVENTORY_SIZE);
            if (FleetUnitItem.kindOf(stack) == kind && FleetUnitItem.health(stack) > bestHealth) {
                best = stack;
                bestHealth = FleetUnitItem.health(stack);
            }
        }
        return best.isEmpty() ? ItemStack.EMPTY : best.split(1);
    }

    /** 出动着的编队里没在外面的（区块卸载了、刚补上的）重新放出来。 */
    private static void rejoin(ServerPlayer player, FleetData data, Live live) {
        if (!(player.level() instanceof ServerLevel level) || !Mecha.powered(player)) {
            return;
        }
        for (int s = 0; s < Squadrons.COUNT; s++) {
            if (!live.deployed[s]) {
                continue;
            }
            for (int slot = 0; slot < Squadrons.slots(s); slot++) {
                int index = Squadrons.flatIndex(s, slot);
                FleetUnit unit = live.units[index];
                ItemStack stack = data.slots().get(index);
                FleetUnitKind kind = FleetUnitItem.kindOf(stack);
                if (kind == null || kind.space() != Squadrons.space(s) || unit != null && !unit.isRemoved()) {
                    continue;
                }
                live.units[index] = spawn(level, player, s, slot, stack, kind);
            }
        }
    }

    /** 客户端按了舰队键。 */
    public static void handleToggle(FleetPayloads.Toggle payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.isAlive() && !player.isSpectator()) {
            toggle(player);
        }
    }
}
