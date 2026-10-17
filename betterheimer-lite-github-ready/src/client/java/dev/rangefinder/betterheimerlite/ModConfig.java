package dev.rangefinder.betterheimerlite;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

final class ModConfig {
    static final int HARD_MAX_HOSTS = 4096;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("betterheimerlite.json");

    final String range;
    final int port;
    final int timeoutMs;
    final int delayMs;
    final int maxHosts;
    final int parallelism;

    ModConfig(String range, int port, int timeoutMs, int delayMs, int maxHosts, int parallelism) {
        this.range = range;
        this.port = clamp(port, 1, 65535, 25565);
        this.timeoutMs = clamp(timeoutMs, 200, 3000, 700);
        this.delayMs = clamp(delayMs, 0, 5000, 25);
        this.maxHosts = clamp(maxHosts, 1, HARD_MAX_HOSTS, 1024);
        this.parallelism = clamp(parallelism, 1, 16, 8);
    }

    static ModConfig load() {
        try {
            Files.createDirectories(PATH.getParent());
            if (Files.notExists(PATH)) {
                ModConfig defaults = defaults();
                defaults.save();
                return defaults;
            }

            try (Reader reader = Files.newBufferedReader(PATH)) {
                JsonElement element = JsonParser.parseReader(reader);
                if (!element.isJsonObject()) {
                    throw new IOException("Config root must be a JSON object.");
                }
                JsonObject json = element.getAsJsonObject();
                return new ModConfig(
                        stringValue(json, "range", "192.168.1.0/24"),
                        intValue(json, "port", 25565),
                        intValue(json, "timeoutMs", 700),
                        intValue(json, "delayMs", 25),
                        intValue(json, "maxHosts", 1024),
                        intValue(json, "parallelism", 8)
                );
            }
        } catch (Exception exception) {
            BetterHeimerLiteClient.LOGGER.error("Could not load {}. Using safe defaults.", PATH, exception);
            return defaults();
        }
    }

    void save() {
        JsonObject json = new JsonObject();
        json.addProperty("range", range);
        json.addProperty("port", port);
        json.addProperty("timeoutMs", timeoutMs);
        json.addProperty("delayMs", delayMs);
        json.addProperty("maxHosts", maxHosts);
        json.addProperty("parallelism", parallelism);
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException exception) {
            BetterHeimerLiteClient.LOGGER.error("Could not write config to {}.", PATH, exception);
        }
    }

    private static ModConfig defaults() {
        return new ModConfig("192.168.1.0/24", 25565, 700, 25, 1024, 8);
    }

    private static String stringValue(JsonObject json, String key, String fallback) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static int intValue(JsonObject json, String key, int fallback) {
        try {
            JsonElement element = json.get(key);
            return element != null && element.isJsonPrimitive() ? element.getAsInt() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max, int fallback) {
        if (value < min) return fallback;
        return Math.min(value, max);
    }
}
