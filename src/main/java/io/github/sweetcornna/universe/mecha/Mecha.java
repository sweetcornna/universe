package io.github.sweetcornna.universe.mecha;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.blueprint.Construction;
import io.github.sweetcornna.universe.core.factory.Proliferation;
import io.github.sweetcornna.universe.core.mecha.LogisticsSlot;
import io.github.sweetcornna.universe.core.mecha.MechaEnergy;
import io.github.sweetcornna.universe.core.research.Upgrades;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.entity.ship.ShipPanel;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.network.MechaPayloads;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.stats.ProductionLog;
import io.github.sweetcornna.universe.survival.SuitHud;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 机甲的服务端（ADR-0023）：能量核心和燃烧室、机械骨骼的加速、驱动引擎的持续飞行，以及机甲面板的设置。
 *
 * <p>飞行用 NeoForge 的 {@code creative_flight} 属性：能飞的时候加上，客户端照原版双击跳跃起飞。核心没电时去掉，
 * 服务端在玩家的这一刻里把飞行状态关掉（{@code ServerPlayer#tick}），这边在落地前一直给缓降。
 */
public final class Mecha {
    private static final Identifier FRAME = Universe.id("mechanical_frame");
    private static final Identifier FLIGHT = Universe.id("mecha_flight");
    /** 原版的飞行速度。 */
    public static final float BASE_FLYING_SPEED = 0.05F;
    /** 燃料的能量：和火力发电厂一样，燃烧时间 × 15 焦。 */
    public static final long JOULES_PER_BURN_TICK = 15;

    /** 上一刻的位置，算速度用。 */
    private static final Map<UUID, double[]> LAST = new ConcurrentHashMap<>();
    /** 没电掉下来的玩家：落地前一直缓降。 */
    private static final Set<UUID> CUTOUT = ConcurrentHashMap.newKeySet();
    /** 改过飞行速度的玩家，收回飞行时改回去。 */
    private static final Set<UUID> SPED_UP = ConcurrentHashMap.newKeySet();

    private Mecha() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                long start = ServerPerf.begin();
                tick(player);
                ServerPerf.end("player/mecha", start);
            }
        });
        gameBus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> forget(event.getEntity().getUUID()));
        gameBus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> LAST.remove(event.getEntity().getUUID()));
        // 和货舱一样撒在死亡的地方
        gameBus.addListener(EventPriority.HIGH, LivingDropsEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player && !player.level().getGameRules().get(GameRules.KEEP_INVENTORY)
                    && !player.isSpectator()) {
                MechaData data = player.getData(UniverseAttachments.MECHA);
                if (!data.fuel().isEmpty()) {
                    ItemEntity drop = player.createItemStackToDrop(data.fuel(), true, false);
                    if (drop != null) {
                        event.getDrops().add(drop);
                    }
                    data.setFuel(ItemStack.EMPTY);
                }
            }
        });
    }

    private static void forget(UUID id) {
        LAST.remove(id);
        CUTOUT.remove(id);
        SPED_UP.remove(id);
    }

    // ---- 数值

    /** 穿着外骨骼胸甲：核心、燃烧室、无人机都在胸甲里。 */
    public static boolean chest(Player player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).is(UniverseItems.EXOSUIT_CHESTPLATE);
    }

    public static long capacity(Level level) {
        return Upgrades.mechaCapacity(Research.level(level, Upgrades.MECHA_CORE));
    }

    public static long fuelPower(Level level) {
        return Upgrades.fuelPower(Research.level(level, Upgrades.ENERGY_CIRCUIT));
    }

    /** 核心里有没有电（创造模式总是有）。 */
    public static boolean powered(Player player) {
        return player.isCreative() || chest(player) && !player.getData(UniverseAttachments.MECHA).energy().empty();
    }

    /** 从核心用电；不够时返回假（创造模式不扣）。 */
    public static boolean draw(ServerPlayer player, double joules) {
        if (player.isCreative()) {
            return true;
        }
        MechaData data = player.getData(UniverseAttachments.MECHA);
        data.drew(Math.min(joules, data.energy().energy()));
        return data.energy().draw(joules);
    }

    /** 无线输电塔这一刻最多想给这名玩家充多少。 */
    public static double towerDemand(ServerPlayer player) {
        if (!chest(player) || player.isCreative() || player.isSpectator()) {
            return 0;
        }
        return Math.min(MechaEnergy.TOWER_CHARGE, player.getData(UniverseAttachments.MECHA).energy().room(capacity(player.level())));
    }

    public static void towerCharge(ServerPlayer player, double joules) {
        MechaData data = player.getData(UniverseAttachments.MECHA);
        data.charged(data.energy().charge(joules, capacity(player.level())));
    }

    /** 燃料能放出多少焦（不算增产剂）；不是燃料时为 0。 */
    public static long fuelEnergy(ServerLevel level, Player player, ItemStack fuel) {
        if (fuel.isEmpty() || !fuel.has(DataComponents.COOKING_FUEL)) {
            return 0;
        }
        try {
            LootContext context = new LootContext.Builder(new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, player.position())
                    .withParameter(LootContextParams.THIS_ENTITY, player)
                    .create(LootContextParamSets.EQUIPMENT)).create(Optional.empty());
            return ResolvableInt.getFromItem(fuel, DataComponents.COOKING_FUEL, CookingFuel::burnTime, context, 0) * JOULES_PER_BURN_TICK;
        } catch (RuntimeException e) {
            // 别的模组的燃料要的上下文参数这里给不了
            return 0;
        }
    }

    // ---- 每刻

    private static void tick(ServerPlayer player) {
        ServerLevel level = player.level();
        MechaData data = player.getData(UniverseAttachments.MECHA);
        MechaEnergy energy = data.energy();
        long capacity = capacity(level);
        energy.clamp(capacity);
        data.roll();
        double[] speed = speed(player);
        if (player.isCreative() || player.isSpectator()) {
            energy.fill(capacity);
            setFrame(player, 0);
            revokeFlight(player, false);
            CUTOUT.remove(player.getUUID());
            return;
        }
        boolean chest = chest(player);
        if (chest) {
            refuel(player, data, capacity, level);
            data.charged(energy.burn(capacity, fuelPower(level)));
        }
        boolean flying = player.getAbilities().flying && hasFlight(player);
        // 机械骨骼：多出来的那部分速度耗电
        double bonus = chest && !energy.empty() ? Upgrades.frameBonus(Research.level(level, Upgrades.MECHANICAL_FRAME)) : 0;
        setFrame(player, bonus);
        if (bonus > 0 && !flying && player.onGround() && speed[0] > 0.5) {
            draw(player, MechaEnergy.frame(speed[0], bonus));
        }
        // 驱动引擎：持续飞行
        double flight = Upgrades.flightSpeed(Research.level(level, Upgrades.DRIVE_ENGINE));
        if (flying) {
            draw(player, MechaEnergy.flight(speed[1]));
        }
        boolean canFly = chest && flight > 0 && !energy.empty() && !player.isPassenger() && !SpaceDimensions.isSpace(level.dimension());
        if (canFly) {
            grantFlight(player, (float) (BASE_FLYING_SPEED * flight));
            CUTOUT.remove(player.getUUID());
        } else {
            revokeFlight(player, true);
        }
        if (CUTOUT.contains(player.getUUID())) {
            if (player.onGround() || player.isInWater() || player.isPassenger()) {
                CUTOUT.remove(player.getUUID());
            } else {
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false, true));
            }
        }
    }

    /** 水平速度和总速度（m/s），按这一刻和上一刻的位置算。 */
    private static double[] speed(ServerPlayer player) {
        double[] last = LAST.computeIfAbsent(player.getUUID(), id -> new double[] {player.getX(), player.getY(), player.getZ()});
        double dx = player.getX() - last[0];
        double dy = player.getY() - last[1];
        double dz = player.getZ() - last[2];
        last[0] = player.getX();
        last[1] = player.getY();
        last[2] = player.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz) * 20;
        double total = Math.sqrt(dx * dx + dy * dy + dz * dz) * 20;
        // 传送、换维度时一刻走很远，不算
        return horizontal > 100 ? new double[] {0, 0} : new double[] {horizontal, total};
    }

    /** 燃烧室空了（没有正在烧的）、核心又没满时放一个燃料进去；燃料格空了从背包里补上一次烧的那种。 */
    private static void refuel(ServerPlayer player, MechaData data, long capacity, ServerLevel level) {
        if (!data.energy().wantsFuel(capacity)) {
            return;
        }
        if (data.fuel().isEmpty() && !data.lastFuel().isEmpty() && player.getData(UniverseAttachments.MECHA_SETTINGS).autoRefuel()) {
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty() && id(stack).equals(data.lastFuel())) {
                    data.setFuel(inventory.removeItemNoUpdate(slot));
                    break;
                }
            }
        }
        ItemStack fuel = data.fuel();
        long joules = fuelEnergy(level, player, fuel);
        if (joules <= 0) {
            return;
        }
        ItemStackTemplate remainder = fuel.getCraftingRemainder();
        int points = fuel.getOrDefault(UniverseDataComponents.PROLIFERATION.get(), 0);
        data.setLastFuel(id(fuel));
        ProductionLog.consumed(level, fuel.getItem(), 1);
        fuel.shrink(1);
        // 喷过增产剂的燃料多出热量（ADR-0022）
        data.energy().addFuel(joules * (1 + Proliferation.extraProducts(points)));
        if (remainder != null) {
            if (fuel.isEmpty()) {
                data.setFuel(remainder.create());
            } else {
                player.getInventory().placeItemBackInInventory(remainder.create(), Prediction.SERVER_ONLY);
            }
        } else if (fuel.isEmpty()) {
            data.setFuel(ItemStack.EMPTY);
        }
    }

    static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static void setFrame(ServerPlayer player, double bonus) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        AttributeModifier current = speed.getModifier(FRAME);
        if (current != null && Math.abs(current.amount() - bonus) < 1e-6 || current == null && bonus <= 0) {
            return;
        }
        speed.removeModifier(FRAME);
        if (bonus > 0) {
            speed.addTransientModifier(new AttributeModifier(FRAME, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    private static boolean hasFlight(ServerPlayer player) {
        AttributeInstance flight = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        return flight != null && flight.getModifier(FLIGHT) != null;
    }

    private static void grantFlight(ServerPlayer player, float flyingSpeed) {
        AttributeInstance flight = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (flight != null && flight.getModifier(FLIGHT) == null) {
            flight.addTransientModifier(new AttributeModifier(FLIGHT, 1, AttributeModifier.Operation.ADD_VALUE));
        }
        if (Math.abs(player.getAbilities().getFlyingSpeed() - flyingSpeed) > 1e-6) {
            player.getAbilities().setFlyingSpeed(flyingSpeed);
            player.onUpdateAbilities();
            SPED_UP.add(player.getUUID());
        }
    }

    /** 收回飞行；cutout 为真且正在飞时，落地前给缓降。 */
    private static void revokeFlight(ServerPlayer player, boolean cutout) {
        AttributeInstance flight = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        boolean changed = false;
        if (flight != null && flight.getModifier(FLIGHT) != null) {
            flight.removeModifier(FLIGHT);
            if (cutout && player.getAbilities().flying && !player.mayFly()) {
                player.getAbilities().flying = false;
                CUTOUT.add(player.getUUID());
                player.sendOverlayMessage(Component.translatable("hud.universe.mecha.cutout"));
            }
            changed = true;
        }
        if (SPED_UP.remove(player.getUUID())) {
            player.getAbilities().setFlyingSpeed(BASE_FLYING_SPEED);
            changed = true;
        }
        if (changed) {
            player.onUpdateAbilities();
        }
    }

    // ---- HUD

    /** 给外骨骼的读数加上机甲的部分。 */
    public static SuitHud hud(ServerPlayer player, SuitHud hud) {
        if (!chest(player)) {
            return hud;
        }
        MechaEnergy energy = player.getData(UniverseAttachments.MECHA).energy();
        int flags = 0;
        flags |= hasFlight(player) ? SuitHud.FLIGHT : 0;
        flags |= player.getAbilities().flying ? SuitHud.FLYING : 0;
        flags |= Construction.flying(player) > 0 ? SuitHud.DRONES : 0;
        return hud.withMecha(energy.energy(), capacity(player.level()), flags, Construction.left(player), Construction.missing(player));
    }

    // ---- 机甲面板

    /** 客户端按了机甲面板键。 */
    public static void handleOpen(MechaPayloads.Open payload, IPayloadContext context) {
        // 驾驶飞船时打开的是飞船面板（ADR-0024）
        if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof ShipEntity ship && ship.pilot() == player) {
            ShipPanel.openFlying(player, ship);
            return;
        }
        if (context.player() instanceof ServerPlayer player && player.isAlive()) {
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> MechaMenu.server(id, inventory, player),
                    Component.translatable("gui.universe.mecha.title")));
        }
    }

    /** 客户端改了一个物流栏位的上下限。 */
    public static void handleBounds(MechaPayloads.Bounds payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || payload.index() < 0 || payload.index() >= MechaSettings.SLOTS) {
            return;
        }
        MechaSettings settings = player.getData(UniverseAttachments.MECHA_SETTINGS);
        LogisticsSlot slot = settings.slot(payload.index());
        if (!slot.isEmpty()) {
            player.setData(UniverseAttachments.MECHA_SETTINGS, settings.withSlot(payload.index(), slot.withBounds(payload.min(), payload.max())));
        }
    }

    /** 物流栏位解锁了没有。 */
    public static boolean logisticsUnlocked(Level level) {
        return Research.isUnlocked(level, MechaMenu.LOGISTICS_TECH);
    }
}
