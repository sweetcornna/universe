package io.github.sweetcornna.universe.dyson;

import java.util.OptionalInt;

import io.github.sweetcornna.universe.core.dyson.DysonSphere;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import io.github.sweetcornna.universe.stats.ProductionLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 垂直发射井（ADR-0016）：每 240 刻发射一枚小型运载火箭，去戴森球编号最小的、还有能建结构的层，工作 900 焦/刻
 * （戴森球计划 18 MW）。两格高，井口上面要看得见天空，不需要看见恒星。
 */
public class LaunchSiloBlockEntity extends MachineBlockEntity {
    public static final long WORK_POWER = 900;
    public static final long IDLE_POWER = 3;
    public static final int LAUNCH_TICKS = 240;
    /** 火箭升空的动画画多少刻（之后就飞出视野了）。 */
    public static final int ASCENT_TICKS = 100;
    public static final int ROCKET_SLOT = 0;
    public static final int STATUS = BASE_DATA;
    public static final int PROGRESS = BASE_DATA + 1;
    public static final int DATA_COUNT = BASE_DATA + 2;
    private static final int RECHECK = 20;

    public enum Status {
        WORKING, NO_ROCKETS, NO_LAYER, BUILT, NO_SKY, NO_STAR, NO_POWER
    }

    private double progress;
    private Status status = Status.NO_ROCKETS;
    private boolean sky;
    private boolean ready;
    private int recheck;
    /** 最近一次发射的游戏刻，客户端据此画升空的火箭。 */
    private long lastLaunch = Long.MIN_VALUE;
    /** 井里有火箭：客户端画一枚立在井口的火箭。 */
    private boolean loaded;

    public LaunchSiloBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.VERTICAL_LAUNCHING_SILO.get(), pos, state, 1);
    }

    public long lastLaunch() {
        return this.lastLaunch;
    }

    public boolean loaded() {
        return this.loaded;
    }

    /** 发射后 t 刻火箭升了多高（格）：匀加速，100 刻升到 125 格。 */
    public static double rise(double t) {
        return t <= 0 ? 0 : 0.0125 * t * t;
    }

    /** 客户端每刻：发射时井口喷出的白汽，火箭尾部拖着火焰和烟。 */
    public void clientTick() {
        if (this.level == null || this.lastLaunch == Long.MIN_VALUE) {
            return;
        }
        long t = this.level.getGameTime() - this.lastLaunch;
        if (t < 0 || t > ASCENT_TICKS) {
            return;
        }
        RandomSource random = this.level.getRandom();
        double cx = this.worldPosition.getX() + 0.5;
        double cz = this.worldPosition.getZ() + 0.5;
        double floor = this.worldPosition.getY() + 1.0;
        if (t < 30) {
            int puffs = t < 8 ? 7 : 2;
            for (int i = 0; i < puffs; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double speed = 0.08 + random.nextDouble() * 0.12;
                this.level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, cx + Math.cos(angle) * 0.4, floor + 0.7, cz + Math.sin(angle) * 0.4,
                        Math.cos(angle) * speed, 0.02 + random.nextDouble() * 0.05, Math.sin(angle) * speed);
            }
        }
        // 尾焰：这一刻火箭走过的一段上都撒一些，快起来以后也连成一条
        for (int k = 0; k < 4; k++) {
            double y = floor + rise(t - k / 4.0);
            double speed = 0.025 * t;
            this.level.addAlwaysVisibleParticle(ParticleTypes.FLAME, cx + (random.nextDouble() - 0.5) * 0.15, y - 0.1,
                    cz + (random.nextDouble() - 0.5) * 0.15, (random.nextDouble() - 0.5) * 0.05, -0.15 - speed * 0.3,
                    (random.nextDouble() - 0.5) * 0.05);
            if (k % 2 == 0) {
                this.level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, cx + (random.nextDouble() - 0.5) * 0.3, y - 0.3,
                        cz + (random.nextDouble() - 0.5) * 0.3, (random.nextDouble() - 0.5) * 0.04, -0.02, (random.nextDouble() - 0.5) * 0.04);
            }
        }
    }

    @Override
    public long demand() {
        return this.ready ? WORK_POWER : IDLE_POWER;
    }

    @Override
    protected boolean tickMachine() {
        if (!(this.level instanceof ServerLevel level)) {
            return false;
        }
        if (--this.recheck <= 0) {
            this.recheck = RECHECK;
            this.sky = DysonSight.sky(level, this.worldPosition.above(2));
        }
        OptionalInt system = Dyson.systemOf(level.dimension());
        DysonSphere sphere = system.isPresent() ? Dyson.sphere(level.getServer(), system.getAsInt()) : null;
        double limit = Dyson.latitudeLimit(level.getServer());
        boolean rockets = this.getItem(ROCKET_SLOT).is(UniverseItems.SMALL_CARRIER_ROCKET.get());
        if (rockets != this.loaded) {
            this.loaded = rockets;
            level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
        }
        boolean target = sphere != null && sphere.hasTarget(limit);
        if (sphere == null) {
            this.status = Status.NO_STAR;
        } else if (sphere.layers().isEmpty()) {
            this.status = Status.NO_LAYER;
        } else if (!target) {
            this.status = Status.BUILT;
        } else if (!rockets) {
            this.status = Status.NO_ROCKETS;
        } else if (!this.sky) {
            this.status = Status.NO_SKY;
        } else if (!this.connected() || this.satisfaction <= 0) {
            this.status = Status.NO_POWER;
        } else {
            this.status = Status.WORKING;
        }
        boolean wasReady = this.ready;
        this.ready = target && rockets && this.sky;
        if (!this.ready) {
            if (wasReady) {
                this.progress = 0;
            }
            return false;
        }
        this.progress += this.satisfaction;
        if (this.progress >= LAUNCH_TICKS) {
            this.progress = 0;
            ItemStack rocket = this.getItem(ROCKET_SLOT);
            if (sphere.launchRocket(limit, level.getServer().overworld().getGameTime()) != null) {
                ProductionLog.consumed(level, rocket.getItem(), 1);
                rocket.shrink(1);
                this.lastLaunch = level.getGameTime();
                this.setChanged();
                level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
                level.playSound(null, this.worldPosition.above(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 3.0F, 0.5F);
            }
        }
        return this.satisfaction > 0;
    }

    @Override
    protected boolean isInput(int slot) {
        return slot == ROCKET_SLOT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == ROCKET_SLOT && stack.is(UniverseItems.SMALL_CARRIER_ROCKET.get());
    }

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        return switch (index) {
            case STATUS -> this.status.ordinal();
            case PROGRESS -> (int) Math.round(this.progress / LAUNCH_TICKS * 1000);
            default -> super.dataValue(index);
        };
    }

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.VERTICAL_LAUNCHING_SILO;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getDoubleOr("Progress", 0);
        this.lastLaunch = input.getLongOr("LastLaunch", Long.MIN_VALUE);
        this.loaded = input.getBooleanOr("Loaded", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("Progress", this.progress);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("LastLaunch", this.lastLaunch);
        tag.putBoolean("Loaded", this.loaded);
        return tag;
    }
}
