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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Component
public class HttpWebhookSender implements WebhookSender {
    private static final Logger log = LoggerFactory.getLogger(HttpWebhookSender.class);
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
        log.info("webhook send started url={}", url);

        try {

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                log.info("webhook send completed url={} status={}", url, status);
                return SendResult.success(status);
            }

            log.warn("webhook send returned non-2xx url={} status={}", url, status);
            return SendResult.httpFailure(status, "receiver returned HTTP " + status);

        } catch (HttpTimeoutException exception) {
            String error = describe(exception);
            log.warn("webhook send timed out url={} timeoutMs={} error={}",
                    url,
                    requestTimeout.toMillis(),
                    error);
            return SendResult.timeOut("request timed out");

        } catch (IOException exception) {
            String error = describe(exception);
            log.warn("webhook send failed url={} error={}", url, error);
            return SendResult.timeOut("connection error: " + error);

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("webhook send interrupted url={}", url);
            return SendResult.timeOut("send interrupted");

        } catch (IllegalArgumentException exception) {
            String error = describe(exception);
            log.warn("webhook send invalid url={} error={}", url, error);
            return SendResult.timeOut("invalid endpoint URL: " + error);
        }
    }

    private String describe(Exception exception) {
        String type = exception.getClass().getSimpleName();
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return type;
        }

        return type + ": " + message;
    }

}

