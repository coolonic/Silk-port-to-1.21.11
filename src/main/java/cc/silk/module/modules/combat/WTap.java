package cc.silk.module.modules.combat;

import cc.silk.event.impl.player.DoAttackEvent;
import cc.silk.event.impl.player.TickEvent;
import cc.silk.module.Category;
import cc.silk.module.Module;
import cc.silk.module.setting.BooleanSetting;
import cc.silk.module.setting.NumberSetting;
import meteordevelopment.orbit.EventHandler;
import org.lwjgl.glfw.GLFW;

public class WTap extends Module {
    private final NumberSetting chance = new NumberSetting("Chance (%)", 1, 100, 100, 1);
    private final NumberSetting msDelay = new NumberSetting("Ms", 1, 500, 60, 1);
    private final BooleanSetting onlyOnGround = new BooleanSetting("Only on ground", true);

    private boolean isTapping = false;
    private boolean wasWHeld = false;
    private long tapEndTime = 0L;

    public WTap() {
        super("WTap", "Makes you automatically WTAP", -1, Category.COMBAT);
        this.addSettings(msDelay, chance, onlyOnGround);
    }

    @EventHandler
    private void onAttackEvent(DoAttackEvent event) {
        if (isNull() || isTapping) return;
        if (Math.random() * 100 > chance.getValueFloat()) return;
        if (!mc.player.isOnGround() && onlyOnGround.getValue()) return;
        var target = mc.targetedEntity;
        if (target == null || !target.isAlive()) return;
        if (!isWPhysicallyPressed()) return;
        if (!mc.player.isSprinting()) return;

        isTapping = true;
        wasWHeld = true;
        tapEndTime = System.currentTimeMillis() + msDelay.getValueInt();
        mc.options.forwardKey.setPressed(false);
    }

    @EventHandler
    private void onTickEvent(TickEvent event) {
        if (isNull() || !isTapping) return;

        if (!isWPhysicallyPressed()) {
            clearState();
            return;
        }

        if (System.currentTimeMillis() >= tapEndTime) {
            if (wasWHeld) mc.options.forwardKey.setPressed(true);
            isTapping = false;
            wasWHeld = false;
        } else {
            mc.options.forwardKey.setPressed(false);
        }
    }

    private void clearState() {
        isTapping = false;
        wasWHeld = false;
    }

    private boolean isWPhysicallyPressed() {
        return GLFW.glfwGetKey(mc.getWindow().getHandle(), GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS;
    }

    @Override
    public void onDisable() {
        if (isTapping && wasWHeld && isWPhysicallyPressed()) {
            mc.options.forwardKey.setPressed(true);
        }
        clearState();
        super.onDisable();
    }
}
