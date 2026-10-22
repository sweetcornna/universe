package io.github.sweetcornna.universe.client.darkfog.hive;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.darkfog.HiveBuildingKind;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.phys.Vec3;

/** 渲染巢穴建筑要用到的状态。 */
public class HiveBuildingRenderState extends LivingEntityRenderState {
    public HiveBuildingKind kind = HiveBuildingKind.CORE;
    public boolean powered = true;
    /** 炮头、接收盘相对机身转了多少（弧度）。 */
    public float headYaw;
    public float headPitch;
    /** 光子接收器往恒星送的光束：起点、终点，相对于建筑的位置；没有是空的。 */
    public @Nullable Vec3 beamFrom;
    public @Nullable Vec3 beamTo;
    /** 核心连到别的建筑的系索的另一头，相对于核心的位置；别的建筑是空的。 */
    public List<Vec3> tethers = List.of();
    public float phase;
}
