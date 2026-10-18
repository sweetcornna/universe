package io.github.sweetcornna.universe.factory.power;

import java.util.Optional;

import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 风力涡轮机（玩法设计 §9.4）：发电量随星球的风力、海拔和天气变化，没有大气的星球上不转。最大 300 W。
 */
public class WindTurbineBlockEntity extends MachineBlockEntity {
    /** 风力系数为 1 时每刻发的电（戴森球计划 300 kW）。 */
    public static final long MAX_POWER = 15;
    public static final int WIND = BASE_DATA;
    public static final int DATA_COUNT = BASE_DATA + 1;
    private static final int RECHECK = 40;

    /** 风力系数：0 不转，1 是标准风力，最高 2 倍多一点。客户端用它定转速。 */
    private float wind;
    private int recheck;

    public WindTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.WIND_TURBINE.get(), pos, state, 0);
    }

    public float wind() {
        return this.wind;
    }

    // 客户端：风轮的角度按时间累积，风力变化时转速平滑过渡，不会跳
    private float rotor;
    private double rotorTime = Double.NaN;
    private float rotorSpeed;

    /** 风轮现在的角度（度）。只在客户端渲染时调用。 */
    public float rotorAngle(float partialTicks, float degreesPerTick) {
        if (this.level == null) {
            return this.rotor;
        }
        double now = this.level.getGameTime() + partialTicks;
        double elapsed = Double.isNaN(this.rotorTime) ? 0 : Math.max(0, Math.min(20, now - this.rotorTime));
        this.rotorTime = now;
        float target = this.wind * degreesPerTick;
        this.rotorSpeed += (target - this.rotorSpeed) * (float) Math.min(1, elapsed * 0.05);
        this.rotor = (float) ((this.rotor + this.rotorSpeed * elapsed) % 360);
        return this.rotor;
    }

    @Override
    public long generation() {
        return Math.round(MAX_POWER * this.wind);
    }

    @Override
    protected boolean tickMachine() {
        if (--this.recheck <= 0 && this.level instanceof ServerLevel serverLevel) {
            this.recheck = RECHECK;
            float wind = (float) windFactor(serverLevel, this.worldPosition);
            if (Math.abs(wind - this.wind) > 0.02F) {
                this.wind = wind;
                this.setChanged();
                serverLevel.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            }
        }
        return this.wind > 0;
    }

    /** 这个位置的风力系数：星球的风力 × 大气压 × 海拔 × 天气。 */
    public static double windFactor(ServerLevel level, BlockPos pos) {
        Optional<Planet> body = Planets.body(level);
        double base;
        if (body.isPresent()) {
            Planet planet = body.get();
            if (!planet.atmosphere().present()) {
                return 0;
            }
            base = (0.5 + planet.weather().wind()) * Mth.clamp(Math.sqrt(planet.atmosphere().pressure()), 0.3, 1.5);
        } else {
            // 下界、末地这些：有顶的维度风小
            base = level.dimensionType().hasCeiling() ? 0.3 : 0.9;
        }
        // 海平面上 60% 左右，越高风越大，山顶上一倍半
        double altitude = Mth.clamp(0.6 + (pos.getY() - level.getSeaLevel()) / 80.0, 0.5, 1.6);
        double weather = level.isThundering() ? 1.5 : level.isRaining() ? 1.2 : 1;
        return base * altitude * weather;
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
        return index == WIND ? Math.round(this.wind * 100) : super.dataValue(index);
    }

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.WIND_TURBINE;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.wind = input.getFloatOr("Wind", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("Wind", this.wind);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Wind", this.wind);
        return tag;
    }
}
