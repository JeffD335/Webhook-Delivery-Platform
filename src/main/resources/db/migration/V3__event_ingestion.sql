CREATE TABLE webhook_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE webhook_deliveries (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    endpoint_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_webhook_deliveries_event
        FOREIGN KEY (event_id)
            REFERENCES webhook_events(id),

    CONSTRAINT fk_webhook_deliveries_endpoint
        FOREIGN KEY (endpoint_id)
            REFERENCES webhook_endpoints(id),

    CONSTRAINT uq_webhook_deliveries_event_endpoint
        UNIQUE (event_id, endpoint_id)
);

CREATE INDEX idx_webhook_deliveries_status_created_at
    ON webhook_deliveries(status, created_at);

CREATE INDEX idx_webhook_deliveries_event_id
    ON webhook_deliveries(event_id);

CREATE INDEX idx_webhook_deliveries_endpoint_id
    ON webhook_deliveries(endpoint_id);
