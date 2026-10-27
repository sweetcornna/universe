package io.github.sweetcornna.universe.terraform;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.space.SpaceLayout;
import io.github.sweetcornna.universe.core.terraform.TerraformState;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.power.LevelPower;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 轨道反射镜（ADR-0036 §六）：放下以后登记到对着的那颗星球，拆掉时注销。
 * 界面里切换反射镜和遮阳板。照祖布林和麦凯 1993 年的轨道反射镜方案，镜面是太阳帆一样的薄膜，不用电。
 */
public class OrbitalMirrorBlockEntity extends MachineBlockEntity implements MachineMenu.ButtonTarget {
    /** 界面按钮：切换反射镜和遮阳板。 */
    public static final int TOGGLE = 0;
    /** 多久重新对一次登记（刻）。 */
    private static final int CHECK_INTERVAL = 100;

    /** 界面数据：状态（{@link #OK} 等）。 */
    public static final int STATUS = BASE_DATA;
    /** 是遮阳板为 1。 */
    public static final int SHADE = BASE_DATA + 1;
    /** 对着的星球现在有几面反射镜、几面遮阳板。 */
    public static final int MIRRORS = BASE_DATA + 2;
    public static final int SHADES = BASE_DATA + 3;
    /** 对着的星球：恒星系、行星的序号，卫星的序号 + 1（行星为 0）。 */
    public static final int SYSTEM = BASE_DATA + 4;
    public static final int PLANET = BASE_DATA + 5;
    public static final int MOON = BASE_DATA + 6;
    public static final int DATA_COUNT = BASE_DATA + 7;

    public static final int OK = 0;
    /** 不在太空里。 */
    public static final int NOT_IN_SPACE = 1;
    /** 附近没有能改造的星球。 */
    public static final int NO_PLANET = 2;

    private @Nullable BodyId target;
    private int checkTimer;

    public OrbitalMirrorBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.ORBITAL_MIRROR.get(), pos, state, 0);
    }

    /** 不用电，不接电网。 */
    @Override
    protected void joinGrid(LevelPower power) {
    }

    @Override
    protected void leaveGrid(LevelPower power) {
    }

    @Override
    public void onLoad() {
        super.onLoad();
        this.checkTimer = 0;
    }

    /** 方块拆掉、被别的方块换掉时（区块卸载不算）：这颗星球少一面。 */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level instanceof ServerLevel level) {
            Terraforming.removeMirror(level, pos);
        }
    }

    @Override
    protected boolean tickMachine() {
        if (--this.checkTimer <= 0) {
            this.checkTimer = CHECK_INTERVAL;
            this.check();
        }
        return this.target != null;
    }

    /** 找对着的星球；还没登记的（命令放的、结构里带的）补登记上。 */
    private void check() {
        if (!(this.level instanceof ServerLevel level)) {
            return;
        }
        Optional<TerraformData.Mirror> mirror = Terraforming.mirror(level, this.worldPosition);
        if (mirror.isPresent()) {
            this.target = BodyId.parse(mirror.get().planet()).orElse(null);
            return;
        }
        Optional<SpaceLayout.Body> body = TerraformBlocks.OrbitalMirror.target(level, this.worldPosition);
        this.target = body.map(b -> b.planet().id()).orElse(null);
        if (this.target != null) {
            Terraforming.addMirror(level, this.worldPosition, this.target, this.shade());
        }
    }

    private boolean shade() {
        BlockState state = this.getBlockState();
        return state.hasProperty(TerraformBlocks.OrbitalMirror.SHADE) && state.getValue(TerraformBlocks.OrbitalMirror.SHADE);
    }

    @Override
    public boolean clickButton(ServerPlayer player, int button) {
        if (button != TOGGLE || !(this.level instanceof ServerLevel level)) {
            return false;
        }
        boolean shade = !this.shade();
        BlockState state = this.getBlockState();
        if (state.hasProperty(TerraformBlocks.OrbitalMirror.SHADE)) {
            level.setBlock(this.worldPosition, state.setValue(TerraformBlocks.OrbitalMirror.SHADE, shade), 3);
        }
        Terraforming.setShade(level, this.worldPosition, shade);
        this.setChanged();
        return true;
    }

    private int status() {
        if (this.level instanceof ServerLevel level && SpaceDimensions.system(level.dimension()).isEmpty()) {
            return NOT_IN_SPACE;
        }
        return this.target == null ? NO_PLANET : OK;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.ORBITAL_MIRROR;
    }

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        if (index == STATUS) {
            return this.status();
        }
        if (index == SHADE) {
            return this.shade() ? 1 : 0;
        }
        if (index == MIRRORS || index == SHADES) {
            if (this.target == null || !(this.level instanceof ServerLevel level)) {
                return 0;
            }
            TerraformState state = Terraforming.state(level.getServer(), this.target);
            return index == MIRRORS ? state.mirrors() : state.shades();
        }
        if (index == SYSTEM) {
            return this.target == null ? -1 : this.target.system();
        }
        if (index == PLANET) {
            return this.target == null ? -1 : this.target.planet();
        }
        if (index == MOON) {
            return this.target == null ? 0 : this.target.moon() + 1;
        }
        return super.dataValue(index);
    }
}
