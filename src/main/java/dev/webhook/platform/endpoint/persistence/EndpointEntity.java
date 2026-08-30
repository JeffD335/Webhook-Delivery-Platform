package dev.webhook.platform.endpoint.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_endpoints")
public class EndpointEntity {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    @Column(name = "url", nullable = false, length = 2048)
    private String url;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EndpointEntity() {
        //required by JPA
    }

    public EndpointEntity(UUID id, String name, String url, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
