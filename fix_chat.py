import subprocess, re, sys
sys.stdout.reconfigure(encoding='utf-8')

# Get original from git
result = subprocess.run(['git', 'show', 'mc-26.1.2-server:src/main/java/com/example/mcai/handler/ChatHandler.java'],
                       capture_output=True)
src = result.stdout.decode('utf-8')

# Yarn API replacements
repl = [
    ('net.minecraft.network.chat.Component', 'net.minecraft.text.Text'),
    ('net.minecraft.commands.CommandSourceStack', 'net.minecraft.server.command.ServerCommandSource'),
    ('net.minecraft.commands.Commands', 'net.minecraft.server.command.CommandManager'),
    ('net.minecraft.server.level.ServerPlayer', 'net.minecraft.server.network.ServerPlayerEntity'),
    ('net.minecraft.server.level.ServerLevel', 'net.minecraft.server.world.ServerWorld'),
    ('net.minecraft.server.permissions.LevelBasedPermissionSet', 'net.minecraft.command.permission.LeveledPermissionPredicate'),
    ('LevelBasedPermissionSet.OWNER', 'LeveledPermissionPredicate.OWNERS'),
    ('net.minecraft.world.level.gamerules.GameRules', 'net.minecraft.world.GameRules'),
    ('net.minecraft.network.chat.ChatType', 'net.minecraft.network.message.MessageType'),
    ('net.minecraft.network.chat.PlayerChatMessage', 'net.minecraft.network.message.SignedMessage'),
    ('ServerPlayerEntityEntity', 'ServerPlayerEntity'),
]
for old, new in repl:
    src = src.replace(old, new)

# Method replacements
src = re.sub(r'\bComponent\.literal\(', 'Text.literal(', src)
src = re.sub(r'\bCommands\.literal\(', 'CommandManager.literal(', src)
src = re.sub(r'\bCommands\.argument\(', 'CommandManager.argument(', src)
src = src.replace('.getScoreboardName()', '.getNameForScoreboard()')
src = src.replace('.getPlayerList()', '.getPlayerManager()')
src = src.replace('.broadcastSystemMessage(', '.broadcast(')
src = src.replace('.sendSystemMessage(', '.sendMessage(')
src = src.replace('player.level()', 'player.getEntityWorld()')
src = src.replace('p.level()', 'p.getEntityWorld()')
src = src.replace('.isRemoved()', '.isDisconnected()')
src = src.replace('new ClientboundSetActionBarTextPacket(', '')
src = src.replace('player.connection.send(', 'player.sendMessage(')
src = src.replace("Text.literal(bar)))", "Text.literal(bar), true)")
src = src.replace("Text.literal(\"\")))", "Text.literal(\"\"), true)")

# Fix leftover multi-catch with bare Exception  
src = src.replace('new ClientboundSetActionBarTextPacket(Text.literal(bar))', 'Text.literal(bar), true)')

# Cleanup: remove any remaining Component. references
src = re.sub(r'(?<!\w)Component\.', 'Text.', src)

with open('C:/Users/Lecoo/mc/src/main/java/com/example/mcai/handler/ChatHandler.java', 'w', encoding='utf-8') as f:
    f.write(src)
print(f"Written {len(src)} chars")
