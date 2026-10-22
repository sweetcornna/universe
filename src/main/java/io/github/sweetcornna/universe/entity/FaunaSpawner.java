package io.github.sweetcornna.universe.entity;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.ecology.Biotope;
import io.github.sweetcornna.universe.core.ecology.CreatureSpecies;
import io.github.sweetcornna.universe.core.ecology.Fauna;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.planet.gen.PlanetBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ModifyCustomSpawnersEvent;

/**
 * 星球上的动物刷新：每隔两秒看一眼每个玩家周围，动物不够就在 24–56 格外刷一群。有海的星球另外刷水生动物（ADR-0026），
 * 名额和地上的分开算：在 20–56 格外找一列水，按水深挑物种，深水里才有鳗和巨型乌贼。
 *
 * <p>原版的自然刷怪按群系的刷怪表来，而星球的物种是程序生成的、群系里没有登记，所以单独写一个刷怪器，
 * 通过 {@link ModifyCustomSpawnersEvent} 挂到星球维度上（运行时创建的维度也会触发这个事件）。
 * 动物离玩家太远会像原版动物一样消失，玩家回来时再刷。
 */
public final class FaunaSpawner implements CustomSpawner {
    private static final int INTERVAL = 40;
    private static final int RADIUS = 64;
    /** 水至少这么深才刷水生动物。 */
    private static final int MIN_DEPTH = 3;
    /** 水深到这么多算深水：鳗、巨型乌贼只在深水里刷（和 {@code AquaticCreature} 挑物种的标准一样）。 */
    private static final int DEEP_WATER = 12;
    /** 巨型乌贼要的水深。 */
    private static final int GIANT_DEPTH = 20;
    /** 挑中巨型乌贼时只有这么大的机会真的刷出来，而且一个玩家周围最多一只。 */
    private static final float GIANT_CHANCE = 0.25F;

    private int cooldown;

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ModifyCustomSpawnersEvent.class, event -> {
            if (Planets.planet(event.getLevel()).isPresent()) {
                event.addCustomSpawner(new FaunaSpawner());
            }
        });
    }

    @Override
    public void tick(ServerLevel level, boolean spawnEnemies) {
        if (--this.cooldown > 0) {
            return;
        }
        this.cooldown = INTERVAL;
        Optional<Fauna> fauna = Planets.fauna(level);
        Optional<Planet> planet = Planets.planet(level);
        if (fauna.isEmpty() || planet.isEmpty()) {
            return;
        }
        int cap = cap(planet.get());
        boolean sea = !fauna.get().marine().isEmpty();
        int marineCap = sea ? marineCap(planet.get()) : 0;
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            // 驯服的动物不占野生动物的名额
            List<AlienCreature> around = around(level, player.getX(), player.getZ()).stream().filter(c -> !c.isTamed()).toList();
            if (around.size() < cap) {
                // 飞兽到处乱飞、不会被地形挡住，单独限一个份额，免得天上全是飞兽
                long flyers = around.stream().filter(c -> c instanceof AlienFlyer).count();
                spawnGroup(level, player, fauna.get(), random, flyers < Math.max(2, cap * 0.35));
            }
            if (sea) {
                List<AquaticCreature> swimmers = aroundMarine(level, player.getX(), player.getZ());
                if (swimmers.size() < marineCap) {
                    boolean giantNearby = swimmers.stream().anyMatch(c -> Fauna.giant(c.species()));
                    spawnMarineGroup(level, player, fauna.get(), random, !giantNearby);
                }
            }
        }
    }

    /** 这一点周围 64 格内（上下不限，飞在高空时也算地面上的）的动物。 */
    public static List<AlienCreature> around(ServerLevel level, double x, double z) {
        AABB column = new AABB(x - RADIUS, level.getMinY(), z - RADIUS, x + RADIUS, level.getMaxY(), z + RADIUS);
        return level.getEntitiesOfClass(AlienCreature.class, column);
    }

    /** 一个玩家周围最多几只：生态越繁盛越多。 */
    public static int cap(Planet planet) {
        return (int) Math.round(3 + planet.ecology().fauna() * 12);
    }

    /** 这一点周围 64 格内的水生动物。 */
    public static List<AquaticCreature> aroundMarine(ServerLevel level, double x, double z) {
        AABB column = new AABB(x - RADIUS, level.getMinY(), z - RADIUS, x + RADIUS, level.getMaxY(), z + RADIUS);
        return level.getEntitiesOfClass(AquaticCreature.class, column);
    }

    /** 一个玩家周围最多几只水生动物：生态越繁盛越多，海洋星球翻一倍。一群鱼就有 4–10 条，所以比地上的多一些。 */
    public static int marineCap(Planet planet) {
        int cap = (int) Math.round(4 + planet.ecology().fauna() * 8);
        return planet.type() == PlanetType.OCEAN ? cap * 2 : cap;
    }

    /** 在玩家 20–56 格外找一列水刷一群；水太浅、区块没在运转就这次不刷。只查一列方块，不扫一片。 */
    private static void spawnMarineGroup(ServerLevel level, ServerPlayer player, Fauna fauna, RandomSource random, boolean giantsAllowed) {
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = 20 + random.nextDouble() * 36;
        int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
        int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
        Water water = water(level, x, z);
        if (water == null || water.depth() < MIN_DEPTH) {
            return;
        }
        CreatureSpecies species = fauna.pickMarine(water.depth() >= DEEP_WATER, random.nextDouble());
        if (species == null) {
            return;
        }
        if (Fauna.giant(species) && (!giantsAllowed || water.depth() < GIANT_DEPTH || random.nextFloat() > GIANT_CHANCE)) {
            return;
        }
        // 住深水的贴着下半截，别的上下都有
        int span = Fauna.deepWater(species) ? Math.max(1, water.depth() / 2) : water.depth();
        BlockPos centre = new BlockPos(x, water.floor() + random.nextInt(span), z);
        int count = species.groupMin() + random.nextInt(species.groupMax() - species.groupMin() + 1);
        for (int i = 0; i < count; i++) {
            BlockPos pos = i == 0 ? centre : centre.offset(random.nextInt(7) - 3, random.nextInt(5) - 2, random.nextInt(7) - 3);
            if (pos.getY() >= water.floor() && pos.getY() <= water.top()) {
                spawnAquatic(level, species, pos, water.top(), random);
            }
        }
    }

    /** 一列水：最低和最高的水方块（冰面下的海也算）。 */
    private record Water(int floor, int top) {
        int depth() {
            return this.top - this.floor + 1;
        }
    }

    /** 这一列的水；区块没在运转、这里不是水时返回 null。 */
    private static @Nullable Water water(ServerLevel level, int x, int z) {
        BlockPos column = new BlockPos(x, 0, z);
        if (!level.isPositionEntityTicking(column)) {
            return null;
        }
        // 海床上面第一格（不算液体的高度图）和最上面挡路的方块（算液体）
        int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
        // 冰面下的海：往下几格找到水
        for (int i = 0; i < 4 && top >= floor && !level.getFluidState(pos.setY(top)).is(FluidTags.WATER); i++) {
            top--;
        }
        if (top < floor || !level.getFluidState(pos.setY(top)).is(FluidTags.WATER) || !level.getFluidState(pos.setY(floor)).is(FluidTags.WATER)) {
            return null;
        }
        return new Water(floor, top);
    }

    /** 在这一格水里生成一只；个子大的往下挪，免得露出水面；地方不合适时返回 null。 */
    public static @Nullable AquaticCreature spawnAquatic(ServerLevel level, CreatureSpecies species, BlockPos pos, int waterTop, RandomSource random) {
        if (!level.getFluidState(pos).is(FluidTags.WATER)) {
            return null;
        }
        AquaticCreature creature = UniverseEntities.AQUATIC_CREATURE.get().create(level, EntitySpawnReason.NATURAL);
        if (creature == null) {
            return null;
        }
        creature.setSpecies(species);
        double y = Math.min(pos.getY(), waterTop + 1 - creature.getBbHeight());
        if (y < pos.getY() - 8 || !level.getFluidState(BlockPos.containing(pos.getX(), y, pos.getZ())).is(FluidTags.WATER)) {
            creature.discard();
            return null;
        }
        creature.snapTo(pos.getX() + 0.5, y, pos.getZ() + 0.5, random.nextFloat() * 360, 0);
        if (!level.noCollision(creature)) {
            creature.discard();
            return null;
        }
        creature.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
        level.addFreshEntity(creature);
        return creature;
    }

    private static void spawnGroup(ServerLevel level, ServerPlayer player, Fauna fauna, RandomSource random, boolean flyersAllowed) {
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = 24 + random.nextDouble() * 32;
        int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
        int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
        BlockPos centre = surface(level, x, z);
        if (centre == null) {
            return;
        }
        BiomeKind biome = PlanetBiomes.kind(level.getBiome(centre));
        if (biome == null) {
            return;
        }
        // 海面、悬崖这类不属于任何生境的地方只有飞兽路过，少刷一些
        if (Biotope.of(biome) == null && random.nextFloat() > 0.3F) {
            return;
        }
        CreatureSpecies species = fauna.pick(biome, random.nextDouble());
        if (species == null || species.plan().flying() && !flyersAllowed) {
            return;
        }
        int count = species.groupMin() + random.nextInt(species.groupMax() - species.groupMin() + 1);
        for (int i = 0; i < count; i++) {
            BlockPos pos = i == 0 ? centre : surface(level, centre.getX() + random.nextInt(9) - 4, centre.getZ() + random.nextInt(9) - 4);
            if (pos != null) {
                spawn(level, species, pos, random);
            }
        }
    }

    /** 在这一处地面上生成一只；地方不合适时返回 null。 */
    public static @Nullable AlienCreature spawn(ServerLevel level, CreatureSpecies species, BlockPos pos, RandomSource random) {
        boolean flying = species.plan().flying();
        EntityType<? extends AlienCreature> type = flying ? UniverseEntities.ALIEN_FLYER.get() : UniverseEntities.ALIEN_CREATURE.get();
        BlockPos at = flying ? pos.above(1 + random.nextInt(4)) : pos;
        if (!flying) {
            // 地上的动物要站在实心地面上，不刷进水里
            BlockState below = level.getBlockState(at.below());
            if (!below.isFaceSturdy(level, at.below(), Direction.UP) || !level.getFluidState(at).isEmpty()) {
                return null;
            }
        }
        AlienCreature creature = type.create(level, EntitySpawnReason.NATURAL);
        if (creature == null) {
            return null;
        }
        creature.setSpecies(species);
        creature.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360, 0);
        if (!level.noCollision(creature)) {
            creature.discard();
            return null;
        }
        creature.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.NATURAL, null);
        level.addFreshEntity(creature);
        return creature;
    }

    /** 这一列最上面能站的地方；区块没在运转时返回 null。 */
    private static @Nullable BlockPos surface(ServerLevel level, int x, int z) {
        BlockPos column = new BlockPos(x, 0, z);
        if (!level.isPositionEntityTicking(column)) {
            return null;
        }
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
    }
}
