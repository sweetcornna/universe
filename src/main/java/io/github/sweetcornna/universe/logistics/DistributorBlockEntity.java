package io.github.sweetcornna.universe.logistics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.blueprint.BlueprintSettings;
import io.github.sweetcornna.universe.core.mecha.Distribution;
import io.github.sweetcornna.universe.core.research.Upgrades;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.mecha.Flyers;
import io.github.sweetcornna.universe.network.FlyersPayload;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * 物流配送器（ADR-0023）：装在有物品存储的方块顶上（储物仓整叠算一个），管一种物品。对机甲可以供应、回收；对别的配送器可以
 * 供应、需求。派机器人由 {@link Distributors} 统一规划，这里管机器人的飞行、交货和取货。
 *
 * <p>机器人不是实体：每趟记着要去哪、带着什么、飞到了哪里，存进方块实体；客户端的画法见 {@code FlyersClient}。
 */
public class DistributorBlockEntity extends MachineBlockEntity implements MachineMenu.ButtonTarget, BlueprintSettings {
    public static final int BOT_SLOT = 0;
    public static final int MAX_BOTS = 10;
    /** 电池：最多 9 kW 从电网充，存 60 kJ。 */
    public static final long CAPACITY = 60_000;
    public static final long CHARGE_RATE = 450;
    /** 待机：18 W（四舍五入成 1 焦/刻）。 */
    public static final long IDLE = 1;
    /** 机器人每飞一格耗电。 */
    public static final long JOULES_PER_BLOCK = 20;
    /** 机器人原来的速度：6 m/s。 */
    public static final double BOT_SPEED = 0.3;
    /** 机器人追的玩家离配送器超过这么远（水平）就带着货回来。 */
    static final double LEASH = Distribution.RANGE + 24;

    public static final int SET_FILTER = 0;
    public static final int CLEAR_FILTER = 1;
    public static final int CYCLE_ICARUS = 2;
    public static final int CYCLE_PEER = 3;

    /** 界面数据：物品（两格）、两个模式、空闲和全部机器人、电量（两格）、下面有几个、还放得下几个、有没有容器。 */
    public static final int FILTER = BASE_DATA;
    public static final int ICARUS = BASE_DATA + 2;
    public static final int PEER = BASE_DATA + 3;
    public static final int IDLE_BOTS = BASE_DATA + 4;
    public static final int BOTS = BASE_DATA + 5;
    public static final int ENERGY = BASE_DATA + 6;
    public static final int STOCK = BASE_DATA + 8;
    public static final int SPACE = BASE_DATA + 9;
    public static final int HAS_STORAGE = BASE_DATA + 10;
    public static final int DATA_COUNT = BASE_DATA + 11;
    /** 界面上的空位只试放这么多，免得每 tick 把整个仓库试一遍。 */
    public static final int SPACE_PROBE = 999;

    /** 在飞的一个机器人。 */
    static final class Trip {
        static final Codec<Trip> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("kind").forGetter(t -> t.kind.name()),
                UUIDUtil.CODEC.optionalFieldOf("player").forGetter(t -> Optional.ofNullable(t.player)),
                BlockPos.CODEC.optionalFieldOf("peer").forGetter(t -> Optional.ofNullable(t.peer)),
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(t -> t.item),
                Codec.INT.fieldOf("amount").forGetter(t -> t.amount),
                Codec.INT.optionalFieldOf("cargo", 0).forGetter(t -> t.cargo),
                Codec.BOOL.optionalFieldOf("back", false).forGetter(t -> t.back),
                Vec3.CODEC.fieldOf("at").forGetter(t -> new Vec3(t.x, t.y, t.z))
        ).apply(i, (kind, player, peer, item, amount, cargo, back, at) -> {
            Trip trip = new Trip(Distribution.Kind.valueOf(kind), player.orElse(null), peer.orElse(null), item, amount, at);
            trip.cargo = cargo;
            trip.back = back;
            return trip;
        }));

        final Distribution.Kind kind;
        final @Nullable UUID player;
        final @Nullable BlockPos peer;
        final Item item;
        /** 要送、要收、要取几个。 */
        final int amount;
        /** 现在带着几个。 */
        int cargo;
        boolean back;
        double x;
        double y;
        double z;
        /** 客户端画的那一架（不存档）。 */
        int flyer;

        Trip(Distribution.Kind kind, @Nullable UUID player, @Nullable BlockPos peer, Item item, int amount, Vec3 at) {
            this.kind = kind;
            this.player = player;
            this.peer = peer;
            this.item = item;
            this.amount = amount;
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
        }
    }

    private @Nullable Item filter;
    private Distribution.Icarus icarus = Distribution.Icarus.NONE;
    private Distribution.Peer peer = Distribution.Peer.NONE;
    private long energy;
    private final List<Trip> trips = new ArrayList<>();
    /** 放下它的玩家：控制面板远程改模式时认他（ADR-0033 §三）。0.14 的档里没有，谁都能改。 */
    private @Nullable UUID owner;

    public DistributorBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.LOGISTICS_DISTRIBUTOR.get(), pos, state, 1);
    }

    // ---- 设置

    public @Nullable Item filter() {
        return this.filter;
    }

    public Distribution.Icarus icarus() {
        return this.icarus;
    }

    public Distribution.Peer peer() {
        return this.peer;
    }

    public Optional<UUID> owner() {
        return Optional.ofNullable(this.owner);
    }

    /** 玩家放下了这台配送器（{@link DistributorBlock#setPlacedBy}）。 */
    public void setOwner(UUID owner) {
        this.owner = owner;
        this.setChanged();
        this.register();
    }

    @Override
    public boolean clickButton(ServerPlayer player, int button) {
        switch (button) {
            case SET_FILTER -> {
                ItemStack carried = player.containerMenu.getCarried();
                if (carried.isEmpty()) {
                    return false;
                }
                this.filter = carried.getItem();
            }
            case CLEAR_FILTER -> this.filter = null;
            case CYCLE_ICARUS -> this.icarus = Distribution.Icarus.values()[(this.icarus.ordinal() + 1) % Distribution.Icarus.values().length];
            case CYCLE_PEER -> this.peer = Distribution.Peer.values()[(this.peer.ordinal() + 1) % Distribution.Peer.values().length];
            default -> {
                return false;
            }
        }
        this.setChanged();
        this.register();
        return true;
    }

    /** 控制面板远程改模式（ADR-0033 §三）：只认两个模式按钮，物品要走到跟前改。 */
    public boolean remoteButton(int button) {
        switch (button) {
            case CYCLE_ICARUS -> this.icarus = DistributorRegistry.next(this.icarus);
            case CYCLE_PEER -> this.peer = DistributorRegistry.next(this.peer);
            default -> {
                return false;
            }
        }
        this.setChanged();
        this.register();
        return true;
    }

    /** 把现状记进全局的登记表。 */
    void register() {
        if (this.level instanceof ServerLevel level && !this.isRemoved()) {
            DistributorRegistry.get(level.getServer()).record(level, this);
        }
    }

    /** 蓝图记下物品和两个模式。 */
    @Override
    public @Nullable CompoundTag blueprintSettings() {
        if (this.filter == null && this.icarus == Distribution.Icarus.NONE && this.peer == Distribution.Peer.NONE) {
            return null;
        }
        CompoundTag tag = new CompoundTag();
        if (this.filter != null) {
            tag.putString("Filter", BuiltInRegistries.ITEM.getKey(this.filter).toString());
        }
        tag.putString("Icarus", this.icarus.name());
        tag.putString("Peer", this.peer.name());
        return tag;
    }

    @Override
    public void applyBlueprintSettings(CompoundTag settings) {
        Identifier id = Identifier.tryParse(settings.getStringOr("Filter", ""));
        Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
        this.filter = item == Items.AIR ? null : item;
        this.icarus = enumOr(Distribution.Icarus.class, settings.getStringOr("Icarus", ""), Distribution.Icarus.NONE);
        this.peer = enumOr(Distribution.Peer.class, settings.getStringOr("Peer", ""), Distribution.Peer.NONE);
        this.setChanged();
        this.register();
    }

    // ---- 电

    @Override
    public long demand() {
        return IDLE + Math.min(CHARGE_RATE, CAPACITY - this.energy);
    }

    @Override
    public void settle(double satisfaction, double load, long stored) {
        long charge = Math.min(CHARGE_RATE, CAPACITY - this.energy);
        super.settle(satisfaction, load, stored);
        this.energy = Math.min(CAPACITY, this.energy + Math.round(charge * satisfaction));
    }

    // ---- 下面的容器

    /** 下面的容器：储物仓整叠算一个（从最下面那层拿物品能力），别的方块直接用它朝上的那面。 */
    @Nullable ResourceHandler<ItemResource> storage() {
        if (this.level == null) {
            return null;
        }
        BlockPos below = this.worldPosition.below();
        if (this.level.getBlockState(below).is(UniverseBlocks.STORAGE_DEPOT.get())) {
            for (int i = 0; i < 16 && this.level.getBlockState(below.below()).is(UniverseBlocks.STORAGE_DEPOT.get()); i++) {
                below = below.below();
            }
        }
        return this.level.getCapability(Capabilities.Item.BLOCK, below, Direction.UP);
    }

    /** 下面有几个这种物品（只算不带组件的）。 */
    int stock() {
        ResourceHandler<ItemResource> storage = this.storage();
        if (storage == null || this.filter == null) {
            return 0;
        }
        ItemResource resource = ItemResource.of(this.filter);
        long count = 0;
        for (int i = 0; i < storage.size(); i++) {
            if (storage.getResource(i).equals(resource)) {
                count += storage.getAmountAsLong(i);
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    /** 下面还放得下几个（最多算到 999，规划用不着更多）。 */
    int space() {
        ResourceHandler<ItemResource> storage = this.storage();
        if (storage == null || this.filter == null) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            return ResourceHandlerUtil.insertStacking(storage, ItemResource.of(this.filter), SPACE_PROBE, transaction);
        }
    }

    private int take(Item item, int amount) {
        ResourceHandler<ItemResource> storage = this.storage();
        if (storage == null || amount <= 0) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int taken = storage.extract(ItemResource.of(item), amount, transaction);
            transaction.commit();
            return taken;
        }
    }

    /** 放进下面的容器，放不下的从顶上掉出来。 */
    private void store(Item item, int amount) {
        if (amount <= 0 || !(this.level instanceof ServerLevel level)) {
            return;
        }
        ResourceHandler<ItemResource> storage = this.storage();
        int stored = 0;
        if (storage != null) {
            try (Transaction transaction = Transaction.openRoot()) {
                stored = ResourceHandlerUtil.insertStacking(storage, ItemResource.of(item), amount, transaction);
                transaction.commit();
            }
        }
        int left = amount - stored;
        while (left > 0) {
            int n = Math.min(left, item.getDefaultMaxStackSize());
            Containers.dropItemStack(level, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.2, this.worldPosition.getZ() + 0.5,
                    new ItemStack(item, n));
            left -= n;
        }
    }

    // ---- 机器人

    public int bots() {
        ItemStack stack = this.getItem(BOT_SLOT);
        return stack.is(UniverseItems.LOGISTICS_BOT.get()) ? stack.getCount() : 0;
    }

    /** 空闲的机器人；电不够飞一趟时算 0。 */
    public int idleBots() {
        return this.energy < JOULES_PER_BLOCK * 32 ? 0 : Math.max(0, this.bots() - this.trips.size());
    }

    /** 往这名玩家送着、收着这种物品的有多少：正数是在路上要送来的，负数是要收走的。 */
    int pending(UUID player, Item item) {
        int pending = 0;
        for (Trip trip : this.trips) {
            if (player.equals(trip.player) && trip.item == item && !trip.back) {
                pending += trip.kind == Distribution.Kind.DELIVER ? trip.cargo : trip.kind == Distribution.Kind.COLLECT ? -trip.amount : 0;
            }
        }
        return pending;
    }

    private Vec3 dock() {
        return Vec3.atBottomCenterOf(this.worldPosition).add(0, 1.3, 0);
    }

    private double speed(ServerLevel level) {
        return BOT_SPEED * Upgrades.carrierSpeed(Research.level(level, Upgrades.CARRIER_ENGINE));
    }

    /**
     * 派一个机器人。
     *
     * @param target 玩家（送货、收货）；取货时为空
     * @param supplier 供应的配送器（取货）
     * @return 派出去了
     */
    boolean launch(Distribution.Dispatch dispatch, @Nullable ServerPlayer target, @Nullable DistributorBlockEntity supplier) {
        if (!(this.level instanceof ServerLevel level) || this.filter == null || this.idleBots() <= 0) {
            return false;
        }
        Vec3 home = this.dock();
        Vec3 to = target != null ? target.position() : supplier != null ? supplier.dock() : null;
        if (to == null) {
            return false;
        }
        long cost = Math.round(home.distanceTo(to) * 2 * JOULES_PER_BLOCK);
        if (this.energy < cost) {
            return false;
        }
        Trip trip = new Trip(dispatch.kind(), target == null ? null : target.getUUID(), supplier == null ? null : supplier.worldPosition,
                this.filter, dispatch.amount(), home);
        if (dispatch.kind() == Distribution.Kind.DELIVER) {
            trip.cargo = this.take(this.filter, dispatch.amount());
            if (trip.cargo <= 0) {
                return false;
            }
        }
        this.energy -= cost;
        this.trips.add(trip);
        trip.flyer = Flyers.nextId();
        this.fly(level, trip);
        this.setChanged();
        return true;
    }

    /** 告诉客户端这一架现在这一段往哪飞。 */
    private void fly(ServerLevel level, Trip trip) {
        if (trip.flyer == 0) {
            trip.flyer = Flyers.nextId();
        }
        int entity = -1;
        Vec3 to = this.dock();
        if (!trip.back) {
            ServerPlayer player = trip.player == null ? null : level.getServer().getPlayerList().getPlayer(trip.player);
            if (player != null) {
                entity = player.getId();
                to = player.position();
            } else if (trip.peer != null) {
                to = Vec3.atBottomCenterOf(trip.peer).add(0, 1.3, 0);
            }
        }
        int cargo = trip.cargo > 0 ? BuiltInRegistries.ITEM.getId(trip.item) : -1;
        Flyers.fly(level, new FlyersPayload.Flyer(trip.flyer, FlyersPayload.BOT, trip.x, trip.y, trip.z, level.getGameTime(),
                (float) this.speed(level), entity, to.x, to.y, to.z, cargo));
    }

    @Override
    protected boolean tickMachine() {
        if (!(this.level instanceof ServerLevel level) || this.trips.isEmpty()) {
            return this.filter != null && this.bots() > 0;
        }
        double speed = this.speed(level);
        Vec3 home = this.dock();
        Iterator<Trip> iterator = this.trips.iterator();
        while (iterator.hasNext()) {
            Trip trip = iterator.next();
            if (trip.flyer == 0) {
                // 读档以后第一刻：重新告诉客户端
                this.fly(level, trip);
            }
            Vec3 target = home;
            if (!trip.back) {
                Vec3 out = this.outbound(level, trip);
                if (out == null) {
                    trip.back = true;
                    this.fly(level, trip);
                } else {
                    target = out;
                }
            }
            if (!step(trip, target, speed)) {
                continue;
            }
            if (!trip.back) {
                this.arrive(level, trip);
                trip.back = true;
                this.fly(level, trip);
                continue;
            }
            this.store(trip.item, trip.cargo);
            Flyers.land(level.dimension(), trip.flyer);
            iterator.remove();
        }
        this.setChanged();
        return true;
    }

    /** 去程的目标；目标不在了（玩家下线、走远了，配送器拆了）时为空。 */
    private @Nullable Vec3 outbound(ServerLevel level, Trip trip) {
        if (trip.player != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(trip.player);
            if (player == null || player.level() != level || !player.isAlive() || horizontal(player.position(), this.dock()) > LEASH) {
                return null;
            }
            return player.position().add(0, player.getBbHeight() * 0.8, 0);
        }
        if (trip.peer != null && level.getBlockEntity(trip.peer) instanceof DistributorBlockEntity supplier) {
            return supplier.dock();
        }
        return null;
    }

    /** 到了：交货、收货、取货。 */
    private void arrive(ServerLevel level, Trip trip) {
        switch (trip.kind) {
            case DELIVER -> {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(trip.player);
                if (player != null) {
                    trip.cargo -= Distributors.give(player, trip.item, trip.cargo);
                }
            }
            case COLLECT -> {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(trip.player);
                if (player != null) {
                    trip.cargo += Distributors.collect(player, trip.item, Math.min(trip.amount, Distributors.surplus(player, trip.item)));
                }
            }
            case FETCH -> {
                if (trip.peer != null && level.getBlockEntity(trip.peer) instanceof DistributorBlockEntity supplier
                        && supplier.filter == trip.item && supplier.peer == Distribution.Peer.SUPPLY) {
                    trip.cargo += supplier.take(trip.item, trip.amount);
                    supplier.setChanged();
                }
            }
        }
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z));
    }

    /** 朝目标飞一步，到了返回真。 */
    private static boolean step(Trip trip, Vec3 target, double speed) {
        double dx = target.x - trip.x;
        double dy = target.y - trip.y;
        double dz = target.z - trip.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= Math.max(0.8, speed)) {
            trip.x = target.x;
            trip.y = target.y;
            trip.z = target.z;
            return true;
        }
        double s = speed / distance;
        trip.x += dx * s;
        trip.y += dy * s;
        trip.z += dz * s;
        return false;
    }

    // ---- 加载和拆除

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel level) {
            Distributors.add(level, this.worldPosition);
            // 没加载的时候控制面板改过模式，现在照着改
            DistributorRegistry.Entry pending = DistributorRegistry.get(level.getServer()).takePending(level, this.worldPosition);
            if (pending != null) {
                this.icarus = pending.icarus();
                this.peer = pending.peer();
                this.setChanged();
            }
            this.register();
        }
    }

    @Override
    public void setRemoved() {
        if (this.level instanceof ServerLevel level) {
            Distributors.remove(level, this.worldPosition);
            for (Trip trip : this.trips) {
                if (trip.flyer != 0) {
                    Flyers.land(level.dimension(), trip.flyer);
                    trip.flyer = 0;
                }
            }
        }
        super.setRemoved();
    }

    /** 拆掉时在飞的机器人带着的货掉在这里，机器人本身留在格子里一起掉。 */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level instanceof ServerLevel level) {
            DistributorRegistry.get(level.getServer()).remove(level, pos);
            for (Trip trip : this.trips) {
                int left = trip.cargo;
                while (left > 0) {
                    int n = Math.min(left, trip.item.getDefaultMaxStackSize());
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(trip.item, n));
                    left -= n;
                }
                trip.cargo = 0;
            }
        }
        super.preRemoveSideEffects(pos, state);
    }

    // ---- 物品栏

    @Override
    protected boolean isInput(int slot) {
        return slot == BOT_SLOT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == BOT_SLOT && stack.is(UniverseItems.LOGISTICS_BOT.get());
    }

    @Override
    public int getMaxStackSize() {
        return MAX_BOTS;
    }

    // ---- 界面和存档

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.LOGISTICS_DISTRIBUTOR;
    }

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        long item = this.filter == null ? 0 : BuiltInRegistries.ITEM.getId(this.filter) + 1L;
        return switch (index) {
            case FILTER -> MachineMenu.low(item);
            case FILTER + 1 -> MachineMenu.high(item);
            case ICARUS -> this.icarus.ordinal();
            case PEER -> this.peer.ordinal();
            case IDLE_BOTS -> Math.max(0, this.bots() - this.trips.size());
            case BOTS -> this.bots();
            case ENERGY -> MachineMenu.low(this.energy);
            case ENERGY + 1 -> MachineMenu.high(this.energy);
            case STOCK -> Math.min(Short.MAX_VALUE, this.stock());
            case SPACE -> Math.min(Short.MAX_VALUE, this.space());
            case HAS_STORAGE -> this.storage() != null ? 1 : 0;
            default -> super.dataValue(index);
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.filter = input.read("filter", BuiltInRegistries.ITEM.byNameCodec()).filter(item -> item != Items.AIR).orElse(null);
        this.icarus = input.read("icarus", Codec.STRING).map(name -> enumOr(Distribution.Icarus.class, name, Distribution.Icarus.NONE))
                .orElse(Distribution.Icarus.NONE);
        this.peer = input.read("peer", Codec.STRING).map(name -> enumOr(Distribution.Peer.class, name, Distribution.Peer.NONE))
                .orElse(Distribution.Peer.NONE);
        this.energy = input.getLongOr("energy", 0);
        this.trips.clear();
        input.read("trips", Trip.CODEC.listOf()).ifPresent(this.trips::addAll);
        this.owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (this.filter != null) {
            output.store("filter", BuiltInRegistries.ITEM.byNameCodec(), this.filter);
        }
        output.store("icarus", Codec.STRING, this.icarus.name());
        output.store("peer", Codec.STRING, this.peer.name());
        output.putLong("energy", this.energy);
        if (!this.trips.isEmpty()) {
            output.store("trips", Trip.CODEC.listOf(), this.trips);
        }
        if (this.owner != null) {
            output.store("owner", UUIDUtil.CODEC, this.owner);
        }
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
