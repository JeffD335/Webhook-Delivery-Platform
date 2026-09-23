package dev.webhook.platform.endpoint.application;

import dev.webhook.platform.endpoint.api.CreateEndpointRequest;
import dev.webhook.platform.endpoint.api.EndpointResponse;
import java.util.List;
import java.util.UUID;


public interface EndpointService {

    EndpointResponse create(CreateEndpointRequest request);

    EndpointResponse get(UUID endpointId);

    List<EndpointResponse> list();

    EndpointResponse setEnabled(UUID id, boolean enabled);

}
