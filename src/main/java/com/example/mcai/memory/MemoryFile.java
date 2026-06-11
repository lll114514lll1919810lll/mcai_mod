package com.example.mcai.memory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MemoryFile {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCAI-Memory");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path MEMORY_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("mcai_memory.json");

    private final List<String> entries = new ArrayList<>();

    public void load() {
        entries.clear();
        if (Files.exists(MEMORY_PATH)) {
            try (Reader r = Files.newBufferedReader(MEMORY_PATH)) {
                JsonObject obj = GSON.fromJson(r, JsonObject.class);
                if (obj != null && obj.has("entries")) {
                    JsonArray arr = obj.getAsJsonArray("entries");
                    for (var e : arr) {
                        entries.add(e.getAsString());
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load memory", e);
            }
        }
        LOGGER.info("Memory loaded: {} entries", entries.size());
    }

    public void save() {
        try {
            Files.createDirectories(MEMORY_PATH.getParent());
            JsonObject obj = new JsonObject();
            JsonArray arr = new JsonArray();
            for (String e : entries) {
                arr.add(e);
            }
            obj.add("entries", arr);
            try (Writer w = Files.newBufferedWriter(MEMORY_PATH)) {
                GSON.toJson(obj, w);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save memory", e);
        }
    }

    public void addEntry(String content) {
        entries.add(content);
        save();
    }

    public String getAll() {
        if (entries.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            sb.append("[").append(i + 1).append("] ").append(entries.get(i)).append("\n");
        }
        return sb.toString().trim();
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
        save();
    }

    public static Path getPath() {
        return MEMORY_PATH;
    }
}
