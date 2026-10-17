package io.github.sweetcornna.universe.survival;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.survival.Environment;
import io.github.sweetcornna.universe.core.survival.GravityModel;
import io.github.sweetcornna.universe.core.survival.LifeSupport;
import io.github.sweetcornna.universe.core.survival.Suit;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.network.JetpackPayload;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.world.planet.PlanetWeather;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 星球生存的服务端部分（玩法设计 §3）：每刻按玩家所处的环境推进外骨骼的 {@link LifeSupport}，造成缺氧和危害伤害，
 * 数值低时自动用背包里的氧气罐、防护电池；生物进入星球维度时按星球重力改属性。
 *
 * <p>喷气背包的推力在客户端加（玩家的移动由客户端决定），客户端推进时发 {@link JetpackPayload}，这里只扣能量、
 * 清掉摔落距离。
 */
public final class PlayerSurvival {
    private static final Identifier GRAVITY_MODIFIER = Universe.id("planet_gravity");
    /** 每 5 刻把读数同步给玩家一次（变了才发）。 */
    private static final int HUD_INTERVAL = 5;
    /** 热源、冷源每秒查一次，半径 3 格。 */
    private static final int SOURCE_INTERVAL = 20;
    private static final int SOURCE_RADIUS = 3;
    /** 自动补给的阈值。 */
    private static final float AUTO_REFILL = 0.2F;

    private static final Set<UUID> JETTING = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, int[]> SOURCES = new ConcurrentHashMap<>();
    private static final Map<Long, Map<Hazard, Double>> HAZARDS = new LinkedHashMap<>(8, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Map<Hazard, Double>> eldest) {
            return this.size() > 16;
        }
    };

    private PlayerSurvival() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                tick(player);
            }
        });
        gameBus.addListener(EntityJoinLevelEvent.class, event -> {
            if (event.getEntity() instanceof LivingEntity living && event.getLevel() instanceof ServerLevel level) {
                applyGravity(living, level);
            }
        });
        gameBus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            JETTING.remove(event.getEntity().getUUID());
            SOURCES.remove(event.getEntity().getUUID());
        });
    }

    /** 客户端报告喷气背包开、关。 */
    public static void handleJetpack(JetpackPayload payload, IPayloadContext context) {
        UUID id = context.player().getUUID();
        if (payload.thrusting()) {
            JETTING.add(id);
        } else {
            JETTING.remove(id);
        }
    }

    // ---- 重力

    /** 按所在星球的重力改重力加速度、起跳速度、安全摔落高度和摔落伤害；不在星球上时去掉。 */
    static void applyGravity(LivingEntity entity, ServerLevel level) {
        double g = Planets.planet(level).map(Planet::gravity).orElse(1.0);
        setModifier(entity, Attributes.GRAVITY, GravityModel.gravity(g) - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setModifier(entity, Attributes.JUMP_STRENGTH, GravityModel.jump(g) - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setModifier(entity, Attributes.SAFE_FALL_DISTANCE, GravityModel.safeFall(g) - GravityModel.safeFall(1),
                AttributeModifier.Operation.ADD_VALUE);
        setModifier(entity, Attributes.FALL_DAMAGE_MULTIPLIER, GravityModel.fallDamage(g) - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static void setModifier(LivingEntity entity, Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(GRAVITY_MODIFIER);
        if (Math.abs(amount) > 1e-6) {
            instance.addTransientModifier(new AttributeModifier(GRAVITY_MODIFIER, amount, operation));
        }
    }

    // ---- 生命维持

    private static void tick(ServerPlayer player) {
        LifeSupport support = player.getData(UniverseAttachments.LIFE_SUPPORT);
        if (player.isCreative() || player.isSpectator()) {
            support.refill();
            JETTING.remove(player.getUUID());
            publish(player, SuitHud.FULL);
            return;
        }
        ServerLevel level = player.level();
        Suit suit = Exosuit.of(player);
        Environment env = environment(player, level, suit);
        boolean jetting = JETTING.contains(player.getUUID()) && suit.chest() && !player.onGround() && !player.isPassenger()
                && support.canJet();
        LifeSupport.Damage damage = support.tick(env, suit, jetting, player.onGround());
        if (jetting) {
            player.resetFallDistance();
        }
        if (!support.canJet()) {
            JETTING.remove(player.getUUID());
        }
        autoRefill(player, support, env, suit);
        // 密封的外骨骼在水下用自己的氧气
        if (suit.sealed() && support.oxygen() > 0 && player.isEyeInFluid(FluidTags.WATER)) {
            player.setAirSupply(player.getMaxAirSupply());
        }
        if (damage.suffocation() > 0) {
            player.hurtServer(level, level.damageSources().source(SurvivalDamage.VACUUM), damage.suffocation());
        } else if (!damage.hazards().isEmpty()) {
            // 几种危害同一刻到期时合成一次伤害（受伤后的无敌时间会吞掉第二次）
            float total = 0;
            Hazard worst = null;
            for (Map.Entry<Hazard, Float> entry : damage.hazards().entrySet()) {
                total += entry.getValue();
                worst = worst == null ? entry.getKey() : worst;
            }
            player.hurtServer(level, level.damageSources().source(SurvivalDamage.BY_HAZARD.get(worst)), total);
        }
        if (player.tickCount % HUD_INTERVAL == 0) {
            publish(player, SuitHud.of(support, env, suit));
        }
    }

    private static void publish(ServerPlayer player, SuitHud hud) {
        if (!hud.equals(player.getData(UniverseAttachments.SUIT_HUD))) {
            player.setData(UniverseAttachments.SUIT_HUD, hud);
        }
    }

    static Environment environment(ServerPlayer player, ServerLevel level, Suit suit) {
        boolean supplied = player.getVehicle() instanceof ShipEntity;
        boolean underwater = suit.sealed() && player.isEyeInFluid(FluidTags.WATER);
        Optional<Planet> planet = Planets.planet(level);
        if (planet.isEmpty() && !SpaceDimensions.isSpace(level.dimension())) {
            return underwater || supplied ? new Environment(!underwater, Map.of(), false, 0, false, false, supplied) : Environment.SAFE;
        }
        boolean breathable = planet.map(p -> p.atmosphere().breathable()).orElse(false) && !underwater;
        Map<Hazard, Double> hazards = planet.map(PlayerSurvival::hazards).orElse(Map.of());
        boolean sheltered = supplied || !level.canSeeSky(BlockPos.containing(player.getEyePosition()));
        int[] sources = sources(player, level, hazards);
        return new Environment(breathable, hazards, sheltered, PlanetWeather.severity(level), sources[0] > 0, sources[1] > 0, supplied);
    }

    private static Map<Hazard, Double> hazards(Planet planet) {
        synchronized (HAZARDS) {
            return HAZARDS.computeIfAbsent(planet.seed(), seed -> Environment.hazardsOf(planet));
        }
    }

    /** 附近有没有热源、冷源（只在有寒冷、高温危害时查）。结果缓存一秒。 */
    private static int[] sources(ServerPlayer player, ServerLevel level, Map<Hazard, Double> hazards) {
        int[] cached = SOURCES.computeIfAbsent(player.getUUID(), id -> new int[] {0, 0, -1});
        boolean cold = hazards.containsKey(Hazard.COLD);
        boolean heat = hazards.containsKey(Hazard.HEAT);
        if (!cold && !heat) {
            cached[0] = 0;
            cached[1] = 0;
            return cached;
        }
        if (cached[2] >= 0 && player.tickCount - cached[2] < SOURCE_INTERVAL) {
            return cached;
        }
        cached[2] = player.tickCount;
        cached[0] = 0;
        cached[1] = 0;
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-SOURCE_RADIUS, -SOURCE_RADIUS, -SOURCE_RADIUS),
                center.offset(SOURCE_RADIUS, SOURCE_RADIUS, SOURCE_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (cold && cached[0] == 0 && heatSource(state)) {
                cached[0] = 1;
            }
            if (heat && cached[1] == 0 && coldSource(state)) {
                cached[1] = 1;
            }
        }
        return cached;
    }

    private static boolean heatSource(BlockState state) {
        if (state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK) || state.getFluidState().is(FluidTags.LAVA)) {
            return true;
        }
        boolean lit = state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
        return lit && (state.is(BlockTags.CAMPFIRES) || state.getBlock() instanceof AbstractFurnaceBlock);
    }

    private static boolean coldSource(BlockState state) {
        return state.is(BlockTags.ICE) || state.is(BlockTags.SNOW) || state.is(UniverseBlocks.PLANET_SNOW) || state.is(UniverseBlocks.PLANET_ICE);
    }

    /** 数值低于两成时从背包里取一个氧气罐或防护电池。 */
    private static void autoRefill(ServerPlayer player, LifeSupport support, Environment env, Suit suit) {
        if (suit.sealed() && env.needsOxygen() && support.oxygen() < LifeSupport.OXYGEN_MAX * AUTO_REFILL) {
            take(player, SuitRefillItem.Kind.OXYGEN, support);
        }
        if (suit.pieces() > 0) {
            for (Hazard hazard : Hazard.values()) {
                if (support.low(hazard, env, AUTO_REFILL)) {
                    take(player, SuitRefillItem.Kind.HAZARD, support);
                    break;
                }
            }
        }
    }

    private static void take(ServerPlayer player, SuitRefillItem.Kind kind, LifeSupport support) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof SuitRefillItem refill && refill.kind() == kind) {
                SuitRefillItem.apply(player, stack, kind, support);
                return;
            }
        }
    }
}
