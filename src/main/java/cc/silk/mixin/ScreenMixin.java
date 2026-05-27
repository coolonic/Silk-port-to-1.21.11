package cc.silk.mixin;

import cc.silk.gui.ClickGui;
import cc.silk.gui.newgui.NewClickGUI;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Shadow
    @Nullable
    protected MinecraftClient client;

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void renderBackgroundInject(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (client == null) return;
        Screen currentScreen = client.currentScreen;
        if (currentScreen instanceof ClickGui) {
            ci.cancel();
            return;
        }
        if (currentScreen instanceof NewClickGUI && !cc.silk.module.modules.client.ClientSettingsModule.isGuiBlurEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderTail(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (client == null) return;
        Screen currentScreen = client.currentScreen;
        if (currentScreen instanceof NewClickGUI || currentScreen instanceof ClickGui) {
            // Reset GL state after our custom GUI renders (1.21.11: RenderSystem high-level helpers removed)
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(0x0302, 0x0303, 1, 0); // SRC_ALPHA, ONE_MINUS_SRC_ALPHA
            GlStateManager._enableDepthTest();
            GlStateManager._depthFunc(515); // GL_LEQUAL
            GlStateManager._enableCull();
            GlStateManager._disableScissorTest();
            GlStateManager._colorMask(true, true, true, true);
            GlStateManager._depthMask(true);
        }
    }
}
