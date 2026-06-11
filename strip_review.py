import re
with open('C:/Users/Lecoo/mc/src/main/java/com/example/mcai/MCAIMod.java', 'r', encoding='utf-8') as f:
    src = f.read()

src = src.replace('import com.example.mcai.behavior.PlayerBehaviorTracker;\n', '')
src = src.replace('import com.example.mcai.behavior.ChatReviewSystem;\n', '')
src = src.replace('    private PlayerBehaviorTracker behaviorTracker;\n', '')
src = src.replace('    private ChatReviewSystem chatReviewSystem;\n', '')
src = src.replace('        behaviorTracker = new PlayerBehaviorTracker(config);\n', '')

# Remove server_started block
lines = src.split('\n')
new_lines = []
skip_block = False
for line in lines:
    if 'SERVER_STARTED.register' in line:
        skip_block = True
    if skip_block and 'chatReviewSystem.start()' in line:
        skip_block = False
        continue
    if skip_block and 'SERVER_STOPPING.register' in line:
        skip_block = True
    if skip_block and 'behaviorTracker.save()' in line:
        skip_block = False
        continue
    if not skip_block:
        new_lines.append(line)
    # Handle start/stop properly
    if 'SERVER_STARTED.register(s -> this.server = s)' in line:
        new_lines.append(line + ';')

src = '\n'.join(new_lines)

# Simpler: just replace the exact text
src = src.replace('''            chatReviewSystem = new ChatReviewSystem(this, behaviorTracker);
            if (config.isEnableAutoReview()) {
                chatReviewSystem.start();
                LOGGER.info("Auto behavior review enabled");
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            if (chatReviewSystem != null) chatReviewSystem.stop();
            if (behaviorTracker != null) behaviorTracker.save();
        });''', '')

src = src.replace('    public ChatReviewSystem getChatReviewSystem() { return chatReviewSystem; }\n', '')
src = src.replace('    public PlayerBehaviorTracker getBehaviorTracker() { return behaviorTracker; }\n', '')
src = src.replace('''        if (chatReviewSystem != null) chatReviewSystem.stop();
        behaviorTracker = new PlayerBehaviorTracker(config);
        chatReviewSystem = new ChatReviewSystem(this, behaviorTracker);
        if (config.isEnableAutoReview()) chatReviewSystem.start();''', '')

with open('C:/Users/Lecoo/mc/src/main/java/com/example/mcai/MCAIMod.java', 'w', encoding='utf-8') as f:
    f.write(src)
print('MCAIMod fixed')
