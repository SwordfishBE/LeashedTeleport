package net.leashedteleport.handler;

import net.leashedteleport.compat.OpenPartiesAndClaimsCompat.TeleportType;
import net.leashedteleport.config.LeashedTeleportConfig;
import net.leashedteleport.safety.TeleportSafetyChecker;
import net.leashedteleport.util.LoadedMobLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayDeque;
import java.util.Queue;

/** Bounded waits for terrain requested by the player's teleport, without chunk loads. */
public final class PendingLeashTeleports {
    private static final int TIMEOUT_TICKS = 100;
    private record Request(UUID player, ResourceKey<Level> source, ResourceKey<Level> target,
                           Vec3 sourcePos, Vec3 targetPos, TeleportType type, int deadline) {}
    private static final Map<UUID, Request> pending = new HashMap<>();
    private record LeadDrop(ResourceKey<Level> dimension, Vec3 position) {}
    private static final Queue<LeadDrop> leadDrops = new ArrayDeque<>();

    private PendingLeashTeleports() {}

    public static void schedule(ServerPlayer player, Mob mob, TeleportType type) {
        // A re-leashed mob can start another transfer before the old request is ticked.
        cancelMob(mob);
        mob.removeLeash();
        pending.put(mob.getUUID(), new Request(player.getUUID(), mob.level().dimension(),
                player.level().dimension(), mob.position(), player.position(), type,
                player.level().getServer().getTickCount() + TIMEOUT_TICKS));
    }

    public static void cancelPlayer(MinecraftServer server, UUID player) {
        for (var entry : List.copyOf(pending.entrySet())) {
            if (entry.getValue().player().equals(player)) cancel(server, entry.getKey(), entry.getValue());
        }
    }

    public static void prepareTeleport(ServerPlayer player, TeleportType type) {
        MinecraftServer server = player.level().getServer();
        for (var entry : List.copyOf(pending.entrySet())) {
            Request request = entry.getValue();
            if (!request.player().equals(player.getUUID())) continue;
            Mob mob = resolve(server, entry.getKey(), request);
            if (type != TeleportType.GENERAL || request.type() != TeleportType.GENERAL
                    || player.isRemoved() || !player.isAlive() || player.isShiftKeyDown()
                    || player.level().dimension() != request.target()
                    || player.position().distanceToSqr(request.targetPos()) > 4
                    || server.getTickCount() >= request.deadline()
                    || (mob != null && (mob.isRemoved() || !mob.isAlive() || mob.getLeashHolder() != null
                    || mob.position().distanceToSqr(request.sourcePos()) > 1))) {
                cancel(server, entry.getKey(), request);
            }
        }
    }

    public static void retargetPlayer(ServerPlayer player) {
        for (var entry : List.copyOf(pending.entrySet())) {
            Request request = entry.getValue();
            if (request.player().equals(player.getUUID())) {
                pending.put(entry.getKey(), new Request(request.player(), request.source(),
                        player.level().dimension(), request.sourcePos(), player.position(),
                        request.type(), request.deadline()));
            }
        }
    }

    public static void clear(MinecraftServer server) {
        try {
            for (var entry : List.copyOf(pending.entrySet())) cancel(server, entry.getKey(), entry.getValue());
        } finally {
            pending.clear();
            flushLeadDrops(server);
        }
    }

    public static void cancelMob(Mob mob) {
        Request request = pending.remove(mob.getUUID());
        // ENTITY_UNLOAD may fire after isRemoved becomes true; still return the detached lead.
        if (request != null) {
            // Entity-section iteration is still active during ENTITY_UNLOAD.
            leadDrops.add(new LeadDrop(mob.level().dimension(), mob.position()));
        }
    }

    private static Mob resolve(MinecraftServer server, UUID id, Request request) {
        ServerLevel source = server.getLevel(request.source());
        return LoadedMobLookup.find(source, id, request.sourcePos());
    }

    private static void cancel(MinecraftServer server, UUID id, Request request) {
        pending.remove(id);
        Mob mob = resolve(server, id, request);
        if (mob != null && !mob.isRemoved()) release(mob, server.getPlayerList().getPlayer(request.player()), true);
        else leadDrops.add(new LeadDrop(request.source(), request.sourcePos()));
    }

    private static void release(Mob mob, ServerPlayer player, boolean cancelled) {
        if (mob.isRemoved()) return;
        if (mob.getLeashHolder() != null) {
            // A new leash does not account for the original lead detached by this request.
            if (cancelled) mob.spawnAtLocation((ServerLevel) mob.level(), Items.LEAD);
            return;
        }
        double radius = LeashedTeleportConfig.get().getLeashRadius();
        if (player != null && !player.isRemoved() && player.level() == mob.level()
                && mob.distanceToSqr(player) <= radius * radius) {
            mob.setLeashedTo(player, true);
        } else {
            mob.spawnAtLocation((ServerLevel) mob.level(), Items.LEAD);
        }
    }

    public static void tick(MinecraftServer server) {
        for (var entry : List.copyOf(pending.entrySet())) {
            UUID mobId = entry.getKey();
            Request request = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(request.player());
            Mob mob = resolve(server, mobId, request);
            if (player == null || player.isRemoved() || !player.isAlive()
                    || player.level().dimension() != request.target()
                    || player.position().distanceToSqr(request.targetPos()) > 4) {
                cancel(server, mobId, request);
                continue;
            }
            if (server.getTickCount() >= request.deadline()) {
                cancel(server, mobId, request);
                player.sendSystemMessage(Component.literal(
                        "[LeashedTeleport] Transfer timed out while waiting for terrain or your leashed mob to become available."));
                continue;
            }
            if (mob == null) continue;
            if (mob.isRemoved() || !mob.isAlive() || mob.getLeashHolder() != null
                    || mob.position().distanceToSqr(request.sourcePos()) > 1) {
                cancel(server, mobId, request);
                continue;
            }
            boolean chunksReady = TeleportSafetyChecker.areSearchChunksLoaded(player.level(), mob, request.targetPos());
            if (!chunksReady && server.getTickCount() % 5 != 0) continue;
            if (!chunksReady
                    && TeleportSafetyChecker.findSafeLocation(player.level(), mob,
                    player.getX(), player.getY(), player.getZ()) == null) continue;
            pending.remove(mobId);
            try {
                LeashTeleportHandler.resumePendingMob(player, mob, request.type());
            } finally {
                // A denied or failed retry must return the detached lead exactly once.
                release(mob, player, false);
            }
        }
        flushLeadDrops(server);
    }

    private static void flushLeadDrops(MinecraftServer server) {
        LeadDrop drop;
        while ((drop = leadDrops.poll()) != null) {
            ServerLevel level = server.getLevel(drop.dimension());
            if (level == null) continue;
            Vec3 pos = drop.position();
            ItemEntity item = new ItemEntity(level, pos.x, pos.y, pos.z, new ItemStack(Items.LEAD));
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
    }
}
