const fs = require('fs');

// 1. App.tsx
let appContent = fs.readFileSync('P:/FPT/Capstone/FE_SecondLife/SEP490_FE_SecondLife/src/App.tsx', 'utf-8');
appContent = appContent.replace(/const \[chatListing, setChatListing\] = useState<Listing \| null>\(null\);/, 
  'const [chatContext, setChatContext] = useState<{listing: Listing, room?: any} | null>(null);');

appContent = appContent.replace(/onOpenChat=\{\(listing\) => setChatListing\(listing\)\}/g, 
  'onOpenChat={(listing, room) => setChatContext({listing, room})}');

appContent = appContent.replace(/setChatListing\(listing\);/g, 
  'setChatContext({listing});');

appContent = appContent.replace(/setChatListing\(item\);/g, 
  'setChatContext({listing: item});');

appContent = appContent.replace(/setChatListing\(null\);/g, 
  'setChatContext(null);');

appContent = appContent.replace(/\{chatListing && \(/, 
  '{chatContext && (');

appContent = appContent.replace(/listing=\{chatListing\}/, 
  'listing={chatContext.listing}\n            roomDto={chatContext.room}');

appContent = appContent.replace(/onClose=\{\(\) => setChatListing\(null\)\}/, 
  'onClose={() => setChatContext(null)}');

fs.writeFileSync('P:/FPT/Capstone/FE_SecondLife/SEP490_FE_SecondLife/src/App.tsx', appContent);
console.log('Patched App.tsx');

// 2. InboxView.tsx
let inboxContent = fs.readFileSync('P:/FPT/Capstone/FE_SecondLife/SEP490_FE_SecondLife/src/pages/InboxView.tsx', 'utf-8');
inboxContent = inboxContent.replace(/onOpenChat: \(listing: Listing\) => void;/, 
  'onOpenChat: (listing: Listing, room?: ChatRoomDto) => void;');

inboxContent = inboxContent.replace(/onOpenChat\(found\);/, 
  'onOpenChat(found, room);');

inboxContent = inboxContent.replace(/onOpenChat\(fallbackListing\);/, 
  'onOpenChat(fallbackListing as Listing, room);');

fs.writeFileSync('P:/FPT/Capstone/FE_SecondLife/SEP490_FE_SecondLife/src/pages/InboxView.tsx', inboxContent);
console.log('Patched InboxView.tsx');

// 3. ChatModal.tsx
let modalContent = fs.readFileSync('P:/FPT/Capstone/FE_SecondLife/SEP490_FE_SecondLife/src/components/modals/ChatModal.tsx', 'utf-8');

modalContent = modalContent.replace(/interface ChatModalProps \{/, 
  'interface ChatModalProps {\n  roomDto?: ChatRoomDto;');

modalContent = modalContent.replace(/export const ChatModal: React\.FC<ChatModalProps> = \(\{/, 
  'export const ChatModal: React.FC<ChatModalProps> = ({\n    roomDto,');

const getRoomLogic = `        if (!listing?.id) return;
        setLoadingRoom(true);
        try {
          let room = roomDto;
          if (!room) {
            room = await chatService.getOrCreateRoom(listing.id);
          }
          if (!isMounted) return;
  
          if (room?.id) {
            setRoomId(room.id);
            setRoomData(room);
            const historyDtos = await chatService.getMessages(room.id);`;

modalContent = modalContent.replace(/        if \(\!listing\?\.id\) return;\n\s*setLoadingRoom\(true\);\n\s*try \{\n\s*\/\/ API: POST \/api\/v1\/chats\/rooms\?postId=\{postId\}\n\s*const room = await chatService\.getRoom\(listing\.id\);\n\s*if \(\!isMounted\) return;\n\n\s*if \(room\?\.id\) \{\n\s*setRoomId\(room\.id\);\n\s*setRoomData\(room\);\n\s*\/\/ API: GET \/api\/v1\/chats\/\{roomId\}\/messages\n\s*const historyDtos = await chatService\.getMessages\(room\.id\);/, getRoomLogic);

// Replace avatar
const oldAvatar = 'src={currentRole === \'buyer\' ? listing.photos.front : "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&q=80&w=200"}';
const newAvatar = 'src={currentRole === \'buyer\' ? (listing.sellerAvatarUrl || listing.photos.front) : (roomData?.buyerAvatar || "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&q=80&w=200")}';
modalContent = modalContent.replace(oldAvatar, newAvatar);

// Replace name
const oldName = "{currentRole === 'buyer' ? listing.sellerName : 'Hoàng Quốc Khang (Người mua)'}";
const newName = "{currentRole === 'buyer' ? listing.sellerName : (roomData?.buyerName ? `${roomData.buyerName} (Người mua)` : 'Người mua')}";
modalContent = modalContent.replace(oldName, newName);

fs.writeFileSync('P:/FPT/Capstone/FE_SecondLife/SEP490_FE_SecondLife/src/components/modals/ChatModal.tsx', modalContent);
console.log('Patched ChatModal.tsx');
