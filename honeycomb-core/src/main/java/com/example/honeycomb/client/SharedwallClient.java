package com.example.honeycomb.client;

import com.example.honeycomb.util.HoneycombConstants;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class SharedwallClient {
    private final WebClient webClient;
    private final String baseUrl;
    private final String fromCell;
    private final MediaType defaultContentType;
    private final String registrationId;
    private final Supplier<String> bearerTokenSupplier;
    private final boolean autoRequestId;

    private SharedwallClient(Builder builder) {
        this.webClient = builder.webClient;
        this.baseUrl = builder.baseUrl;
        this.fromCell = builder.fromCell;
        this.defaultContentType = builder.defaultContentType;
        this.registrationId = builder.registrationId;
        this.bearerTokenSupplier = builder.bearerTokenSupplier;
        this.autoRequestId = builder.autoRequestId;
    }

    public static Builder builder(WebClient webClient, String baseUrl) {
        return new Builder(webClient, baseUrl);
    }

    public Mono<Map<String, Object>> invoke(String methodName, Object body) {
        return invoke(methodName, body, null, new ParameterizedTypeReference<>() {});
    }

    public Mono<Map<String, Object>> invoke(String methodName, Object body, MediaType contentType) {
        return invoke(methodName, body, contentType, new ParameterizedTypeReference<>() {});
    }

    @SuppressWarnings("null")
    public <T> Mono<T> invoke(String methodName,
                              Object body,
                              MediaType contentType,
                              ParameterizedTypeReference<T> responseType) {
        String url = baseUrl + HoneycombConstants.Paths.HONEYCOMB_SHARED
                + HoneycombConstants.Names.SEPARATOR_SLASH + methodName;
        MediaType resolvedType = resolveContentType(body, contentType);

        WebClient.RequestBodySpec req = webClient.post()
                .uri(url)
                .headers(h -> {
                    if (StringUtils.hasText(fromCell)) {
                        h.set(HoneycombConstants.Headers.FROM_CELL, fromCell);
                    }
                    if (autoRequestId && !h.containsKey(HoneycombConstants.Headers.REQUEST_ID)) {
                        h.set(HoneycombConstants.Headers.REQUEST_ID, UUID.randomUUID().toString());
                    }
                    if (resolvedType != null) {
                        h.setContentType(resolvedType);
                    }
                    if (bearerTokenSupplier != null) {
                        String token = bearerTokenSupplier.get();
                        if (StringUtils.hasText(token)) {
                            h.setBearerAuth(Objects.requireNonNull(token));
                        }
                    }
                });

        if (StringUtils.hasText(registrationId)) {
            req = req.attributes(attrs ->
                    ServerOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId(registrationId)
                            .accept(attrs));
        }

        if (body == null) {
            return req.retrieve().bodyToMono(responseType);
        }

        return req.bodyValue(body).retrieve().bodyToMono(responseType);
    }

    private MediaType resolveContentType(Object body, MediaType contentType) {
        if (contentType != null) return contentType;
        if (body instanceof String || body instanceof byte[]) return MediaType.TEXT_PLAIN;
        if (body == null) return defaultContentType;
        return defaultContentType != null ? defaultContentType : MediaType.APPLICATION_JSON;
    }

    public static final class Builder {
        private final WebClient webClient;
        private final String baseUrl;
        private String fromCell;
        private MediaType defaultContentType = MediaType.APPLICATION_JSON;
        private String registrationId;
        private Supplier<String> bearerTokenSupplier;
        private boolean autoRequestId = true;

        private Builder(WebClient webClient, String baseUrl) {
            this.webClient = webClient;
            this.baseUrl = baseUrl;
        }

        public Builder fromCell(String fromCell) {
            this.fromCell = fromCell;
            return this;
        }

        public Builder defaultContentType(MediaType defaultContentType) {
            this.defaultContentType = defaultContentType;
            return this;
        }

        public Builder registrationId(String registrationId) {
            this.registrationId = registrationId;
            return this;
        }

        public Builder bearerTokenSupplier(Supplier<String> bearerTokenSupplier) {
            this.bearerTokenSupplier = bearerTokenSupplier;
            return this;
        }

        public Builder autoRequestId(boolean autoRequestId) {
            this.autoRequestId = autoRequestId;
            return this;
        }

        public SharedwallClient build() {
            return new SharedwallClient(this);
        }
    }
}
