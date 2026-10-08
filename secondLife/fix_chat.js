const fs = require('fs');
let modalContent = fs.readFileSync('P:/FPT/Capstone/FE_SecondLife/SEP490_FE_SecondLife/src/components/modals/ChatModal.tsx', 'utf-8');

const replaceStr = `        let room = roomDto;
        if (!room) {
          room = await chatService.getOrCreateRoom(listing.id);
        }`;

modalContent = modalContent.replace(/        \/\/ API: POST \/api\/v1\/chats\/rooms\?postId=\{postId\}\r?\n\s*const room = await chatService\.getRoom\(listing\.id\);/g, replaceStr);

fs.writeFileSync('P:/FPT/Capstone/FE_SecondLife/SEP490_FE_SecondLife/src/components/modals/ChatModal.tsx', modalContent);
console.log('Fixed ChatModal.tsx');
