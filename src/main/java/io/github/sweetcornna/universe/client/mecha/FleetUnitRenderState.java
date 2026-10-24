package io.github.sweetcornna.universe.client.mecha;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.mecha.fleet.FleetUnitKind;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.phys.Vec3;

/** 渲染舰队单位要用到的状态。 */
public class FleetUnitRenderState extends LivingEntityRenderState {
    public FleetUnitKind kind = FleetUnitKind.PROTOTYPE;
    /** 每架浮动的相位不同。 */
    public float phase;
    /** 转弯时侧倾的角度（度）。 */
    public float roll;
    /** 炮塔对着目标：相对机身的偏航角（度）。 */
    public boolean aiming;
    public float aimYaw;
    /** 激光的终点，相对于单位的位置；没在开火是空的。 */
    public @Nullable Vec3 beam;
    /** 激光还剩多亮（0–1）。 */
    public float beamFade;
    /** 驱逐舰点防御的光束终点。 */
    public @Nullable Vec3 pointDefense;
    public float pointDefenseFade;
}
