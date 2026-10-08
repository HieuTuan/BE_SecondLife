import sys
file_path = r'P:\FPT\Capstone\FE_SecondLife\SEP490_FE_SecondLife\src\services\chatService.ts'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(r'\/v1/chats/rooms?postId=\, {', '`/v1/chats/rooms?postId=${postId}`, {')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print('Fixed!')
