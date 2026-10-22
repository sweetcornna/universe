package io.github.sweetcornna.universe.core.vehicle;

/**
 * 载具的种类（ADR-0017、ADR-0026）：注册名、货舱格数、相对探索车的速度、放在哪儿。召唤台的列表、载具的实体都按它来。
 * 只能往后加：召唤台的存档里记的是注册名，不是序号。
 */
public enum VehicleKind {
    EXOCRAFT("exocraft", 27, 1.0, false),
    SUBMARINE("submarine", 18, SubmarineDrive.MAX_SPEED / RoverDrive.MAX_SPEED, true),
    HOVER_CAR("hover_car", 18, VehicleKind.HOVER_CAR_SPEED, false),
    HEAVY_ROVER("heavy_rover", 54, VehicleKind.HEAVY_ROVER_SPEED, false),
    EXO_MECH("exo_mech", 27, VehicleKind.EXO_MECH_SPEED, false);

    /** 悬浮车最快是探索车的几倍。 */
    public static final double HOVER_CAR_SPEED = 1.4;
    /** 重型车最快是探索车的几倍。 */
    public static final double HEAVY_ROVER_SPEED = 0.7;
    /** 机甲走路最快是探索车的几倍；喷气时见 {@link MechDrive#JET_SPEED}。 */
    public static final double EXO_MECH_SPEED = 0.6;

    private final String id;
    private final int cargoSlots;
    private final double speedFactor;
    private final boolean aquatic;

    VehicleKind(String id, int cargoSlots, double speedFactor, boolean aquatic) {
        this.id = id;
        this.cargoSlots = cargoSlots;
        this.speedFactor = speedFactor;
        this.aquatic = aquatic;
    }

    /** 实体和物品的注册名。 */
    public String id() {
        return this.id;
    }

    public int cargoSlots() {
        return this.cargoSlots;
    }

    /** 最快的速度是探索车的几倍。 */
    public double speedFactor() {
        return this.speedFactor;
    }

    /** 只能放在水里（潜艇）：召唤时台子旁边要有水。 */
    public boolean aquatic() {
        return this.aquatic;
    }

    /** 按注册名找；不认识的是 null。 */
    public static VehicleKind byId(String id) {
        for (VehicleKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return null;
    }
}
