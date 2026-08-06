package com.example.mcai.handler;

import com.example.mcai.MCAIMod;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class PersonaManager {
    private static final String PERSONAS_RESOURCE_DIR = "mcai/personas/";
    private static final String[] BUILT_IN_PERSONAS = {
        "tsundere", "pirate", "chuuni", "gentle"
    };
    private static final Gson GSON = new Gson();

    /** 人格记录（从 JSON 文件解析） */
    public static class PersonaRecord {
        public final String id;
        public final String name;
        public final String summary;
        public final String content;
        public final boolean i18n;

        public PersonaRecord(String id, String name, String summary, String content) {
            this(id, name, summary, content, false);
        }

        public PersonaRecord(String id, String name, String summary, String content, boolean i18n) {
            this.id = id;
            this.name = name;
            this.summary = summary != null ? summary : "";
            this.content = content;
            this.i18n = i18n;
        }
    }

    /** 默认人格（无额外设定，name/summary 为 i18n key） */
    public static final PersonaRecord DEFAULT_PERSONA = new PersonaRecord(
        "default", "mcai.persona.default.name", "mcai.persona.default.summary", null, true);

    private final Path personasDir;
    private volatile List<PersonaRecord> availablePersonas = new ArrayList<>(List.of(DEFAULT_PERSONA));
    private volatile Map<String, PersonaRecord> personaMap = new LinkedHashMap<>();
    private int lastTotalFiles = 0;
    private int lastLoadedCount = 0;
    private int lastFailedCount = 0;
    private final List<String> lastDuplicateIds = new ArrayList<>();
    private final List<String> lastFailedFiles = new ArrayList<>();

    public PersonaManager() {
        this.personasDir = FabricLoader.getInstance().getConfigDir().resolve("mcai/personas");
        extractBuiltInPersonas();
        refreshPersonaList();
    }

    /** 从 JAR 资源中提取内置人设文件（不覆盖已有文件） */
    private void extractBuiltInPersonas() {
        try {
            Files.createDirectories(personasDir);
            for (String name : BUILT_IN_PERSONAS) {
                Path target = personasDir.resolve(name + ".json");
                if (!Files.exists(target)) {
                    String resourcePath = PERSONAS_RESOURCE_DIR + name + ".json";
                    try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                        if (is != null) {
                            Files.writeString(target, new String(is.readAllBytes(), StandardCharsets.UTF_8), StandardCharsets.UTF_8);
                            MCAIMod.LOGGER.info("Extracted built-in persona: {}", name);
                        } else {
                            MCAIMod.LOGGER.warn("Built-in persona resource not found: {}", resourcePath);
                        }
                    }
                }
            }
            // 清理旧版 .txt 文件（迁移）
            try (Stream<Path> stream = Files.list(personasDir)) {
                stream.filter(p -> p.toString().endsWith(".txt"))
                      .forEach(p -> {
                          try { Files.delete(p); MCAIMod.LOGGER.info("Removed legacy persona file: {}", p.getFileName()); }
                          catch (Exception ignored) {}
                      });
            }
        } catch (Exception e) {
            MCAIMod.LOGGER.error("Failed to extract built-in personas", e);
        }
    }

    /** 刷新可用人设列表（扫描目录中的 .json 文件，解析并校验） */
    public void refreshPersonaList() {
        Map<String, PersonaRecord> map = new LinkedHashMap<>();
        map.put("default", DEFAULT_PERSONA);

        if (!Files.exists(personasDir)) {
            MCAIMod.LOGGER.warn("Personas directory does not exist: {}, creating it", personasDir);
            try {
                Files.createDirectories(personasDir);
            } catch (Exception e) {
                MCAIMod.LOGGER.error("Failed to create personas directory", e);
            }
        }

        int totalFiles = 0;
        int loadedCount = 0;
        int failedCount = 0;
        List<String> duplicateIds = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();

        try (Stream<Path> stream = Files.list(personasDir)) {
            List<Path> jsonFiles = stream.filter(p -> p.toString().endsWith(".json"))
                                          .sorted()
                                          .toList();
            totalFiles = jsonFiles.size();
            MCAIMod.LOGGER.debug("Scanning personas directory: {} ({} .json files found)", personasDir, totalFiles);

            for (Path p : jsonFiles) {
                PersonaRecord record = loadAndValidate(p);
                if (record != null) {
                    if (map.containsKey(record.id)) {
                        String dupInfo = record.id + " (in " + p.getFileName() + ")";
                        duplicateIds.add(dupInfo);
                        MCAIMod.LOGGER.warn("Duplicate persona id '{}' in {}, skipping", record.id, p.getFileName());
                        failedCount++;
                    } else {
                        map.put(record.id, record);
                        loadedCount++;
                        MCAIMod.LOGGER.debug("Loaded persona: id='{}', name='{}' from {}", record.id, record.name, p.getFileName());
                    }
                } else {
                    failedFiles.add(p.getFileName().toString());
                    failedCount++;
                }
            }
        } catch (Exception e) {
            MCAIMod.LOGGER.warn("Failed to scan personas directory: {}", personasDir, e);
        }

        this.lastTotalFiles = totalFiles;
        this.lastLoadedCount = loadedCount;
        this.lastFailedCount = failedCount;
        synchronized (lastDuplicateIds) {
            lastDuplicateIds.clear();
            lastDuplicateIds.addAll(duplicateIds);
        }
        synchronized (lastFailedFiles) {
            lastFailedFiles.clear();
            lastFailedFiles.addAll(failedFiles);
        }

        this.personaMap = Collections.unmodifiableMap(map);
        this.availablePersonas = Collections.unmodifiableList(new ArrayList<>(map.values()));
        MCAIMod.LOGGER.info("Persona refresh complete: {} total, {} loaded, {} failed/invalid. Available: {}",
            totalFiles, loadedCount, failedCount,
            availablePersonas.stream().map(r -> r.id).toList());
    }

    /** 加载并校验单个 JSON 人设文件，返回 null 表示校验失败 */
    private PersonaRecord loadAndValidate(Path path) {
        String fileName = path.getFileName().toString();
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (json.isEmpty()) {
                MCAIMod.LOGGER.warn("Persona file is empty: {}", fileName);
                return null;
            }
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj == null) {
                MCAIMod.LOGGER.warn("Persona file is not valid JSON: {}", fileName);
                return null;
            }

            // 校验必填字段
            String id = getJsonString(obj, "id");
            String name = getJsonString(obj, "name");
            String content = getJsonString(obj, "content");

            if (id == null || id.isEmpty()) {
                MCAIMod.LOGGER.warn("Persona '{}' missing required field 'id'", fileName);
                return null;
            }
            if (name == null || name.isEmpty()) {
                MCAIMod.LOGGER.warn("Persona '{}' missing required field 'name'", fileName);
                return null;
            }
            if (content == null || content.isEmpty()) {
                MCAIMod.LOGGER.warn("Persona '{}' missing required field 'content'", fileName);
                return null;
            }

            // id 安全检查
            if (id.contains("/") || id.contains("\\") || id.contains("..")) {
                MCAIMod.LOGGER.warn("Persona '{}' has unsafe id, skipping", fileName);
                return null;
            }

            String summary = getJsonString(obj, "summary");
            return new PersonaRecord(id, name, summary, content);
        } catch (JsonSyntaxException e) {
            MCAIMod.LOGGER.warn("Persona '{}' has invalid JSON syntax: {}", fileName, e.getMessage());
            return null;
        } catch (Exception e) {
            MCAIMod.LOGGER.warn("Failed to read persona file: {}", fileName, e);
            return null;
        }
    }

    private static String getJsonString(JsonObject obj, String field) {
        if (!obj.has(field) || obj.get(field).isJsonNull()) return null;
        try {
            return obj.get(field).getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    /** 获取所有人设记录（含 default） */
    public List<PersonaRecord> getAvailablePersonas() {
        return availablePersonas;
    }

    /** 获取指定人设记录，不存在返回 null */
    public PersonaRecord getPersona(String personaId) {
        return personaMap.get(personaId);
    }

    /** 获取指定人设的注入内容，不存在或 default 返回 null */
    public String getPersonaContent(String personaId) {
        PersonaRecord record = personaMap.get(personaId);
        return (record != null && record.content != null) ? record.content : null;
    }

    /** 获取人设文件目录路径 */
    public Path getPersonasDir() {
        return personasDir;
    }

    /** 检查人设 ID 是否有效 */
    public boolean isValidPersona(String personaId) {
        return personaMap.containsKey(personaId);
    }

    /** 获取上次刷新的统计信息 */
    public int getLastTotalFiles() { return lastTotalFiles; }
    public int getLastLoadedCount() { return lastLoadedCount; }
    public int getLastFailedCount() { return lastFailedCount; }
    public List<String> getLastDuplicateIds() {
        synchronized (lastDuplicateIds) { return new ArrayList<>(lastDuplicateIds); }
    }
    public List<String> getLastFailedFiles() {
        synchronized (lastFailedFiles) { return new ArrayList<>(lastFailedFiles); }
    }
}
