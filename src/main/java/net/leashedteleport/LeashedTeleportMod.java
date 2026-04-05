package net.leashedteleport;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.leashedteleport.command.LeashedTeleportCommand;
import net.leashedteleport.config.LeashedTeleportConfig;
import net.leashedteleport.handler.LeashTeleportHandler;
import net.leashedteleport.permission.PermissionManager;
import net.leashedteleport.util.ModrinthUpdateChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LeashedTeleportMod implements ModInitializer {

    public static final String MOD_ID = "leashedteleport";
    public static final String MOD_NAME = FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            .map(container -> container.getMetadata().getName())
            .orElse("Leashed Teleport");
    public static final String VERSION = FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LeashedTeleportConfig.load();
        PermissionManager.refreshState();
        LeashedTeleportCommand.register();
        LeashTeleportHandler.registerEvents();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> ModrinthUpdateChecker.checkOnceAsync());
        LOGGER.info("[{}] Mod initialized. Version: {}", MOD_NAME, VERSION);
    }

    public static LeashedTeleportConfig loadConfigForEditing() {
        return LeashedTeleportConfig.loadCopy();
    }

    public static void applyEditedConfig(LeashedTeleportConfig editedConfig) {
        LeashedTeleportConfig normalizedConfig = editedConfig.copy();
        normalizedConfig.normalize();
        LeashedTeleportConfig.replace(normalizedConfig);
        PermissionManager.refreshState();
    }
}
