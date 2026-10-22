package io.github.sweetcornna.universe.client.darkfog;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.darkfog.UnitKind;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.phys.Vec3;

/** 渲染黑雾单位要用到的状态。 */
public class DarkFogUnitRenderState extends LivingEntityRenderState {
    public UnitKind kind = UnitKind.RAIDER;
    /** 每个单位浮动的相位不同。 */
    public float bobPhase;
    /** 正在打设施或者打人，发光层亮一些。 */
    public boolean angry;
    /** 电弧的终点，相对于单位的位置；没有电弧是空的。 */
    public @Nullable Vec3 arc;
}
