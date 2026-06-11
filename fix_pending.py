import re
with open('src/main/java/com/example/mcai/handler/ChatHandler.java', 'r', encoding='utf-8') as f:
    src = f.read()

# Find and replace the needsApproval block in executeCommand
old = '''        if (needsApproval(command)) {
            int num = addPendingCommand(player.getUuid(), command);
            return "[需要审批] 已加入审批队列 #" + num + "，管理员可使用 /aiaccept " + num + " 批准";
        }'''

new = '''        if (needsApproval(command)) {
            int num = addPendingCommand(player.getUuid(), command);
            String key = player.getUuid() + ":" + num;
            CompletableFuture<String> future = new CompletableFuture<>();
            pendingFutures.put(key, future);
            notifyAdminsPending(player, command, num);
            try {
                String result = future.get(3, TimeUnit.MINUTES);
                return result != null ? result : "指令已执行";
            } catch (java.util.concurrent.TimeoutException e) {
                pendingFutures.remove(key);
                return "[审批超时] 3分钟内无人批准，指令已自动取消: /" + command;
            } catch (Exception e) {
                return "[审批异常] " + e.getMessage();
            }
        }'''

if old in src:
    src = src.replace(old, new)
    print('executeCommand: replaced')
else:
    print('executeCommand: NOT FOUND')
    # Try to find what's actually there
    m = re.search(r'if \(needsApproval\(command\)\) \{[^}]+return[^;]+;\n        \}', src)
    if m:
        print('Found:', repr(m.group()[:100]))

# onPlayerDisconnect
src = src.replace(
    'pendingCommands.remove(id);\n    }',
    'pendingCommands.remove(id);\n        pendingFutures.keySet().removeIf(k -> k.startsWith(id.toString() + ":"));\n    }')

# approveCommand - add pendingFutures release
src = src.replace(
    'src.sendFeedback(() -> Text.literal("§a[AI] 已批准 #" + num + " 并执行: /" + cmd), true);\n        return 1;',
    'src.sendFeedback(() -> Text.literal("§a[AI] 已批准 #" + num + " 并执行: /" + cmd), true);\n        String key = player.getUuid() + ":" + num;\n        CompletableFuture<String> f = pendingFutures.remove(key);\n        if (f != null) f.complete(result);\n        return 1;')

# rejectCommand - add pendingFutures release  
src = src.replace(
    'src.sendFeedback(() -> Text.literal("§c[AI] 已拒绝 #" + num + ": /" + cmd), true);\n        return 1;',
    'src.sendFeedback(() -> Text.literal("§c[AI] 已拒绝 #" + num + ": /" + cmd), true);\n        String key = player.getUuid() + ":" + num;\n        CompletableFuture<String> f = pendingFutures.remove(key);\n        if (f != null) f.complete("[审批拒绝] 管理员拒绝了指令: /" + cmd);\n        return 1;')

with open('src/main/java/com/example/mcai/handler/ChatHandler.java', 'w', encoding='utf-8') as f:
    f.write(src)
print('Done')
