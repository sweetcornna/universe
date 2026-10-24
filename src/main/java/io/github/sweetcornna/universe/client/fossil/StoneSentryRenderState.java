package io.github.sweetcornna.universe.client.fossil;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** 渲染石哨兵要用到的状态。 */
public class StoneSentryRenderState extends LivingEntityRenderState {
    /** 还在浮起来：1 是刚醒，0 是浮好了。 */
    public float rising;
    /** 抬手扔石块的进度。 */
    public float windup;
    /** 点着以后的进度：0 没点着，1 马上炸。 */
    public float fuse;
    /** 每个石哨兵浮动的相位不同。 */
    public float bobPhase;
    public boolean hostile;
}
