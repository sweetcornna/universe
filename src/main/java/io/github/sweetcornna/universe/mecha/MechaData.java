package io.github.sweetcornna.universe.mecha;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.mecha.MechaEnergy;
import net.minecraft.world.item.ItemStack;

/**
 * 玩家身上的机甲（ADR-0023）：能量核心、燃烧室里的燃料和上一次烧的是什么（自动补燃料用）。只在服务端，存档，死亡不清空
 * （燃料格的东西在死亡时掉出来，见 {@link Mecha}）。
 */
public final class MechaData {
    public static final MapCodec<MechaData> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("energy", 0.0).forGetter(data -> data.energy.energy()),
            Codec.DOUBLE.optionalFieldOf("burning", 0.0).forGetter(data -> data.energy.burning()),
            Codec.DOUBLE.optionalFieldOf("burning_total", 0.0).forGetter(data -> data.energy.burningTotal()),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("fuel", ItemStack.EMPTY).forGetter(data -> data.fuel),
            Codec.STRING.optionalFieldOf("last_fuel", "").forGetter(data -> data.lastFuel)
    ).apply(i, MechaData::new));

    private final MechaEnergy energy;
    private ItemStack fuel;
    private String lastFuel;
    /** 这一刻充进来、用掉的电，和平滑过的每刻功率（面板显示用，不存档）。 */
    private double in;
    private double out;
    private double inflow;
    private double outflow;

    public MechaData() {
        this(0, 0, 0, ItemStack.EMPTY, "");
    }

    private MechaData(double energy, double burning, double burningTotal, ItemStack fuel, String lastFuel) {
        this.energy = new MechaEnergy(energy, burning, burningTotal);
        this.fuel = fuel;
        this.lastFuel = lastFuel;
    }

    public MechaEnergy energy() {
        return this.energy;
    }

    public ItemStack fuel() {
        return this.fuel;
    }

    public void setFuel(ItemStack fuel) {
        this.fuel = fuel;
    }

    /** 上一次烧的燃料（物品 ID），自动补燃料时找这种。 */
    public String lastFuel() {
        return this.lastFuel;
    }

    public void setLastFuel(String lastFuel) {
        this.lastFuel = lastFuel;
    }

    void charged(double joules) {
        this.in += joules;
    }

    void drew(double joules) {
        this.out += joules;
    }

    /** 一刻开始时把上一刻的进出算进平均功率。 */
    void roll() {
        this.inflow = this.inflow * 0.9 + this.in * 0.1;
        this.outflow = this.outflow * 0.9 + this.out * 0.1;
        this.in = 0;
        this.out = 0;
    }

    /** 平均每刻充进来多少焦。 */
    public double inflow() {
        return this.inflow;
    }

    /** 平均每刻用掉多少焦。 */
    public double outflow() {
        return this.outflow;
    }
}
