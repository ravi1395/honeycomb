package com.example.honeycomb.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.honeycomb.dto.SharedwallInvokeInfo;
import com.example.honeycomb.util.HoneycombConstants;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
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
    private final Duration discoveryTimeout;
    private final int discoveryRetryCount;
    private final Duration discoveryCacheTtl;
    private final ObjectMapper objectMapper;
    private final SharedwallResponseMapper responseMapper;
    private final AtomicReference<List<SharedwallInvokeInfo>> discoveryCache = new AtomicReference<>(List.of());
    private final AtomicLong discoveryCacheAtMs = new AtomicLong(0);

    private SharedwallClient(Builder builder) {
        this.webClient = builder.webClient;
        this.baseUrl = builder.baseUrl;
        this.fromCell = builder.fromCell;
        this.defaultContentType = builder.defaultContentType;
        this.registrationId = builder.registrationId;
        this.bearerTokenSupplier = builder.bearerTokenSupplier;
        this.autoRequestId = builder.autoRequestId;
        this.discoveryTimeout = builder.discoveryTimeout;
        this.discoveryRetryCount = builder.discoveryRetryCount;
        this.discoveryCacheTtl = builder.discoveryCacheTtl;
        this.objectMapper = builder.objectMapper == null ? new ObjectMapper().findAndRegisterModules() : builder.objectMapper;
        this.responseMapper = builder.responseMapper == null ? new DefaultSharedwallResponseMapper() : builder.responseMapper;
    }

    public static Builder builder(WebClient webClient, String baseUrl) {
        return new Builder(webClient, baseUrl);
    }

    public <T> T createTypedClient(Class<T> apiType) {
        return new TypedSharedwallClientFactory(this).create(apiType);
    }

    public <T> T createTypedClient(Class<T> apiType, boolean validateAtStartup) {
        return new TypedSharedwallClientFactory(this).create(apiType, validateAtStartup);
    }

    public <T> T createTypedClient(Class<T> apiType,
                                   boolean validateAtStartup,
                                   SharedwallValidationOptions validationOptions) {
        return new TypedSharedwallClientFactory(this).create(apiType, validateAtStartup, validationOptions);
    }

    String fromCell() {
        return fromCell;
    }

    SharedwallResponseMapper responseMapper() {
        return responseMapper;
    }

    <T> T convertValue(Object value, Class<T> targetType) {
        return objectMapper.convertValue(value, targetType);
    }

    public Mono<List<SharedwallInvokeInfo>> listInvokableMethods() {
        if (isDiscoveryCacheValid()) {
            return Mono.just(discoveryCache.get());
        }

        String url = baseUrl + HoneycombConstants.Paths.HONEYCOMB_SHARED
                + HoneycombConstants.Names.SEPARATOR_SLASH + "methods";

        WebClient.RequestHeadersSpec<?> req = webClient.get()
                .uri(url)
                .headers(h -> {
                    if (StringUtils.hasText(fromCell)) {
                        h.set(HoneycombConstants.Headers.FROM_CELL, fromCell);
                    }
                    if (autoRequestId && !h.containsKey(HoneycombConstants.Headers.REQUEST_ID)) {
                        h.set(HoneycombConstants.Headers.REQUEST_ID, UUID.randomUUID().toString());
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

        Mono<List<SharedwallInvokeInfo>> remote = req.retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<SharedwallInvokeInfo>>() {})
                .timeout(discoveryTimeout)
                .retry(discoveryRetryCount)
                .doOnNext(this::updateDiscoveryCache);

        return remote.onErrorResume(ex -> {
            List<SharedwallInvokeInfo> cached = discoveryCache.get();
            if (!cached.isEmpty()) {
                return Mono.just(cached);
            }
            return Mono.error(ex);
        });
    }

    private boolean isDiscoveryCacheValid() {
        long cachedAt = discoveryCacheAtMs.get();
        if (cachedAt <= 0) {
            return false;
        }
        long ageMs = System.currentTimeMillis() - cachedAt;
        return ageMs >= 0 && ageMs <= discoveryCacheTtl.toMillis() && !discoveryCache.get().isEmpty();
    }

    private void updateDiscoveryCache(List<SharedwallInvokeInfo> methods) {
        if (methods == null) {
            return;
        }
        discoveryCache.set(List.copyOf(methods));
        discoveryCacheAtMs.set(System.currentTimeMillis());
    }

    public Mono<Map<String, Object>> invoke(String methodName, Object body) {
        return invoke(methodName, body, null, new ParameterizedTypeReference<>() {});
    }

    public Mono<Map<String, Object>> invoke(String methodName, Object body, MediaType contentType) {
        return invoke(methodName, body, contentType, new ParameterizedTypeReference<>() {});
    }

    public <T> Mono<T> invokeTyped(String methodName, Object body, Class<T> responseType) {
        return invokeTyped(methodName, body, responseType, SharedwallEnvelopeMode.FIRST_RESULT, null);
    }

    public <T> Mono<T> invokeTyped(String methodName,
                                   Object body,
                                   Class<T> responseType,
                                   SharedwallEnvelopeMode mode,
                                   String targetCell) {
        return invoke(methodName, body, null, new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(envelope -> {
                    Object mapped = responseMapper.map(envelope, mode, targetCell);
                    if (responseType == Object.class) {
                        @SuppressWarnings("unchecked")
                        T cast = (T) mapped;
                        return cast;
                    }
                    return convertValue(mapped, responseType);
                });
    }

    /**
     * Invokes a shared method and returns a typed map result, using the provided fallback value on error.
     */
    public Mono<Map<String, Object>> invoke(String methodName,
                                            Object body,
                                            Map<String, Object> fallbackValue) {
        return invoke(methodName, body, null, new ParameterizedTypeReference<Map<String, Object>>() {})
            .onErrorResume(ex -> fallbackToValue(fallbackValue));
    }

    /**
     * Invokes a shared method and returns a typed map result, using a lazily computed fallback value on error.
     */
    public Mono<Map<String, Object>> invoke(String methodName,
                                            Object body,
                                            Supplier<? extends Map<String, Object>> fallbackSupplier) {
        return invoke(methodName, body, null, new ParameterizedTypeReference<Map<String, Object>>() {})
            .onErrorResume(ex -> fallbackFromSupplier(fallbackSupplier));
    }

    /**
     * Invokes a shared method and returns a typed map result, delegating error handling to the supplied fallback function.
     */
    public Mono<Map<String, Object>> invoke(String methodName,
                                            Object body,
                                            Function<? super Throwable, ? extends Mono<? extends Map<String, Object>>> fallbackFunction) {
        return invoke(methodName, body, null, new ParameterizedTypeReference<Map<String, Object>>() {})
                .onErrorResume(fallbackFunction);
    }

    public <T> Mono<T> invoke(String methodName,
                              Object body,
                              MediaType contentType,
                              ParameterizedTypeReference<T> responseType,
                              T fallbackValue) {
        return invoke(methodName, body, contentType, responseType)
                .onErrorResume(ex -> fallbackToValue(fallbackValue));
    }

    public <T> Mono<T> invoke(String methodName,
                              Object body,
                              MediaType contentType,
                              ParameterizedTypeReference<T> responseType,
                              Supplier<? extends T> fallbackSupplier) {
        return invoke(methodName, body, contentType, responseType)
                .onErrorResume(ex -> fallbackFromSupplier(fallbackSupplier));
    }

    public <T> Mono<T> invoke(String methodName,
                              Object body,
                              MediaType contentType,
                              ParameterizedTypeReference<T> responseType,
                              Function<? super Throwable, ? extends Mono<? extends T>> fallbackFunction) {
        return invoke(methodName, body, contentType, responseType)
                .onErrorResume(fallbackFunction);
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

    private <T> Mono<T> fallbackToValue(T fallbackValue) {
        return fallbackValue == null ? Mono.empty() : Mono.just(fallbackValue);
    }

    private <T> Mono<T> fallbackFromSupplier(Supplier<? extends T> fallbackSupplier) {
        if (fallbackSupplier == null) {
            return Mono.empty();
        }
        T fallbackValue = fallbackSupplier.get();
        return fallbackValue == null ? Mono.empty() : Mono.just(fallbackValue);
    }

    public static final class Builder {
        private final WebClient webClient;
        private final String baseUrl;
        private String fromCell;
        private MediaType defaultContentType = MediaType.APPLICATION_JSON;
        private String registrationId;
        private Supplier<String> bearerTokenSupplier;
        private boolean autoRequestId = true;
        private Duration discoveryTimeout = Duration.ofSeconds(5);
        private int discoveryRetryCount = 1;
        private Duration discoveryCacheTtl = Duration.ofSeconds(30);
        private ObjectMapper objectMapper;
        private SharedwallResponseMapper responseMapper;

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

        public Builder discoveryTimeout(Duration discoveryTimeout) {
            this.discoveryTimeout = discoveryTimeout == null ? Duration.ofSeconds(5) : discoveryTimeout;
            return this;
        }

        public Builder discoveryRetryCount(int discoveryRetryCount) {
            this.discoveryRetryCount = Math.max(0, discoveryRetryCount);
            return this;
        }

        public Builder discoveryCacheTtl(Duration discoveryCacheTtl) {
            this.discoveryCacheTtl = discoveryCacheTtl == null ? Duration.ofSeconds(30) : discoveryCacheTtl;
            return this;
        }

        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        public Builder responseMapper(SharedwallResponseMapper responseMapper) {
            this.responseMapper = responseMapper;
            return this;
        }

        public SharedwallClient build() {
            return new SharedwallClient(this);
        }
    }
}
