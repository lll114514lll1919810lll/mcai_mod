import subprocess, re, sys
sys.stdout.reconfigure(encoding='utf-8')

result = subprocess.run(['git', 'show', 'mc-26.1.2-server:src/main/java/com/example/mcai/handler/ChatHandler.java'],
                       capture_output=True)
orig = result.stdout.decode('utf-8', errors='replace')

with open('C:/Users/Lecoo/mc/src/main/java/com/example/mcai/handler/ChatHandler.java', 'r', encoding='utf-8', errors='replace') as f:
    curr = f.read()

# Check for corrupted Chinese in addToChatLog calls
pat = re.compile(r'addToChatLog\("[^"]+"')
matches = pat.findall(orig)
for m in matches:
    name = m.split('"')[1].strip()
    if any(ord(c) > 127 for c in name):
        pass
print(f"Current: {len(curr)} chars, Original: {len(orig)} chars")

# Replace corrupted Chinese strings in current with originals
# Find strings with high-byte characters in curr that look corrupted
currLines = curr.split('\n')
origLines = orig.split('\n')
for i in range(min(len(currLines), len(origLines))):
    if currLines[i] != origLines[i]:
        # Check if only Chinese characters are corrupted
        pass
print("Done checking")
