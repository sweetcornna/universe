package io.github.sweetcornna.universe.client.exocraft;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** 机甲一帧的渲染状态。 */
public class ExoMechRenderState extends EntityRenderState {
    float yRot;
    /** 腿迈过的相位（弧度）和这一刻迈得多大（0 到 1）。 */
    float stride;
    float strideAmount;
    /** 喷气开着、没踩着地：腿收起来。 */
    boolean thrusting;
    boolean grounded;
    float hurtTime;
    int hurtDir;
}
