package com.example.mcai.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

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

    private TextFieldWidget apiEndpointField, apiKeyField, modelField;
    private TextFieldWidget prefixField, maxTokensField, tempField;
    private TextFieldWidget ctxField, thinkingField, toolCallsField, approvalField;
    private ButtonWidget chatBtn, cmdBtn;

    private String status = "";
    private int statusTimer = 0;

    public MCAIConfigScreen(Screen parent) {
        super(Text.literal("MCAI 设置"));
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
        int rows = 12;
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
        approvalField = new TextFieldWidget(textRenderer, inX, sy + rowH * 11, fieldW, 20, Text.literal(""));
        if (cfg.has("requireApprovalCommands")) {
            JsonArray arr = cfg.getAsJsonArray("requireApprovalCommands");
            String val = arr.asList().stream()
                    .map(e -> e.getAsString()).collect(Collectors.joining(", "));
            approvalField.setText(val);
        }
        approvalField.setTooltip(Tooltip.of(Text.literal("逗号分隔的命令列表")));
        addDrawableChild(approvalField);

        int by = sy + rowH * 12 + 15;
        addDrawableChild(ButtonWidget.builder(Text.literal("§a保存并重载"), b -> save())
                .dimensions(cx - 105, by, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("§7取消"), b -> close())
                .dimensions(cx + 5, by, 100, 20).build());
    }

    private TextFieldWidget mkField(int x, int y, int w, String val, String tooltip) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 20, Text.literal(""));
        f.setMaxLength(1024);
        f.setText(val);
        f.setTooltip(Tooltip.of(Text.literal(tooltip)));
        addDrawableChild(f);
        return f;
    }

    private TextFieldWidget mkNumField(int x, int y, int val, String tooltip) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, 80, 20, Text.literal(""));
        f.setMaxLength(8);
        f.setTextPredicate(s -> s.matches("\\d*"));
        f.setText(String.valueOf(val));
        f.setTooltip(Tooltip.of(Text.literal(tooltip)));
        addDrawableChild(f);
        return f;
    }

    private void drawLabel(int x, int y, String text) {
        addDrawableChild(new net.minecraft.client.gui.widget.TextWidget(x, y, LABEL_W, 20,
                Text.literal(text), textRenderer));
    }

    private ButtonWidget mkToggle(int x, int y, boolean initial) {
        ButtonWidget b = ButtonWidget.builder(
                Text.literal(initial ? "§a开启" : "§c关闭"), btn -> {
                    boolean now = btn.getMessage().getString().contains("开启");
                    btn.setMessage(Text.literal(now ? "§c关闭" : "§a开启"));
                })
                .dimensions(x, y, 60, 20).build();
        addDrawableChild(b);
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

            o.addProperty("apiEndpoint", apiEndpointField.getText());
            o.addProperty("apiKey", apiKeyField.getText());
            o.addProperty("model", modelField.getText());
            o.addProperty("triggerPrefix", prefixField.getText());
            o.addProperty("maxTokens", parseInt(maxTokensField.getText(), 1024));
            o.addProperty("temperature", parseInt(tempField.getText(), 70) / 100.0);
            o.addProperty("contextMaxChars", parseInt(ctxField.getText(), 20000));
            o.addProperty("thinkingLevel", parseInt(thinkingField.getText(), 0));
            o.addProperty("maxToolCalls", parseInt(toolCallsField.getText(), 5));
            o.addProperty("enableChatInterception", chatBtn.getMessage().getString().contains("开启"));
            o.addProperty("enableCommandExecution", cmdBtn.getMessage().getString().contains("开启"));

            JsonArray arr = new JsonArray();
            Arrays.stream(approvalField.getText().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .forEach(arr::add);
            o.add("requireApprovalCommands", arr);

            try (Writer w = Files.newBufferedWriter(configPath)) {
                GSON.toJson(o, w);
            }

            // Reload: direct call for single player, command for dedicated server
            if (client != null && client.getServer() != null) {
                // Single player / integrated server: reload directly (bypasses cheat permission)
                com.example.mcai.MCAIMod.getInstance().reloadConfig();
                status = "§a✓ 配置已保存并重载";
                statusTimer = 100;
            } else if (client != null && client.player != null) {
                // Dedicated server: send reload command (requires permission)
                client.player.networkHandler.sendChatCommand("aireload");
                status = "§a✓ 配置已保存，重载指令已发送";
                statusTimer = 100;
            } else {
                status = "§a✓ 配置已保存 (§6请手动执行 /aireload)";
                statusTimer = 150;
            }
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
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xffffff);
        if (statusTimer > 0) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(status), width / 2, height - 20, 0xffffaa);
        }
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
