package net.leashedteleport.handler;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.leashedteleport.LeashedTeleportMod;
import net.leashedteleport.compat.OpenPartiesAndClaimsCompat;
import net.leashedteleport.config.LeashedTeleportConfig;
import net.leashedteleport.mixin.LeashableEntityAccessor;
import net.leashedteleport.permission.PermissionManager;
import net.leashedteleport.safety.TeleportSafetyChecker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LeashTeleportHandler {
    private record PendingTeleport(ServerLevel originLevel, List<Mob> mobs,
                                   OpenPartiesAndClaimsCompat.TeleportType teleportType) {}

    private static final Map<UUID, UUID> PENDING_LEASH = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingTeleport> PENDING_TELEPORTS = new ConcurrentHashMap<>();

    public static void registerEvents() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clearPlayer(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clearAllPending());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearAllPending());
        // All captures complete synchronously. Anything left at tick end was interrupted.
        ServerTickEvents.END_SERVER_TICK.register(server -> clearAllPending());
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!(entity instanceof Mob mob)) return;
            UUID playerUUID = PENDING_LEASH.remove(mob.getUUID());
            if (playerUUID == null) return;
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerUUID);
            if (player == null || player.isRemoved() || player.level() != level) return;
            mob.setLeashedTo(player, true);
            applyProtection(mob, LeashedTeleportConfig.get().getDamageResistanceDuration());
        });
    }

    public static void clearPlayer(UUID playerUuid) {
        PENDING_TELEPORTS.remove(playerUuid);
        PENDING_LEASH.entrySet().removeIf(entry -> entry.getValue().equals(playerUuid));
    }

    public static void clearAllPending() {
        PENDING_TELEPORTS.clear();
        PENDING_LEASH.clear();
    }

    public static List<Mob> collectEligibleMobs(ServerPlayer player) {
        LeashedTeleportConfig config = LeashedTeleportConfig.get();
        double radius = config.getLeashRadius();
        List<Mob> eligible = new ArrayList<>();
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(radius))) {
            if (mob.distanceToSqr(player) <= radius * radius && isEligible(mob, player, config)) {
                eligible.add(mob);
            }
        }
        return eligible;
    }

    public static void capturePendingTeleport(ServerPlayer player) {
        capturePendingTeleport(player, OpenPartiesAndClaimsCompat.TeleportType.GENERAL);
    }

    public static void capturePendingTeleport(ServerPlayer player, OpenPartiesAndClaimsCompat.TeleportType type) {
        if (PENDING_TELEPORTS.containsKey(player.getUUID())) return;
        // Keep the cause even for an empty/denied capture; generic hooks must not override it.
        PENDING_TELEPORTS.put(player.getUUID(), new PendingTeleport(player.level(), collectEligibleMobs(player), type));
    }

    public static void clearPendingTeleport(ServerPlayer player) {
        PENDING_TELEPORTS.remove(player.getUUID());
    }

    public static void handlePendingTeleport(ServerPlayer player) {
        PendingTeleport pending = PENDING_TELEPORTS.remove(player.getUUID());
        if (pending == null || player.isRemoved()) return;
        try {
            teleportMobs(player, pending);
        } finally {
            // A player who crossed dimensions cannot remain the holder of animals left behind.
            for (Mob mob : pending.mobs()) {
                if (!mob.isRemoved() && mob.level() != player.level() && mob.getLeashHolder() == player) {
                    mob.dropLeash();
                }
            }
        }
    }

    private static boolean isEligible(Mob mob, ServerPlayer player, LeashedTeleportConfig config) {
        if (mob.isRemoved() || mob.getLeashHolder() != player) return false;
        var typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (typeKey != null && config.getBlacklist().contains(typeKey.toString())) return false;
        if (mob instanceof TamableAnimal tamable && tamable.isTame()) {
            Entity owner = tamable.getOwner();
            if (owner == null || !owner.getUUID().equals(player.getUUID())) return false;
        }
        return true;
    }

    private static boolean canFollow(ServerPlayer player, OpenPartiesAndClaimsCompat.TeleportType type,
                                     LeashedTeleportConfig config) {
        if (!PermissionManager.canUse(player)) return false;
        return switch (type) {
            case GENERAL -> true;
            case ENDER_PEARL -> config.isEnderPearlTeleport() && PermissionManager.canEnderPearlTeleport(player);
            case CHORUS_FRUIT -> config.isChorusFruitTeleport() && PermissionManager.canChorusFruitTeleport(player);
        };
    }

    private static void teleportMobs(ServerPlayer player, PendingTeleport pending) {
        LeashedTeleportConfig config = LeashedTeleportConfig.get();
        if (!canFollow(player, pending.teleportType(), config)) return;
        ServerLevel targetLevel = player.level();
        boolean crossDim = pending.originLevel() != targetLevel;
        if (crossDim && (!config.isCrossDimensionTeleport() || !PermissionManager.canCrossDimensionTeleport(player))) return;

        boolean unsafe = false;
        boolean claimBlocked = false;
        for (Mob mob : pending.mobs()) {
            if (mob.level() != pending.originLevel() || !isEligible(mob, player, config)) continue;
            BlockPos safePos = TeleportSafetyChecker.findSafeLocation(targetLevel, mob,
                    player.getX(), player.getY(), player.getZ());
            if (safePos == null) {
                unsafe = true;
                continue;
            }
            if (config.isRespectOpenPartiesAndClaims()
                    && !OpenPartiesAndClaimsCompat.canTeleportInto(targetLevel, safePos, player, pending.teleportType())) {
                claimBlocked = true;
                continue;
            }
            if (crossDim) {
                teleportAcrossDimensions(mob, player, targetLevel, safePos, config);
            } else {
                mob.snapTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5, mob.getYRot(), mob.getXRot());
                applyProtection(mob, config.getDamageResistanceDuration());
            }
        }
        if (unsafe) player.sendSystemMessage(Component.literal(
                "[LeashedTeleport] Not enough safe space at destination; some leashed mobs stayed behind."));
        if (claimBlocked) player.sendSystemMessage(Component.literal(
                "[LeashedTeleport] Open Parties and Claims blocked your leashed mobs from entering this claim."));
    }

    private static void teleportAcrossDimensions(Mob mob, ServerPlayer player, ServerLevel target,
                                                 BlockPos pos, LeashedTeleportConfig config) {
        UUID mobId = mob.getUUID();
        var originalLeash = mob.getLeashData();
        boolean teleported = false;
        PENDING_LEASH.put(mobId, player.getUUID());
        try {
            mob.removeLeash();
            applyProtection(mob, config.getDamageResistanceDuration());
            teleported = mob.teleportTo(target, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                    Set.of(), mob.getYRot(), mob.getXRot(), false);
            if (!teleported) {
                LeashedTeleportMod.LOGGER.warn("[{}] Failed to teleport leashed mob {}.",
                        LeashedTeleportMod.MOD_NAME, mobId);
            }
        } finally {
            PENDING_LEASH.remove(mobId);
            if (!teleported && !mob.isRemoved()) {
                // Restore the data only to let vanilla detach/drop correctly on failure.
                ((LeashableEntityAccessor) mob).leashedteleport_setLeashData(originalLeash);
                if (!player.isRemoved() && mob.level() == player.level()) {
                    mob.setLeashedTo(player, true);
                } else {
                    mob.dropLeash();
                }
            }
        }
    }

    private static void applyProtection(Mob mob, int durationTicks) {
        mob.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, durationTicks, 4, false, false));
        mob.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, durationTicks, 0, false, false));
    }
}
