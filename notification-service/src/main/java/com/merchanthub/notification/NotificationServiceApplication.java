package com.merchanthub.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A small, independent microservice. It owns ONE responsibility — turning domain
 * events into outbound notifications (email/Slack, simulated here) — and shares
 * nothing with the main backend except the Kafka event contract. It has its own
 * deployable, its own process, and no database.
 */
@SpringBootApplication
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
