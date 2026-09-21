package com.merchanthub.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * S3 client/presigner, built only when a bucket is configured. Credentials
 * and region come from the SDK's default provider chain (env vars, instance
 * profile, ECS task role, ...) — never hardcoded here.
 */
@Configuration
@ConditionalOnProperty(prefix = "merchanthub", name = "reports-s3-bucket")
public class S3Config {

    @Bean
    public S3Client s3Client(AppProperties props) {
        var builder = S3Client.builder();
        applyEndpointOverride(props, builder::endpointOverride, builder::serviceConfiguration);
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner(AppProperties props) {
        var builder = S3Presigner.builder();
        applyEndpointOverride(props, builder::endpointOverride, builder::serviceConfiguration);
        return builder.build();
    }

    private void applyEndpointOverride(AppProperties props,
                                        java.util.function.Consumer<URI> endpointSetter,
                                        java.util.function.Consumer<S3Configuration> configSetter) {
        if (props.getReportsS3EndpointOverride() != null && !props.getReportsS3EndpointOverride().isBlank()) {
            endpointSetter.accept(URI.create(props.getReportsS3EndpointOverride()));
            // Path-style addressing is what LocalStack and most non-AWS S3-compatible stores expect.
            configSetter.accept(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
    }
}
