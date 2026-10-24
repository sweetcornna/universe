package io.github.sweetcornna.universe.fishing;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.fishing.Bait;
import io.github.sweetcornna.universe.core.fishing.Catch;
import io.github.sweetcornna.universe.core.fishing.CatchRoll;
import io.github.sweetcornna.universe.core.fishing.FishPool;
import io.github.sweetcornna.universe.core.fishing.FishSpecies;
import io.github.sweetcornna.universe.core.fishing.FishingLog;
import io.github.sweetcornna.universe.core.fishing.Habitat;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.poi.Poi;
import io.github.sweetcornna.universe.core.poi.PoiLayout;
import io.github.sweetcornna.universe.core.procgen.NameGenerator.Name;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.survival.ToolMode;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;
import io.github.sweetcornna.universe.fluid.AcidFluid;
import io.github.sweetcornna.universe.poi.PoiProgress;
import io.github.sweetcornna.universe.poi.PoiStructures;
import io.github.sweetcornna.universe.poi.Pois;
import io.github.sweetcornna.universe.poi.Waypoint;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.sound.FishingSounds;
import io.github.sweetcornna.universe.registry.sound.WorldSounds;
import io.github.sweetcornna.universe.survival.DishItem;
import io.github.sweetcornna.universe.survival.Multitool;
import io.github.sweetcornna.universe.survival.MultitoolItem;
import io.github.sweetcornna.universe.world.planet.PlanetWeather;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.planet.gen.PlanetBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.FluidTags;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 钓鱼（ADR-0034 §一）的服务端部分：钓鱼器的甩竿、收回，饵格，钓上来的东西进背包、记进图鉴，放生，漂流瓶和补给舱，
 * 以及给聚落鱼塘用的 {@link #pondCatch}。浮标自己怎么飞、怎么等鱼、怎么收线在 {@link FishingBobber}，规则在
 * {@code core.fishing}。
 */
public final class Fishing {
    /** 浮标离人超过这么远就收回去。 */
    static final double LINE_LENGTH = 32;
    /** 往下数水深最多数到几格。 */
    private static final int MAX_DEPTH = 48;
    /** 漂流瓶找多远以内的兴趣点。 */
    private static final int BOTTLE_RADIUS = 1200;
    private static final Map<UUID, FishingBobber> BOBBERS = new ConcurrentHashMap<>();

    /**
     * 浮标那一格的水。
     *
     * @param depth 往下数有几格水（含浮标那一格）
     */
    public record Water(Habitat habitat, int depth, boolean acid) {
    }

    private Fishing() {
    }

    public static void registerEvents(IEventBus modBus) {
        // 捕鱼笼：漏斗和分拣器从上面放饵，从侧面、下面取东西
        modBus.addListener(RegisterCapabilitiesEvent.class, event -> event.registerBlockEntity(Capabilities.Item.BLOCK,
                UniverseBlockEntities.FISH_TRAP.get(), (trap, side) -> trap.handler(side)));
    }

    // ---- 钓鱼器

    /** 主手拿着的多功能工具正处在钓鱼模式。 */
    public static boolean fishing(ItemStack tool) {
        return tool.getItem() instanceof MultitoolItem && Multitool.activeMode(tool) == ToolMode.FISHING;
    }

    /** 钓鱼模式下右键：没有浮标就甩竿，浮标在等鱼就收回，鱼咬钩了就钩住。两端都按住使用键，好接着收线。 */
    public static InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            FishingBobber bobber = bobber(serverPlayer);
            if (bobber == null) {
                cast(serverPlayer);
            } else {
                bobber.click(serverPlayer);
            }
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    /** 这个人现在的浮标；已经没了的清掉。 */
    public static @Nullable FishingBobber bobber(Player player) {
        FishingBobber bobber = BOBBERS.get(player.getUUID());
        if (bobber != null && (bobber.isRemoved() || bobber.level() != player.level())) {
            BOBBERS.remove(player.getUUID(), bobber);
            return null;
        }
        return bobber;
    }

    static void forget(FishingBobber bobber) {
        if (bobber.getOwner() instanceof Player owner) {
            BOBBERS.remove(owner.getUUID(), bobber);
        }
    }

    /** 甩竿：浮标从右手边飞出去，最远 24 格。这一竿用的饵和放生的加成在这时定下。 */
    public static FishingBobber cast(ServerPlayer player) {
        ServerLevel level = player.level();
        FishingLog log = log(player);
        FishingBobber bobber = new FishingBobber(level, player, baitKind(bait(player.getMainHandItem())), log.blessed());
        player.setData(UniverseAttachments.FISHING_LOG, log.cast());
        level.addFreshEntity(bobber);
        BOBBERS.put(player.getUUID(), bobber);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), FishingSounds.CAST.get(), SoundSource.PLAYERS, 0.6F,
                0.9F + level.getRandom().nextFloat() * 0.2F);
        return bobber;
    }

    /** 潜行右键：打开饵格。 */
    public static void openBait(ServerPlayer player) {
        int slot = player.getInventory().getSelectedSlot();
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> BaitMenu.server(id, inventory, slot),
                Component.translatable("gui.universe.bait.title")), buf -> buf.writeVarInt(slot));
    }

    // ---- 鱼饵

    /** 工具饵格里的东西（一样，可能是一叠）。 */
    public static ItemStack bait(ItemStack tool) {
        return tool.getOrDefault(UniverseDataComponents.TOOL_BAIT.get(), ItemContainerContents.EMPTY).copyOne();
    }

    public static void setBait(ItemStack tool, ItemStack bait) {
        if (bait.isEmpty()) {
            tool.remove(UniverseDataComponents.TOOL_BAIT.get());
        } else {
            tool.set(UniverseDataComponents.TOOL_BAIT.get(), ItemContainerContents.fromItems(List.of(bait)));
        }
    }

    /** 这样东西当饵算哪种：专门的饵、做好的菜、别的能吃的，都不是就不算饵。 */
    public static Bait baitKind(ItemStack stack) {
        if (stack.isEmpty()) {
            return Bait.NONE;
        }
        if (stack.getItem() instanceof BaitItem bait) {
            return bait.bait();
        }
        if (!stack.has(DataComponents.FOOD)) {
            return Bait.NONE;
        }
        return stack.getItem() instanceof DishItem ? Bait.DISH : Bait.FOOD;
    }

    /** 能不能放进饵格。 */
    public static boolean isBait(ItemStack stack) {
        return baitKind(stack) != Bait.NONE;
    }

    /** 钩住一条：工具饵格里的饵用掉一个。 */
    static void consumeBait(ServerPlayer player) {
        ItemStack tool = player.getMainHandItem();
        ItemStack bait = bait(tool);
        if (!bait.isEmpty() && !player.isCreative()) {
            bait.shrink(1);
            setBait(tool, bait);
        }
    }

    // ---- 水域

    /** 能钓鱼的液体：水和酸液（熔岩不行）。 */
    public static boolean fishable(FluidState fluid) {
        return fluid.is(FluidTags.WATER) || AcidFluid.is(fluid);
    }

    /** {@code pos} 那一格的水是哪种水域、多深；不是能钓鱼的液体时返回空。 */
    public static Optional<Water> water(ServerLevel level, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        if (!fishable(fluid)) {
            return Optional.empty();
        }
        int depth = 0;
        BlockPos.MutableBlockPos cursor = pos.mutable();
        while (depth < MAX_DEPTH && fishable(level.getFluidState(cursor))) {
            depth++;
            cursor.move(0, -1, 0);
        }
        boolean acid = AcidFluid.is(fluid);
        boolean sky = level.canSeeSky(pos.above());
        Holder<Biome> biome = level.getBiome(pos);
        Optional<Planet> planet = Planets.planet(level);
        Habitat habitat;
        if (planet.isPresent()) {
            PlanetType type = planet.get().type();
            BiomeKind kind = PlanetBiomes.kind(biome);
            habitat = Habitat.of(type, kind, null, acid, sky, depth);
        } else {
            habitat = Habitat.of(null, null, vanilla(biome), acid, sky, depth);
        }
        return Optional.of(new Water(habitat, depth, acid));
    }

    /** 主世界（和别的不是星球的维度）的群系归成几类。 */
    private static Habitat.Vanilla vanilla(Holder<Biome> biome) {
        if (biome.is(Tags.Biomes.IS_SNOWY) || biome.is(Tags.Biomes.IS_ICY) || biome.is(Tags.Biomes.IS_COLD_OVERWORLD)) {
            return Habitat.Vanilla.COLD;
        }
        if (biome.is(BiomeTags.IS_DEEP_OCEAN)) {
            return Habitat.Vanilla.DEEP_OCEAN;
        }
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_BEACH)) {
            return Habitat.Vanilla.OCEAN;
        }
        if (biome.is(BiomeTags.IS_RIVER)) {
            return Habitat.Vanilla.RIVER;
        }
        if (biome.is(Tags.Biomes.IS_HOT_OVERWORLD) || biome.is(BiomeTags.IS_JUNGLE)) {
            return Habitat.Vanilla.HOT;
        }
        return Habitat.Vanilla.OTHER;
    }

    /** 现在是白天还是夜里、有没有风暴。 */
    public static CatchRoll.Situation situation(ServerLevel level, BlockPos pos, int depth) {
        double sun = level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, pos);
        boolean day = Math.cos(Math.toRadians(sun)) > 0;
        boolean storm = level.isThundering() || PlanetWeather.severity(level) >= 2;
        return new CatchRoll.Situation(day, storm, depth);
    }

    /** 这个维度用哪颗星球的种子挑鱼：星球和主世界（母星）用星球的，别的维度用世界种子和维度名。 */
    public static long planetSeed(ServerLevel level) {
        return Planets.body(level).map(Planet::seed).orElse(level.getSeed() ^ level.dimension().identifier().hashCode());
    }

    /** 这颗星球、这种水域里有的鱼。 */
    public static List<FishSpecies> pool(ServerLevel level, Habitat habitat) {
        return FishPool.of(planetSeed(level), habitat);
    }

    /** 这颗星球的名字（中英文），记进图鉴。 */
    static Name planetName(ServerLevel level) {
        Optional<Planet> body = Planets.body(level);
        if (body.isPresent()) {
            return body.get().name();
        }
        String path = level.dimension().identifier().getPath();
        return new Name(path, path);
    }

    // ---- 钓上来的东西

    /** 把咬钩的东西变成物品。 */
    public static ItemStack stack(Catch caught, RandomSource random) {
        return switch (caught.kind()) {
            case FISH -> new ItemStack(UniverseItems.FISH.get(caught.species()).get());
            case JUNK -> new ItemStack(UniverseItems.JUNK.get(caught.junk()).get());
            case BOTTLE -> new ItemStack(UniverseItems.MESSAGE_BOTTLE.get());
            case CACHE -> new ItemStack(UniverseItems.SEALED_CACHE.get());
            case MODULE -> module(random);
        };
    }

    /** 随机一种多功能工具或外骨骼的模块，C 档七成、B 档三成。 */
    private static ItemStack module(RandomSource random) {
        List<ModuleType> types = ModuleType.personal();
        ModuleType type = types.get(random.nextInt(types.size()));
        Grade grade = random.nextFloat() < 0.3F ? Grade.B : Grade.C;
        ItemStack stack = new ItemStack(UniverseItems.MODULES.get(type).get());
        stack.set(UniverseDataComponents.TECH_MODULE.get(), TechModule.roll(type, grade, Rng.of(random.nextLong())));
        return stack;
    }

    /**
     * 钓上来了：东西进背包（放不下掉在脚下），鱼记进图鉴，弹卡片，放声音、给一点经验。
     *
     * @param where 浮标在哪（溅水的地方）
     */
    static void land(ServerPlayer player, Catch caught, Vec3 where) {
        ServerLevel level = player.level();
        ItemStack stack = stack(caught, level.getRandom());
        String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        level.sendParticles(ParticleTypes.SPLASH, where.x, where.y + 0.2, where.z, 12, 0.3, 0.1, 0.3, 0.1);
        if (caught.isFish()) {
            FishSpecies species = caught.species();
            FishingLog log = log(player);
            boolean first = !log.caught(species);
            boolean record = log.record(species, caught.weight());
            Name planet = planetName(level);
            player.setData(UniverseAttachments.FISHING_LOG, log.record(species, caught.weight(), planet.zh(), planet.en()));
            player.awardStat(Stats.FISH_CAUGHT);
            if (player.connection != null) {
                PacketDistributor.sendToPlayer(player, new FishCaughtPayload(species.index(), item, (float) caught.weight(), record && !first, first));
            }
            boolean trophy = species.rarity().ordinal() >= io.github.sweetcornna.universe.core.fishing.Rarity.RARE.ordinal();
            level.playSound(null, player.getX(), player.getY(), player.getZ(), trophy ? FishingSounds.TROPHY.get() : FishingSounds.CATCH.get(),
                    SoundSource.PLAYERS, 0.8F, 1.0F);
            level.addFreshEntity(new ExperienceOrb(level, player.getX(), player.getY() + 0.5, player.getZ(), 1 + species.rarity().ordinal() * 3
                    + level.getRandom().nextInt(4)));
        } else {
            if (player.connection != null) {
                PacketDistributor.sendToPlayer(player, new FishCaughtPayload(-1, item, 0, false, false));
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), FishingSounds.CATCH.get(), SoundSource.PLAYERS, 0.5F, 0.8F);
        }
        give(player, stack);
    }

    /** 放进背包，放不下掉在脚下（假玩家没有连接，不发预测包）。 */
    static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().placeItemBackInInventory(stack, player.connection != null, Prediction.SERVER_ONLY);
    }

    public static FishingLog log(Player player) {
        return player.getData(UniverseAttachments.FISHING_LOG);
    }

    /** 屏幕中间的一行提示；没有连接的玩家（游戏内测试里的假玩家）跳过。 */
    static void tell(ServerPlayer player, Component message) {
        if (player.connection != null) {
            player.sendOverlayMessage(message);
        }
    }

    // ---- 放生

    /**
     * 拿着鱼对着水右键：鱼跳回水里，图鉴记一笔，接下来 3 次甩竿等鱼的时间减半、少见以上的概率 +15%。
     *
     * @return 放了没有（对着的不是能钓鱼的液体就不放）
     */
    public static boolean release(ServerPlayer player, ItemStack stack, BlockPos water) {
        ServerLevel level = player.level();
        if (!fishable(level.getFluidState(water)) || !(stack.getItem() instanceof FishItem fish)) {
            return false;
        }
        Component name = stack.getHoverName();
        stack.consume(1, player);
        player.setData(UniverseAttachments.FISHING_LOG, log(player).release());
        Vec3 at = Vec3.atCenterOf(water).add(0, 0.4, 0);
        level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 16, 0.25, 0.05, 0.25, 0.15);
        level.sendParticles(ParticleTypes.BUBBLE, at.x, at.y - 0.4, at.z, 8, 0.2, 0.2, 0.2, 0.05);
        level.playSound(null, water, FishingSounds.RELEASE.get(), SoundSource.PLAYERS, 0.8F, fish.species().size().ordinal() < 2 ? 1.1F : 0.8F);
        tell(player, Component.translatable("message.universe.fishing.released", name));
        return true;
    }

    // ---- 漂流瓶、补给舱

    /** 打开漂流瓶：标出这颗星球上附近一个没去过的兴趣点（和导航数据一样设成航点）。找不到就不开。 */
    public static boolean openBottle(ServerPlayer player) {
        ServerLevel level = player.level();
        Optional<PoiLayout> layout = Pois.layout(level);
        if (layout.isEmpty()) {
            tell(player, Component.translatable("message.universe.fishing.bottle_no_map"));
            return false;
        }
        PoiProgress progress = player.getData(UniverseAttachments.POI_PROGRESS);
        Optional<Poi> found = layout.get().nearest(player.getX(), player.getZ(), BOTTLE_RADIUS,
                poi -> Pois.locatable().contains(poi.type()) && !progress.visited(poi.key()));
        if (found.isEmpty()) {
            tell(player, Component.translatable("message.universe.fishing.bottle_blank"));
            return true;
        }
        Poi poi = found.get();
        BlockPos target = new BlockPos(poi.x(), Pois.groundY(level, layout.get(), poi), poi.z());
        player.setData(UniverseAttachments.WAYPOINT, new Waypoint(level.dimension(), target, poi.type().serializedName()));
        int distance = (int) Math.round(Math.sqrt(poi.distanceSq(player.getX(), player.getZ())));
        player.sendSystemMessage(Component.translatable("message.universe.fishing.bottle", Pois.name(poi.type()),
                Component.translatable("direction.universe." + direction(player, poi)), distance));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), WorldSounds.SIGNAL.get(), SoundSource.PLAYERS, 0.7F, 1.2F);
        return true;
    }

    /** 八个方位，和信号增强器的提示一样：东 0°，按逆时针。 */
    private static String direction(Player player, Poi poi) {
        String[] directions = {"east", "southeast", "south", "southwest", "west", "northwest", "north", "northeast"};
        double dx = poi.x() + 0.5 - player.getX();
        double dz = poi.z() + 0.5 - player.getZ();
        return directions[Math.floorMod(Math.round((float) Math.toDegrees(Math.atan2(dz, dx)) / 45F), 8)];
    }

    /** 撬开密封补给舱：开出一份前哨站补给箱的东西。 */
    public static void openCache(ServerPlayer player) {
        ServerLevel level = player.level();
        LootTable table = level.getServer().reloadableRegistries().getLootTable(PoiStructures.OUTPOST_LOOT);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                .create(LootContextParamSets.CHEST);
        table.getRandomItems(params, stack -> give(player, stack));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), WorldSounds.CRATE_OPEN.get(), SoundSource.PLAYERS, 0.8F, 1.1F);
        tell(player, Component.translatable("message.universe.fishing.cache_opened"));
    }

    // ---- 给聚落鱼塘用

    /**
     * 聚落鱼塘（ADR-0034 §二）每天出一条：按 {@code pos} 那里的水域和这颗星球的鱼挑；那里没有水就当是淡水的池塘。
     * {@code grade} 是鱼塘的档次（0–3，C 到 S），越高杂物越少、越容易出好的。
     */
    public static ItemStack pondCatch(ServerLevel level, BlockPos pos, RandomSource random, int grade) {
        Water water = water(level, pos).orElse(new Water(Habitat.FRESH, 3, false));
        CatchRoll.Situation situation = situation(level, pos, water.depth());
        Catch caught = CatchRoll.roll(pool(level, water.habitat()), situation, Bait.NONE, false, CatchRoll.Odds.pond(grade),
                Rng.of(random.nextLong()));
        return stack(caught, random);
    }
}
