package dev.webhook.platform.endpoint.api;

import dev.webhook.platform.endpoint.application.EndpointService;
import jakarta.validation.Valid;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PatchMapping("/{id}/enabled")
    public EndpointResponse setEnabled(@PathVariable UUID id, @RequestBody UpdateEndpointEnabledRequest request) {
        return endpointService.setEnabled(id, request.enabled());
    }
}
