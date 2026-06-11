import subprocess, re, os

branches = {
    '26.1.2-server': 'mc-26.1.2-server',
    '26.1.2-client': '26.1.2-client',
    '1.21.11-server': '1.21.11-server',
    '1.21.11-client': '1.21.11-client',
    '1.21.1-server': '1.21.1-server',
    '1.21.1-client': '1.21.1-client',
}

def get_file(branch, path):
    r = subprocess.run(['git', 'show', f'{branch}:{path}'], capture_output=True)
    if r.returncode != 0:
        return None
    return r.stdout.decode('utf-8', errors='replace')

def has_feature(content, pattern):
    if content is None:
        return 'N/A'
    if re.search(pattern, content):
        return 'OK'
    return 'MISS'

# ========== ChatHandler checks ==========
chat_features = {
    'executeCommand pendingFutures.put': r'pendingFutures\.put\(',
    'executeCommand future.get(3min)': r'future\.get\(3,\s*TimeUnit\.MINUTES\)',
    'executeCommand notifyAdminsPending': r'notifyAdminsPending\(',
    'approveCommand pendingFutures.remove+complete': r'pendingFutures\.remove\(key\)[\s\S]{0,50}complete\(',
    'rejectCommand pendingFutures.remove+complete': r'rejectCommand[\s\S]{0,500}pendingFutures\.remove[\s\S]{0,100}complete\(',
    'onPlayerDisconnect removeIf': r'pendingFutures\.keySet\(\)\.removeIf',
    'addToChatLog Asia/Shanghai': r'Asia/Shanghai',
    'addToChatLog isAdmin param': r'addToChatLog\(String name, String message, boolean isAdmin\)',
    'isAdminOrConsole': r'isAdminOrConsole',
    'isAdminPlayer': r'isAdminPlayer',
    'handleConsoleAIQuery': r'handleConsoleAIQuery',
    'notifyAdminsPending': r'notifyAdminsPending',
    'getServerStatus': r'getServerStatus\(',
    'getGameRules': r'getGameRules\(',
    'getDebugInfo': r'getDebugInfo\(',
    'startThinkingAnimation': r'startThinkingAnimation\(',
    'peekChatLog': r'peekChatLog\(',
    'clearChatLog': r'clearChatLog\(',
    'buildPlayerContext': r'buildPlayerContext\(',
    'formatGameTime': r'formatGameTime\(',
    'handleResponse': r'handleResponse\(',
    'FORBIDDEN_COMMANDS': r'FORBIDDEN_COMMANDS',
}

# ========== Other file checks ==========
other_checks = {
    'MCAIMod: behaviorTracker field': (r'PlayerBehaviorTracker\s+behaviorTracker', 'MCAIMod.java'),
    'MCAIMod: chatReviewSystem field': (r'ChatReviewSystem\s+chatReviewSystem', 'MCAIMod.java'),
    'MCAIMod: reloadConfig': (r'reloadConfig', 'MCAIMod.java'),
    'MCAIMod: getChatHandler': (r'getChatHandler', 'MCAIMod.java'),
    'ModConfig: review fields': (r'reviewIntervalMinutes', 'config/ModConfig.java'),
    'ModConfig: systemPrompt': (r'systemPrompt', 'config/ModConfig.java'),
    'ModConfig: requireApprovalCommands': (r'requireApprovalCommands', 'config/ModConfig.java'),
    'ModConfig: safeCommands': (r'safeCommands', 'config/ModConfig.java'),
    'fabric.mod.json: modmenu entry': (r'modmenu', 'fabric.mod.json'),
    'fabric.mod.json: icon': (r'icon\.png', 'fabric.mod.json'),
    'icon.png exists': (None, 'assets/mcai/icon.png'),
    'OpenAIClient: chatSimple': (r'chatSimple', 'api/OpenAIClient.java'),
    'OpenAIClient: chatSimpleFull': (r'chatSimpleFull', 'api/OpenAIClient.java'),
    'KnowledgeBase: exists': (r'class KnowledgeBase', 'kb/KnowledgeBase.java'),
    'WikiSearchClient: exists': (r'class WikiSearchClient', 'api/WikiSearchClient.java'),
    'ModConfig: review getters': (r'getReviewIntervalMinutes', 'config/ModConfig.java'),
}

print('=' * 80)
print('FULL FEATURE AUDIT REPORT')
print('=' * 80)

for vname, branch in branches.items():
    print(f'\n{"─" * 70}')
    print(f'  {vname} ({branch})')
    print(f'{"─" * 70}')
    
    chat = get_file(branch, 'src/main/java/com/example/mcai/handler/ChatHandler.java')
    
    for feat, pattern in chat_features.items():
        status = has_feature(chat, pattern)
        if status != 'OK':
            print(f'  ❌ {feat}: {status}')
    
    # Server vs client detection
    mcaimod = get_file(branch, 'src/main/java/com/example/mcai/MCAIMod.java')
    fabric_json = get_file(branch, 'src/main/resources/fabric.mod.json')
    modconfig = get_file(branch, 'src/main/java/com/example/mcai/config/ModConfig.java')
    kb = get_file(branch, 'src/main/java/com/example/mcai/kb/KnowledgeBase.java')
    wiki = get_file(branch, 'src/main/java/com/example/mcai/api/WikiSearchClient.java')
    oai = get_file(branch, 'src/main/java/com/example/mcai/api/OpenAIClient.java')
    
    # Check review system
    has_review = has_feature(mcaimod, r'behavior\.PlayerBehaviorTracker\b')
    if 'server' in vname and has_review != 'OK':
        print(f'  ❌ SERVER but missing behavior review: {has_review}')
    if 'client' in vname and has_review == 'OK':
        print(f'  ❌ CLIENT but has behavior review: {has_review}')
    
    # Check Mod Menu
    has_modmenu = has_feature(fabric_json, r'modmenu')
    if 'client' in vname and has_modmenu != 'OK':
        print(f'  ❌ CLIENT but missing modmenu: {has_modmenu}')
    
    # Check icon
    icon = subprocess.run(['git', 'show', f'{branch}:src/main/resources/assets/mcai/icon.png'], capture_output=True)
    if icon.returncode != 0:
        print(f'  ❌ icon.png missing')
    
    # Check OpenAIClient
    for name, (pattern, path) in [('chatSimple', (r'chatSimple', 'api/OpenAIClient.java')),
                                   ('chatSimpleFull', (r'chatSimpleFull', 'api/OpenAIClient.java')),
                                   ('ChatSimpleResult', (r'ChatSimpleResult', 'api/OpenAIClient.java'))]:
        content = globals().get('oai') or get_file(branch, f'src/main/java/com/example/mcai/{path}')
        if not re.search(pattern, content or ''):
            print(f'  ❌ OpenAIClient missing {name}')
    
    # Check KnowledgeBase and WikiSearchClient
    if kb and 'class KnowledgeBase' not in kb:
        print(f'  ❌ KnowledgeBase broken')
    if wiki and 'class WikiSearchClient' not in wiki:
        print(f'  ❌ WikiSearchClient broken')

print(f'\n{"=" * 80}')
print('DONE')
print(f'{"=" * 80}')
