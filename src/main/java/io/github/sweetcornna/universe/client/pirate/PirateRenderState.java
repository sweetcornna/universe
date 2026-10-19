package io.github.sweetcornna.universe.client.pirate;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** 渲染海盗战机要用到的状态。 */
public class PirateRenderState extends EntityRenderState {
    public float yRot;
    public float xRot;
    /** 转弯时的侧倾（度）。 */
    public float bank;
    public boolean leaving;
    /** 每架战机抖动的相位不同。 */
    public float phase;
}
