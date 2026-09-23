package dev.webhook.platform.endpoint.application;

import dev.webhook.platform.common.exception.ApiException;
import dev.webhook.platform.common.exception.NotFoundException;
import dev.webhook.platform.endpoint.api.CreateEndpointRequest;
import dev.webhook.platform.endpoint.api.EndpointResponse;
import dev.webhook.platform.endpoint.persistence.EndpointEntity;
import dev.webhook.platform.endpoint.persistence.EndpointRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.val;
import org.springframework.stereotype.Service;

/**
 * Compiling Lab 1 stub. Implement one method at the checkpoint that owns it.
 */

@Service
public class EndpointServiceImpl implements EndpointService {

    private final EndpointRepository repository;

    public EndpointServiceImpl(EndpointRepository repository) {
        this.repository = repository;
    }

    /**
     * create an EndpointEntity and save into database, return EndpointResponse
     * @param request
     * @return EndpointResponse
     */
    @Override
    public EndpointResponse create(CreateEndpointRequest request) {
        String name = request.name();
        String url = request.url();

        Instant createdAt = Instant.now();
        UUID id = UUID.randomUUID();
        EndpointEntity endpointEntity = new EndpointEntity(id, name, url, createdAt);

        EndpointEntity saved = repository.save(endpointEntity);

        return toResponse(saved);
    }

    @Override
    public EndpointResponse get(UUID endpointId) {
        EndpointEntity endpoint = repository.findById(endpointId)
                .orElseThrow(() -> new NotFoundException(
                        "ENDPOINT_NOT_FOUND",
                        "Endpoint does not exist"
                ));

        return toResponse(endpoint);
    }

    @Override
    public List<EndpointResponse> list() {
        return repository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public EndpointResponse setEnabled(UUID id, boolean enabled) {
        EndpointEntity endpoint = repository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "ENDPOINT_NOT_FOUND",
                        "Endpoint does not exist"
                ));

        endpoint.setEnabled(enabled);
        EndpointEntity saved = repository.save(endpoint);
        return toResponse(saved);
    }

    private EndpointResponse toResponse(EndpointEntity endpoint) {
        return new EndpointResponse(
                endpoint.getId(),
                endpoint.getName(),
                endpoint.getUrl(),
                endpoint.getCreatedAt(),
                endpoint.isEnabled()
        );
    }
}
