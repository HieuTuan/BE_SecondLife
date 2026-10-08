-- Remove duplicate chat_rooms by keeping only the one with the smallest room_id
DELETE FROM chat_rooms
WHERE room_id NOT IN (
    SELECT MIN(room_id::text)::uuid
    FROM chat_rooms
    GROUP BY post_id, buyer_id, seller_id
);

-- Add UNIQUE constraint to chat_rooms table to prevent duplicate rooms
ALTER TABLE chat_rooms 
ADD CONSTRAINT uk_chat_rooms_post_buyer_seller 
UNIQUE (post_id, buyer_id, seller_id);
