const fs = require('fs');

// 1. types/index.ts
let typesContent = fs.readFileSync('P:/FPT/Capstone/FE_SecondLife/SEP490_FE_SecondLife/src/types/index.ts', 'utf-8');
typesContent = typesContent.replace(/sellerName: string;/g, 'sellerName: string;\n  sellerAvatarUrl?: string;');
fs.writeFileSync('P:/FPT/Capstone/FE_SecondLife/SEP490_FE_SecondLife/src/types/index.ts', typesContent);

// 2. App.tsx
let appContent = fs.readFileSync('P:/FPT/Capstone/FE_SecondLife/SEP490_FE_SecondLife/src/App.tsx', 'utf-8');
appContent = appContent.replace(/sellerName:\s*isOwner\s*&&\s*currentUser\?\.name\s*\?\s*currentUser\.name\s*:\s*\(post\.user\?\.fullName\s*\|\|\s*post\.sellerName\s*\|\|\s*'Ng[^']*'\)/, 
  "sellerName: isOwner && currentUser?.name ? currentUser.name : (post.user?.fullName || post.user?.email || post.sellerName || 'Người bán SecondLife')");
appContent = appContent.replace(/sellerRating: 5.0,/, 
  "sellerAvatarUrl: post.user?.avatarUrl,\n      sellerRating: 5.0,");
fs.writeFileSync('P:/FPT/Capstone/FE_SecondLife/SEP490_FE_SecondLife/src/App.tsx', appContent);

// 3. ChatModal.tsx (ensure correct avatar fallback)
let chatContent = fs.readFileSync('P:/FPT/Capstone/FE_SecondLife/SEP490_FE_SecondLife/src/components/modals/ChatModal.tsx', 'utf-8');
chatContent = chatContent.replace(/listing\.photos\.front/g, "(listing.sellerAvatarUrl || listing.photos.front)");
fs.writeFileSync('P:/FPT/Capstone/FE_SecondLife/SEP490_FE_SecondLife/src/components/modals/ChatModal.tsx', chatContent);

console.log('Patched types and App for seller name/avatar');
