package net.leashedteleport.mixin;

import net.leashedteleport.config.LeashedTeleportConfig;
import net.leashedteleport.handler.LeashTeleportHandler;
import net.leashedteleport.permission.PermissionManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.consume_effects.TeleportRandomlyConsumeEffect;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TeleportRandomlyConsumeEffect.class)
public class TeleportRandomlyConsumeEffectMixin {

    @Inject(
            method = "apply(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("HEAD")
    )
    private void leashedteleport$captureChorusFruitTeleport(
            Level level,
            ItemStack stack,
            LivingEntity livingEntity,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!(level instanceof ServerLevel)) {
            return;
        }

        if (!(livingEntity instanceof ServerPlayer player)) {
            return;
        }

        if (!LeashedTeleportConfig.get().isChorusFruitTeleport()) {
            return;
        }

        if (!PermissionManager.canChorusFruitTeleport(player)) {
            return;
        }

        LeashTeleportHandler.capturePendingTeleport(player);
    }

    @Inject(
            method = "apply(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("RETURN")
    )
    private void leashedteleport$handleChorusFruitTeleport(
            Level level,
            ItemStack stack,
            LivingEntity livingEntity,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (!(livingEntity instanceof ServerPlayer player)) {
            return;
        }

        if (!LeashedTeleportConfig.get().isChorusFruitTeleport()) {
            return;
        }

        if (!PermissionManager.canChorusFruitTeleport(player)) {
            return;
        }

        if (!cir.getReturnValue()) {
            LeashTeleportHandler.clearPendingTeleport(player);
            return;
        }

        LeashTeleportHandler.schedulePendingTeleport(player, serverLevel, player.getX(), player.getY(), player.getZ());
    }
}
