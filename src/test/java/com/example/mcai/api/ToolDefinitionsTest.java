package com.example.mcai.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ToolDefinitionsTest {

    @Test
    void buildAll_returns10Tools() {
        JsonArray tools = ToolDefinitions.buildAll();

        assertEquals(10, tools.size());
    }

    @Test
    void buildAll_allToolsHaveCorrectStructure() {
        JsonArray tools = ToolDefinitions.buildAll();

        for (int i = 0; i < tools.size(); i++) {
            JsonObject tool = tools.get(i).getAsJsonObject();
            assertEquals("function", tool.get("type").getAsString(), "Tool " + i + " should have type=function");

            JsonObject fn = tool.getAsJsonObject("function");
            assertNotNull(fn, "Tool " + i + " should have function object");
            assertTrue(fn.has("name"), "Tool " + i + " should have name");
            assertTrue(fn.has("description"), "Tool " + i + " should have description");
            assertTrue(fn.has("parameters"), "Tool " + i + " should have parameters");
        }
    }

    @Test
    void buildAll_allToolNamesAreUnique() {
        JsonArray tools = ToolDefinitions.buildAll();
        Set<String> names = new HashSet<>();

        for (int i = 0; i < tools.size(); i++) {
            String name = tools.get(i).getAsJsonObject()
                    .getAsJsonObject("function")
                    .get("name").getAsString();
            assertTrue(names.add(name), "Duplicate tool name: " + name);
        }

        assertEquals(10, names.size());
    }

    @Test
    void buildAll_containsExpectedTools() {
        JsonArray tools = ToolDefinitions.buildAll();
        Set<String> names = new HashSet<>();

        for (int i = 0; i < tools.size(); i++) {
            names.add(tools.get(i).getAsJsonObject()
                    .getAsJsonObject("function")
                    .get("name").getAsString());
        }

        assertTrue(names.contains("search_knowledge_base"));
        assertTrue(names.contains("execute_minecraft_command"));
        assertTrue(names.contains("execute_command_chain"));
        assertTrue(names.contains("get_server_status"));
        assertTrue(names.contains("get_game_rules"));
        assertTrue(names.contains("get_debug_info"));
        assertTrue(names.contains("get_installed_mods"));
        assertTrue(names.contains("get_player_effects"));
        assertTrue(names.contains("get_player_advancements"));
        assertTrue(names.contains("get_player_inventory"));
    }

    @Test
    void buildAll_commandChainHasCorrectParameters() {
        JsonArray tools = ToolDefinitions.buildAll();
        JsonObject chainTool = null;

        for (int i = 0; i < tools.size(); i++) {
            JsonObject tool = tools.get(i).getAsJsonObject();
            String name = tool.getAsJsonObject("function").get("name").getAsString();
            if ("execute_command_chain".equals(name)) {
                chainTool = tool;
                break;
            }
        }

        assertNotNull(chainTool, "Should find execute_command_chain tool");

        JsonObject params = chainTool.getAsJsonObject("function").getAsJsonObject("parameters");
        JsonObject props = params.getAsJsonObject("properties");

        assertTrue(props.has("commands"), "Chain tool should have commands parameter");
        assertTrue(props.has("interval"), "Chain tool should have interval parameter");

        // commands 应该是 array 类型
        assertEquals("array", props.getAsJsonObject("commands").get("type").getAsString());

        // interval 应该是 integer 类型
        assertEquals("integer", props.getAsJsonObject("interval").get("type").getAsString());
    }

    @Test
    void buildAll_noParamToolsHaveEmptyParameters() {
        JsonArray tools = ToolDefinitions.buildAll();

        // 无参数工具列表
        Set<String> noParamTools = Set.of(
                "get_server_status", "get_game_rules", "get_debug_info",
                "get_installed_mods", "get_player_effects",
                "get_player_advancements", "get_player_inventory"
        );

        for (int i = 0; i < tools.size(); i++) {
            JsonObject tool = tools.get(i).getAsJsonObject();
            String name = tool.getAsJsonObject("function").get("name").getAsString();

            if (noParamTools.contains(name)) {
                JsonObject params = tool.getAsJsonObject("function").getAsJsonObject("parameters");
                assertEquals("object", params.get("type").getAsString());
                assertTrue(params.getAsJsonObject("properties").size() == 0,
                        name + " should have empty properties");
            }
        }
    }

    @Test
    void buildAll_singleParamToolsHaveCorrectStructure() {
        JsonArray tools = ToolDefinitions.buildAll();

        // 单参数工具
        Set<String> singleParamTools = Set.of(
                "search_knowledge_base", "execute_minecraft_command"
        );

        for (int i = 0; i < tools.size(); i++) {
            JsonObject tool = tools.get(i).getAsJsonObject();
            String name = tool.getAsJsonObject("function").get("name").getAsString();

            if (singleParamTools.contains(name)) {
                JsonObject params = tool.getAsJsonObject("function").getAsJsonObject("parameters");
                JsonObject props = params.getAsJsonObject("properties");
                assertTrue(params.has("required"), name + " should have required array");

                // 应该只有一个必需参数
                assertEquals(1, params.getAsJsonArray("required").size(),
                        name + " should have exactly one required parameter");
            }
        }
    }
}
