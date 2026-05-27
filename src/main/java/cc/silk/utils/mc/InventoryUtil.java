package cc.silk.utils.mc;

import cc.silk.utils.IMinecraft;
import lombok.experimental.UtilityClass;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.TagKey;

import java.util.Objects;

@UtilityClass
public final class InventoryUtil implements IMinecraft {
    public static void swapToSlot(Item item) {
        for (byte i = 0; i < 9; i++) {
            assert mc.player != null;
            var stack = mc.player.getInventory().getStack(i);

            if (stack.isEmpty()) continue;
            if (stack.getItem().equals(item)) {
                mc.player.getInventory().setSelectedSlot(i);
                return;
            }
        }
    }

    public static boolean hasItem(Item item) {
        for (byte i = 0; i < Objects.requireNonNull(mc.player).getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == item) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasWeapon(Class<? extends Item> weaponClass) {
        for (byte i = 0; i < Objects.requireNonNull(mc.player).getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (weaponClass.isInstance(stack.getItem())) {
                return true;
            }
        }
        return false;
    }

    public static void swapToWeapon(Class<? extends Item> weaponClass) {
        for (byte i = 0; i < Objects.requireNonNull(mc.player).getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (weaponClass.isInstance(stack.getItem())) {
                mc.player.getInventory().setSelectedSlot(i);
                break;
            }
        }
    }

    // 1.21.11 — SwordItem/MiningToolItem were removed; use item tags instead.
    public static boolean hasWeapon(TagKey<Item> tag) {
        for (byte i = 0; i < Objects.requireNonNull(mc.player).getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).isIn(tag)) return true;
        }
        return false;
    }

    public static void swapToWeapon(TagKey<Item> tag) {
        for (byte i = 0; i < Objects.requireNonNull(mc.player).getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isIn(tag)) {
                mc.player.getInventory().setSelectedSlot(i);
                break;
            }
        }
    }

}

