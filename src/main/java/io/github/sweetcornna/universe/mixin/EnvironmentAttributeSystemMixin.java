package io.github.sweetcornna.universe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.sweetcornna.universe.world.planet.PlanetEnvironment;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

/**
 * 在环境属性系统里插入星球的两层（{@link PlanetEnvironment}）。
 *
 * <p>原版的层序是：维度、生物群系（静态层），时间线、天气（动态层）。底色层接在静态层末尾，
 * 收尾层接在动态层末尾。客户端世界随后还会加闪电照亮天空的一层，不受影响。
 */
@Mixin(EnvironmentAttributeSystem.class)
abstract class EnvironmentAttributeSystemMixin {
    @Inject(method = "addStaticLayers(Lnet/minecraft/world/attribute/EnvironmentAttributeSystem$Builder;Lnet/minecraft/world/level/LevelAccessor;)V",
            at = @At("TAIL"))
    private static void universe$addPlanetBaseLayers(EnvironmentAttributeSystem.Builder builder, LevelAccessor level, CallbackInfo ci) {
        PlanetEnvironment.addBaseLayers(builder, level);
    }

    @Inject(method = "addDynamicLayers(Lnet/minecraft/world/attribute/EnvironmentAttributeSystem$Builder;Lnet/minecraft/world/level/Level;)V",
            at = @At("TAIL"))
    private static void universe$addPlanetFinishLayers(EnvironmentAttributeSystem.Builder builder, Level level, CallbackInfo ci) {
        PlanetEnvironment.addFinishLayers(builder, level);
    }
}
