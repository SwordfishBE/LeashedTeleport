package net.leashedteleport.permission;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.loader.api.FabricLoader;
import net.leashedteleport.LeashedTeleportMod;
import net.leashedteleport.config.LeashedTeleportConfig;
import net.minecraft.server.level.ServerPlayer;

public final class PermissionManager {

    public static final String USE_PERMISSION = "leashedteleport.use";
    public static final String CROSS_DIMENSION_TELEPORT_PERMISSION =
            "leashedteleport.crossdimensionteleport";
    public static final String ENDER_PEARL_TELEPORT_PERMISSION =
            "leashedteleport.enderpearlteleport";
    public static final String CHORUS_FRUIT_TELEPORT_PERMISSION =
            "leashedteleport.chorusfruitteleport";

    private static boolean luckPermsInstalled;
    private static boolean luckPermsActive;

    private PermissionManager() {
    }

    public static void refreshState() {
        luckPermsInstalled = FabricLoader.getInstance().isModLoaded("luckperms");
        luckPermsActive = LeashedTeleportConfig.get().isUseLuckPerms() && luckPermsInstalled;

        if (luckPermsActive) {
            LeashedTeleportMod.LOGGER.debug("[{}] LuckPerms permissions are active.", LeashedTeleportMod.MOD_NAME);
            return;
        }

        if (LeashedTeleportConfig.get().isUseLuckPerms()) {
            LeashedTeleportMod.LOGGER.warn(
                    "[{}] useLuckPerms is enabled, but LuckPerms is not installed. Everyone can use Leashed Teleport.",
                    LeashedTeleportMod.MOD_NAME);
            return;
        }

        LeashedTeleportMod.LOGGER.debug("[{}] LuckPerms integration is disabled in the config.", LeashedTeleportMod.MOD_NAME);
    }

    public static boolean isLuckPermsInstalled() {
        return luckPermsInstalled;
    }

    public static boolean isLuckPermsActive() {
        return luckPermsActive;
    }

    public static boolean canUse(ServerPlayer player) {
        return !luckPermsActive || Permissions.check(player, USE_PERMISSION, false);
    }

    public static boolean canCrossDimensionTeleport(ServerPlayer player) {
        return !luckPermsActive || Permissions.check(player, CROSS_DIMENSION_TELEPORT_PERMISSION, false);
    }

    public static boolean canEnderPearlTeleport(ServerPlayer player) {
        return !luckPermsActive || Permissions.check(player, ENDER_PEARL_TELEPORT_PERMISSION, false);
    }

    public static boolean canChorusFruitTeleport(ServerPlayer player) {
        return !luckPermsActive || Permissions.check(player, CHORUS_FRUIT_TELEPORT_PERMISSION, false);
    }
}
