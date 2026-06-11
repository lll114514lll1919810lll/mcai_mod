package com.example.mcai.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
import java.util.Arrays;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class MCAIConfigScreen extends Screen {
    private static final int LABEL_W = 120;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Screen parent;
    private final Path configPath;
    private JsonObject cfg;

    private EditBox apiEndpointField, apiKeyField, modelField;
    private EditBox prefixField, maxTokensField, tempField;
    private EditBox ctxField, thinkingField, toolCallsField, approvalField;
    private Button chatBtn, cmdBtn, strictBtn;

    private String status = "";
    private int statusTimer = 0;

    public MCAIConfigScreen(Screen parent) {
        super(Component.literal("MCAI 设置"));
        this.parent = parent;
        this.configPath = FabricLoader.getInstance().getConfigDir().resolve("mcai.json");
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

    private String get(String key, String def) {
        return cfg.has(key) ? cfg.get(key).getAsString() : def;
    }
    private int getInt(String key, int def) {
        return cfg.has(key) ? cfg.get(key).getAsInt() : def;
    }
    private double getDouble(String key, double def) {
        return cfg.has(key) ? cfg.get(key).getAsDouble() : def;
    }
    private boolean getBool(String key, boolean def) {
        return cfg.has(key) ? cfg.get(key).getAsBoolean() : def;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int leftX = Math.max(cx - 200, 5);
        int inX = cx + 5;
        int fieldW = Math.min(180, width - inX - 10);
        int keyW = Math.min(300, width - inX - 10);
        int rows = 13;
        int rowH = Math.min(24, (height - 100) / rows);
        int sy = 30;

        apiEndpointField = mkField(inX, sy + rowH * 0, keyW, get("apiEndpoint", ""), "API 地址");
        apiKeyField    = mkField(inX, sy + rowH * 1, keyW, get("apiKey", ""), "API 密钥");
        modelField     = mkField(inX, sy + rowH * 2, fieldW, get("model", "deepseek-v4-flash"), "模型名称");
        prefixField    = mkField(inX, sy + rowH * 3, 80, get("triggerPrefix", "!ai"), "触发前缀");
        maxTokensField = mkNumField(inX, sy + rowH * 4, getInt("maxTokens", 1024), "最大令牌");
        tempField      = mkNumField(inX, sy + rowH * 5, (int)(getDouble("temperature", 0.7) * 100), "温度(0-100)");
        ctxField       = mkNumField(inX, sy + rowH * 6, getInt("contextMaxChars", 20000), "上下文字符上限");
        thinkingField  = mkNumField(inX, sy + rowH * 7, getInt("thinkingLevel", 0), "思考等级");
        toolCallsField = mkNumField(inX, sy + rowH * 8, getInt("maxToolCalls", 5), "工具调用上限");

        drawLabel(leftX, sy + rowH * 0, "API 地址");
        drawLabel(leftX, sy + rowH * 1, "API 密钥");
        drawLabel(leftX, sy + rowH * 2, "模型名称");
        drawLabel(leftX, sy + rowH * 3, "触发前缀");
        drawLabel(leftX, sy + rowH * 4, "最大令牌");
        drawLabel(leftX, sy + rowH * 5, "温度 (%)");
        drawLabel(leftX, sy + rowH * 6, "上下文字符上限");
        drawLabel(leftX, sy + rowH * 7, "思考等级 0-3");
        drawLabel(leftX, sy + rowH * 8, "工具调用上限");

        drawLabel(leftX, sy + rowH * 9, "聊天监听");
        chatBtn = mkToggle(inX + fieldW + 5, sy + rowH * 9, getBool("enableChatInterception", true));

        drawLabel(leftX, sy + rowH * 10, "指令执行");
        cmdBtn = mkToggle(inX + fieldW + 5, sy + rowH * 10, getBool("enableCommandExecution", true));

        drawLabel(leftX, sy + rowH * 11, "需审批指令");
        approvalField = new EditBox(font, inX, sy + rowH * 11, fieldW, 20, Component.literal(""));
        approvalField.setMaxLength(2048);
        if (cfg.has("requireApprovalCommands")) {
            JsonArray arr = cfg.getAsJsonArray("requireApprovalCommands");
            String val = arr.asList().stream()
                    .map(e -> e.getAsString()).collect(Collectors.joining(", "));
            approvalField.setValue(val);
        }
        approvalField.setTooltip(Tooltip.create(Component.literal("逗号分隔的命令列表")));
        addRenderableWidget(approvalField);

        drawLabel(leftX, sy + rowH * 12, "严格模式");
        strictBtn = mkToggle(inX + fieldW + 5, sy + rowH * 12, getBool("strictMode", false));

        int by = sy + rowH * 13 + 15;
        var saveBtn = Button.builder(Component.literal("§a保存并关闭"), b -> save())
                .pos(cx - 105, by).size(100, 20).build();
        saveBtn.setTooltip(Tooltip.create(Component.literal("§7保存后需手动执行 /aireload 或重新进入游戏以应用配置")));
        addRenderableWidget(saveBtn);
        addRenderableWidget(Button.builder(Component.literal("§7取消"), b -> onClose())
                .pos(cx + 5, by).size(100, 20).build());
    }

    private EditBox mkField(int x, int y, int w, String val, String tooltip) {
        EditBox f = new EditBox(font, x, y, w, 20, Component.literal(""));
        f.setMaxLength(1024);
        f.setValue(val);
        f.setTooltip(Tooltip.create(Component.literal(tooltip)));
        addRenderableWidget(f);
        return f;
    }

    private EditBox mkNumField(int x, int y, int val, String tooltip) {
        EditBox f = new EditBox(font, x, y, 80, 20, Component.literal(""));
        f.setMaxLength(8);
        f.setValue(String.valueOf(val));
        f.setTooltip(Tooltip.create(Component.literal(tooltip)));
        addRenderableWidget(f);
        return f;
    }

    private void drawLabel(int x, int y, String text) {
        addRenderableWidget(new StringWidget(x, y, LABEL_W, 20,
                Component.literal(text), font));
    }

    private Button mkToggle(int x, int y, boolean initial) {
        Button b = Button.builder(
                Component.literal(initial ? "§a开启" : "§c关闭"), btn -> {
                    boolean now = btn.getMessage().getString().contains("开启");
                    btn.setMessage(Component.literal(now ? "§c关闭" : "§a开启"));
                })
                .pos(x, y).size(60, 20).build();
        addRenderableWidget(b);
        return b;
    }

    private void save() {
        try {
            Files.createDirectories(configPath.getParent());
            JsonObject o;
            if (Files.exists(configPath)) {
                try (Reader r = Files.newBufferedReader(configPath)) {
                    o = GSON.fromJson(r, JsonObject.class);
                } catch (Exception e) {
                    o = new JsonObject();
                }
            } else {
                o = new JsonObject();
            }

            o.addProperty("apiEndpoint", apiEndpointField.getValue());
            o.addProperty("apiKey", apiKeyField.getValue());
            o.addProperty("model", modelField.getValue());
            o.addProperty("triggerPrefix", prefixField.getValue());
            o.addProperty("maxTokens", parseInt(maxTokensField.getValue(), 1024));
            o.addProperty("temperature", parseInt(tempField.getValue(), 70) / 100.0);
            o.addProperty("contextMaxChars", parseInt(ctxField.getValue(), 20000));
            o.addProperty("thinkingLevel", parseInt(thinkingField.getValue(), 0));
            o.addProperty("maxToolCalls", parseInt(toolCallsField.getValue(), 5));
            o.addProperty("enableChatInterception", chatBtn.getMessage().getString().contains("开启"));
            o.addProperty("enableCommandExecution", cmdBtn.getMessage().getString().contains("开启"));
            o.addProperty("strictMode", strictBtn.getMessage().getString().contains("开启"));

            JsonArray arr = new JsonArray();
            Arrays.stream(approvalField.getValue().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .forEach(arr::add);
            o.add("requireApprovalCommands", arr);

            try (Writer w = Files.newBufferedWriter(configPath)) {
                GSON.toJson(o, w);
            }
            status = "§a✓ 已保存";
            statusTimer = 100;
            minecraft.setScreen(parent);
        } catch (IOException e) {
            status = "§c保存失败: " + e.getMessage();
            statusTimer = 200;
        }
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    @Override
    public void tick() {
        if (statusTimer > 0) statusTimer--;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
        super.extractRenderState(graphics, mx, my, delta);
        graphics.text(font, title, width / 2 - font.width(title) / 2, 12, 0xffffff);
        if (statusTimer > 0) {
            Component statusComp = Component.literal(status);
            graphics.text(font, statusComp, width / 2 - font.width(statusComp) / 2, height - 20, 0xffffaa);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
