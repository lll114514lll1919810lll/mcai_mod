import re
with open('C:/Users/Lecoo/mc/src/main/java/com/example/mcai/handler/ChatHandler.java', 'r', encoding='utf-8') as f:
    src = f.read()

# Fix remaining ServerPlayer (not Entity)
src = re.sub(r'\bServerPlayer\b(?!Entity)', 'ServerPlayerEntity', src)

# Fix remaining ActionBar references 
src = src.replace("player.connection.send(new ClientboundSetActionBarTextPacket(Text.literal(bar)))",
                  "player.sendMessage(Text.literal(bar), true)")
src = src.replace('''player.connection.send(new ClientboundSetActionBarTextPacket(Text.literal("")))''',
                  '''player.sendMessage(Text.literal(""), true)''')
# Remove import
src = src.replace('import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;\n', '')

with open('C:/Users/Lecoo/mc/src/main/java/com/example/mcai/handler/ChatHandler.java', 'w', encoding='utf-8') as f:
    f.write(src)

print("Fixed. Checking...")
with open('C:/Users/Lecoo/mc/src/main/java/com/example/mcai/handler/ChatHandler.java', 'r', encoding='utf-8') as f:
    src = f.read()
issues = []
if re.search(r'ClientboundSetActionBarTextPacket', src):
    issues.append('ActionBarPacket remains')
if re.search(r'\bServerPlayer\b(?!Entity)', src):
    issues.append('ServerPlayer remains')
print('Issues:', issues if issues else 'None')
