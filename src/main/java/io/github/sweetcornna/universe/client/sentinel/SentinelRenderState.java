package io.github.sweetcornna.universe.client.sentinel;

import io.github.sweetcornna.universe.core.sentinel.SentinelKind;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** 渲染守卫机要用到的状态。 */
public class SentinelRenderState extends LivingEntityRenderState {
    public boolean heavy;
    public SentinelKind kind = SentinelKind.DRONE;
    /** 召唤无人机正在聚光。 */
    public boolean charging;
    public boolean hostile;
    public boolean scanning;
    /** 每架守卫机浮动的相位不同，免得一群守卫机步调一致。 */
    public float bobPhase;
}
