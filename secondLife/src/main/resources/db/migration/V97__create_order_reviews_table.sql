-- Migration V97: Tạo bảng order_reviews cho chức năng đánh giá sản phẩm sau khi mua hàng thành công
CREATE TABLE IF NOT EXISTS order_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    buyer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    seller_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    post_id UUID REFERENCES posts(id) ON DELETE SET NULL,
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment TEXT NOT NULL CHECK (length(btrim(comment)) > 0),
    tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    photos JSONB NOT NULL DEFAULT '[]'::jsonb,
    edit_count INT NOT NULL DEFAULT 0 CHECK (edit_count >= 0),
    is_verified_purchase BOOLEAN NOT NULL DEFAULT TRUE,
    condition_grade VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_order_review_order UNIQUE (order_id)
);

CREATE INDEX IF NOT EXISTS idx_order_reviews_seller ON order_reviews(seller_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_order_reviews_post ON order_reviews(post_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_order_reviews_buyer ON order_reviews(buyer_id, created_at DESC);
