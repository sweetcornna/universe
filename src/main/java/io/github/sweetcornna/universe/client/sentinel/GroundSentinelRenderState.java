package io.github.sweetcornna.universe.client.sentinel;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** 渲染地面守卫机（四足机、步行机）要用到的状态。 */
public class GroundSentinelRenderState extends LivingEntityRenderState {
    public boolean hostile;
    /** 四足机光束蓄力的进度 0–1；没在蓄力时为 0。 */
    public float charge;
    /** 四足机正扑在空中。 */
    public boolean pouncing;
    /** 步行机抬腿的进度 0–1；没在抬腿时为 0。 */
    public float stomp;
}
