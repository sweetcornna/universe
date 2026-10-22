package io.github.sweetcornna.universe.client.exocraft;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** 探索车（以及悬浮车、重型车）一帧的渲染状态。 */
public class ExocraftRenderState extends EntityRenderState {
    float yRot;
    float wheelSpin;
    /** 前轮偏的角度（弧度）。 */
    float steer;
    boolean laser;
    /** 挨敲时晃一下：剩余刻数和方向。 */
    float hurtTime;
    int hurtDir;
    /** 悬浮车有人开着：推力垫喷着，车身轻轻浮动。 */
    boolean hovering;
    /** 重型车车尾的精炼机正在炼：观察窗亮。 */
    boolean refining;
}
