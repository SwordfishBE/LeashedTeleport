package net.leashedteleport.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.leashedteleport.compat.OpenPartiesAndClaimsCompat;
import net.leashedteleport.handler.LeashTeleportHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.consume_effects.TeleportRandomlyConsumeEffect;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

// Wrap TpWithMe's mounted chorus handler as well as the vanilla consume effect.
@Mixin(value = TeleportRandomlyConsumeEffect.class, priority = 1100)
public class TeleportRandomlyConsumeEffectMixin {
    @WrapMethod(method = "apply(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Z")
    private boolean leashedteleport$handleChorusFruitTeleport(
            Level level, ItemStack stack, LivingEntity livingEntity, Operation<Boolean> original
    ) {
        if (!(level instanceof ServerLevel) || !(livingEntity instanceof ServerPlayer player)) {
            return original.call(level, stack, livingEntity);
        }
        LeashTeleportHandler.capturePendingTeleport(player, OpenPartiesAndClaimsCompat.TeleportType.CHORUS_FRUIT);
        try {
            boolean teleported = original.call(level, stack, livingEntity);
            if (teleported) LeashTeleportHandler.handlePendingTeleport(player);
            return teleported;
        } finally {
            LeashTeleportHandler.clearPendingTeleport(player);
        }
    }
}
