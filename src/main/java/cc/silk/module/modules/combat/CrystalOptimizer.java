package cc.silk.module.modules.combat;

import cc.silk.event.impl.player.AttackEvent;
import cc.silk.event.impl.player.DoAttackEvent;
import cc.silk.module.Category;
import cc.silk.module.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public final class CrystalOptimizer extends Module {

    public CrystalOptimizer() {
        super("Crystal Optimizer", "Makes crystals disappear faster client-side for quicker placement.", -1, Category.COMBAT);
    }

    @EventHandler
    private void onAttackEvent(DoAttackEvent event) {
        if (isNull()) return;
        if (mc.crosshairTarget == null) return;

        if (mc.crosshairTarget.getType() != HitResult.Type.ENTITY) return;
        if (!(mc.crosshairTarget instanceof EntityHitResult hit)) return;

        Entity target = hit.getEntity();
        if (!(target instanceof EndCrystalEntity crystal)) return;

        StatusEffectInstance weakness = mc.player.getStatusEffect(StatusEffects.WEAKNESS);
        StatusEffectInstance strength = mc.player.getStatusEffect(StatusEffects.STRENGTH);
        ItemStack mainHand = mc.player.getMainHandStack();

        boolean canAttack =
                (weakness == null)
                        || (strength != null && strength.getAmplifier() > weakness.getAmplifier())
                        || mainHand.isIn(ItemTags.PICKAXES)
                        || mainHand.isIn(ItemTags.AXES)
                        || mainHand.isIn(ItemTags.SHOVELS)
                        || mainHand.isIn(ItemTags.HOES)
                        || mainHand.isIn(ItemTags.SWORDS);

        if (!canAttack) return;

        crystal.setRemoved(Entity.RemovalReason.KILLED);
        crystal.onRemoved();
    }
}
