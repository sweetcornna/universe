package io.github.sweetcornna.universe.client.exocraft;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** 探索车一帧的渲染状态。 */
public class ExocraftRenderState extends EntityRenderState {
    float yRot;
    float wheelSpin;
    /** 前轮偏的角度（弧度）。 */
    float steer;
    boolean laser;
    /** 挨敲时晃一下：剩余刻数和方向。 */
    float hurtTime;
    int hurtDir;
}
