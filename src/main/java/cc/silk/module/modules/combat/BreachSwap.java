package cc.silk.module.modules.combat;

import cc.silk.event.impl.player.AttackEvent;
import cc.silk.event.impl.player.TickEvent;
import cc.silk.mixin.MinecraftClientAccessor;
import cc.silk.module.Category;
import cc.silk.module.Module;
import cc.silk.module.setting.BooleanSetting;
import cc.silk.module.setting.NumberSetting;
import cc.silk.utils.mc.EnchantmentUtil;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Identifier;

public final class BreachSwap extends Module {

    private final NumberSetting switchDelay  = new NumberSetting("Switch Delay",  10,  100, 30,  1);
    private final NumberSetting yThreshold   = new NumberSetting("Y Threshold",   0.0, 5.0, 1.5, 0.1);
    private final BooleanSetting onlyOnGround = new BooleanSetting("Only on ground", true);
    private final BooleanSetting silentSwap   = new BooleanSetting("Silent Swap",   true);
    private final BooleanSetting swordOnly    = new BooleanSetting("Sword Only",    true);

    private int originalSlot = -1;
    private boolean shouldSwitchBack = false;
    private long switchTime = 0;
    private boolean isSwappingAttack = false;
    private double lastGroundY = Double.MIN_VALUE;

    public BreachSwap() {
        super("Breach Swap", "Switches to a Breach enchanted mace when attacking", Category.COMBAT);
        addSettings(switchDelay, yThreshold, onlyOnGround, silentSwap, swordOnly);
    }

    @EventHandler
    public void onAttack(AttackEvent event) {
        if (isNull() || isSwappingAttack)
            return;
        if (ShieldBreaker.breakingShield)
            return;
        if (!(event.getTarget() instanceof LivingEntity))
            return;

        if (swordOnly.getValue()) {
            ItemStack held = mc.player.getMainHandStack();
            if (held.getItem() instanceof AxeItem) return;
            if (!held.isIn(ItemTags.SWORDS)) return;
        }

        // Unified air check: when off ground, allow swap only within yThreshold of last ground.
        // onlyOnGround additionally requires a known ground reference (blocks firing if never touched ground).
        if (!mc.player.isOnGround()) {
            if (onlyOnGround.getValue() && lastGroundY == Double.MIN_VALUE) return;
            if (lastGroundY != Double.MIN_VALUE && mc.player.getY() - lastGroundY > yThreshold.getValue()) return;
        }

        int maceSlot = findBreachMaceSlot();
        if (maceSlot == -1) return;

        if (originalSlot == -1)
            originalSlot = mc.player.getInventory().getSelectedSlot();

        if (silentSwap.getValue()) {
            int prevSlot = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(maceSlot);

            isSwappingAttack = true;
            ((MinecraftClientAccessor) mc).invokeDoAttack();
            isSwappingAttack = false;

            mc.player.getInventory().setSelectedSlot(prevSlot);
            originalSlot = -1;
        } else {
            mc.player.getInventory().setSelectedSlot(maceSlot);

            isSwappingAttack = true;
            ((MinecraftClientAccessor) mc).invokeDoAttack();
            isSwappingAttack = false;

            shouldSwitchBack = true;
            switchTime = System.currentTimeMillis();
        }
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (isNull()) return;

        if (mc.player.isOnGround()) {
            lastGroundY = mc.player.getY();
        }

        if (ShieldBreaker.breakingShield) return;

        if (shouldSwitchBack && System.currentTimeMillis() - switchTime >= switchDelay.getValue()) {
            if (originalSlot != -1) {
                mc.player.getInventory().setSelectedSlot(originalSlot);
                originalSlot = -1;
            }
            shouldSwitchBack = false;
        }
    }

    private int findBreachMaceSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            Item item = stack.getItem();
            if (item instanceof MaceItem && hasBreach(stack)) return i;
        }
        return -1;
    }

    private boolean hasBreach(ItemStack stack) {
        RegistryKey<net.minecraft.enchantment.Enchantment> breachKey =
                RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of("minecraft", "breach"));
        return EnchantmentUtil.hasEnchantment(stack, mc.world, breachKey);
    }

    @Override
    public void onEnable() {
        lastGroundY = Double.MIN_VALUE;
        originalSlot = -1;
        shouldSwitchBack = false;
        isSwappingAttack = false;
    }

    @Override
    public void onDisable() {
        if (!isNull() && originalSlot != -1) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
        }
        originalSlot = -1;
        shouldSwitchBack = false;
        isSwappingAttack = false;
    }
}
