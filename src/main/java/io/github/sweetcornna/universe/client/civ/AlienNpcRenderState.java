package io.github.sweetcornna.universe.client.civ;

import io.github.sweetcornna.universe.core.civ.Race;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** 渲染外星 NPC 要用到的状态。 */
public class AlienNpcRenderState extends LivingEntityRenderState {
    public Race race = Race.TRADER;
    /** 手势的进度：1 刚开始，0 没在比。 */
    public float gesture;
    /** 每个人摆动的相位不同。 */
    public float phase;
    /** 个体的色差，乘在贴图上。 */
    public int tint = 0xFFFFFF;
}
