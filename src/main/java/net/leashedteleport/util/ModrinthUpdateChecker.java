package net.leashedteleport.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.leashedteleport.LeashedTeleportMod;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModrinthUpdateChecker {

    private static final String PROJECT_ID = "G12zLjMK";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
    private static final AtomicBoolean CHECK_STARTED = new AtomicBoolean(false);

    private ModrinthUpdateChecker() {
    }

    public static void checkOnceAsync() {
        if (!CHECK_STARTED.compareAndSet(false, true)) {
            return;
        }

        Thread thread = new Thread(ModrinthUpdateChecker::checkForUpdate, "leashedteleport-modrinth-update-check");
        thread.setDaemon(true);
        thread.start();
    }

    private static void checkForUpdate() {
        String currentVersion = currentModVersion();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.modrinth.com/v2/project/" + PROJECT_ID + "/version"))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "LeashedTeleport/" + currentVersion)
                .GET()
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LeashedTeleportMod.LOGGER.debug("[{}] Update check returned HTTP {}.", LeashedTeleportMod.MOD_NAME, response.statusCode());
                return;
            }

            Optional<String> latestVersion = extractLatestVersion(response.body(), currentMinecraftVersion());
            if (latestVersion.isEmpty()) {
                LeashedTeleportMod.LOGGER.debug("[{}] Update check returned no usable versions.", LeashedTeleportMod.MOD_NAME);
                return;
            }

            String newestVersion = latestVersion.get();
            if (isNewerVersion(newestVersion, currentVersion)) {
                LeashedTeleportMod.LOGGER.info("[{}] New version available: {} (current: {})",
                        LeashedTeleportMod.MOD_NAME, newestVersion, currentVersion);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LeashedTeleportMod.LOGGER.debug("[{}] Update check failed.", LeashedTeleportMod.MOD_NAME, e);
        }
    }

    private static Optional<String> extractLatestVersion(String responseBody, String minecraftVersion) {
        JsonElement root = JsonParser.parseString(responseBody);
        if (!root.isJsonArray()) {
            return Optional.empty();
        }

        JsonArray versions = root.getAsJsonArray();
        VersionCandidate newestCompatible = null;
        VersionCandidate newestRelease = null;

        for (JsonElement versionElement : versions) {
            if (!versionElement.isJsonObject()) {
                continue;
            }

            JsonObject versionObject = versionElement.getAsJsonObject();
            String versionType = getString(versionObject, "version_type");
            if (!"release".equalsIgnoreCase(versionType)) {
                continue;
            }

            String versionNumber = getString(versionObject, "version_number");
            if (versionNumber == null || versionNumber.isBlank()) {
                continue;
            }

            Instant publishedAt = getPublishedAt(versionObject);
            if (publishedAt == null) {
                continue;
            }

            VersionCandidate candidate = new VersionCandidate(versionNumber, publishedAt);
            if (newestRelease == null || publishedAt.isAfter(newestRelease.publishedAt())) {
                newestRelease = candidate;
            }

            if (jsonArrayContains(versionObject, "loaders", "fabric")
                    && jsonArrayContains(versionObject, "game_versions", minecraftVersion)
                    && (newestCompatible == null || publishedAt.isAfter(newestCompatible.publishedAt()))) {
                newestCompatible = candidate;
            }
        }

        VersionCandidate selected = newestCompatible != null ? newestCompatible : newestRelease;
        return selected != null ? Optional.of(selected.versionNumber()) : Optional.empty();
    }

    private static String getString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) {
            return null;
        }

        return value.getAsString();
    }

    private static boolean jsonArrayContains(JsonObject object, String key, String expectedValue) {
        if (expectedValue == null || expectedValue.isBlank()) {
            return false;
        }

        JsonElement value = object.get(key);
        if (value == null || !value.isJsonArray()) {
            return false;
        }

        for (JsonElement element : value.getAsJsonArray()) {
            if (element != null && element.isJsonPrimitive() && expectedValue.equalsIgnoreCase(element.getAsString())) {
                return true;
            }
        }

        return false;
    }

    private static Instant getPublishedAt(JsonObject object) {
        String publishedAt = getString(object, "date_published");
        if (publishedAt == null || publishedAt.isBlank()) {
            return null;
        }

        try {
            return Instant.parse(publishedAt);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String currentModVersion() {
        return currentVersionForMod(LeashedTeleportMod.MOD_ID);
    }

    private static String currentMinecraftVersion() {
        return currentVersionForMod("minecraft");
    }

    private static String currentVersionForMod(String modId) {
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(modId);
        return container
                .map(modContainer -> modContainer.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static boolean isNewerVersion(String candidate, String current) {
        try {
            Version candidateVersion = Version.parse(candidate);
            Version currentVersion = Version.parse(current);
            return candidateVersion.compareTo(currentVersion) > 0;
        } catch (VersionParsingException e) {
            LeashedTeleportMod.LOGGER.debug("[{}] Could not compare versions. Candidate: {}, current: {}",
                    LeashedTeleportMod.MOD_NAME, candidate, current, e);
            return false;
        }
    }

    private record VersionCandidate(String versionNumber, Instant publishedAt) {
    }
}
