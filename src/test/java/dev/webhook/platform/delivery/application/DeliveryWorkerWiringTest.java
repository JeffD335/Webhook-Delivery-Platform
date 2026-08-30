package dev.webhook.platform.delivery.application;

import dev.webhook.platform.delivery.domain.DeliveryStatus;
import dev.webhook.platform.delivery.persistence.DeliveryEntity;
import dev.webhook.platform.delivery.persistence.DeliveryRepository;
import dev.webhook.platform.delivery.sender.SendResult;
import dev.webhook.platform.delivery.sender.WebhookSender;
import dev.webhook.platform.testsupport.ApiIntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryWorkerWiringTest extends ApiIntegrationTestSupport {

    @Autowired
    private DeliveryWorkerRunner deliveryWorkerRunner;

    @Autowired
    private DeliveryRepository deliveryRepository;

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

        ProcessResult result = deliveryWorkerRunner.processOne();

        DeliveryEntity delivery = deliveryRepository.findById(deliveryId).orElseThrow();
        assertThat(result.outcome()).isEqualTo(ProcessOutcome.SUCCEEDED);
        assertThat(result.deliveryId()).isEqualTo(deliveryId);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SUCCEEDED);
        assertThat(delivery.getClaimedBy()).isNull();
        assertThat(delivery.getClaimExpiresAt()).isNull();

        verify(webhookSender).send(endpointUrl, payload);
    }
}
