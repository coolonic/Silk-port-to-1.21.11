package cc.silk.module.modules.movement;

import cc.silk.event.impl.player.TickEvent;
import cc.silk.mixin.KeyBindingAccessor;
import cc.silk.module.Category;
import cc.silk.module.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class SnapTap extends Module {
    private boolean prevLeft = false;
    private boolean prevRight = false;
    private boolean prevForward = false;
    private boolean prevBack = false;
    private boolean lastStrafe = false;
    private boolean lastAxis = false;

    public SnapTap() {
        super("Snap Tap", "Cancels opposing movement inputs instantly, like a Snap Tap keyboard", Category.MOVEMENT);
    }

    @EventHandler
    private void onTick(TickEvent event) {
        if (isNull() || mc.currentScreen != null) return;

        boolean left = pressed(mc.options.leftKey);
        boolean right = pressed(mc.options.rightKey);
        boolean forward = pressed(mc.options.forwardKey);
        boolean back = pressed(mc.options.backKey);

        if (left && !prevLeft) lastStrafe = true;
        if (right && !prevRight) lastStrafe = false;
        if (forward && !prevForward) lastAxis = true;
        if (back && !prevBack) lastAxis = false;

        mc.options.leftKey.setPressed(left && right ? lastStrafe : left);
        mc.options.rightKey.setPressed(left && right ? !lastStrafe : right);
        mc.options.forwardKey.setPressed(forward && back ? lastAxis : forward);
        mc.options.backKey.setPressed(forward && back ? !lastAxis : back);

        prevLeft = left;
        prevRight = right;
        prevForward = forward;
        prevBack = back;
    }

    @Override
    public void onDisable() {
        if (mc.options != null) {
            mc.options.leftKey.setPressed(pressed(mc.options.leftKey));
            mc.options.rightKey.setPressed(pressed(mc.options.rightKey));
            mc.options.forwardKey.setPressed(pressed(mc.options.forwardKey));
            mc.options.backKey.setPressed(pressed(mc.options.backKey));
            prevLeft = prevRight = prevForward = prevBack = false;
        }
        super.onDisable();
    }

    private boolean pressed(KeyBinding key) {
        int code = ((KeyBindingAccessor) key).getBoundKey().getCode();
        return GLFW.glfwGetKey(mc.getWindow().getHandle(), code) == GLFW.GLFW_PRESS;
    }
}
