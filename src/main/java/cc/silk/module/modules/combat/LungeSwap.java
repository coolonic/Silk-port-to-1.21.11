package cc.silk.module.modules.combat;

import cc.silk.event.impl.player.TickEvent;
import cc.silk.mixin.MinecraftClientAccessor;
import cc.silk.module.Category;
import cc.silk.module.Module;
import cc.silk.module.setting.NumberSetting;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

public final class LungeSwap extends Module {

    private final NumberSetting attackClicks = new NumberSetting("Attack Clicks", 1, 5, 2, 1);
    private final NumberSetting postDelay    = new NumberSetting("Post Delay (ms)", 0, 500, 100, 10);

    private int  previousSlot    = -1;
    private int  state           = 0;
    private int  tickDelay       = 0;
    private int  clicksRemaining = 0;
    private long stateTimestamp  = 0;

    public LungeSwap() {
        super("Lunge Swap", "Macro: spear lunge + attack + swap back", -1, Category.COMBAT);
        addSettings(attackClicks, postDelay);
    }

    @Override
    public void onEnable() {
        if (isNull()) {
            setEnabled(false);
            return;
        }
        state           = 1;
        tickDelay       = 0;
        clicksRemaining = 0;
        previousSlot    = -1;
        stateTimestamp  = 0;
    }

    @EventHandler
    private void onTick(TickEvent event) {
        if (isNull() || state == 0) return;

        if (tickDelay > 0) {
            tickDelay--;
            return;
        }

        switch (state) {
            case 1 -> {
                int spearSlot = findSpearSlot();
                if (spearSlot == -1) {
                    abort();
                    return;
                }
                previousSlot = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(spearSlot);
                tickDelay = 1;
                state = 2;
            }
            case 2 -> {
                ((MinecraftClientAccessor) mc).invokeDoItemUse();
                clicksRemaining = attackClicks.getValueInt();
                tickDelay = 1;
                state = 3;
            }
            case 3 -> {
                if (clicksRemaining > 0) {
                    ((MinecraftClientAccessor) mc).invokeDoAttack();
                    clicksRemaining--;
                }
                if (clicksRemaining <= 0) {
                    stateTimestamp = System.currentTimeMillis();
                    state = 4;
                }
            }
            case 4 -> {
                if (System.currentTimeMillis() - stateTimestamp >= postDelay.getValueInt()) {
                    restoreSlot();
                    state = 0;
                    setEnabled(false);
                }
            }
        }
    }

    private int findSpearSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && isSpear(stack)) return i;
        }
        return -1;
    }

    private boolean isSpear(ItemStack stack) {
        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        return path.equals("spear") || path.endsWith("_spear");
    }

    private void restoreSlot() {
        if (!isNull() && previousSlot != -1) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
            previousSlot = -1;
        }
    }

    private void abort() {
        restoreSlot();
        state           = 0;
        clicksRemaining = 0;
        setEnabled(false);
    }

    @Override
    public void onDisable() {
        if (state != 0) restoreSlot();
        state           = 0;
        clicksRemaining = 0;
        tickDelay       = 0;
        super.onDisable();
    }
}
