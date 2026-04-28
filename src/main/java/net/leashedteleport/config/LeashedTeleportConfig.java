package net.leashedteleport.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import net.leashedteleport.LeashedTeleportMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LeashedTeleportConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("leashedteleport.json");

    private static LeashedTeleportConfig instance = new LeashedTeleportConfig();

    public double leash_radius = 10.0;
    public boolean useLuckPerms = false;
    public boolean cross_dimension_teleport = true;
    public boolean ender_pearl_teleport = true;
    public boolean chorus_fruit_teleport = true;
    public boolean respectOpenPartiesAndClaims = true;
    public int damage_resistance_duration_ticks = 100;
    public List<String> entity_blacklist = new ArrayList<>();

    public static LeashedTeleportConfig get() { return instance; }
    public double getLeashRadius() { return leash_radius; }
    public boolean isUseLuckPerms() { return useLuckPerms; }
    public boolean isCrossDimensionTeleport() { return cross_dimension_teleport; }
    public boolean isEnderPearlTeleport() { return ender_pearl_teleport; }
    public boolean isChorusFruitTeleport() { return chorus_fruit_teleport; }
    public boolean isRespectOpenPartiesAndClaims() { return respectOpenPartiesAndClaims; }
    public int getDamageResistanceDuration() { return Math.max(1, damage_resistance_duration_ticks); }
    public List<String> getBlacklist() { return entity_blacklist != null ? entity_blacklist : new ArrayList<>(); }
    public LeashedTeleportConfig copy() {
        LeashedTeleportConfig copy = new LeashedTeleportConfig();
        copy.leash_radius = leash_radius;
        copy.useLuckPerms = useLuckPerms;
        copy.cross_dimension_teleport = cross_dimension_teleport;
        copy.ender_pearl_teleport = ender_pearl_teleport;
        copy.chorus_fruit_teleport = chorus_fruit_teleport;
        copy.respectOpenPartiesAndClaims = respectOpenPartiesAndClaims;
        copy.damage_resistance_duration_ticks = damage_resistance_duration_ticks;
        copy.entity_blacklist = entity_blacklist != null ? new ArrayList<>(entity_blacklist) : new ArrayList<>();
        return copy;
    }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            LeashedTeleportMod.LOGGER.debug("[{}] No config found, creating default.", LeashedTeleportMod.MOD_NAME);
            instance.normalize();
            save();
            return;
        }
        try {
            String rawConfig = Files.readString(CONFIG_PATH);
            LeashedTeleportConfig loaded = GSON.fromJson(stripJsonComments(rawConfig), LeashedTeleportConfig.class);
            instance = (loaded != null) ? loaded : new LeashedTeleportConfig();
            instance.normalize();
            save();
            LeashedTeleportMod.LOGGER.debug("[{}] Config loaded successfully.", LeashedTeleportMod.MOD_NAME);
        } catch (IOException | JsonSyntaxException e) {
            LeashedTeleportMod.LOGGER.error("[{}] Failed to load config: {}", LeashedTeleportMod.MOD_NAME, e.getMessage());
            instance = new LeashedTeleportConfig();
            instance.normalize();
        }
    }

    public static LeashedTeleportConfig loadCopy() {
        load();
        return instance.copy();
    }

    public static void replace(LeashedTeleportConfig updatedConfig) {
        instance = updatedConfig != null ? updatedConfig.copy() : new LeashedTeleportConfig();
        instance.normalize();
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, buildCommentedConfig(instance));
        } catch (IOException e) {
            LeashedTeleportMod.LOGGER.error("[{}] Failed to save config: {}", LeashedTeleportMod.MOD_NAME, e.getMessage());
        }
    }

    public void normalize() {
        if (leash_radius < 0.0D) {
            leash_radius = 0.0D;
        }

        damage_resistance_duration_ticks = Math.max(1, damage_resistance_duration_ticks);

        if (entity_blacklist == null) {
            entity_blacklist = new ArrayList<>();
            return;
        }

        List<String> normalized = new ArrayList<>();
        for (String entry : entity_blacklist) {
            if (entry == null) {
                continue;
            }

            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        entity_blacklist = normalized;
    }

    private static String buildCommentedConfig(LeashedTeleportConfig config) {
        List<String> blacklist = config.entity_blacklist != null ? config.entity_blacklist : Collections.emptyList();

        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"leash_radius\": ").append(GSON.toJson(config.leash_radius))
                .append(", // Maximum distance in blocks from the player. Mobs further away are left behind.\n");
        builder.append("  \"useLuckPerms\": ").append(config.useLuckPerms)
                .append(", // When true, Leashed Teleport checks LuckPerms permission nodes if LuckPerms is installed.\n");
        builder.append("  \"cross_dimension_teleport\": ").append(config.cross_dimension_teleport)
                .append(", // Allow leashed mobs to follow you between dimensions such as the Overworld, Nether, and End.\n");
        builder.append("  \"ender_pearl_teleport\": ").append(config.ender_pearl_teleport)
                .append(", // Allow ender pearls to teleport your leashed mobs with you. Disable for vanilla pearl behaviour.\n");
        builder.append("  \"chorus_fruit_teleport\": ").append(config.chorus_fruit_teleport)
                .append(", // Allow chorus fruit to teleport your leashed mobs with you. Disable for vanilla chorus fruit behaviour.\n");
        builder.append("  \"respectOpenPartiesAndClaims\": ").append(config.respectOpenPartiesAndClaims)
                .append(", // Respect Open Parties and Claims protections when OPAC is installed.\n");
        builder.append("  \"damage_resistance_duration_ticks\": ").append(config.damage_resistance_duration_ticks)
                .append(", // Duration of Damage Resistance V and Slow Falling after teleport. 20 ticks = 1 second.\n");
        builder.append("  \"entity_blacklist\": ");

        if (blacklist.isEmpty()) {
            builder.append("[]");
        } else {
            builder.append("[\n");
            for (int i = 0; i < blacklist.size(); i++) {
                builder.append("    ").append(GSON.toJson(blacklist.get(i)));
                if (i < blacklist.size() - 1) {
                    builder.append(",");
                }
                builder.append("\n");
            }
            builder.append("  ]");
        }

        builder.append(" // Entity type IDs that should never teleport, even when leashed to you.\n");
        builder.append("}\n");
        return builder.toString();
    }

    private static String stripJsonComments(String input) {
        StringBuilder result = new StringBuilder();
        boolean inString = false;
        boolean escaping = false;

        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);

            if (escaping) {
                result.append(current);
                escaping = false;
                continue;
            }

            if (current == '\\' && inString) {
                result.append(current);
                escaping = true;
                continue;
            }

            if (current == '"') {
                inString = !inString;
                result.append(current);
                continue;
            }

            if (!inString && current == '/' && i + 1 < input.length() && input.charAt(i + 1) == '/') {
                while (i < input.length() && input.charAt(i) != '\n') {
                    i++;
                }
                if (i < input.length()) {
                    result.append('\n');
                }
                continue;
            }

            result.append(current);
        }

        return result.toString();
    }
}
