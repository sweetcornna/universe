package io.github.sweetcornna.universe.client.darkfog.space;

import io.github.sweetcornna.universe.core.darkfog.SpaceUnitKind;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** 渲染太空单位要用到的状态。 */
public class SpaceUnitRenderState extends LivingEntityRenderState {
    public SpaceUnitKind kind = SpaceUnitKind.LANCER;
    /** 每个单位摆动的相位不同。 */
    public float phase;
    /** 在打人、在轰炸，发光层亮一些。 */
    public boolean angry;
    /** 转弯时侧倾的角度（度）。 */
    public float roll;
}
