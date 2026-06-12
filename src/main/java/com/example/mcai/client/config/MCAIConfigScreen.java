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

    private final Screen parent;
    private final Path configPath;
    private JsonObject cfg;
    private double scrollOffset;
    private StringWidget statusWidget;

    private EditBox apiEndpointField, apiKeyField, modelField, prefixField;
    private EditBox maxTokensField, tempField, ctxField, thinkingField, toolCallsField;
    private EditBox sysPromptPathField, reviewPromptPathField;
    private EditBox reviewIntervalField, yellowCardField, redCardField;
    private EditBox scoreRecoveryField, approvalTimeoutField;
    private Button chatBtn, cmdBtn, strictBtn, autoReviewBtn;

    public MCAIConfigScreen(Screen parent) {
        super(Component.literal("MCAI 设置"));
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
    protected void init() {
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        int cx = width / 2;
        int inX = cx + 5;
        int fw = Math.min(180, width - inX - 10);
        int kw = Math.min(300, width - inX - 10);
        int r = 0;

        t(cx - 40, 8, 100, "§lMCAI 设置"); r++;

        // ── API ──
        l(inX, ry(r), "§e=== API ==="); r++;
        apiEndpointField = f(inX, ry(r), kw, gs("apiEndpoint", ""), "API 地址");         l(Math.max(cx - 200, 5), ry(r), "API 地址"); r++;
        apiKeyField     = f(inX, ry(r), kw, gs("apiKey", ""), "API 密钥");                l(Math.max(cx - 200, 5), ry(r), "API 密钥"); r++;
        modelField      = f(inX, ry(r), fw, gs("model", "deepseek-v4-flash"), "模型名称"); l(Math.max(cx - 200, 5), ry(r), "模型名称"); r++;

        // ── AI ──
        l(inX, ry(r), "§e=== AI 设置 ==="); r++;
        prefixField     = f(inX, ry(r), 80, gs("triggerPrefix", "!ai"), "触发前缀");   l(Math.max(cx - 200, 5), ry(r), "触发前缀"); r++;
        maxTokensField  = n(inX, ry(r), gi("maxTokens", 2048), "单次回复最大token数");   l(Math.max(cx - 200, 5), ry(r), "最大令牌"); r++;
        tempField       = n(inX, ry(r), (int)(gd("temperature", 0.75) * 100), "回复随机性"); l(Math.max(cx - 200, 5), ry(r), "温度 (0-100)"); r++;
        ctxField        = n(inX, ry(r), gi("contextMaxChars", 20000), "对话历史最大字符数"); l(Math.max(cx - 200, 5), ry(r), "上下文字符上限"); r++;
        thinkingField   = n(inX, ry(r), gi("thinkingLevel", 1), "0=关 1=开 3=最强");    l(Math.max(cx - 200, 5), ry(r), "思考等级 0-3"); r++;
        toolCallsField  = n(inX, ry(r), gi("maxToolCalls", 15), "单次对话最多工具调用");  l(Math.max(cx - 200, 5), ry(r), "工具调用上限"); r++;

        // ── 行为 ──
        l(inX, ry(r), "§e=== 行为审查 ==="); r++;
        tg(inX, fw, r, "聊天监听", gb("enableChatInterception", true), b -> chatBtn = b); r++;
        tg(inX, fw, r, "指令执行", gb("enableCommandExecution", true), b -> cmdBtn = b); r++;
        tg(inX, fw, r, "严格模式", gb("strictMode", true), b -> strictBtn = b); r++;
        tg(inX, fw, r, "自动审查", gb("enableAutoReview", true), b -> autoReviewBtn = b); r++;
        reviewIntervalField  = n(inX, ry(r), gi("reviewIntervalMinutes", 30), "自动审查间隔(分)");     l(Math.max(cx - 200, 5), ry(r), "审查间隔(分)"); r++;
        yellowCardField      = n(inX, ry(r), gi("yellowCardThreshold", -30), "低于此分触发黄牌");       l(Math.max(cx - 200, 5), ry(r), "黄牌阈值"); r++;
        redCardField         = n(inX, ry(r), gi("redCardThreshold", -60), "低于此分触发红牌");          l(Math.max(cx - 200, 5), ry(r), "红牌阈值"); r++;
        scoreRecoveryField   = n(inX, ry(r), gi("scoreRecoveryPerInterval", 5), "每周期自动恢复分数");  l(Math.max(cx - 200, 5), ry(r), "每周期恢复"); r++;
        approvalTimeoutField = n(inX, ry(r), gi("approvalTimeoutMinutes", 10), "踢出审批超时(分)");     l(Math.max(cx - 200, 5), ry(r), "审批超时(分)"); r++;

        // ── 提示词 ──
        l(inX, ry(r), "§e=== 提示词 ==="); r++;
        sysPromptPathField    = f(inX, ry(r), kw, gs("systemPromptPath", ""), "AI提示词文件(空=内置)");   l(Math.max(cx - 200, 5), ry(r), "AI 提示词"); r++;
        reviewPromptPathField = f(inX, ry(r), kw, gs("reviewPromptPath", ""), "审查提示词文件(空=内置)"); l(Math.max(cx - 200, 5), ry(r), "审查提示词"); r++;

        // ── 底部按钮 ──
        statusWidget = new StringWidget(0, height - 25, width, 20, Component.literal(""), font);
        addRenderableWidget(statusWidget);
        int by = height - 25;
        addRenderableWidget(Button.builder(Component.literal("§a保存并关闭"), b -> save())
                .bounds(cx - 105, by, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§7取消"), b -> onClose())
                .bounds(cx + 5, by, 100, 20).build());
    }

    private void l(int x, int y, String text) { addRenderableWidget(new StringWidget(x, y, 150, 20, Component.literal(text), font)); }
    private EditBox f(int x, int y, int w, String val, String tip) {
        EditBox e = new EditBox(font, x, y, w, 20, Component.literal(""));
        e.setMaxLength(1024); e.setValue(val);
        e.setTooltip(Tooltip.create(Component.literal(tip)));
        addRenderableWidget(e); return e;
    }
    private EditBox n(int x, int y, int val, String tip) {
        EditBox e = new EditBox(font, x, y, 80, 20, Component.literal(""));
        e.setMaxLength(12); e.setValue(String.valueOf(val));
        e.setTooltip(Tooltip.create(Component.literal(tip)));
        addRenderableWidget(e); return e;
    }
    private void t(int x, int y, int w, String text) {
        addRenderableWidget(new StringWidget(x, y, w, 20, Component.literal(text), font));
    }
    private void tg(int inX, int fw, int row, String label, boolean initial, Consumer<Button> setter) {
        int x = inX + fw + 5; int y = ry(row);
        l(Math.max(x - 200, 5), y, label);
        Button btn = Button.builder(Component.literal(initial ? "§a开启" : "§c关闭"), b -> {
            b.setMessage(Component.literal(b.getMessage().getString().contains("开启") ? "§c关闭" : "§a开启"));
        }).bounds(x, y, 60, 20).build();
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
            try (Writer w = Files.newBufferedWriter(configPath)) { GSON.toJson(o, w); }
            statusWidget.setMessage(Component.literal("§a✓ 已保存"));
            minecraft.setScreen(parent);
        } catch (IOException e) {
            statusWidget.setMessage(Component.literal("§c保存失败: " + e.getMessage()));
        }
    }

    private int pi(String s, int def) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; } }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        int totalH = 28 * ROW_H; // enough for all rows
        int visibleH = height - 50;
        double maxS = Math.max(0, totalH - visibleH);
        scrollOffset = Math.clamp(scrollOffset - scrollY * ROW_H * 2, 0, maxS);
        rebuildWidgets();
        return true;
    }

    @Override
    public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
}
