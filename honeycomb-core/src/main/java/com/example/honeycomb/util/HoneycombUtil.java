package com.example.honeycomb.util;

import com.example.honeycomb.config.HoneycombSecurityProperties;
import com.example.honeycomb.client.SharedwallClient;
import com.example.honeycomb.security.JwtAudienceValidator;
import org.springframework.http.MediaType;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.core.ParameterizedTypeReference;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;

public final class HoneycombUtil {
    private HoneycombUtil() {}

    public static void configureOAuth2(ServerHttpSecurity http, HoneycombSecurityProperties securityProperties) {
        var jwt = securityProperties != null ? securityProperties.getJwt() : null;
        if (jwt != null && jwt.isEnabled()) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwtSpec -> jwtSpec.jwtAuthenticationConverter(jwtAuthenticationConverter(securityProperties))));
        }
    }

    public static ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter(HoneycombSecurityProperties securityProperties) {
        JwtGrantedAuthoritiesConverter roleAuthorities = new JwtGrantedAuthoritiesConverter();
        JwtGrantedAuthoritiesConverter scopeAuthorities = new JwtGrantedAuthoritiesConverter();
        JwtGrantedAuthoritiesConverter sharedAuthorities = new JwtGrantedAuthoritiesConverter();
        var jwtProps = securityProperties != null ? securityProperties.getJwt() : null;
        if (jwtProps != null) {
            if (StringUtils.hasText(jwtProps.getRolesClaim())) {
                roleAuthorities.setAuthoritiesClaimName(jwtProps.getRolesClaim());
            }
            if (StringUtils.hasText(jwtProps.getRolePrefix())) {
                roleAuthorities.setAuthorityPrefix(jwtProps.getRolePrefix());
            }
            if (StringUtils.hasText(jwtProps.getScopesClaim())) {
                scopeAuthorities.setAuthoritiesClaimName(jwtProps.getScopesClaim());
            }
            if (StringUtils.hasText(jwtProps.getScopePrefix())) {
                scopeAuthorities.setAuthorityPrefix(jwtProps.getScopePrefix());
            }
            if (StringUtils.hasText(jwtProps.getSharedRolesClaim())) {
                sharedAuthorities.setAuthoritiesClaimName(jwtProps.getSharedRolesClaim());
            }
            if (StringUtils.hasText(jwtProps.getSharedRolePrefix())) {
                sharedAuthorities.setAuthorityPrefix(jwtProps.getSharedRolePrefix());
            }
        }
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var roles = roleAuthorities.convert(jwt);
            var scopes = scopeAuthorities.convert(jwt);
            var shared = sharedAuthorities.convert(jwt);
            if (roles == null && scopes == null) return shared;
            if (roles == null && shared == null) return scopes;
            if (scopes == null && shared == null) return roles;
            java.util.List<org.springframework.security.core.GrantedAuthority> merged = new java.util.ArrayList<>();
            if (roles != null) merged.addAll(roles);
            if (scopes != null) merged.addAll(scopes);
            if (shared != null) merged.addAll(shared);
            return merged;
        });
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    public static ReactiveJwtDecoder jwtDecoder(HoneycombSecurityProperties securityProperties) {
        var jwt = securityProperties != null ? securityProperties.getJwt() : null;
        String issuer = jwt != null ? jwt.getIssuerUri() : null;
        String jwkSetUri = jwt != null ? jwt.getJwkSetUri() : null;
        NimbusReactiveJwtDecoder decoder;
        if (StringUtils.hasText(jwkSetUri)) {
            decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        } else if (StringUtils.hasText(issuer)) {
            decoder = (NimbusReactiveJwtDecoder) NimbusReactiveJwtDecoder.withIssuerLocation(issuer).build();
        } else {
            throw new IllegalStateException(HoneycombConstants.Messages.JWT_MISSING_ISSUER_OR_JWK);
        }

        OAuth2TokenValidator<Jwt> withIssuer = StringUtils.hasText(issuer)
                ? JwtValidators.createDefaultWithIssuer(issuer)
                : JwtValidators.createDefault();
        OAuth2TokenValidator<Jwt> audience = new JwtAudienceValidator(jwt != null ? jwt.getAudience() : null);
        decoder.setJwtValidator(token -> {
            var issuerResult = withIssuer.validate(token);
            if (issuerResult.hasErrors()) return issuerResult;
            return audience.validate(token);
        });
        return decoder;
    }

    public static WebClient createOAuth2WebClient(WebClient.Builder builder,
                                                  ReactiveOAuth2AuthorizedClientManager manager,
                                                  String registrationId) {
        ServerOAuth2AuthorizedClientExchangeFilterFunction oauth2 =
                new ServerOAuth2AuthorizedClientExchangeFilterFunction(manager);
        if (StringUtils.hasText(registrationId)) {
            oauth2.setDefaultClientRegistrationId(registrationId);
        }
        return builder.filter(oauth2).build();
    }

    public static Mono<Map<String, Object>> invokeSharedwallOAuth2(WebClient webClient,
                                                                   String baseUrl,
                                                                   String methodName,
                                                                   Object body,
                                                                   String fromCell,
                                                                   MediaType contentType,
                                                                   String registrationId) {
        String url = baseUrl + HoneycombConstants.Paths.HONEYCOMB_SHARED + HoneycombConstants.Names.SEPARATOR_SLASH + methodName;
        MediaType resolvedType = contentType;
        if (resolvedType == null) {
            if (body instanceof String || body instanceof byte[]) {
                resolvedType = MediaType.TEXT_PLAIN;
            } else {
                resolvedType = MediaType.APPLICATION_JSON;
            }
        }

        final MediaType finalContentType = resolvedType;
        WebClient.RequestBodySpec req = webClient.post()
                .uri(url)
                .headers(h -> {
                    if (fromCell != null && !fromCell.isBlank()) {
                        h.add(HoneycombConstants.Headers.FROM_CELL, fromCell);
                    }
                    h.setContentType(finalContentType);
                });

        if (StringUtils.hasText(registrationId)) {
            req = req.attributes(attrs ->
                ServerOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId(registrationId)
                    .accept(attrs));
        }

        if (body == null) {
            return req.retrieve().bodyToMono(new ParameterizedTypeReference<>() {});
        }

        return req.bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<>() {});
    }

    public static Mono<Map<String, Object>> invokeSharedwall(WebClient webClient,
                                                             String baseUrl,
                                                             String methodName,
                                                             Object body,
                                                             String accessToken,
                                                             String fromCell,
                                                             MediaType contentType) {
        String url = baseUrl + HoneycombConstants.Paths.HONEYCOMB_SHARED + HoneycombConstants.Names.SEPARATOR_SLASH + methodName;
        MediaType resolvedType = contentType;
        if (resolvedType == null) {
            if (body instanceof String || body instanceof byte[]) {
                resolvedType = MediaType.TEXT_PLAIN;
            } else {
                resolvedType = MediaType.APPLICATION_JSON;
            }
        }

        final MediaType finalContentType = resolvedType;
        WebClient.RequestBodySpec req = webClient.post()
                .uri(url)
                .headers(h -> {
                    if (StringUtils.hasText(accessToken)) {
                        h.setBearerAuth(Objects.requireNonNull(accessToken));
                    }
                    if (fromCell != null && !fromCell.isBlank()) {
                        h.add(HoneycombConstants.Headers.FROM_CELL, fromCell);
                    }
                    h.setContentType(finalContentType);
                });

        if (body == null) {
            return req.retrieve().bodyToMono(new ParameterizedTypeReference<>() {});
        }

        return req.bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<>() {});
    }

    public static SharedwallClient sharedwallClient(WebClient webClient, String baseUrl, String fromCell) {
        return SharedwallClient.builder(webClient, baseUrl)
                .fromCell(fromCell)
                .build();
    }

    public static SharedwallClient sharedwallOAuth2Client(WebClient.Builder builder,
                                                          ReactiveOAuth2AuthorizedClientManager manager,
                                                          String baseUrl,
                                                          String fromCell,
                                                          String registrationId) {
        ServerOAuth2AuthorizedClientExchangeFilterFunction oauth2 =
                new ServerOAuth2AuthorizedClientExchangeFilterFunction(manager);
        if (StringUtils.hasText(registrationId)) {
            oauth2.setDefaultClientRegistrationId(registrationId);
        }
        WebClient client = builder.filter(oauth2).build();
        return SharedwallClient.builder(client, baseUrl)
                .fromCell(fromCell)
                .registrationId(registrationId)
                .build();
    }
}
