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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AIDebugLogger {
    private static final Path LOG_DIR = FabricLoader.getInstance().getConfigDir().resolve("mcai/debug");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter LOG_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int MAX_SESSIONS = 5;

    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private BufferedWriter writer;
    private String currentLogFile;

    // In-memory session tracking for /aidebug show
    private final LinkedList<DebugSession> sessions = new LinkedList<>();
    private final AtomicInteger sessionIdCounter = new AtomicInteger(0);
    private volatile DebugSession currentSession;

    /** A single AI interaction session (one query → thinking → tool calls → response). */
    public static class DebugSession {
        public final int id;
        public final String playerName;
        public final String query;
        public final long timestamp;
        public String thinking;
        public final List<ToolCallRecord> toolCalls = new ArrayList<>();
        public String response;
        private ToolCallRecord pendingToolCall; // tracks current tool call waiting for result

        public DebugSession(int id, String playerName, String query) {
            this.id = id;
            this.playerName = playerName;
            this.query = query;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /** A single tool call with its arguments and result. */
    public static class ToolCallRecord {
        public final String toolName;
        public final String arguments;
        public String result;

        public ToolCallRecord(String toolName, String arguments) {
            this.toolName = toolName;
            this.arguments = arguments;
        }
    }

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

    // ---- Session tracking ----

    /** Start a new debug session (called when a player query begins). */
    public void startSession(String playerName, String query) {
        int id = sessionIdCounter.incrementAndGet();
        DebugSession session = new DebugSession(id, playerName, query);
        currentSession = session;
        synchronized (sessions) {
            sessions.addLast(session);
            while (sessions.size() > MAX_SESSIONS) {
                sessions.removeFirst();
            }
        }
    }

    /** Mark the current session as complete. */
    public void endSession() {
        currentSession = null;
    }

    /** Get the most recent N sessions. */
    public List<DebugSession> getLastSessions(int count) {
        synchronized (sessions) {
            int size = sessions.size();
            int from = Math.max(0, size - count);
            return new ArrayList<>(sessions.subList(from, size));
        }
    }

    /** Get the most recent session, or null if none. */
    public DebugSession getLastSession() {
        synchronized (sessions) {
            return sessions.isEmpty() ? null : sessions.getLast();
        }
    }

    /** Get a session by ID, or null if not found. */
    public DebugSession getSession(int id) {
        synchronized (sessions) {
            for (DebugSession s : sessions) {
                if (s.id == id) return s;
            }
        }
        return null;
    }

    /** Clear all stored sessions. */
    public void clearSessions() {
        synchronized (sessions) {
            sessions.clear();
        }
        sessionIdCounter.set(0);
    }

    // ---- Logging methods (no truncation) ----

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
        write(String.format("[%s] TOOL CALL: %s(%s)", now(), toolName, arguments));
        // Track in session
        DebugSession s = currentSession;
        if (s != null) {
            ToolCallRecord rec = new ToolCallRecord(toolName, arguments);
            s.pendingToolCall = rec;
            s.toolCalls.add(rec);
        }
    }

    public synchronized void logToolResult(String toolName, String result) {
        if (!enabled.get()) return;
        write(String.format("[%s] TOOL RESULT [%s]: %s", now(), toolName, result));
        // Update session
        DebugSession s = currentSession;
        if (s != null && s.pendingToolCall != null && s.pendingToolCall.toolName.equals(toolName)) {
            s.pendingToolCall.result = result;
            s.pendingToolCall = null;
        }
    }

    public synchronized void logAIResponse(String response) {
        if (!enabled.get()) return;
        write(String.format("[%s] AI RESPONSE: %s", now(), response));
        // Update session
        DebugSession s = currentSession;
        if (s != null) {
            s.response = response;
        }
    }

    public synchronized void logThinking(String reasoning) {
        if (!enabled.get()) return;
        write(String.format("[%s] THINKING: %s", now(), reasoning));
        // Update session
        DebugSession s = currentSession;
        if (s != null) {
            s.thinking = reasoning;
        }
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
