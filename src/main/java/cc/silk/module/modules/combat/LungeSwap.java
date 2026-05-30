package cc.silk.module.modules.combat;

import cc.silk.event.impl.player.TickEvent;
import cc.silk.mixin.MinecraftClientAccessor;
import cc.silk.module.Category;
import cc.silk.module.Module;
import cc.silk.module.setting.BooleanSetting;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

public final class LungeSwap extends Module {

    private final BooleanSetting swapBack = new BooleanSetting("Swap Back", true);

    private boolean isSwapping   = false;
    private int     originalSlot = -1;
    private int     swapTicks    = 0;
    private static final int TARGET_TICKS = 3;

    public LungeSwap() {
        super("Lunge Swap", "Automatically swaps to lunge spear and attacks", -1, Category.MOVEMENT);
        addSettings(swapBack);
    }

    @Override
    public void onEnable() {
        if (isNull()) {
            setEnabled(false);
            return;
        }
        originalSlot = mc.player.getInventory().getSelectedSlot();
        int lungeSlot = findLungeSpearSlot();
        if (lungeSlot == -1) {
            setEnabled(false);
            return;
        }
        isSwapping = true;
        swapTicks = 0;
        mc.player.getInventory().setSelectedSlot(lungeSlot);
        if (mc.player.getAttackCooldownProgress(0.0f) >= 1.0f) {
            ((MinecraftClientAccessor) mc).invokeDoAttack();
            swapTicks = 1;
        }
    }

    @EventHandler
    private void onTick(TickEvent event) {
        if (!isSwapping || isNull()) return;
        swapTicks++;
        if (swapTicks >= TARGET_TICKS) {
            if (swapBack.getValue() && originalSlot != -1) {
                mc.player.getInventory().setSelectedSlot(originalSlot);
            }
            isSwapping = false;
            originalSlot = -1;
            swapTicks = 0;
            setEnabled(false);
        }
    }

    @Override
    public void onDisable() {
        isSwapping = false;
        originalSlot = -1;
        swapTicks = 0;
        super.onDisable();
    }

    private int findLungeSpearSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && isLungeSpear(stack)) return i;
        }
        return -1;
    }

    private boolean isLungeSpear(ItemStack stack) {
        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        if (!path.equals("spear") && !path.endsWith("_spear")) return false;
        return stack.getEnchantments().getEnchantments().stream()
                .anyMatch(e -> e.getIdAsString().toLowerCase().contains("lunge"));
    }
}
