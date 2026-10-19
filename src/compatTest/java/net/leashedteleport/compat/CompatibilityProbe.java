package net.leashedteleport.compat;

import com.mojang.authlib.GameProfile;
import me.lucko.luckperms.common.api.implementation.ApiUser;
import me.lucko.luckperms.fabric.model.MixinUser;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.leashedteleport.config.LeashedTeleportConfig;
import net.leashedteleport.permission.PermissionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.player.config.PlayerConfig;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigOptionSpecAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CompatibilityProbe implements ModInitializer {
    private final List<String> passed = new ArrayList<>();

    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                runChecks(server);
                Files.writeString(Path.of("compat-result.txt"), "PASS\n" + String.join("\n", passed));
            } catch (Throwable failure) {
                failure.printStackTrace();
                try {
                    Files.writeString(Path.of("compat-result.txt"), "FAIL\n" + failure + "\n" + String.join("\n", passed));
                } catch (Exception ignored) { }
            } finally {
                server.halt(false);
            }
        });
    }

    private void runChecks(MinecraftServer server) throws Exception {
        var config = LeashedTeleportConfig.get();
        config.useLuckPerms = true;
        config.respectOpenPartiesAndClaims = true;
        PermissionManager.refreshState();
        check("LuckPerms detected and activated", PermissionManager.isLuckPermsActive());
        UUID uuid = UUID.nameUUIDFromBytes("LeashedTeleportCompatProbe".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        User user = LuckPermsProvider.get().getUserManager().loadUser(uuid, "LeashProbe").get(30, java.util.concurrent.TimeUnit.SECONDS);
        var player = new ServerPlayer(server, server.overworld(), new GameProfile(uuid, "LeashProbe"), ClientInformation.createDefault());
        // The probe player has no network login; perform LuckPerms' normal login initialization.
        ((MixinUser) player).luckperms$initializePermissions(ApiUser.cast(user));

        setPermission(user, PermissionManager.USE_PERMISSION, false);
        check("General use denied", !PermissionManager.canUse(player));
        setPermission(user, PermissionManager.USE_PERMISSION, true);
        check("General use granted", PermissionManager.canUse(player));
        for (String node : List.of(PermissionManager.CROSS_DIMENSION_TELEPORT_PERMISSION,
                PermissionManager.ENDER_PEARL_TELEPORT_PERMISSION, PermissionManager.CHORUS_FRUIT_TELEPORT_PERMISSION)) {
            setPermission(user, node, false);
            check(node + " denied", !permission(player, node));
            setPermission(user, node, true);
            check(node + " granted", permission(player, node));
        }
        config.useLuckPerms = false;
        PermissionManager.refreshState();
        setPermission(user, PermissionManager.USE_PERMISSION, false);
        check("Config disables LuckPerms checks", PermissionManager.canUse(player));
        config.useLuckPerms = true;
        PermissionManager.refreshState();

        var api = OpenPACServerAPI.get(server);
        check("OPAC server API initialized", api != null);
        var level = server.overworld();
        BlockPos wilderness = new BlockPos(4096, 64, 4096);
        check("Wilderness allowed", allowed(player, wilderness, OpenPartiesAndClaimsCompat.TeleportType.GENERAL));
        BlockPos claimPos = new BlockPos(2048, 64, 2048);
        api.getServerClaimsManager().claim(level.dimension().identifier(), PlayerConfig.SERVER_CLAIM_UUID,
                0, claimPos.getX() >> 4, claimPos.getZ() >> 4, false);
        check("Live claim created", api.getServerClaimsManager().get(level.dimension().identifier(), claimPos) != null);
        IPlayerConfigAPI claimConfig = api.getPlayerConfigManager().getServerClaimConfig();
        setOption(claimConfig, PlayerConfigOptions.PROTECT_CLAIMED_CHUNKS, true);
        setOption(claimConfig, PlayerConfigOptions.FULL_ACCESS, "N");
        check("Foreign claim blocks general teleport", !allowed(player, claimPos, OpenPartiesAndClaimsCompat.TeleportType.GENERAL));
        setOption(claimConfig, PlayerConfigOptions.CLAIM_EXCEPTION_CHORUS_FRUIT, "N");
        check("Chorus entry denied", !allowed(player, claimPos, OpenPartiesAndClaimsCompat.TeleportType.CHORUS_FRUIT));
        setOption(claimConfig, PlayerConfigOptions.CLAIM_EXCEPTION_CHORUS_FRUIT, "E");
        check("Chorus exception grants entry", allowed(player, claimPos, OpenPartiesAndClaimsCompat.TeleportType.CHORUS_FRUIT));

        @SuppressWarnings("unchecked")
        IPlayerConfigOptionSpecAPI<String> pearl = (IPlayerConfigOptionSpecAPI<String>) api.getPlayerConfigManager()
                .getOptionForId("claims.protection.exceptions.groups.entity.barrier.Ender_Pearls");
        check("Ender pearl option found", pearl != null);
        setOption(claimConfig, pearl, "N");
        check("Pearl entry denied", !allowed(player, claimPos, OpenPartiesAndClaimsCompat.TeleportType.ENDER_PEARL));
        setOption(claimConfig, pearl, "E");
        check("Pearl exception grants entry", allowed(player, claimPos, OpenPartiesAndClaimsCompat.TeleportType.ENDER_PEARL));
        setOption(claimConfig, PlayerConfigOptions.FULL_ACCESS, "E");
        check("Claim full access grants general entry", allowed(player, claimPos, OpenPartiesAndClaimsCompat.TeleportType.GENERAL));
    }

    private static boolean allowed(ServerPlayer player, BlockPos pos, OpenPartiesAndClaimsCompat.TeleportType type) {
        return OpenPartiesAndClaimsCompat.canTeleportInto(player.level(), pos, player, type);
    }

    private static <T> void setOption(IPlayerConfigAPI config, IPlayerConfigOptionSpecAPI<T> option, T value) {
        var result = config.tryToSet(option, value);
        if (!value.equals(config.getEffective(option))) throw new AssertionError("Cannot set OPAC option: " + result);
    }

    private static void setPermission(User user, String node, boolean value) {
        user.data().clear(existing -> existing.getKey().equals(node));
        user.data().add(Node.builder(node).value(value).build());
        user.getCachedData().invalidate();
    }

    private static boolean permission(ServerPlayer player, String node) {
        return switch (node) {
            case PermissionManager.CROSS_DIMENSION_TELEPORT_PERMISSION -> PermissionManager.canCrossDimensionTeleport(player);
            case PermissionManager.ENDER_PEARL_TELEPORT_PERMISSION -> PermissionManager.canEnderPearlTeleport(player);
            default -> PermissionManager.canChorusFruitTeleport(player);
        };
    }

    private void check(String label, boolean condition) {
        if (!condition) throw new AssertionError(label);
        passed.add(label);
        System.out.println("COMPAT PASS: " + label);
    }
}
