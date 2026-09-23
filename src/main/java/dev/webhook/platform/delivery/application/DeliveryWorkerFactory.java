package dev.webhook.platform.delivery.application;

import dev.webhook.platform.delivery.claim.DeliveryClaimer;
import dev.webhook.platform.delivery.domain.RetryPolicy;
import dev.webhook.platform.delivery.observability.DeliveryMetrics;
import dev.webhook.platform.delivery.persistence.DeliveryAttemptRepository;
import dev.webhook.platform.delivery.persistence.DeliveryRepository;
import dev.webhook.platform.delivery.sender.WebhookSender;
import dev.webhook.platform.endpoint.persistence.EndpointRepository;
import dev.webhook.platform.event.persistence.EventRepository;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class DeliveryWorkerFactory {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final EventRepository eventRepository;
    private final EndpointRepository endpointRepository;
    private final ObjectProvider<WebhookSender> webhookSenderProvider;
    private final RetryPolicy retryPolicy;
    private final DeliveryClaimer deliveryClaimer;
    private final DeliveryMetrics deliveryMetrics;

    public DeliveryWorkerFactory(
            DeliveryRepository deliveryRepository,
            DeliveryAttemptRepository deliveryAttemptRepository,
            EventRepository eventRepository,
            EndpointRepository endpointRepository,
            ObjectProvider<WebhookSender> webhookSenderProvider,
            RetryPolicy retryPolicy,
            DeliveryClaimer deliveryClaimer,
            DeliveryMetrics deliveryMetrics) {
        this.deliveryRepository = Objects.requireNonNull(deliveryRepository);
        this.deliveryAttemptRepository = Objects.requireNonNull(deliveryAttemptRepository);
        this.eventRepository = Objects.requireNonNull(eventRepository);
        this.endpointRepository = Objects.requireNonNull(endpointRepository);
        this.webhookSenderProvider = Objects.requireNonNull(webhookSenderProvider);
        this.retryPolicy = Objects.requireNonNull(retryPolicy);
        this.deliveryClaimer = Objects.requireNonNull(deliveryClaimer);
        this.deliveryMetrics = Objects.requireNonNull(deliveryMetrics);
    }

    public DeliveryWorker create(String workerId) {
        Objects.requireNonNull(workerId);
        WebhookSender webhookSender = webhookSenderProvider.getIfAvailable();
        if (webhookSender == null) {
            throw new IllegalStateException("WebhookSender bean is required to create a DeliveryWorker");
        }

        return new DeliveryWorker(
                workerId,
                deliveryRepository,
                deliveryAttemptRepository,
                eventRepository,
                endpointRepository,
                webhookSender,
                retryPolicy,
                deliveryClaimer,
                deliveryMetrics);
    }
}
