package cc.silk.mixin;

import cc.silk.SilkClient;
import cc.silk.event.impl.render.Render3DEvent;
import cc.silk.utils.render.W2SUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    // In 1.21.11, renderHand became a private method (float, boolean, Matrix4f) instead of a boolean field.
    // Target its INVOKE site inside renderWorld so we capture matrices right before the hand is drawn.
    @Inject(method = "renderWorld",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/render/GameRenderer;renderHand(FZLorg/joml/Matrix4f;)V"))
    private void renderHand(RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        MatrixStack matrixStack = new MatrixStack();
        Camera camera = mc.gameRenderer.getCamera();

        RenderSystem.getModelViewStack().pushMatrix().mul(matrixStack.peek().getPositionMatrix());
        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0f));
        // applyModelViewMatrix removed in newer versions; the stack state is used directly

        // 1.21.11: RenderSystem.getProjectionMatrix() removed; use GameRenderer.getBasicProjectionMatrix() instead
        W2SUtil.matrixProject.set(mc.gameRenderer.getBasicProjectionMatrix(tickCounter.getTickProgress(false)));
        W2SUtil.matrixModel.set(RenderSystem.getModelViewMatrix());
        W2SUtil.matrixWorldSpace.set(matrixStack.peek().getPositionMatrix());

        RenderSystem.getModelViewStack().popMatrix();
    }

    @Inject(method = "renderWorld", at = @At("TAIL"))
    private void onRenderWorldTail(RenderTickCounter tickCounter, CallbackInfo ci) {
        SilkClient.INSTANCE.getSilkEventBus().post(new Render3DEvent(new MatrixStack()));
    }
}
