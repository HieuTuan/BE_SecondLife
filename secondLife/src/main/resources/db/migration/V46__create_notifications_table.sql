-- ==============================================================================
-- V46: CREATE NOTIFICATIONS TABLE AND INDEXES FOR IN-APP & REAL-TIME SYSTEM
-- ==============================================================================

CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    reference_id UUID NULL,
    reference_type VARCHAR(50) NULL,
    action_url VARCHAR(255) NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP WITH TIME ZONE NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_list 
    ON notifications (user_id, created_at DESC) 
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_notifications_user_unread 
    ON notifications (user_id) 
    WHERE is_deleted = FALSE AND is_read = FALSE;

CREATE INDEX IF NOT EXISTS idx_notifications_reference 
    ON notifications (reference_type, reference_id);
