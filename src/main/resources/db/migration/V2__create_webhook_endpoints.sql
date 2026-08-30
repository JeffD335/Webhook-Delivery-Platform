CREATE TABLE webhook_endpoints (
                                   id UUID PRIMARY KEY,
                                   name VARCHAR(100) NOT NULL,
                                   url VARCHAR(2048) NOT NULL,
                                   created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
