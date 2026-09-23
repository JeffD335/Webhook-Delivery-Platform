package dev.webhook.platform.delivery.sender;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

class HttpWebhookSenderTest {

    private final HttpWebhookSender sender = new HttpWebhookSender(
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(100))
                    .build(),
            Duration.ofMillis(300));

    @ExtendWith(OutputCaptureExtension.class)
    @Test
    void send_whenUrlContainsToken_doesNotLogUrlOrPayload(
            CapturedOutput output) throws IOException {
        String token = "secret-test-value";
        String payload = "{\"private\":\"sensitive-payload-value\"}";
        HttpServer server = startServer(
                204, new AtomicReference<>(), new AtomicReference<>());
        String endpointUrl = url(server) + "?token=" + token;

        try {
            SendResult result = sender.send(endpointUrl, payload);

            assertThat(result.succeeded()).isTrue();
            assertThat(output.getAll())
                    .doesNotContain(endpointUrl, token, payload);
        } finally {
            server.stop(0);
        }
    }


    @Test
    void twoHundredResponse_returnsSuccess() throws IOException {
        String payload = "{\"orderId\":\"ord_http_success\"}";
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedContentType = new AtomicReference<>();
        HttpServer server = startServer(204, receivedBody, receivedContentType);

        try {
            SendResult result = sender.send(url(server), payload);

            assertThat(result.succeeded()).isTrue();
            assertThat(result.httpStatus()).isEqualTo(204);
            assertThat(result.errorMessage()).isNull();
            assertThat(receivedBody).hasValue(payload);
            assertThat(receivedContentType.get()).startsWith("application/json");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void notFoundResponse_returnsHttpFailure() throws IOException {
        HttpServer server = startServer(404, new AtomicReference<>(), new AtomicReference<>());

        try {
            SendResult result = sender.send(url(server), "{\"orderId\":\"ord_http_404\"}");

            assertThat(result.succeeded()).isFalse();
            assertThat(result.httpStatus()).isEqualTo(404);
            assertThat(result.errorMessage()).isEqualTo("receiver returned HTTP 404");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void networkFailure_returnsFailureWithoutHttpStatus() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String url = url(server) + "?token=secret-test-value";
        server.stop(0);

        SendResult result = sender.send(url, "{\"orderId\":\"ord_connection_failure\"}");

        assertThat(result.succeeded()).isFalse();
        assertThat(result.httpStatus()).isNull();
        assertThat(result.errorMessage()).isIn("connection error", "request timed out");
        assertThat(result.errorMessage()).doesNotContain("secret-test-value");
    }

    @Test
    void invalidUrl_returnsControlledErrorWithoutToken() {
        String url = "http://[invalid?token=secret-test-value";

        SendResult result = sender.send(url, "{}");

        assertThat(result.succeeded()).isFalse();
        assertThat(result.httpStatus()).isNull();
        assertThat(result.errorMessage()).isEqualTo("invalid endpoint URL");
        assertThat(result.errorMessage()).doesNotContain("secret-test-value");
    }

    private HttpServer startServer(
            int status,
            AtomicReference<String> receivedBody,
            AtomicReference<String> receivedContentType) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook", exchange -> {
            receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        return server;
    }

    private String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/webhook";
    }
}
