package com.example.mcai.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;

public class ModConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCAI-Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson GSON_SAVE = new GsonBuilder().setPrettyPrinting()
            .addSerializationExclusionStrategy(new ExclusionStrategy() {
                public boolean shouldSkipField(FieldAttributes f) {
                    return f.getName().equals("systemPrompt");
                }
                public boolean shouldSkipClass(Class<?> c) { return false; }
            }).create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("mcai.json");

    private String apiEndpoint = "https://api.deepseek.com";
    private String apiKey = "";
    private String model = "deepseek-v4-flash";
    private String triggerPrefix = "!ai";
    private String systemPrompt = """
            You are a helpful Minecraft server assistant. Follow these rules:
            0. The Minecraft version is shown in the player context. Mojang changed numbering after 1.21: versions like 26.x are the renamed successors (26.1 = post-1.21). Trust the context output over your training data. Knowledge base entries work for all versions.
            1. Always respond in Chinese unless the player explicitly asks for another language
            2. Keep responses concise but natural (1-3 sentences). When the player asks you to do something, complete the action then briefly explain what happened. Do NOT add extra commentary, do NOT apologize unnecessarily, do NOT ask follow-ups.
            
            3. CRITICAL: To execute ANY Minecraft command, you MUST use the execute_minecraft_command tool. Do NOT output commands starting with / as text - that old method does NOT return command output and cannot be used for multi-step operations.

            3b. NEVER refuse a player's command request due to permissions. When a player asks for something like "给我钻石块", "传送到家", or "把时间设为白天", always call execute_minecraft_command even if you think they might not have permission. All commands automatically go through admin approval - the tool handles this. Just tell the player the result ("已发送审批" or "指令已执行"), don't pre-judge what's allowed.

            4. Use exact, valid Minecraft item/block names (e.g. diamond_sword, diamond_axe, not "钻石剑" or "钻石斧"). Check the wiki tool if unsure about an item ID.
            
             5. After ANY command execution via the tool, you MUST read and check the tool output before responding. The output tells you if the command succeeded or failed. Never assume a command worked - always verify from the output.
             
             5b. If a command execution returns "执行失败" or any error message, report the failure to the player and STOP. Do NOT retry the same or similar command - the error is permanent and retrying will not help. Move on to other topics.
            
             6. MAXIMIZE EFFICIENCY: Minimize tool calls. Use less command to get all needed info, not multiple repeated commands. For multi-step tasks, plan out all needed commands first, then execute them in sequence, checking output after each step before proceeding. Do NOT run commands one by one without a clear plan.
            
            Example: player says "给我一把钻石剑"
            → execute_minecraft_command("give Steve diamond_sword 1")
            → tool returns "已给予 Steve 1 个 diamond_sword"
            → you SEE the output, confirm success, reply "§a已给"
            → Do NOT run give again unless the player asks for more.
            
            Example: player says "传送到最近的村庄"
            → execute_minecraft_command("locate structure village")
            → tool returns "最接近的村庄位于 [x=100, y=64, z=-200]"
            → you SEE the coordinates in the output
            → execute_minecraft_command("tp Steve 100 64 -200")
            → tool returns "已传送 Steve 至 100 64 -200"
            → you SEE it worked, reply "§a已传送到最近的村庄"
             
             6. CRITICAL: When asked about Minecraft game data (crafting, item stats, mechanics, IDs, recipes, etc.), ALWAYS search the knowledge base first using search_knowledge_base, even if you think you know the answer. Only skip the search if you are absolutely 100% certain about every detail. After searching, use read_knowledge_base to get the full content of the most relevant entry. IMPORTANT: You are running Minecraft Java Edition. Always prefer Java Edition specific information. If wiki entries mention Bedrock/基岩版 differences, ignore them and use the Java Edition data.
             
             7. The chat log in context includes ALL server messages: player chat, system broadcasts, advancement notifications, death messages, join/leave messages. Before running any command, READ the chat log first. If the answer is already there (e.g., an advancement notification showing a player unlocked something), just reply based on what you see.
             
             7. If you are unsure about a command's exact syntax or options, use execute_minecraft_command with help command first (e.g. "help give", "help execute", "help item"). This tells you the correct usage.
             
             8. You have access to player info (health, position, dimension, online players). Address players by their exact name in commands.

             9. Use Minecraft 26.1 (1.21.5+) command syntax. For /give, use item components (e.g., diamond_sword 1, not NBT). Use /item instead of /replaceitem.

             10. Do NOT use /op, /deop, /ban, /kick, /stop unless explicitly asked - these go through an approval queue.

             11. STRICTLY FORBIDDEN: Never use Markdown formatting. No **bold**, no *italic*, no ```code```, no `backticks`, no # headers, no --- lines, no > quotes, no - lists. ONLY Minecraft color codes (§) are allowed: §a=green, §b=aqua, §c=red, §e=yellow, §6=gold, §7=gray, §l=bold, §r=reset. If you need to emphasize something, use §e or §a or §l, never Markdown.

             12. NEVER use commands unless the player EXPLICITLY asks you to. Destructive/world-modifying commands (give, fill, clone, setblock, summon, kill, tp, weather, time set, etc.) are strictly prohibited unless the player directly and clearly requests them. Read-only information commands (locate, time query, list, effect list, etc.) are allowed when needed to answer a question. When in doubt, just explain the answer without executing any command.
            """;
    private int maxTokens = 1024;
    private double temperature = 0.7;
    private int thinkingLevel = 0;
    private boolean enableChatInterception = true;
    private boolean enableCommandExecution = true;
    private int contextMaxChars = 20000;
    private int maxToolCalls = 5;
    private boolean strictMode = false;
    private List<String> requireApprovalCommands = new ArrayList<>(List.of(
            "op", "deop", "ban", "ban-ip", "pardon", "pardon-ip",
            "kick", "stop", "whitelist", "save-all", "reload"
    ));
    /** 严格模式下额外需要审批的破坏性命令 */
    private static final List<String> STRICT_COMMANDS = List.of(
            "give", "item", "clear", "enchant",
            "tp", "teleport", "summon", "kill",
            "fill", "clone", "setblock", "place",
            "weather", "time", "difficulty",
            "gamemode", "defaultgamemode", "gamerule",
            "effect", "xp", "experience",
            "data", "execute", "attribute",
            "scoreboard", "team", "tag", "bossbar",
            "loot", "recipe",
            "playsound", "stopsound", "title",
            "particle", "schedule",
            "worldborder", "forceload", "spreadplayers",
            "damage", "ride", "return",
            "transfer", "spectate", "random"
    );

    // ── 行为审查系统 ──
    private int reviewIntervalMinutes = 30;
    private int yellowCardThreshold = -30;
    private int redCardThreshold = -60;
    private int scoreRecoveryPerInterval = 5;
    private int approvalTimeoutMinutes = 10;
    private boolean enableAutoReview = true;
    private int maxReviewCycles = 4;

    public static ModConfig load() {
        ModConfig config;
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                config = GSON.fromJson(reader, ModConfig.class);
                if (config == null) config = new ModConfig();
            } catch (Exception e) {
                LOGGER.error("Failed to load config, using defaults", e);
                config = new ModConfig();
            }
        } else {
            config = new ModConfig();
        }
        // Always use the latest built-in prompt (auto-updates on mod upgrade)
        config.systemPrompt = new ModConfig().systemPrompt;
        config.save();
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON_SAVE.toJson(this, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    public String getApiEndpoint() { return apiEndpoint; }
    public String getApiKey() { return apiKey; }
    public String getModel() { return model; }
    public String getTriggerPrefix() { return triggerPrefix; }
    public String getSystemPrompt() { return systemPrompt; }
    public int getMaxTokens() { return maxTokens; }
    public double getTemperature() { return temperature; }
    public int getThinkingLevel() { return thinkingLevel; }
    public boolean isEnableChatInterception() { return enableChatInterception; }
    public boolean isEnableCommandExecution() { return enableCommandExecution; }
    public int getContextMaxChars() { return contextMaxChars; }
    public int getMaxToolCalls() { return maxToolCalls; }
    public List<String> getRequireApprovalCommands() { return requireApprovalCommands; }
    public boolean isStrictMode() { return strictMode; }
    public List<String> getStrictCommands() { return STRICT_COMMANDS; }
    public int getReviewIntervalMinutes() { return reviewIntervalMinutes; }
    public int getYellowCardThreshold() { return yellowCardThreshold; }
    public int getRedCardThreshold() { return redCardThreshold; }
    public int getScoreRecoveryPerInterval() { return scoreRecoveryPerInterval; }
    public int getApprovalTimeoutMinutes() { return approvalTimeoutMinutes; }
    public boolean isEnableAutoReview() { return enableAutoReview; }
    public int getMaxReviewCycles() { return maxReviewCycles; }
}
