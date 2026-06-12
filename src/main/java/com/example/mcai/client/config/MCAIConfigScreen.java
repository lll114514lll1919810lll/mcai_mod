package com.example.mcai.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class MCAIConfigScreen extends Screen {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int ROW_H = 22;
    private static final int LABEL_W = 110;
    private static final int COL2 = 120; // second column start (label + gap)

    private final Screen parent;
    private final Path configPath;
    private JsonObject cfg;
    private double scrollOffset;
    private StringWidget statusWidget;

    private EditBox apiEndpointField, apiKeyField, modelField, prefixField;
    private EditBox maxTokensField, tempField, ctxField, thinkingField, toolCallsField;
    private EditBox sysPromptPathField, reviewPromptPathField;
    private EditBox reviewIntervalField, yellowCardField, redCardField;
    private EditBox scoreRecoveryField, approvalTimeoutField, promptLangField;
    private Button chatBtn, cmdBtn, strictBtn, autoReviewBtn;

    public MCAIConfigScreen(Screen parent) {
        super(Component.translatable("mcai.config.title"));
        this.parent = parent;
        this.configPath = FabricLoader.getInstance().getConfigDir().resolve("mcai/config.json");
        this.cfg = loadConfig();
    }

    private JsonObject loadConfig() {
        if (Files.exists(configPath)) {
            try (Reader r = Files.newBufferedReader(configPath)) {
                JsonObject obj = GSON.fromJson(r, JsonObject.class);
                if (obj != null) return obj;
            } catch (Exception ignored) {}
        }
        return new JsonObject();
    }

    private String gs(String key, String def) { return cfg.has(key) ? cfg.get(key).getAsString() : def; }
    private int gi(String key, int def) { return cfg.has(key) ? cfg.get(key).getAsInt() : def; }
    private double gd(String key, double def) { return cfg.has(key) ? cfg.get(key).getAsDouble() : def; }
    private boolean gb(String key, boolean def) { return cfg.has(key) ? cfg.get(key).getAsBoolean() : def; }
    private int ry(int row) { return (int)(28 + row * ROW_H - scrollOffset); }

    @Override
    protected void init() { rebuildWidgets(); }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        int cx = width / 2;
        int inX = cx + 5;
        int fw = Math.min(180, width - inX - 10);
        int kw = Math.min(280, width - inX - 10);
        int lx = Math.max(cx - 210, 5);
        int r = 0;

        // Title
        addRenderableWidget(new StringWidget(cx - 40, 8, 100, 20,
                Component.translatable("mcai.config.title"), font));
        r++;

        // ── API ──
        addRenderableWidget(new StringWidget(lx, ry(r), LABEL_W, 20,
                Component.translatable("mcai.config.group.api"), font)); r++;
        addRow(lx, inX, r, "mcai.config.api_endpoint", apiEndpointField = f(inX, ry(r), kw, gs("apiEndpoint", ""))); r++;
        addRow(lx, inX, r, "mcai.config.api_key",      apiKeyField     = f(inX, ry(r), kw, gs("apiKey", ""))); r++;
        addRow(lx, inX, r, "mcai.config.model",        modelField      = f(inX, ry(r), fw, gs("model", "deepseek-v4-flash"))); r++;

        // ── AI ──
        addRenderableWidget(new StringWidget(lx, ry(r), LABEL_W, 20,
                Component.translatable("mcai.config.group.ai"), font)); r++;
        addRow(lx, inX, r, "mcai.config.trigger_prefix", prefixField     = f(inX, ry(r), 80, gs("triggerPrefix", "!ai"))); r++;
        addRow(lx, inX, r, "mcai.config.max_tokens",     maxTokensField  = n(inX, ry(r), gi("maxTokens", 2048))); r++;
        addRow(lx, inX, r, "mcai.config.temperature",    tempField       = n(inX, ry(r), (int)(gd("temperature", 0.75)*100))); r++;
        addRow(lx, inX, r, "mcai.config.context_chars",  ctxField        = n(inX, ry(r), gi("contextMaxChars", 20000))); r++;
        addRow(lx, inX, r, "mcai.config.thinking_level", thinkingField   = n(inX, ry(r), gi("thinkingLevel", 1))); r++;
        addRow(lx, inX, r, "mcai.config.tool_calls",     toolCallsField  = n(inX, ry(r), gi("maxToolCalls", 15))); r++;

        // ── 行为 ──
        addRenderableWidget(new StringWidget(lx, ry(r), LABEL_W, 20,
                Component.translatable("mcai.config.group.review"), font)); r++;
        tg(lx, inX, fw, r, "mcai.config.chat_listen",  gb("enableChatInterception", true), b -> chatBtn = b); r++;
        tg(lx, inX, fw, r, "mcai.config.cmd_exec",     gb("enableCommandExecution", true), b -> cmdBtn = b); r++;
        tg(lx, inX, fw, r, "mcai.config.strict_mode",  gb("strictMode", true), b -> strictBtn = b); r++;
        tg(lx, inX, fw, r, "mcai.config.auto_review",  gb("enableAutoReview", true), b -> autoReviewBtn = b); r++;
        addRow(lx, inX, r, "mcai.config.review_interval", reviewIntervalField  = n(inX, ry(r), gi("reviewIntervalMinutes", 30))); r++;
        addRow(lx, inX, r, "mcai.config.yellow_card",     yellowCardField      = n(inX, ry(r), gi("yellowCardThreshold", -30))); r++;
        addRow(lx, inX, r, "mcai.config.red_card",        redCardField         = n(inX, ry(r), gi("redCardThreshold", -60))); r++;
        addRow(lx, inX, r, "mcai.config.score_recovery",  scoreRecoveryField   = n(inX, ry(r), gi("scoreRecoveryPerInterval", 5))); r++;
        addRow(lx, inX, r, "mcai.config.approval_timeout", approvalTimeoutField = n(inX, ry(r), gi("approvalTimeoutMinutes", 10))); r++;

        // ── 提示词 ──
        addRenderableWidget(new StringWidget(lx, ry(r), LABEL_W, 20,
                Component.translatable("mcai.config.group.prompts"), font)); r++;
        addRow(lx, inX, r, "mcai.config.sys_prompt_path",    sysPromptPathField    = f(inX, ry(r), kw, gs("systemPromptPath", ""))); r++;
        addRow(lx, inX, r, "mcai.config.review_prompt_path", reviewPromptPathField = f(inX, ry(r), kw, gs("reviewPromptPath", ""))); r++;
        addRow(lx, inX, r, "mcai.config.prompt_language",    promptLangField       = f(inX, ry(r), 80, gs("promptLanguage", "zh_cn"))); r++;

        // ── 底部 ──
        statusWidget = new StringWidget(0, height - 25, width, 20, Component.literal(""), font);
        addRenderableWidget(statusWidget);
        int by = height - 25;
        addRenderableWidget(Button.builder(Component.translatable("mcai.config.save"), b -> save())
                .bounds(cx - 105, by, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("mcai.config.cancel"), b -> onClose())
                .bounds(cx + 5, by, 100, 20).build());
    }

    private void addRow(int lx, int inX, int row, String key, EditBox field) {
        int y = ry(row);
        addRenderableWidget(new StringWidget(lx, y, LABEL_W, 20, Component.translatable(key), font));
        addRenderableWidget(field);
        field.setY(y);
    }

    private EditBox f(int x, int y, int w, String val) {
        EditBox e = new EditBox(font, x, y, w, 20, Component.literal(""));
        e.setMaxLength(1024); e.setValue(val);
        e.setTooltip(Tooltip.create(Component.translatable("mcai.config.prompt_tooltip")));
        return e;
    }

    private EditBox n(int x, int y, int val) {
        EditBox e = new EditBox(font, x, y, 80, 20, Component.literal(""));
        e.setMaxLength(12); e.setValue(String.valueOf(val));
        return e;
    }

    private void tg(int lx, int inX, int fw, int row, String key, boolean initial, Consumer<Button> setter) {
        int y = ry(row);
        addRenderableWidget(new StringWidget(lx, y, LABEL_W, 20, Component.translatable(key), font));
        Button btn = Button.builder(Component.translatable(initial ? "§a开启" : "§c关闭"), b -> {
            b.setMessage(Component.translatable(
                    b.getMessage().getString().contains("开启") ? "§c关闭" : "§a开启"));
        }).bounds(inX, y, 60, 20).build();
        addRenderableWidget(btn); setter.accept(btn);
    }

    private void save() {
        try {
            Files.createDirectories(configPath.getParent());
            JsonObject o = Files.exists(configPath)
                    ? GSON.fromJson(Files.newBufferedReader(configPath), JsonObject.class) : new JsonObject();
            if (o == null) o = new JsonObject();
            o.addProperty("apiEndpoint", apiEndpointField.getValue());
            o.addProperty("apiKey", apiKeyField.getValue());
            o.addProperty("model", modelField.getValue());
            o.addProperty("triggerPrefix", prefixField.getValue());
            o.addProperty("maxTokens", pi(maxTokensField.getValue(), 2048));
            o.addProperty("temperature", pi(tempField.getValue(), 75) / 100.0);
            o.addProperty("contextMaxChars", pi(ctxField.getValue(), 20000));
            o.addProperty("thinkingLevel", pi(thinkingField.getValue(), 1));
            o.addProperty("maxToolCalls", pi(toolCallsField.getValue(), 15));
            o.addProperty("enableChatInterception", chatBtn.getMessage().getString().contains("开启"));
            o.addProperty("enableCommandExecution", cmdBtn.getMessage().getString().contains("开启"));
            o.addProperty("strictMode", strictBtn.getMessage().getString().contains("开启"));
            o.addProperty("enableAutoReview", autoReviewBtn.getMessage().getString().contains("开启"));
            o.addProperty("reviewIntervalMinutes", pi(reviewIntervalField.getValue(), 30));
            o.addProperty("yellowCardThreshold", pi(yellowCardField.getValue(), -30));
            o.addProperty("redCardThreshold", pi(redCardField.getValue(), -60));
            o.addProperty("scoreRecoveryPerInterval", pi(scoreRecoveryField.getValue(), 5));
            o.addProperty("approvalTimeoutMinutes", pi(approvalTimeoutField.getValue(), 10));
            o.addProperty("systemPromptPath", sysPromptPathField.getValue());
            o.addProperty("reviewPromptPath", reviewPromptPathField.getValue());
            o.addProperty("promptLanguage", promptLangField.getValue());
            try (Writer w = Files.newBufferedWriter(configPath)) { GSON.toJson(o, w); }
            statusWidget.setMessage(Component.translatable("mcai.config.saved"));
            minecraft.setScreen(parent);
        } catch (IOException e) {
            statusWidget.setMessage(Component.translatable("mcai.config.save_failed", e.getMessage()));
        }
    }

    private int pi(String s, int def) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; } }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        int totalH = 28 * ROW_H;
        int visibleH = height - 50;
        double maxS = Math.max(0, totalH - visibleH);
        scrollOffset = Math.clamp(scrollOffset - scrollY * ROW_H * 2, 0, maxS);
        rebuildWidgets();
        return true;
    }

    @Override
    public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
}
