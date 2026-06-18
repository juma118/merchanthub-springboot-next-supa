package com.merchanthub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.client.RestClient;

/**
 * Core wiring.
 *
 * <p>The transaction interceptor is pinned to the HIGHEST precedence so it is the
 * OUTERMOST advice. That lets {@code TenantIsolationAspect} (default/lowest
 * precedence) run INSIDE the active transaction, where its
 * {@code SET LOCAL app.current_merchant_id} takes effect on the same connection
 * the rest of the transaction uses.
 */
@Configuration
@EnableScheduling
@EnableAspectJAutoProxy
@EnableTransactionManagement(order = Ordered.HIGHEST_PRECEDENCE)
public class AppConfig {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
