package io.github.sweetcornna.universe.mecha;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.mecha.LogisticsSlot;
import io.github.sweetcornna.universe.core.mecha.combat.MechaWeapon;
import io.github.sweetcornna.universe.core.mecha.combat.WeaponMode;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 机甲面板里玩家自己设的东西（ADR-0023）：物流栏位和自动补燃料；机甲三门武器的模式（ADR-0033 §二，按 {@link MechaWeapon}
 * 的顺序，存 {@link WeaponMode} 的序号）。存档，死亡不丢，同步给本人画面板。
 */
public record MechaSettings(List<LogisticsSlot> slots, boolean autoRefuel, List<Integer> weapons) {
    /** 物流栏位的格数。 */
    public static final int SLOTS = 12;
    public static final MechaSettings DEFAULT = new MechaSettings(List.of(), true, List.of());

    private static final Codec<LogisticsSlot> SLOT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("item", "").forGetter(LogisticsSlot::item),
            Codec.INT.optionalFieldOf("min", 0).forGetter(LogisticsSlot::min),
            Codec.INT.optionalFieldOf("max", 0).forGetter(LogisticsSlot::max)
    ).apply(i, LogisticsSlot::new));
    public static final MapCodec<MechaSettings> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SLOT_CODEC.listOf().optionalFieldOf("logistics", List.of()).forGetter(MechaSettings::slots),
            Codec.BOOL.optionalFieldOf("auto_refuel", true).forGetter(MechaSettings::autoRefuel),
            Codec.INT.listOf().optionalFieldOf("weapons", List.of()).forGetter(MechaSettings::weapons)
    ).apply(i, MechaSettings::new));
    private static final StreamCodec<ByteBuf, LogisticsSlot> SLOT_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, LogisticsSlot::item,
            ByteBufCodecs.VAR_INT, LogisticsSlot::min,
            ByteBufCodecs.VAR_INT, LogisticsSlot::max,
            LogisticsSlot::new);
    public static final StreamCodec<ByteBuf, MechaSettings> STREAM_CODEC = StreamCodec.composite(
            SLOT_STREAM_CODEC.apply(ByteBufCodecs.list(SLOTS)), MechaSettings::slots,
            ByteBufCodecs.BOOL, MechaSettings::autoRefuel,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MechaWeapon.values().length)), MechaSettings::weapons,
            MechaSettings::new);

    public MechaSettings {
        List<LogisticsSlot> padded = new ArrayList<>(SLOTS);
        for (int i = 0; i < SLOTS; i++) {
            padded.add(i < slots.size() ? slots.get(i) : LogisticsSlot.EMPTY);
        }
        slots = List.copyOf(padded);
        List<Integer> modes = new ArrayList<>(MechaWeapon.values().length);
        for (int i = 0; i < MechaWeapon.values().length; i++) {
            modes.add(i < weapons.size() ? WeaponMode.byOrdinal(weapons.get(i)).ordinal() : WeaponMode.AUTO.ordinal());
        }
        weapons = List.copyOf(modes);
    }

    public LogisticsSlot slot(int index) {
        return this.slots.get(index);
    }

    public MechaSettings withSlot(int index, LogisticsSlot slot) {
        List<LogisticsSlot> list = new ArrayList<>(this.slots);
        list.set(index, slot);
        return new MechaSettings(list, this.autoRefuel, this.weapons);
    }

    public MechaSettings withAutoRefuel(boolean autoRefuel) {
        return new MechaSettings(this.slots, autoRefuel, this.weapons);
    }

    public WeaponMode mode(MechaWeapon weapon) {
        return WeaponMode.byOrdinal(this.weapons.get(weapon.ordinal()));
    }

    public MechaSettings withMode(MechaWeapon weapon, WeaponMode mode) {
        List<Integer> list = new ArrayList<>(this.weapons);
        list.set(weapon.ordinal(), mode.ordinal());
        return new MechaSettings(this.slots, this.autoRefuel, list);
    }
}
