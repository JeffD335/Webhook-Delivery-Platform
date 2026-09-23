package dev.webhook.platform.delivery.application;

import dev.webhook.platform.delivery.domain.DeliveryStatus;
import dev.webhook.platform.delivery.persistence.DeliveryEntity;
import dev.webhook.platform.delivery.persistence.DeliveryRepository;
import dev.webhook.platform.delivery.sender.SendResult;
import dev.webhook.platform.delivery.sender.WebhookSender;
import dev.webhook.platform.testsupport.ApiIntegrationTestSupport;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestPropertySource(properties = "webhook.delivery.scheduler.enabled=false")
class DeliveryWorkerWiringTest extends ApiIntegrationTestSupport {

    @Autowired
    private DeliveryWorkerRunner deliveryWorkerRunner;

    @Autowired
    private DeliveryWorkerFactory deliveryWorkerFactory;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockBean
    private WebhookSender webhookSender;

    @BeforeEach
    void clearDatabase() {
        clearWebhookTables();
    }

    @Test
    void runnerCanProcessOneDeliveryUsingSpringManagedClaimer() {
        String payload = "{\"orderId\":\"ord_wiring\"}";
        String endpointUrl = "https://receiver.example/webhook";
        UUID eventId = insertEvent("order.created", payload);
        UUID endpointId = insertEndpoint("Receiver", endpointUrl);
        UUID deliveryId = insertDelivery(eventId, endpointId, "PENDING");

        when(webhookSender.send(endpointUrl, payload)).thenReturn(SendResult.success(204));
        long successCountBefore = sendCount("success");
        double succeededCountBefore = transitionCount("succeeded");

        ProcessResult result = deliveryWorkerRunner.processOne();

        DeliveryEntity delivery = deliveryRepository.findById(deliveryId).orElseThrow();
        assertThat(result.outcome()).isEqualTo(ProcessOutcome.SUCCEEDED);
        assertThat(result.deliveryId()).isEqualTo(deliveryId);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SUCCEEDED);
        assertThat(delivery.getClaimedBy()).isNull();
        assertThat(delivery.getClaimExpiresAt()).isNull();
        assertThat(sendCount("success")).isEqualTo(successCountBefore + 1);
        assertThat(transitionCount("succeeded")).isEqualTo(succeededCountBefore + 1);

        verify(webhookSender).send(endpointUrl, payload);
    }

    @Test
    void workersCreatedByFactoryAggregateMetricsInApplicationRegistry() {
        String payload = "{\"orderId\":\"ord_multi_worker\"}";
        String endpointUrl = "https://receiver.example/webhook";
        UUID eventId = insertEvent("order.created", payload);
        UUID firstEndpointId = insertEndpoint("Receiver A", endpointUrl);
        UUID secondEndpointId = insertEndpoint("Receiver B", endpointUrl);
        UUID firstDeliveryId = insertDelivery(eventId, firstEndpointId, "PENDING");
        UUID secondDeliveryId = insertDelivery(eventId, secondEndpointId, "PENDING");

        when(webhookSender.send(endpointUrl, payload))
                .thenReturn(
                        SendResult.success(204),
                        SendResult.httpFailure(503, "service unavailable"));
        long successBefore = sendCount("success");
        long httpErrorBefore = sendCount("http_error");
        double succeededBefore = transitionCount("succeeded");
        double retryScheduledBefore = transitionCount("retry_scheduled");
        double workerErrorsBefore = workerErrorCount();

        ProcessResult firstResult = deliveryWorkerFactory.create("worker-a").processOne();
        ProcessResult secondResult = deliveryWorkerFactory.create("worker-b").processOne();

        assertThat(List.of(firstResult.outcome(), secondResult.outcome()))
                .containsExactly(ProcessOutcome.SUCCEEDED, ProcessOutcome.FAILED);
        assertThat(List.of(
                deliveryRepository.findById(firstDeliveryId).orElseThrow().getStatus(),
                deliveryRepository.findById(secondDeliveryId).orElseThrow().getStatus()))
                .containsExactlyInAnyOrder(DeliveryStatus.SUCCEEDED, DeliveryStatus.RETRY_SCHEDULED);
        assertThat(sendCount("success")).isEqualTo(successBefore + 1);
        assertThat(sendCount("http_error")).isEqualTo(httpErrorBefore + 1);
        assertThat(transitionCount("succeeded")).isEqualTo(succeededBefore + 1);
        assertThat(transitionCount("retry_scheduled")).isEqualTo(retryScheduledBefore + 1);
        assertThat(workerErrorCount()).isEqualTo(workerErrorsBefore);

        verify(webhookSender, times(2)).send(endpointUrl, payload);
    }

    private long sendCount(String result) {
        return meterRegistry.get("webhook.delivery.send")
                .tag("result", result)
                .timer()
                .count();
    }

    private double transitionCount(String outcome) {
        return meterRegistry.get("webhook.delivery.transitions")
                .tag("outcome", outcome)
                .counter()
                .count();
    }

    private double workerErrorCount() {
        return meterRegistry.get("webhook.delivery.worker.errors")
                .counter()
                .count();
    }
}
