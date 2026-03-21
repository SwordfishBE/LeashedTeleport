package net.leashedteleport.mixin;

import net.leashedteleport.handler.LeashTeleportHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTeleportMixin {

    private static final Map<UUID, List<Mob>>   PENDING_MOBS  = new HashMap<>();
    private static final Map<UUID, ServerLevel> ORIGIN_LEVELS = new HashMap<>();

    @Inject(
        method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z",
        at = @At("HEAD")
    )
    private void leashedteleport_capturePreTeleport(
            ServerLevel level, double x, double y, double z,
            Set<Relative> relativeMovements, float yRot, float xRot, boolean isPortalCause,
            CallbackInfoReturnable<Boolean> cir) {

        ServerPlayer self = (ServerPlayer) (Object) this;
        UUID uuid = self.getUUID();
        ORIGIN_LEVELS.put(uuid, (ServerLevel) self.level());
        PENDING_MOBS.put(uuid, new ArrayList<>(LeashTeleportHandler.collectEligibleMobs(self)));
    }

    @Inject(
        method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z",
        at = @At("RETURN")
    )
    private void leashedteleport_handlePostTeleport(
            ServerLevel level, double x, double y, double z,
            Set<Relative> relativeMovements, float yRot, float xRot, boolean isPortalCause,
            CallbackInfoReturnable<Boolean> cir) {

        ServerPlayer self = (ServerPlayer) (Object) this;
        UUID uuid = self.getUUID();

        if (!cir.getReturnValue()) {
            PENDING_MOBS.remove(uuid);
            ORIGIN_LEVELS.remove(uuid);
            return;
        }

        List<Mob>   mobs        = PENDING_MOBS.remove(uuid);
        ServerLevel originLevel = ORIGIN_LEVELS.remove(uuid);
        if (mobs == null || mobs.isEmpty() || originLevel == null) return;

        LeashTeleportHandler.teleportMobs(self, originLevel, level, x, y, z, mobs);
    }
}
