ALTER TABLE webhook_deliveries
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE webhook_deliveries
    ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE webhook_deliveries
    ADD COLUMN last_attempt_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE webhook_deliveries
    ADD COLUMN last_error TEXT;

ALTER TABLE webhook_deliveries
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX idx_webhook_deliveries_due_work
    ON webhook_deliveries(status, next_attempt_at, created_at);

CREATE TABLE webhook_delivery_attempts (
    id UUID PRIMARY KEY,
    delivery_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    http_status INTEGER,
    error_message TEXT,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_webhook_delivery_attempts_delivery
        FOREIGN KEY (delivery_id)
            REFERENCES webhook_deliveries(id),

    CONSTRAINT uq_webhook_delivery_attempts_delivery_number
        UNIQUE (delivery_id, attempt_number)
);

CREATE INDEX idx_webhook_delivery_attempts_delivery_id
    ON webhook_delivery_attempts(delivery_id);
