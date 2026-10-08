-- Add UNIQUE constraint to chat_rooms table to prevent duplicate rooms
ALTER TABLE chat_rooms 
ADD CONSTRAINT uk_chat_rooms_post_buyer_seller 
UNIQUE (post_id, buyer_id, seller_id);
