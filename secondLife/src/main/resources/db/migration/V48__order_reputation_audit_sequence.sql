-- Preserve legacy event payloads; only new events receive a causal insertion sequence.
ALTER TABLE seller_reputation_events ADD COLUMN event_sequence BIGINT;
CREATE SEQUENCE seller_reputation_event_sequence OWNED BY seller_reputation_events.event_sequence;
ALTER TABLE seller_reputation_events ALTER COLUMN event_sequence
    SET DEFAULT nextval('seller_reputation_event_sequence');
CREATE UNIQUE INDEX uq_reputation_event_sequence ON seller_reputation_events(event_sequence)
    WHERE event_sequence IS NOT NULL;
CREATE INDEX idx_reputation_events_causal_history ON seller_reputation_events
    (seller_id,event_sequence DESC NULLS LAST,created_at DESC,id DESC);
