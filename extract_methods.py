import subprocess, re, sys

# Get the 26.1.2 and 1.21.x ChatHandlers
orig = subprocess.run(['git', 'show', 'mc-26.1.2-server:src/main/java/com/example/mcai/handler/ChatHandler.java'], capture_output=True).stdout.decode('utf-8')
target = subprocess.run(['git', 'show', '1.21.x-server:src/main/java/com/example/mcai/handler/ChatHandler.java'], capture_output=True).stdout.decode('utf-8')

# Extract methods that 1.21.x is missing
def extract_method(src, start_marker, end_marker=None):
    lines = src.split('\n')
    result = []
    in_method = False
    depth = 0
    for i, line in enumerate(lines):
        if start_marker in line:
            in_method = True
            depth = 0
        if in_method:
            result.append(line)
            depth += line.count('{') - line.count('}')
            if depth == 0 and len(result) > 1:
                break
    return '\n'.join(result)

# Methods to add from 26.1.2 to 1.21.x:
methods_to_extract = [
    'private final ConcurrentMap<String, CompletableFuture<String>> pendingFutures',
    'private void addToChatLog(String name, String message, boolean isAdmin)',
    'private static boolean isAdminOrConsole',
    'private boolean isAdminPlayer',
    'private void handleConsoleAIQuery',
    'private void notifyAdminsPending',
]

with open('C:/Users/Lecoo/mc/src_change_log.txt', 'w') as log:
    for m in methods_to_extract:
        code = extract_method(orig, m)
        if code:
            log.write(f"=== {m} ===\n{code}\n\n")
print("Methods extracted to change_log.txt")
