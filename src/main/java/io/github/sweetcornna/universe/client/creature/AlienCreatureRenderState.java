package io.github.sweetcornna.universe.client.creature;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.ecology.CreatureSpecies;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** 渲染异星动物要用到的状态。 */
public class AlienCreatureRenderState extends LivingEntityRenderState {
    public @Nullable CreatureSpecies species;
    /** 飞兽在空中。 */
    public boolean flying;
    /** 扑咬动作的进度，0–1。 */
    public float attackAnim;
}
