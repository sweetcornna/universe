package io.github.sweetcornna.universe.client.exocraft;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** 潜艇一帧的渲染状态。 */
public class SubmarineRenderState extends EntityRenderState {
    float yRot;
    /** 艇头的俯仰（度，正数朝下）。 */
    float xRot;
    /** 螺旋桨转过的角度（弧度）。 */
    float propellerSpin;
    /** 有人开着、在水里：大灯和光柱亮着。 */
    boolean lights;
    /** 挨敲时晃一下：剩余刻数和方向。 */
    float hurtTime;
    int hurtDir;
}
