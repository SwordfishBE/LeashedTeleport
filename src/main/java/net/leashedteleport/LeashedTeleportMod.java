package net.leashedteleport;

import net.fabricmc.api.ModInitializer;
import net.leashedteleport.command.LeashedTeleportCommand;
import net.leashedteleport.config.LeashedTeleportConfig;
import net.leashedteleport.handler.LeashTeleportHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LeashedTeleportMod implements ModInitializer {

    public static final String MOD_ID = "leashedteleport";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LeashedTeleportConfig.load();
        LeashedTeleportCommand.register();
        LeashTeleportHandler.registerEvents();
        LOGGER.info("[LeashedTeleport] Mod initialized. Version: {}", VERSION);
    }
}
