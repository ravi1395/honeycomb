package com.example.honeycomb.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.io.IOException;

/**
 * WebClient configuration with connection and response timeouts for resilience.
 */
@Configuration
public class WebClientConfig {

    @Value("${honeycomb.webclient.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${honeycomb.webclient.response-timeout-ms:30000}")
    private int responseTimeoutMs;

    @Value("${honeycomb.webclient.retry.enabled:false}")
    private boolean retryEnabled;

    @Value("${honeycomb.webclient.retry.max-attempts:3}")
    private int retryMaxAttempts;

    @Value("${honeycomb.webclient.retry.backoff-ms:200}")
    private long retryBackoffMs;

    @Value("${honeycomb.webclient.retry.max-backoff-ms:2000}")
    private long retryMaxBackoffMs;

    @Bean
    @SuppressWarnings("null")
    public WebClient webClient(WebClient.Builder builder) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(responseTimeoutMs));

        WebClient.Builder configured = builder
                .clientConnector(new ReactorClientHttpConnector(httpClient));

        if (retryEnabled) {
            configured.filter(retryFilter());
        }

        return configured.build();
    }

    private ExchangeFilterFunction retryFilter() {
        return (request, next) -> next.exchange(request)
                .flatMap(response -> {
                    if (response.statusCode().is5xxServerError()) {
                        return response.createException().flatMap(Mono::error);
                    }
                    return Mono.just(response);
                })
                .retryWhen(Retry.backoff(retryMaxAttempts, Duration.ofMillis(retryBackoffMs))
                        .maxBackoff(Duration.ofMillis(retryMaxBackoffMs))
                        .filter(this::isRetryable));
    }

    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof IOException) return true;
        if (throwable instanceof WebClientResponseException ex) {
            return ex.getStatusCode().is5xxServerError();
        }
        return false;
    }
}
