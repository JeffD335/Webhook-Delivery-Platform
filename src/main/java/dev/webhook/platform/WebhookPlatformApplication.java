package dev.webhook.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class WebhookPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebhookPlatformApplication.class, args);
    }

}

