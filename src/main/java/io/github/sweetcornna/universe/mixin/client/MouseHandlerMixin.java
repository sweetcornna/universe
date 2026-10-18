package io.github.sweetcornna.universe.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.sweetcornna.universe.client.dev.DevScript;
import net.minecraft.client.MouseHandler;

/**
 * 开发脚本运行时不抓鼠标。
 *
 * <p>无头验收时游戏窗口放在屏幕外。原版在窗口有焦点时抓住鼠标、把光标锁在窗口中间，打开界面时又把光标挪回窗口中间，
 * 都会把正在用电脑的人的鼠标拽走。没抓过鼠标，打开界面时也就不会挪光标。只在带了 {@code -Duniverse.devScript} 启动时生效。
 */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void universe$leaveCursorAlone(CallbackInfo ci) {
        if (DevScript.enabled()) {
            ci.cancel();
        }
    }
}
