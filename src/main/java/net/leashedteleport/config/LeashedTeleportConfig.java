package net.leashedteleport.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.leashedteleport.LeashedTeleportMod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LeashedTeleportConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("leashedteleport.json");

    private static LeashedTeleportConfig instance = new LeashedTeleportConfig();

    public double leash_radius = 10.0;
    public boolean useLuckPerms = false;
    public boolean cross_dimension_teleport = true;
    public int damage_resistance_duration_ticks = 100;
    public List<String> entity_blacklist = new ArrayList<>();

    public static LeashedTeleportConfig get() { return instance; }
    public double getLeashRadius() { return leash_radius; }
    public boolean isUseLuckPerms() { return useLuckPerms; }
    public boolean isCrossDimensionTeleport() { return cross_dimension_teleport; }
    public int getDamageResistanceDuration() { return Math.max(1, damage_resistance_duration_ticks); }
    public List<String> getBlacklist() { return entity_blacklist != null ? entity_blacklist : new ArrayList<>(); }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            LeashedTeleportMod.LOGGER.info("[LeashedTeleport] No config found, creating default.");
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            LeashedTeleportConfig loaded = GSON.fromJson(reader, LeashedTeleportConfig.class);
            instance = (loaded != null) ? loaded : new LeashedTeleportConfig();
            save();
            LeashedTeleportMod.LOGGER.info("[LeashedTeleport] Config loaded successfully.");
        } catch (IOException e) {
            LeashedTeleportMod.LOGGER.error("[LeashedTeleport] Failed to load config: {}", e.getMessage());
            instance = new LeashedTeleportConfig();
        }
    }

    public static void save() {
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            LeashedTeleportMod.LOGGER.error("[LeashedTeleport] Failed to save config: {}", e.getMessage());
        }
    }
}
