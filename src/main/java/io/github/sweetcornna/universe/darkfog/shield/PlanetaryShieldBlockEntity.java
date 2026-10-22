package io.github.sweetcornna.universe.darkfog.shield;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.darkfog.ShieldMath;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 行星护盾发生器（ADR-0028 §八）：接电网充能，存到展开的线就展开穹顶，展开以后一直要维持的电（{@link ShieldMath}）。
 * 能量记在 {@link ShieldData} 里，方块实体只管用电和界面；一颗星球上排在前 80 台以外的不要电、不工作。
 */
public class PlanetaryShieldBlockEntity extends MachineBlockEntity {
    /** 界面数据：状态（{@link #CHARGING}、{@link #RAISED}、{@link #OVER_LIMIT}）。 */
    public static final int STATUS = BASE_DATA;
    /** 界面数据：存的能量（千焦）。 */
    public static final int ENERGY = BASE_DATA + 1;
    /** 界面数据：穹顶的半径（格），没展开是 0。 */
    public static final int RADIUS = BASE_DATA + 2;
    /** 界面数据：这颗星球上在工作的发生器有几台。 */
    public static final int COUNT = BASE_DATA + 3;
    public static final int DATA_COUNT = BASE_DATA + 4;
    public static final int CHARGING = 0;
    public static final int RAISED = 1;
    public static final int OVER_LIMIT = 2;

    private ShieldData.@Nullable Generator generator;
    /** 电网结算时要的电：按它和满足率算这一刻实际充进了多少。 */
    private long settledDemand;
    private int status = CHARGING;

    public PlanetaryShieldBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.PLANETARY_SHIELD_GENERATOR.get(), pos, state, 0);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel level) {
            this.generator = ShieldData.get(level.getServer()).register(level.dimension(), this.worldPosition);
        }
    }

    /** 方块被拆、被黑雾打掉：从存档里删掉，穹顶跟着没了。 */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level instanceof ServerLevel level && ShieldData.get(level.getServer()).remove(level.dimension(), pos)) {
            Shields.changed(level);
        }
        this.generator = null;
    }

    /** 存档里这台的记录（服务端）；被清理掉了就重新登记。 */
    private ShieldData.@Nullable Generator entry() {
        if (!(this.level instanceof ServerLevel level)) {
            return null;
        }
        ShieldData data = ShieldData.get(level.getServer());
        if (this.generator == null || data.find(level.dimension(), this.worldPosition).orElse(null) != this.generator) {
            this.generator = data.register(level.dimension(), this.worldPosition);
        }
        return this.generator;
    }

    // ---- 电

    @Override
    public long demand() {
        ShieldData.Generator generator = this.generator;
        return generator == null || !generator.working() ? 0 : ShieldMath.demand(generator.state());
    }

    @Override
    public void settle(double satisfaction, double load, long stored) {
        super.settle(satisfaction, load, stored);
        this.settledDemand = this.demand();
    }

    // ---- 干活

    @Override
    protected boolean tickMachine() {
        if (!(this.level instanceof ServerLevel level)) {
            return false;
        }
        ShieldData.Generator generator = this.entry();
        if (generator == null) {
            return false;
        }
        if (!generator.working()) {
            this.status = OVER_LIMIT;
            return false;
        }
        double supplied = this.connected() ? this.settledDemand * this.satisfaction : 0;
        ShieldMath.State before = generator.state();
        ShieldMath.State after = ShieldMath.tick(before, supplied);
        ShieldData.get(level.getServer()).set(generator, after);
        if (before.raised() != after.raised()) {
            BlockPos pos = this.worldPosition;
            level.playSound(null, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5,
                    after.raised() ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2.0F, 0.8F);
            Shields.changed(level);
        }
        this.status = after.raised() ? RAISED : CHARGING;
        return after.raised();
    }

    /** 游戏内测试用：直接设能量（和展开没有）。 */
    public void setEnergy(double energy, boolean raised) {
        ShieldData.Generator generator = this.entry();
        if (this.level instanceof ServerLevel level && generator != null) {
            ShieldData.get(level.getServer()).set(generator, new ShieldMath.State(Math.max(0, Math.min(ShieldMath.CAPACITY, energy)), raised));
            Shields.changed(level);
        }
    }

    /** 存的能量（服务端）。 */
    public double energy() {
        ShieldData.Generator generator = this.entry();
        return generator == null ? 0 : generator.energy();
    }

    /** 穹顶展开着（服务端）。 */
    public boolean raised() {
        ShieldData.Generator generator = this.entry();
        return generator != null && generator.raised();
    }

    // ---- 界面

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        return switch (index) {
            case STATUS -> this.status;
            case ENERGY -> (int) Math.round(this.energy() / 1000);
            case RADIUS -> {
                ShieldData.Generator generator = this.entry();
                yield generator == null ? 0 : (int) Math.round(generator.radius());
            }
            case COUNT -> this.level instanceof ServerLevel level ? ShieldData.get(level.getServer()).workingCount(level.dimension()) : 0;
            default -> super.dataValue(index);
        };
    }

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.PLANETARY_SHIELD_GENERATOR;
    }
}
