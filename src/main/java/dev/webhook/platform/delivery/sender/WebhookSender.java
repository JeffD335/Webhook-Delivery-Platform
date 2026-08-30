package dev.webhook.platform.delivery.sender;

/**
 * Boundary for the remote side effect of sending a webhook.
 */
public interface WebhookSender {

    SendResult send(String url, String payload);
}
