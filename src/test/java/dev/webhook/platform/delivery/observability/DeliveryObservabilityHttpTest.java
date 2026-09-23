package dev.webhook.platform.delivery.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.webhook.platform.delivery.sender.SendResult;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles({"test", "observability"})
@AutoConfigureObservability
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.server.port=0",
                "webhook.delivery.scheduler.enabled=false"
        })
class DeliveryObservabilityHttpTest {

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private DeliveryMetrics deliveryMetrics;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PrometheusMeterRegistry prometheusMeterRegistry;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void managementPortExposesDeliveryMetricsAndPrometheusOutput() throws Exception {
        deliveryMetrics.timeSend(() -> SendResult.success(204));
        deliveryMetrics.recordTransitionSucceeded();
        deliveryMetrics.recordWorkerError();

        String actuatorLinks = get("/actuator");
        JsonNode sendMetric = getJson(
                "/actuator/metrics/webhook.delivery.send?tag=result:success");
        JsonNode transitionMetric = getJson(
                "/actuator/metrics/webhook.delivery.transitions?tag=outcome:succeeded");
        JsonNode workerErrorMetric = getJson(
                "/actuator/metrics/webhook.delivery.worker.errors");
        String prometheus = get("/actuator/prometheus");

        assertThat(actuatorLinks).contains("prometheus");
        assertThat(prometheusMeterRegistry.scrape())
                .contains("webhook_delivery_transitions_total");
        assertThat(sendMetric.get("name").asText()).isEqualTo("webhook.delivery.send");
        assertThat(measurement(sendMetric, "COUNT")).isEqualTo(1);
        assertThat(transitionMetric.get("name").asText())
                .isEqualTo("webhook.delivery.transitions");
        assertThat(measurement(transitionMetric, "COUNT")).isEqualTo(1);
        assertThat(workerErrorMetric.get("name").asText())
                .isEqualTo("webhook.delivery.worker.errors");
        assertThat(measurement(workerErrorMetric, "COUNT")).isEqualTo(1);
        assertThat(prometheus)
                .contains("webhook_delivery_send_seconds_count")
                .contains("webhook_delivery_transitions_total")
                .contains("webhook_delivery_worker_errors_total")
                .contains("outcome=\"succeeded\"")
                .contains("result=\"success\"");
    }

    private JsonNode getJson(String path) throws Exception {
        return objectMapper.readTree(get(path));
    }

    private String get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + managementPort + path))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode())
                .withFailMessage(
                        "GET %s returned %s: %s",
                        path,
                        response.statusCode(),
                        response.body())
                .isEqualTo(200);
        return response.body();
    }

    private double measurement(JsonNode metric, String statistic) {
        for (JsonNode measurement : metric.get("measurements")) {
            if (statistic.equals(measurement.get("statistic").asText())) {
                return measurement.get("value").asDouble();
            }
        }
        throw new AssertionError("Missing measurement statistic: " + statistic);
    }
}
