import subprocess, re, sys

# Get original from git
result = subprocess.run(['git', 'show', 'mc-26.1.2-server:src/main/java/com/example/mcai/handler/ChatHandler.java'],
                       capture_output=True)
src = result.stdout.decode('utf-8')

# ---- Package/Import replacements ----
repl_imports = [
    ('net.minecraft.network.chat.Component', 'net.minecraft.text.Text'),
    ('net.minecraft.commands.CommandSourceStack', 'net.minecraft.server.command.ServerCommandSource'),
    ('net.minecraft.commands.Commands', 'net.minecraft.server.command.CommandManager'),
    ('net.minecraft.server.level.ServerPlayer', 'net.minecraft.server.network.ServerPlayerEntity'),
    ('net.minecraft.server.level.ServerLevel', 'net.minecraft.server.world.ServerWorld'),
    ('net.minecraft.server.permissions.LevelBasedPermissionSet', 'net.minecraft.command.permission.LeveledPermissionPredicate'),
    ('net.minecraft.world.level.gamerules.GameRules', 'net.minecraft.world.GameRules'),
    ('net.minecraft.network.chat.ChatType', 'net.minecraft.network.message.MessageType'),
    ('net.minecraft.network.chat.PlayerChatMessage', 'net.minecraft.network.message.SignedMessage'),
    ('LevelBasedPermissionSet.OWNER', 'LeveledPermissionPredicate.OWNERS'),
    ('ServerPlayerEntityEntity', 'ServerPlayerEntity'),
]
for old, new in repl_imports:
    src = src.replace(old, new)

# ---- Method/field name replacements ----
# Method calls
src = src.replace('.getScoreboardName()', '.getNameForScoreboard()')
src = src.replace('.getPlayerList()', '.getPlayerManager()')
src = src.replace('.broadcastSystemMessage(', '.broadcast(')
src = src.replace('.sendSystemMessage(', '.sendMessage(')
src = src.replace('player.level()', 'player.getEntityWorld()')
src = src.replace('p.level()', 'p.getEntityWorld()')
src = src.replace('.isRemoved()', '.isDisconnected()')

# Action bar: 26.1.2 uses packets, 1.21.11 uses sendMessage(text, true)
src = re.sub(r'player\.connection\.send\(new ClientboundSetActionBarTextPacket\(Text\.literal\(\"([^)]*)\"\)\)\)',
             r'player.sendMessage(Text.literal("\1"), true)', src)
src = re.sub(r'player\.connection\.send\(new ClientboundSetActionBarTextPacket\(Text\.empty\(\)\)\)',
             r'player.sendMessage(Text.literal(""), true)', src)
src = re.sub(r'srv\.execute\(\(\) -> player\.connection\.send\(new ClientboundSetActionBarTextPacket\(Text\.literal\((\w+)\)\)\)\)',
             r'srv.execute(() -> player.sendMessage(Text.literal(\1), true))', src)

# ---- Method-level fixes ----
# Component.literal -> Text.literal (as method call)
src = re.sub(r'\bComponent\.literal\(', 'Text.literal(', src)
# Commands.literal -> CommandManager.literal
src = re.sub(r'\bCommands\.literal\(', 'CommandManager.literal(', src)
src = re.sub(r'\bCommands\.argument\(', 'CommandManager.argument(', src)

# Clean up ANY remaining bare Component. (safety net)
src = re.sub(r'(?<!\w)Component\.(?!class)', 'Text.', src)

# ---- Write output ----
with open('C:/Users/Lecoo/mc/src/main/java/com/example/mcai/handler/ChatHandler.java', 'w', encoding='utf-8') as f:
    f.write(src)

# Verify
with open('C:/Users/Lecoo/mc/src/main/java/com/example/mcai/handler/ChatHandler.java', 'r', encoding='utf-8') as f:
    written = f.read()

# Check for remaining issues
issues = []
if re.search(r'\bComponent\.', written):
    issues.append('Component. still present')
if re.search(r'\bServerPlayer\b(?!Entity)', written):
    issues.append('ServerPlayer (without Entity) still present')
if re.search(r'ClientboundSetActionBarTextPacket', written):
    issues.append('ActionBarPacket still present')

print(f"ChatHandler written: {len(written)} chars")
for issue in issues:
    print(f"WARNING: {issue}")
if not issues:
    print("All good!")
