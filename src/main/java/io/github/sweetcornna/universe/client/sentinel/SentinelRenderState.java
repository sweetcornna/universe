package io.github.sweetcornna.universe.client.sentinel;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** 渲染守卫机要用到的状态。 */
public class SentinelRenderState extends LivingEntityRenderState {
    public boolean heavy;
    public boolean hostile;
    public boolean scanning;
    /** 每架守卫机浮动的相位不同，免得一群守卫机步调一致。 */
    public float bobPhase;
}
