package com.example.mcai.config;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class PromptLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCAI-Prompt");

    public static String load(String fileName, String defaultContent) {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("mcai/" + fileName);
        if (Files.exists(path)) {
            try {
                String content = Files.readString(path, StandardCharsets.UTF_8).trim();
                if (!content.isEmpty()) {
                    LOGGER.info("Loaded prompt from {}", path);
                    return content;
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to read {}, using default", fileName, e);
            }
        }
        return defaultContent;
    }
}
