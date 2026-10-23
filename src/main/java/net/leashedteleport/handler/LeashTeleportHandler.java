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
import net.leashedteleport.util.LoadedMobLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
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
    // Positions change before the entity-section index catches up within the same tick.
    private static final Map<UUID, Map<UUID, Vec3>> RECENT_MOBS = new java.util.HashMap<>();

    public static void registerEvents() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PendingLeashTeleports.cancelPlayer(server, handler.player.getUUID());
            clearPlayer(handler.player.getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            PendingLeashTeleports.clear(server);
            clearAllPending();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            PendingLeashTeleports.clear(server);
            clearAllPending();
        });
        // All captures complete synchronously. Anything left at tick end was interrupted.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            clearAllPending();
            PendingLeashTeleports.tick(server);
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            // Losing tracking visibility is not an entity removal.
            if (entity instanceof Mob mob && mob.isRemoved()) PendingLeashTeleports.onEntityRemoved(mob);
        });
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
        RECENT_MOBS.remove(playerUuid);
        PENDING_TELEPORTS.remove(playerUuid);
        PENDING_LEASH.entrySet().removeIf(entry -> entry.getValue().equals(playerUuid));
    }

    public static void clearAllPending() {
        RECENT_MOBS.clear();
        PENDING_TELEPORTS.clear();
        PENDING_LEASH.clear();
    }

    public static List<Mob> collectEligibleMobs(ServerPlayer player) {
        LeashedTeleportConfig config = LeashedTeleportConfig.get();
        double radius = config.getLeashRadius();
        List<Mob> eligible = new ArrayList<>();
        Set<Mob> candidates = new java.util.LinkedHashSet<>(
                player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(radius)));
        for (var entry : RECENT_MOBS.getOrDefault(player.getUUID(), Map.of()).entrySet()) {
            Mob mob = LoadedMobLookup.find(player.level(), entry.getKey(), entry.getValue());
            if (mob != null) candidates.add(mob);
        }
        for (Mob mob : candidates) {
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
        // Chorus may fail without moving the player; preserve terrain waits until success.
        if (type != OpenPartiesAndClaimsCompat.TeleportType.CHORUS_FRUIT) {
            PendingLeashTeleports.prepareTeleport(player, type);
        }
        // Keep the cause even for an empty/denied capture; generic hooks must not override it.
        PENDING_TELEPORTS.put(player.getUUID(), new PendingTeleport(player.level(), collectEligibleMobs(player), type));
    }

    public static void clearPendingTeleport(ServerPlayer player) {
        PENDING_TELEPORTS.remove(player.getUUID());
    }

    public static void handlePendingTeleport(ServerPlayer player) {
        PendingTeleport pending = PENDING_TELEPORTS.remove(player.getUUID());
        if (pending == null || player.isRemoved()) return;
        if (pending.teleportType() == OpenPartiesAndClaimsCompat.TeleportType.CHORUS_FRUIT) {
            PendingLeashTeleports.cancelPlayer(player.level().getServer(), player.getUUID());
        } else {
            PendingLeashTeleports.retargetPlayer(player);
        }
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
        return isEligible(mob, player, config, true);
    }

    private static boolean isEligible(Mob mob, ServerPlayer player, LeashedTeleportConfig config, boolean requireLeash) {
        if (mob.isRemoved() || (requireLeash ? mob.getLeashHolder() != player : mob.getLeashHolder() != null)) return false;
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
        teleportMobs(player, pending, true);
    }

    public static void resumePendingMob(ServerPlayer player, Mob mob, OpenPartiesAndClaimsCompat.TeleportType type) {
        teleportMobs(player, new PendingTeleport((ServerLevel) mob.level(), List.of(mob), type), false);
    }

    private static void teleportMobs(ServerPlayer player, PendingTeleport pending, boolean requireLeash) {
        LeashedTeleportConfig config = LeashedTeleportConfig.get();
        if (!canFollow(player, pending.teleportType(), config)) return;
        ServerLevel targetLevel = player.level();
        boolean crossDim = pending.originLevel() != targetLevel;
        if (crossDim && (!config.isCrossDimensionTeleport() || !PermissionManager.canCrossDimensionTeleport(player))) return;

        boolean unsafe = false;
        boolean claimBlocked = false;
        for (Mob mob : pending.mobs()) {
            if (mob.level() != pending.originLevel() || !isEligible(mob, player, config, requireLeash)) continue;
            Vec3 safePos = TeleportSafetyChecker.findSafeLocation(targetLevel, mob,
                    player.getX(), player.getY(), player.getZ());
            if (safePos == null) {
                if (requireLeash && !TeleportSafetyChecker.areSearchChunksLoaded(targetLevel, mob, player.position())) {
                    PendingLeashTeleports.schedule(player, mob, pending.teleportType());
                    continue;
                }
                unsafe = true;
                continue;
            }
            if (config.isRespectOpenPartiesAndClaims()
                    && !canEnterDestination(targetLevel, mob, safePos, player, pending.teleportType())) {
                claimBlocked = true;
                continue;
            }
            if (crossDim) {
                teleportAcrossDimensions(mob, player, targetLevel, safePos, config);
            } else {
                mob.snapTo(safePos.x, safePos.y, safePos.z, mob.getYRot(), mob.getXRot());
                if (mob.getLeashHolder() != player) mob.setLeashedTo(player, true);
                applyProtection(mob, config.getDamageResistanceDuration());
            }
            RECENT_MOBS.computeIfAbsent(player.getUUID(), id -> new java.util.HashMap<>()).put(mob.getUUID(), safePos);
        }
        if (unsafe) player.sendSystemMessage(Component.literal(
                "[LeashedTeleport] Not enough safe space at destination; some leashed mobs stayed behind."));
        if (claimBlocked) player.sendSystemMessage(Component.literal(
                "[LeashedTeleport] Open Parties and Claims blocked your leashed mobs from entering this claim."));
    }

    private static boolean canEnterDestination(ServerLevel level, Mob mob, Vec3 pos,
                                               ServerPlayer player, OpenPartiesAndClaimsCompat.TeleportType type) {
        var box = mob.getBoundingBox().move(pos.x - mob.getX(), pos.y - mob.getY(), pos.z - mob.getZ());
        for (int x = Mth.floor(box.minX) >> 4; x <= Mth.floor(Math.nextDown(box.maxX)) >> 4; x++) {
            for (int z = Mth.floor(box.minZ) >> 4; z <= Mth.floor(Math.nextDown(box.maxZ)) >> 4; z++) {
                if (!OpenPartiesAndClaimsCompat.canTeleportInto(level,
                        new BlockPos(x << 4, Mth.floor(pos.y), z << 4), player, type)) return false;
            }
        }
        return true;
    }

    private static void teleportAcrossDimensions(Mob mob, ServerPlayer player, ServerLevel target,
                                                 Vec3 pos, LeashedTeleportConfig config) {
        UUID mobId = mob.getUUID();
        var originalLeash = mob.getLeashData();
        boolean teleported = false;
        PENDING_LEASH.put(mobId, player.getUUID());
        try {
            mob.removeLeash();
            applyProtection(mob, config.getDamageResistanceDuration());
            teleported = mob.teleportTo(target, pos.x, pos.y, pos.z,
                    Set.of(), mob.getYRot(), mob.getXRot(), false);
            if (teleported) {
                // ENTITY_LOAD is not guaranteed until a hidden destination becomes tracked.
                Mob transferred = LoadedMobLookup.find(target, mobId, pos);
                if (transferred != null && !transferred.isRemoved() && transferred.getLeashHolder() == null
                        && !player.isRemoved() && player.level() == target) {
                    transferred.setLeashedTo(player, true);
                    applyProtection(transferred, config.getDamageResistanceDuration());
                }
            }
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
