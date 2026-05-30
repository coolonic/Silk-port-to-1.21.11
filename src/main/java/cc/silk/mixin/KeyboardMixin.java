package cc.silk.mixin;

import cc.silk.SilkClient;
import cc.silk.module.Module;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    // In 1.21.4+ the Keyboard#onKey signature changed:
    //   OLD: (long window, int key, int scancode, int action, int mods)
    //   NEW: (long window, int action, KeyInput keyInput)
    // KeyInput is a record of {key, scancode, modifiers}; the standalone int is now the GLFW action.
    @Inject(method = "onKey", at = @At("HEAD"))
    private void onPress(long window, int action, KeyInput keyInput, CallbackInfo ci) {
        if (window == this.client.getWindow().getHandle()) {
            if (this.client.currentScreen == null) {
                // Use key() (the GLFW keycode component) — NOT getKeycode() which belongs
                // to AbstractInput and returns NOT_A_NUMBER for non-character keys like Right Shift.
                int key = keyInput.key();
                for (Module module : SilkClient.INSTANCE.moduleManager.getModules()) {
                    if (key == module.getKey()) {
                        if (module.getKeybindSetting().isHoldMode()) {
                            if (action == GLFW.GLFW_PRESS && !module.isEnabled()) {
                                module.setEnabled(true);
                            } else if (action == GLFW.GLFW_RELEASE && module.isEnabled()) {
                                module.setEnabled(false);
                            }
                        } else {
                            // action == GLFW_PRESS already guarantees the key was just pressed;
                            // the redundant isKeyPressed() re-query can return stale state on
                            // Linux for modifier keys (Shift, Ctrl, etc.) and should be dropped.
                            if (action == GLFW.GLFW_PRESS) {
                                module.toggle();
                            }
                        }
                    }
                }
            }
        }
    }
}
