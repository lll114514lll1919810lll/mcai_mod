package com.example.mcai.handler;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
public class ChatLog {
    private static final int MAX_SIZE = 50;
    private final LinkedList<String> log = new LinkedList<>();
    public void add(String name, String message) { add(name, message, false); }
    public void add(String name, String message, boolean isAdmin) {
        synchronized (log) {
            String time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(ZonedDateTime.now(ZoneId.of("Asia/Shanghai")));
            String prefix = isAdmin ? "[管理员] " : "";
            log.add("[" + time + "] " + prefix + name + ": " + message);
            while (log.size() > MAX_SIZE) log.removeFirst();
        }
    }
    public String peek() { synchronized (log) { return String.join("\n", log); } }
    public void clear() { synchronized (log) { log.clear(); } }
}
