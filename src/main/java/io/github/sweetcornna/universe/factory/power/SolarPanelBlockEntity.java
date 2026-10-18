package io.github.sweetcornna.universe.factory.power;

import java.util.Optional;
import java.util.OptionalInt;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.power.Solar;
import io.github.sweetcornna.universe.core.space.SpaceLayout;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 太阳能板（ADR-0015）：满功率 360 W（戴森球计划 360 kW）× 星球的光照比例 × 太阳高度 × 天气，头顶被挡住时不发电。
 * 太空里没有昼夜，一直按太阳在正上方算，光照比例按离恒星的距离。
 */
public class SolarPanelBlockEntity extends MachineBlockEntity {
    /** 光照系数为 1 时每刻发的电。 */
    public static final long MAX_POWER = 18;
    public static final int RATIO = BASE_DATA;
    public static final int SUN = BASE_DATA + 1;
    public static final int WEATHER = BASE_DATA + 2;
    public static final int SKY = BASE_DATA + 3;
    public static final int DATA_COUNT = BASE_DATA + 4;
    private static final int RECHECK = 20;

    /** 这颗星球（或太空里这个位置）的光照比例，第一次用到时算。 */
    private double ratio = Double.NaN;
    private double sun;
    private double weather = 1;
    private boolean sky = true;
    private int recheck;
    /** 这一刻发的电；功率不是整数时把零头攒到下一刻，平均下来和算出来的一样。 */
    private long output;
    private double carry;

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.SOLAR_PANEL.get(), pos, state, 0);
    }

    /** 现在的光照系数：0 到 {@value Solar#MAX_RATIO}。 */
    public double light() {
        return this.sky ? this.ratio() * this.sun * this.weather : 0;
    }

    private double ratio() {
        return Double.isNaN(this.ratio) ? 0 : this.ratio;
    }

    @Override
    public long generation() {
        return this.output;
    }

    @Override
    protected boolean tickMachine() {
        if (!(this.level instanceof ServerLevel level)) {
            return false;
        }
        if (Double.isNaN(this.ratio)) {
            this.ratio = ratio(level, this.worldPosition);
        }
        if (--this.recheck <= 0) {
            this.recheck = RECHECK;
            BlockPos above = this.worldPosition.above();
            boolean space = SpaceDimensions.system(level.dimension()).isPresent();
            this.sky = level.dimensionType().hasSkyLight() && level.canSeeSky(above);
            this.sun = space ? 1 : Solar.elevation(level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, this.worldPosition));
            boolean raining = !space && level.isRainingAt(above);
            this.weather = Solar.weather(raining, raining && level.isThundering());
        }
        double exact = MAX_POWER * this.light() + this.carry;
        this.output = (long) Math.floor(exact);
        this.carry = exact - this.output;
        return this.output > 0;
    }

    /**
     * 光照比例：星球按它（卫星按母行星）的轨道半径和恒星的光度算；太空里按离恒星的距离；主世界是母星；
     * 下界、末地这些没有太阳的维度是 0。
     */
    static double ratio(ServerLevel level, BlockPos pos) {
        Galaxy galaxy = Planets.galaxy(level.getServer());
        OptionalInt space = SpaceDimensions.system(level.dimension());
        if (space.isPresent()) {
            StarSystem system = galaxy.systems().get(space.getAsInt());
            double distance = Math.hypot(pos.getX() + 0.5, pos.getZ() + 0.5);
            return Solar.ratio(system.star().luminosity(), SpaceLayout.auAt(distance));
        }
        Optional<Planet> body = Planets.body(level);
        if (body.isEmpty()) {
            return level.dimension() == Level.OVERWORLD ? 1 : 0;
        }
        Planet planet = body.get();
        StarSystem system = galaxy.systems().get(planet.id().system());
        Planet orbiting = planet.moon() ? system.planets().get(planet.id().planet()) : planet;
        return Solar.ratio(system.star().luminosity(), orbiting.orbit().radius());
    }

    @Override
    protected boolean generator() {
        return true;
    }

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        return switch (index) {
            case RATIO -> (int) Math.round(this.ratio() * 100);
            case SUN -> (int) Math.round(this.sun * 100);
            case WEATHER -> (int) Math.round(this.weather * 100);
            case SKY -> this.sky ? 1 : 0;
            default -> super.dataValue(index);
        };
    }

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.SOLAR_PANEL;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.carry = input.getDoubleOr("Carry", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("Carry", this.carry);
    }
}
