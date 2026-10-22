package io.github.sweetcornna.universe.client.darkfog.flight;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** 渲染黑雾种子要用到的状态。 */
public class SeedRenderState extends LivingEntityRenderState {
    /** 每颗种子错开的相位，免得几颗一起转。 */
    public float phase;
}
