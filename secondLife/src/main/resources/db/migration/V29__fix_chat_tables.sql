DROP TABLE IF EXISTS chat_messages CASCADE;
DROP TABLE IF EXISTS chat_rooms CASCADE;

CREATE TABLE chat_rooms (
    room_id UUID PRIMARY KEY,
    post_id UUID NOT NULL,
    buyer_id UUID NOT NULL,
    seller_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_rooms_post FOREIGN KEY (post_id) REFERENCES posts(id),
    CONSTRAINT fk_chat_rooms_buyer FOREIGN KEY (buyer_id) REFERENCES users(id),
    CONSTRAINT fk_chat_rooms_seller FOREIGN KEY (seller_id) REFERENCES users(id)
);

CREATE TABLE chat_messages (
    message_id UUID PRIMARY KEY,
    room_id UUID NOT NULL,
    sender_id UUID NOT NULL,
    message_content TEXT NOT NULL,
    safety_warning BOOLEAN NOT NULL DEFAULT FALSE,
    safety_warning_code VARCHAR(100),
    sent_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_messages_room FOREIGN KEY (room_id) REFERENCES chat_rooms(room_id),
    CONSTRAINT fk_chat_messages_sender FOREIGN KEY (sender_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_chat_rooms_buyer ON chat_rooms(buyer_id);
CREATE INDEX IF NOT EXISTS idx_chat_rooms_seller ON chat_rooms(seller_id);
CREATE INDEX IF NOT EXISTS idx_chat_messages_room ON chat_messages(room_id, sent_at);
