package dev.webhook.platform.endpoint.api;

import dev.webhook.platform.endpoint.application.EndpointService;
import jakarta.validation.Valid;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fixed HTTP boundary for Lab 1. Implement each method at its owning checkpoint.
 */
@RestController
@RequestMapping("/api/endpoints")
public class EndpointController {

    private final EndpointService endpointService;

    public EndpointController(EndpointService endpointService) {
        this.endpointService = endpointService;
    }

    @PostMapping
    public ResponseEntity<EndpointResponse> create(@Valid @RequestBody CreateEndpointRequest request) {
        EndpointResponse endpointResponse = endpointService.create(request);
        return ResponseEntity.created(URI.create("/api/endpoints/" + endpointResponse.id()))
                .body(endpointResponse);
    }

    @GetMapping("/{endpointId}")
    public EndpointResponse get(@PathVariable UUID endpointId) {
        return endpointService.get(endpointId);
    }

    @GetMapping
    public List<EndpointResponse> list() {
        return endpointService.list();
    }
}
