CREATE TABLE inspection_return_shipping_policy_history (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    previous_markup_percentage NUMERIC(5, 2) NOT NULL CHECK (previous_markup_percentage BETWEEN 0 AND 100),
    new_markup_percentage NUMERIC(5, 2) NOT NULL CHECK (new_markup_percentage BETWEEN 0 AND 100),
    changed_at TIMESTAMPTZ NOT NULL,
    changed_by UUID NOT NULL REFERENCES users(id),
    CHECK (previous_markup_percentage <> new_markup_percentage)
);

CREATE INDEX idx_inspection_return_shipping_policy_history_recent
    ON inspection_return_shipping_policy_history (changed_at DESC, id DESC);
