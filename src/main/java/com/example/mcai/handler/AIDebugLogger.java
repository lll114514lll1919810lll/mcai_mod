package com.example.mcai.handler;

import com.example.mcai.MCAIMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

public class AIDebugLogger {
    private static final Path LOG_DIR = FabricLoader.getInstance().getConfigDir().resolve("mcai/debug");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter LOG_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private BufferedWriter writer;
    private String currentLogFile;

    public boolean isEnabled() { return enabled.get(); }

    public synchronized void start() {
        if (enabled.get()) return;
        try {
            Files.createDirectories(LOG_DIR);
            String timestamp = LocalDateTime.now().format(TIME_FMT);
            Path file = LOG_DIR.resolve("ai_debug_" + timestamp + ".log");
            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
            currentLogFile = file.getFileName().toString();
            enabled.set(true);
            writeHeader();
            MCAIMod.LOGGER.info("AI debug logging started: {}", currentLogFile);
        } catch (IOException e) {
            MCAIMod.LOGGER.error("Failed to start debug logging", e);
        }
    }

    public synchronized void stop() {
        if (!enabled.get()) return;
        enabled.set(false);
        try {
            if (writer != null) {
                writer.write("=== DEBUG LOG STOPPED ===\n");
                writer.flush();
                writer.close();
                writer = null;
            }
            MCAIMod.LOGGER.info("AI debug logging stopped");
        } catch (IOException e) {
            MCAIMod.LOGGER.error("Failed to stop debug logging", e);
        }
    }

    public String getCurrentLogFile() { return currentLogFile; }

    public synchronized void logQuery(String playerName, String query) {
        if (!enabled.get()) return;
        write(String.format("[%s] QUERY from %s: %s", now(), playerName, query));
    }

    public synchronized void logAPICall(String endpoint, String model, int messageCount) {
        if (!enabled.get()) return;
        write(String.format("[%s] API CALL: endpoint=%s model=%s messages=%d", now(), endpoint, model, messageCount));
    }

    public synchronized void logAPIResponse(int statusCode, int choiceCount, boolean hasToolCalls) {
        if (!enabled.get()) return;
        write(String.format("[%s] API RESPONSE: status=%d choices=%d hasToolCalls=%s", now(), statusCode, choiceCount, hasToolCalls));
    }

    public synchronized void logToolCall(String toolName, String arguments) {
        if (!enabled.get()) return;
        String args = arguments.length() > 300 ? arguments.substring(0, 300) + "..." : arguments;
        write(String.format("[%s] TOOL CALL: %s(%s)", now(), toolName, args));
    }

    public synchronized void logToolResult(String toolName, String result) {
        if (!enabled.get()) return;
        String res = result.length() > 500 ? result.substring(0, 500) + "...(truncated)" : result;
        write(String.format("[%s] TOOL RESULT [%s]: %s", now(), toolName, res));
    }

    public synchronized void logAIResponse(String response) {
        if (!enabled.get()) return;
        String res = response.length() > 1000 ? response.substring(0, 1000) + "...(truncated)" : response;
        write(String.format("[%s] AI RESPONSE: %s", now(), res));
    }

    public synchronized void logThinking(String reasoning) {
        if (!enabled.get()) return;
        String r = reasoning.length() > 2000 ? reasoning.substring(0, 2000) + "...(truncated)" : reasoning;
        write(String.format("[%s] THINKING: %s", now(), r));
    }

    public synchronized void logError(String context, String error) {
        if (!enabled.get()) return;
        write(String.format("[%s] ERROR [%s]: %s", now(), context, error));
    }

    public synchronized void logInfo(String message) {
        if (!enabled.get()) return;
        write(String.format("[%s] INFO: %s", now(), message));
    }

    private void writeHeader() throws IOException {
        writer.write("=== MCAI AI DEBUG LOG ===\n");
        writer.write("Started: " + LocalDateTime.now() + "\n");
        writer.write("========================\n\n");
        writer.flush();
    }

    private void write(String line) {
        try {
            writer.write(line);
            writer.write("\n");
            writer.flush();
        } catch (IOException e) {
            MCAIMod.LOGGER.error("Failed to write debug log", e);
        }
    }

    private static String now() {
        return LocalDateTime.now().format(LOG_TIME_FMT);
    }
}
