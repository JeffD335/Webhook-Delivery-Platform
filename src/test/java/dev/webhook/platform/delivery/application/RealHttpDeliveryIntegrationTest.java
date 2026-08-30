package dev.webhook.platform.delivery.application;

import com.sun.net.httpserver.HttpServer;
import dev.webhook.platform.delivery.domain.DeliveryStatus;
import dev.webhook.platform.delivery.persistence.DeliveryAttemptEntity;
import dev.webhook.platform.delivery.persistence.DeliveryAttemptRepository;
import dev.webhook.platform.delivery.persistence.DeliveryEntity;
import dev.webhook.platform.delivery.persistence.DeliveryRepository;
import dev.webhook.platform.testsupport.ApiIntegrationTestSupport;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RealHttpDeliveryIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired
    private DeliveryWorkerRunner deliveryWorkerRunner;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @BeforeEach
    void clearDatabase() {
        clearWebhookTables();
    }

    @Test
    void eventCanBeDeliveredToLocalReceiverByRunner() throws Exception {
        String payload = "{\"orderId\":\"ord_real_http\"}";
        LocalReceiver receiver = startLocalReceiver(204);

        try {
            createEndpointAndReadId("Local receiver", receiver.url());

            postEvent("order.created", payload)
                    .andExpect(status().isCreated());

            UUID deliveryId = onlyDeliveryId();
            assertThat(receiver.requestCount()).isZero();
            assertThat(countRows("webhook_delivery_attempts")).isZero();

            ProcessResult result = deliveryWorkerRunner.processOne();

            assertThat(result.outcome()).isEqualTo(ProcessOutcome.SUCCEEDED);
            assertThat(result.deliveryId()).isEqualTo(deliveryId);
            assertThat(receiver.requestCount()).isEqualTo(1);
            assertThat(receiver.method()).isEqualTo("POST");
            assertThat(receiver.body()).isEqualTo(payload);
            assertThat(receiver.contentType()).startsWith("application/json");

            DeliveryEntity delivery = deliveryRepository.findById(deliveryId).orElseThrow();
            assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SUCCEEDED);
            assertThat(delivery.getAttemptCount()).isEqualTo(1);
            assertThat(delivery.getNextAttemptAt()).isNull();
            assertThat(delivery.getLastError()).isNull();
            assertThat(delivery.getClaimedBy()).isNull();
            assertThat(delivery.getClaimExpiresAt()).isNull();

            List<DeliveryAttemptEntity> attempts =
                    deliveryAttemptRepository.findByDeliveryIdOrderByAttemptNumberAsc(deliveryId);
            assertThat(attempts).hasSize(1);
            DeliveryAttemptEntity attempt = attempts.get(0);
            assertThat(attempt.getAttemptNumber()).isEqualTo(1);
            assertThat(attempt.getStatus().name()).isEqualTo("SUCCEEDED");
            assertThat(attempt.getHttpStatus()).isEqualTo(204);
            assertThat(attempt.getErrorMessage()).isNull();
            assertThat(attempt.getStartedAt()).isNotNull();
            assertThat(attempt.getFinishedAt()).isNotNull();
        } finally {
            receiver.stop();
        }
    }

    private UUID onlyDeliveryId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM webhook_deliveries",
                (resultSet, rowNumber) -> UUID.fromString(resultSet.getString("id")));
    }

    private LocalReceiver startLocalReceiver(int status) throws IOException {
        AtomicInteger callCount = new AtomicInteger();
        AtomicReference<String> lastMethod = new AtomicReference<>();
        AtomicReference<String> lastBody = new AtomicReference<>();
        AtomicReference<String> lastContentType = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook", exchange -> {
            callCount.incrementAndGet();
            lastMethod.set(exchange.getRequestMethod());
            lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();

        return new LocalReceiver(
                server,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/webhook",
                callCount,
                lastMethod,
                lastBody,
                lastContentType);
    }

    private record LocalReceiver(
            HttpServer server,
            String url,
            AtomicInteger callCount,
            AtomicReference<String> lastMethod,
            AtomicReference<String> lastBody,
            AtomicReference<String> lastContentType) {

        private int requestCount() {
            return callCount.get();
        }

        private String method() {
            return lastMethod.get();
        }

        private String body() {
            return lastBody.get();
        }

        private String contentType() {
            return lastContentType.get();
        }

        private void stop() {
            server.stop(0);
        }
    }
}
