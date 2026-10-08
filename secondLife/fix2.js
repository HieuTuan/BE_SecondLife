const fs = require('fs');
const filepath = 'P:/FPT/Capstone/FE_SecondLife/SEP490_FE_SecondLife/src/services/aiChatService.ts';
let content = fs.readFileSync(filepath, 'utf-8');

const regex = /return \(response as any\)\?\.data \|\| response;\s*\},?\s*\};?/;
const newContent = `return (response as any)?.data || response;
  },

  async regenerate(sessionId: string): Promise<any> {
    const response = await request<any>(\`/v1/posts/regenerate-chat/\${sessionId}\`, {
      method: 'POST',
      requiresAuth: true,
    });
    return (response as any)?.data || response;
  },
};`;

if(content.includes('regenerate(')) {
    console.log('Already patched');
} else {
    content = content.replace(regex, newContent);
    fs.writeFileSync(filepath, content);
    console.log('aiChatService patched');
}
