ALTER TABLE webhook_deliveries
    ADD COLUMN claimed_by VARCHAR(100);

ALTER TABLE webhook_deliveries
    ADD COLUMN claim_expires_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_webhook_deliveries_claimable
    ON webhook_deliveries(status, next_attempt_at, claim_expires_at, created_at);
