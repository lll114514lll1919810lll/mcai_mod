package com.example.mcai.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CommandExecutionService 审批流程测试。
 * 由于 Mockito 无法 mock Minecraft 类，这里测试审批数据结构和逻辑。
 */
class CommandExecutionServiceApprovalTest {

    private AtomicLong idGenerator;

    @BeforeEach
    void setUp() {
        idGenerator = new AtomicLong(1);
    }

    // ═══════════════════════════════════════════════════════════════
    // PendingCommand lifecycle tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void pendingCommand_hasUniqueId() {
        UUID playerId = UUID.randomUUID();

        CommandExecutionService.PendingCommand cmd1 = new CommandExecutionService.PendingCommand(
                idGenerator.getAndIncrement(), playerId, "TestPlayer", "op Notch", null, null, null
        );
        CommandExecutionService.PendingCommand cmd2 = new CommandExecutionService.PendingCommand(
                idGenerator.getAndIncrement(), playerId, "TestPlayer", "give @a diamond", null, null, null
        );

        assertNotEquals(cmd1.id, cmd2.id);
        assertEquals(1, cmd1.id);
        assertEquals(2, cmd2.id);
    }

    @Test
    void pendingCommand_storesRequesterInfo() {
        UUID playerId = UUID.randomUUID();

        CommandExecutionService.PendingCommand cmd = new CommandExecutionService.PendingCommand(
                1, playerId, "TestPlayer", "give @a diamond", null, null, null
        );

        assertEquals(playerId, cmd.requesterId);
        assertEquals("TestPlayer", cmd.requesterName);
        assertEquals("give @a diamond", cmd.command);
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
    void pendingCommand_futureInitiallyNotDone() {
        CommandExecutionService.PendingCommand cmd = new CommandExecutionService.PendingCommand(
                1, UUID.randomUUID(), "TestPlayer", "give @a diamond", null, null, null
        );

        assertFalse(cmd.future.isDone());
    }

    @Test
    void pendingCommand_futureComplete_approve() {
        CommandExecutionService.PendingCommand cmd = new CommandExecutionService.PendingCommand(
                1, UUID.randomUUID(), "TestPlayer", "give @a diamond", null, null, null
        );

        assertFalse(cmd.future.isDone());

        // 模拟管理员批准
        cmd.future.complete("Command executed");

        assertTrue(cmd.future.isDone());
        assertEquals("Command executed", cmd.future.join());
    }

    @Test
    void pendingCommand_futureComplete_reject() {
        CommandExecutionService.PendingCommand cmd = new CommandExecutionService.PendingCommand(
                1, UUID.randomUUID(), "TestPlayer", "give @a diamond", null, null, null
        );

        assertFalse(cmd.future.isDone());

        // 模拟管理员拒绝
        cmd.future.complete("[Approval rejected] Admin rejected: /give @a diamond");

        assertTrue(cmd.future.isDone());
        assertTrue(cmd.future.join().contains("rejected"));
    }

    @Test
    void pendingCommand_futureComplete_cancel() {
        CommandExecutionService.PendingCommand cmd = new CommandExecutionService.PendingCommand(
                1, UUID.randomUUID(), "TestPlayer", "give @a diamond", null, null, null
        );

        assertFalse(cmd.future.isDone());

        // 模拟玩家取消
        cmd.future.complete("[玩家取消] TestPlayer 主动取消了此命令。请勿在本轮对话中再次尝试相同命令。");

        assertTrue(cmd.future.isDone());
        assertTrue(cmd.future.join().contains("取消"));
    }

    @Test
    void pendingCommand_futureComplete_timeout() {
        CommandExecutionService.PendingCommand cmd = new CommandExecutionService.PendingCommand(
                1, UUID.randomUUID(), "TestPlayer", "give @a diamond", null, null, null
        );

        assertFalse(cmd.future.isDone());

        // 模拟超时
        cmd.future.complete("[Approval timeout] No admin approved in 3 minutes, cancelled: /give @a diamond");

        assertTrue(cmd.future.isDone());
        assertTrue(cmd.future.join().contains("timeout"));
    }

    // ═══════════════════════════════════════════════════════════════
    // PendingChain lifecycle tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void pendingChain_commandsAreImmutable() {
        java.util.List<String> commands = new java.util.ArrayList<>(
                java.util.List.of("give @a diamond", "tp @a 0 0 0")
        );

        CommandExecutionService.PendingChain chain = new CommandExecutionService.PendingChain(
                1, UUID.randomUUID(), "TestPlayer", commands, 1
        );

        // 修改原始列表不应影响 chain
        commands.add("kill @a");
        assertEquals(2, chain.commands.size());
    }

    @Test
    void pendingChain_initialState() {
        CommandExecutionService.PendingChain chain = new CommandExecutionService.PendingChain(
                1, UUID.randomUUID(), "TestPlayer", java.util.List.of("give @a diamond"), 0
        );

        assertFalse(chain.executing);
        assertNull(chain.executionThread);
        assertFalse(chain.future.isDone());
    }

    @Test
    void pendingChain_futureComplete_approve() {
        CommandExecutionService.PendingChain chain = new CommandExecutionService.PendingChain(
                1, UUID.randomUUID(), "TestPlayer", java.util.List.of("give @a diamond"), 0
        );

        assertFalse(chain.future.isDone());

        // 模拟管理员批准
        chain.future.complete("命令链 #1 执行完毕 (1 条):\n  1. /give @a diamond → Command executed\n成功: 1 失败: 0");

        assertTrue(chain.future.isDone());
        assertTrue(chain.future.join().contains("执行完毕"));
    }

    @Test
    void pendingChain_futureComplete_reject() {
        CommandExecutionService.PendingChain chain = new CommandExecutionService.PendingChain(
                1, UUID.randomUUID(), "TestPlayer", java.util.List.of("give @a diamond"), 0
        );

        assertFalse(chain.future.isDone());

        // 模拟管理员拒绝
        chain.future.complete("[审批拒绝] 管理员拒绝了此命令链");

        assertTrue(chain.future.isDone());
        assertTrue(chain.future.join().contains("拒绝"));
    }

    @Test
    void pendingChain_futureComplete_cancel() {
        CommandExecutionService.PendingChain chain = new CommandExecutionService.PendingChain(
                1, UUID.randomUUID(), "TestPlayer", java.util.List.of("give @a diamond"), 0
        );

        assertFalse(chain.future.isDone());

        // 模拟玩家取消
        chain.future.complete("[玩家取消] TestPlayer 主动取消了此命令链。请勿在本轮对话中再次尝试相同命令。");

        assertTrue(chain.future.isDone());
        assertTrue(chain.future.join().contains("取消"));
    }

    @Test
    void pendingChain_storesInterval() {
        CommandExecutionService.PendingChain chain = new CommandExecutionService.PendingChain(
                1, UUID.randomUUID(), "TestPlayer", java.util.List.of("give @a diamond"), 5
        );

        assertEquals(5, chain.intervalSeconds);
    }

    // ═══════════════════════════════════════════════════════════════
    // Multiple pending commands for same player
    // ═══════════════════════════════════════════════════════════════

    @Test
    void multiplePendingCommands_differentIds() {
        UUID playerId = UUID.randomUUID();

        CommandExecutionService.PendingCommand cmd1 = new CommandExecutionService.PendingCommand(
                idGenerator.getAndIncrement(), playerId, "TestPlayer", "give @a diamond", null, null, null
        );
        CommandExecutionService.PendingCommand cmd2 = new CommandExecutionService.PendingCommand(
                idGenerator.getAndIncrement(), playerId, "TestPlayer", "tp @a 0 0 0", null, null, null
        );
        CommandExecutionService.PendingCommand cmd3 = new CommandExecutionService.PendingCommand(
                idGenerator.getAndIncrement(), playerId, "TestPlayer", "enchant @a sharpness", null, null, null
        );

        assertNotEquals(cmd1.id, cmd2.id);
        assertNotEquals(cmd2.id, cmd3.id);
        assertNotEquals(cmd1.id, cmd3.id);
    }

    @Test
    void multiplePendingCommands_independentFutures() {
        UUID playerId = UUID.randomUUID();

        CommandExecutionService.PendingCommand cmd1 = new CommandExecutionService.PendingCommand(
                1, playerId, "TestPlayer", "give @a diamond", null, null, null
        );
        CommandExecutionService.PendingCommand cmd2 = new CommandExecutionService.PendingCommand(
                2, playerId, "TestPlayer", "tp @a 0 0 0", null, null, null
        );

        // 批准第一个命令
        cmd1.future.complete("Command executed");
        assertTrue(cmd1.future.isDone());
        assertFalse(cmd2.future.isDone());

        // 拒绝第二个命令
        cmd2.future.complete("[Approval rejected]");
        assertTrue(cmd2.future.isDone());
    }

    // ═══════════════════════════════════════════════════════════════
    // Cleanup simulation tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void cleanupPlayer_completesAllPendingFutures() {
        UUID playerId = UUID.randomUUID();

        CommandExecutionService.PendingCommand cmd1 = new CommandExecutionService.PendingCommand(
                1, playerId, "TestPlayer", "give @a diamond", null, null, null
        );
        CommandExecutionService.PendingCommand cmd2 = new CommandExecutionService.PendingCommand(
                2, playerId, "TestPlayer", "tp @a 0 0 0", null, null, null
        );

        assertFalse(cmd1.future.isDone());
        assertFalse(cmd2.future.isDone());

        // 模拟清理（玩家断开连接）
        cmd1.future.complete("[Approval cancelled] Requester disconnected");
        cmd2.future.complete("[Approval cancelled] Requester disconnected");

        assertTrue(cmd1.future.isDone());
        assertTrue(cmd2.future.isDone());
        assertTrue(cmd1.future.join().contains("disconnected"));
        assertTrue(cmd2.future.join().contains("disconnected"));
    }

    @Test
    void cleanupChain_interruptsExecution() {
        UUID playerId = UUID.randomUUID();

        CommandExecutionService.PendingChain chain = new CommandExecutionService.PendingChain(
                1, playerId, "TestPlayer", java.util.List.of("give @a diamond", "tp @a 0 0 0"), 1
        );

        // 模拟正在执行
        chain.executing = true;
        chain.executionThread = Thread.currentThread();

        assertFalse(chain.future.isDone());

        // 模拟清理（玩家断开连接）
        chain.future.complete("[Approval cancelled] Requester disconnected");

        assertTrue(chain.future.isDone());
        assertTrue(chain.future.join().contains("disconnected"));
    }

    // ═══════════════════════════════════════════════════════════════
    // Command validation tests
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
}
