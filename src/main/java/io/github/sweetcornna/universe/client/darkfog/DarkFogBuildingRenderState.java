package io.github.sweetcornna.universe.client.darkfog;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.darkfog.BuildingKind;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.phys.Vec3;

/** 渲染黑雾建筑要用到的状态。 */
public class DarkFogBuildingRenderState extends LivingEntityRenderState {
    public BuildingKind kind = BuildingKind.CORE;
    public boolean powered = true;
    /** 炮头相对机身转了多少（弧度）。 */
    public float headYaw;
    /** 光束：激光塔烧着的目标、中继站往下连到的钻芯，相对于建筑的位置；没有是空的。 */
    public @Nullable Vec3 beam;
    public float phase;
}
