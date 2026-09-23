package dev.webhook.platform.endpoint.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_endpoints")
public class EndpointEntity {
    @Getter
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @Getter
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    @Getter
    @Column(name = "url", nullable = false, length = 2048)
    private String url;
    @Getter
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Getter
    @Setter
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
    protected EndpointEntity() {
        //required by JPA
    }

    public EndpointEntity(UUID id, String name, String url, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.createdAt = createdAt;
    }


}
