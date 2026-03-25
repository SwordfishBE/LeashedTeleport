package net.leashedteleport.handler;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.leashedteleport.LeashedTeleportMod;
import net.leashedteleport.config.LeashedTeleportConfig;
import net.leashedteleport.mixin.LeashableEntityAccessor;
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
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LeashTeleportHandler {

    /**
     * Pending leash re-attachments: mob UUID -> player UUID.
     * When ENTITY_LOAD fires for a mob whose UUID is in this map,
     * we immediately re-attach the leash to the correct player.
     */
    private static final Map<UUID, UUID> PENDING_LEASH = new ConcurrentHashMap<>();

    /** Register the ENTITY_LOAD listener once at startup. */
    public static void registerEvents() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!(entity instanceof Mob mob)) return;
            UUID playerUUID = PENDING_LEASH.remove(mob.getUUID());
            if (playerUUID == null) return;

            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerUUID);
            if (player == null || player.isRemoved()) {
                LeashedTeleportMod.LOGGER.warn(
                    "[LeashedTeleport] Player {} not found when re-attaching leash to {}.",
                    playerUUID, mob.getUUID());
                return;
            }

            LeashedTeleportConfig config = LeashedTeleportConfig.get();
            mob.setLeashedTo(player, true);
            applyProtection(mob, config.getDamageResistanceDuration());
            LeashedTeleportMod.LOGGER.debug(
                "[LeashedTeleport] Leash re-attached to {} via ENTITY_LOAD event.", mob.getUUID());
        });
    }

    public static List<Mob> collectEligibleMobs(ServerPlayer player) {
        LeashedTeleportConfig config = LeashedTeleportConfig.get();
        double radius = config.getLeashRadius();
        ServerLevel level = (ServerLevel) player.level();

        AABB searchBox = player.getBoundingBox().inflate(radius);
        List<Mob> eligible = new ArrayList<>();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, searchBox)) {
            if (isEligible(mob, player, config)) {
                eligible.add(mob);
            }
        }
        return eligible;
    }

    private static boolean isEligible(Mob mob, ServerPlayer player, LeashedTeleportConfig config) {
        Entity holder = mob.getLeashHolder();
        if (!(holder instanceof ServerPlayer leashedTo)) return false;
        if (!leashedTo.getUUID().equals(player.getUUID())) return false;

        var entityTypeKey = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (entityTypeKey != null && config.getBlacklist().contains(entityTypeKey.toString())) return false;

        if (mob instanceof TamableAnimal tamable && tamable.isTame()) {
            Entity owner = tamable.getOwner();
            // Block if owner is a different player, OR if the owner is offline (null) —
            // we cannot verify ownership when the owner isn't loaded, so we refuse.
            if (owner == null || !owner.getUUID().equals(player.getUUID())) return false;
        }

        return true;
    }

    public static void teleportMobs(ServerPlayer player, ServerLevel originLevel, ServerLevel targetLevel,
                                    double x, double y, double z, List<Mob> mobs) {
        if (mobs.isEmpty()) return;

        LeashedTeleportConfig config = LeashedTeleportConfig.get();

        WorldBorder border = targetLevel.getWorldBorder();
        if (!border.isWithinBounds(BlockPos.containing(x, y, z))) {
            LeashedTeleportMod.LOGGER.debug("[LeashedTeleport] Destination outside world border. Skipping.");
            return;
        }

        // Safety check: find a safe location for the mobs
        BlockPos safePos = TeleportSafetyChecker.findSafeLocation(targetLevel, x, y, z);
        if (safePos == null) {
            LeashedTeleportMod.LOGGER.warn(
                "[LeashedTeleport] No safe location found near destination ({}, {}, {}). Teleport cancelled for {} mob(s).",
                x, y, z, mobs.size());
            player.sendSystemMessage(Component.literal(
                "[LeashedTeleport] Not enough space at destination – your leashed mob stayed behind."));
            return;
        }

        // Use the safe position instead of the original coordinates
        double safeX = safePos.getX() + 0.5;
        double safeY = safePos.getY();
        double safeZ = safePos.getZ() + 0.5;

        boolean crossDim = originLevel != targetLevel;
        if (crossDim && !config.isCrossDimensionTeleport()) {
            // Cross-dim disabled: silently drop the leash so the mob stays behind cleanly.
            // The player crosses dimensions; if they return, vanilla leash distance rules apply.
            for (Mob mob : mobs) {
                if (!mob.isRemoved()) {
                    ((LeashableEntityAccessor) mob).leashedteleport_setLeashData(null);
                }
            }
            LeashedTeleportMod.LOGGER.debug("[LeashedTeleport] Cross-dimension teleport disabled. Leash released.");
            return;
        }

        int count = 0;
        for (Mob mob : mobs) {
            if (mob.isRemoved()) continue;

            if (crossDim) {
                final UUID mobUUID = mob.getUUID();
                // Register pending leash before teleport so ENTITY_LOAD catches it
                PENDING_LEASH.put(mobUUID, player.getUUID());
                // Clear leashData to avoid corrupt "no attachment" crash on save
                ((LeashableEntityAccessor) mob).leashedteleport_setLeashData(null);
                applyProtection(mob, config.getDamageResistanceDuration());
                // Teleport mob to target level — ENTITY_LOAD event handles leash re-attach when mob arrives
                mob.teleportTo(targetLevel, safeX, safeY, safeZ, Set.of(), mob.getYRot(), mob.getXRot(), false);
            } else {
                mob.snapTo(safeX, safeY, safeZ, mob.getYRot(), mob.getXRot());
                applyProtection(mob, config.getDamageResistanceDuration());
            }
            count++;
        }

        if (count > 0) {
            LeashedTeleportMod.LOGGER.info("[LeashedTeleport] Teleported {} leashed mob(s) with player {} to safe location {}.",
                count, player.getName().getString(), safePos);
        }
    }

    private static void applyProtection(Mob mob, int durationTicks) {
        mob.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, durationTicks, 4, false, false));
        mob.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, durationTicks, 0, false, false));
    }
}
