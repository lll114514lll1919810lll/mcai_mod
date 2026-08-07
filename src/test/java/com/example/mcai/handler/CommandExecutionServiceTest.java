package com.example.mcai.handler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CommandExecutionService 纯逻辑测试（不依赖 Mockito）。
 * 测试 normalizeCommand 和数据结构。
 */
class CommandExecutionServiceTest {

    // ═══════════════════════════════════════════════════════════════
    // normalizeCommand tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void normalizeCommand_removesLeadingSlash() {
        assertEquals("give @a diamond", CommandExecutionService.normalizeCommand("/give @a diamond"));
    }

    @Test
    void normalizeCommand_removesMultipleSlashes() {
        assertEquals("give @a diamond", CommandExecutionService.normalizeCommand("///give @a diamond"));
    }

    @Test
    void normalizeCommand_handlesNull() {
        assertEquals("", CommandExecutionService.normalizeCommand(null));
    }

    @Test
    void normalizeCommand_handlesEmptyString() {
        assertEquals("", CommandExecutionService.normalizeCommand(""));
    }

    @Test
    void normalizeCommand_handlesWhitespace() {
        assertEquals("give @a diamond", CommandExecutionService.normalizeCommand("  /give @a diamond  "));
    }

    @Test
    void normalizeCommand_noSlash() {
        assertEquals("give @a diamond", CommandExecutionService.normalizeCommand("give @a diamond"));
    }

    @Test
    void normalizeCommand_slashOnly() {
        assertEquals("", CommandExecutionService.normalizeCommand("/"));
    }

    @Test
    void normalizeCommand_multipleSlashesOnly() {
        assertEquals("", CommandExecutionService.normalizeCommand("///"));
    }

    // ═══════════════════════════════════════════════════════════════
    // PendingCommand tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void pendingCommand_hasUniqueId() {
        UUID playerId = UUID.randomUUID();
        CommandExecutionService.PendingCommand cmd1 = new CommandExecutionService.PendingCommand(
                1, playerId, "TestPlayer", "give @a diamond", null, null, null
        );
        CommandExecutionService.PendingCommand cmd2 = new CommandExecutionService.PendingCommand(
                2, playerId, "TestPlayer", "give @a iron", null, null, null
        );

        assertNotEquals(cmd1.id, cmd2.id);
    }

    @Test
    void pendingCommand_storesCommand() {
        CommandExecutionService.PendingCommand cmd = new CommandExecutionService.PendingCommand(
                1, UUID.randomUUID(), "TestPlayer", "give @a diamond", null, null, null
        );

        assertEquals("give @a diamond", cmd.command);
    }

    @Test
    void pendingCommand_storesRequesterInfo() {
        UUID playerId = UUID.randomUUID();
        CommandExecutionService.PendingCommand cmd = new CommandExecutionService.PendingCommand(
                1, playerId, "TestPlayer", "give @a diamond", null, null, null
        );

        assertEquals(playerId, cmd.requesterId);
        assertEquals("TestPlayer", cmd.requesterName);
    }

    @Test
    void pendingCommand_hasCreatedAt() {
        long before = System.currentTimeMillis();
        CommandExecutionService.PendingCommand cmd = new CommandExecutionService.PendingCommand(
                1, UUID.randomUUID(), "TestPlayer", "give @a diamond", null, null, null
        );
        long after = System.currentTimeMillis();

        assertTrue(cmd.createdAt >= before);
        assertTrue(cmd.createdAt <= after);
    }

    @Test
    void pendingCommand_futureIsNotDone() {
        CommandExecutionService.PendingCommand cmd = new CommandExecutionService.PendingCommand(
                1, UUID.randomUUID(), "TestPlayer", "give @a diamond", null, null, null
        );

        assertFalse(cmd.future.isDone());
    }

    // ═══════════════════════════════════════════════════════════════
    // PendingChain tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void pendingChain_commandsAreImmutable() {
        List<String> commands = new ArrayList<>(List.of("give @a diamond", "tp @a 0 0 0"));
        CommandExecutionService.PendingChain chain = new CommandExecutionService.PendingChain(
                1, UUID.randomUUID(), "TestPlayer", commands, 1
        );

        // 修改原始列表不应影响 chain
        commands.add("kill @a");
        assertEquals(2, chain.commands.size());
    }

    @Test
    void pendingChain_storesInterval() {
        CommandExecutionService.PendingChain chain = new CommandExecutionService.PendingChain(
                1, UUID.randomUUID(), "TestPlayer", List.of("give @a diamond"), 5
        );

        assertEquals(5, chain.intervalSeconds);
    }

    @Test
    void pendingChain_initialState() {
        CommandExecutionService.PendingChain chain = new CommandExecutionService.PendingChain(
                1, UUID.randomUUID(), "TestPlayer", List.of("give @a diamond"), 0
        );

        assertFalse(chain.executing);
        assertNull(chain.executionThread);
        assertFalse(chain.future.isDone());
    }

    @Test
    void pendingChain_hasCreatedAt() {
        long before = System.currentTimeMillis();
        CommandExecutionService.PendingChain chain = new CommandExecutionService.PendingChain(
                1, UUID.randomUUID(), "TestPlayer", List.of("give @a diamond"), 0
        );
        long after = System.currentTimeMillis();

        assertTrue(chain.createdAt >= before);
        assertTrue(chain.createdAt <= after);
    }

    // ═══════════════════════════════════════════════════════════════
    // isAdminOrConsole tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void normalizeCommand_commandWithArgs() {
        assertEquals("tp @a 100 64 200", CommandExecutionService.normalizeCommand("/tp @a 100 64 200"));
    }

    @Test
    void normalizeCommand_commandWithLeadingSpaces() {
        assertEquals("give @a diamond", CommandExecutionService.normalizeCommand("   give @a diamond"));
    }
}
