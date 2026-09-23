package dev.webhook.platform.delivery.sender;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import org.springframework.stereotype.Component;
@Component
public class HttpWebhookSender implements WebhookSender {
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public HttpWebhookSender() {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                        .build(),
                DEFAULT_REQUEST_TIMEOUT);
    }

    HttpWebhookSender(HttpClient httpClient, Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.requestTimeout = Objects.requireNonNull(requestTimeout);
    }

    @Override
    public SendResult send(String url, String payload) {
        Objects.requireNonNull(url);
        Objects.requireNonNull(payload);
        try {

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return SendResult.success(status);
            }

            return SendResult.httpFailure(status, "receiver returned HTTP " + status);

        } catch (HttpTimeoutException exception) {
            return SendResult.timeOut("request timed out");

        } catch (IOException exception) {
            return SendResult.timeOut("connection error");

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return SendResult.timeOut("send interrupted");

        } catch (IllegalArgumentException exception) {
            return SendResult.timeOut("invalid endpoint URL");
        }
    }
}
