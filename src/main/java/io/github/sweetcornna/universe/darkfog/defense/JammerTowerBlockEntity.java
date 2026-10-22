package io.github.sweetcornna.universe.darkfog.defense;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.darkfog.Support;
import io.github.sweetcornna.universe.darkfog.DarkFogEnemy;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 干扰塔（ADR-0028 §五）：两格高，不打伤害。每秒对范围里的黑雾发一次干扰，让它们移动和攻击变慢
 * （{@link DarkFogEnemy#jam}）。只用电时 16 格、慢到七成；装了干扰胶囊 24 格、慢到四成，一个胶囊管 10 秒。
 * 范围里没有黑雾时待机，不用胶囊。分拣器、传送带能往胶囊格里送。
 */
public class JammerTowerBlockEntity extends MachineBlockEntity {
    public static final int CAPSULE_SLOT = 0;
    /** 界面数据：状态、半径、减速到几成（百分数）、范围里几个黑雾、胶囊还能管几次。 */
    public static final int STATUS = BASE_DATA;
    public static final int RADIUS = BASE_DATA + 1;
    public static final int FACTOR = BASE_DATA + 2;
    public static final int TARGETS = BASE_DATA + 3;
    public static final int CHARGE = BASE_DATA + 4;
    public static final int DATA_COUNT = BASE_DATA + 5;

    public static final int IDLE = 0;
    public static final int JAMMING = 1;
    public static final int NO_POWER = 2;
    /** 干扰的光圈颜色：淡紫。 */
    private static final int PULSE_COLOR = 0xB48CFF;

    private double supplied;
    private boolean active;
    private int status = IDLE;
    private int targets;
    /** 装进去的胶囊还能管几次；用完再从格子里拿一个。 */
    private int charge;
    private Support.@Nullable Jam lastJam;

    public JammerTowerBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.JAMMER_TOWER.get(), pos, state, 1);
    }

    @Override
    public long demand() {
        return this.active ? Support.JAMMER_POWER : Support.JAMMER_IDLE;
    }

    @Override
    public void settle(double satisfaction, double load, long stored) {
        super.settle(satisfaction, load, stored);
        this.supplied = satisfaction;
    }

    /** 有没有胶囊可用（正在用的或者格子里的）。 */
    private boolean hasCapsule() {
        return this.charge > 0 || this.getItem(CAPSULE_SLOT).is(UniverseItems.JAMMING_CAPSULE.get());
    }

    @Override
    protected boolean tickMachine() {
        if (!(this.level instanceof ServerLevel level)) {
            return false;
        }
        if (level.getGameTime() % Support.JAM_INTERVAL != Math.floorMod(this.worldPosition.asLong(), Support.JAM_INTERVAL)) {
            return this.status == JAMMING;
        }
        this.pulse(level);
        return this.status == JAMMING;
    }

    /** 发一次干扰：先看范围里有没有黑雾，有就要电；有电就按胶囊的有无减速它们。 */
    void pulse(ServerLevel level) {
        boolean capsule = this.hasCapsule();
        double radius = (capsule ? Support.CAPSULE : Support.POWER_ONLY).radius();
        Vec3 center = this.center();
        java.util.List<Entity> enemies = level.getEntities((Entity) null, new AABB(center, center).inflate(radius),
                entity -> entity instanceof DarkFogEnemy && entity.isAlive() && entity.position().distanceToSqr(center) <= radius * radius);
        this.targets = enemies.size();
        this.active = !enemies.isEmpty();
        if (!this.active) {
            this.status = IDLE;
            return;
        }
        Support.Jam jam = Support.jam(capsule, this.supplied);
        this.lastJam = jam;
        if (jam == null) {
            this.status = NO_POWER;
            return;
        }
        this.status = JAMMING;
        if (capsule) {
            this.useCapsule();
        }
        for (Entity entity : enemies) {
            ((DarkFogEnemy) entity).jam(Support.JAM_TICKS, jam.factor());
            level.sendParticles(new DustParticleOptions(PULSE_COLOR, 1.0F), entity.getX(), entity.getY() + entity.getBbHeight() * 0.5,
                    entity.getZ(), 4, 0.3, 0.3, 0.3, 0);
        }
        // 塔顶放一圈光
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI * 2 / 16;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x + Math.cos(angle) * 1.2, center.y + 0.8, center.z + Math.sin(angle) * 1.2,
                    1, 0, 0, 0, 0.05);
        }
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.4F, 1.6F);
    }

    /** 用掉一次胶囊：正在用的用完了就从格子里拿一个新的。 */
    private void useCapsule() {
        if (this.charge <= 0) {
            ItemStack stack = this.getItem(CAPSULE_SLOT);
            if (!stack.is(UniverseItems.JAMMING_CAPSULE.get())) {
                return;
            }
            stack.shrink(1);
            this.charge = Support.CAPSULE_PULSES;
        }
        this.charge--;
        this.setChanged();
    }

    private Vec3 center() {
        return new Vec3(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.5, this.worldPosition.getZ() + 0.5);
    }

    /** 上一次干扰的效果（测试用）；没电、没目标时是空的。 */
    public Support.@Nullable Jam lastJam() {
        return this.lastJam;
    }

    @Override
    protected boolean isInput(int slot) {
        return slot == CAPSULE_SLOT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == CAPSULE_SLOT && stack.is(UniverseItems.JAMMING_CAPSULE.get());
    }

    @Override
    protected long powerFlow() {
        return Math.round(this.demand() * this.supplied);
    }

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        Support.Jam jam = (this.hasCapsule() ? Support.CAPSULE : Support.POWER_ONLY).scaled(Math.max(this.supplied, 0));
        return switch (index) {
            case STATUS -> this.status;
            case RADIUS -> (int) jam.radius();
            case FACTOR -> (int) Math.round(jam.factor() * 100);
            case TARGETS -> this.targets;
            case CHARGE -> this.charge;
            default -> super.dataValue(index);
        };
    }

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.JAMMER_TOWER;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.charge = input.getIntOr("CapsuleCharge", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("CapsuleCharge", this.charge);
    }
}
