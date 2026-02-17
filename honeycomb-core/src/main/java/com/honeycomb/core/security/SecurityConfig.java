package com.honeycomb.core.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;
import com.honeycomb.core.config.HoneycombSecurityProperties;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import com.honeycomb.core.util.HoneycombConstants;
import com.honeycomb.core.util.HoneycombUtil;

import java.util.List;

/**
 * Central Spring Security configuration for Honeycomb.
 *
 * <p>Defines the reactive {@link SecurityWebFilterChain} with:
 * <ul>
 *   <li>CSRF disabled (API-only service).</li>
 *   <li>Hardened HTTP headers (HSTS, X-Frame-Options DENY, Referrer-Policy, Permissions-Policy).</li>
 *   <li>Path-based authorisation: actuator behind ACTUATOR role, shared methods behind SHARED_INVOKER.</li>
 *   <li>HTTP Basic + optional OAuth2 resource server (JWT) support.</li>
 * </ul>
 *
 * <p>Also registers a {@link MapReactiveUserDetailsService} with built-in
 * actuator and shared-method users, and exposes a {@link BCryptPasswordEncoder}.</p>
 *
 * @see ApiKeyAuthFilter
 * @see JwtCellAccessFilter
 * @see MtlsAuthFilter
 */
@Configuration
public class SecurityConfig {
    @Value(HoneycombConstants.PropertyValues.ACTUATOR_USER)
    private String actuatorUser;

    @Value(HoneycombConstants.PropertyValues.ACTUATOR_PASSWORD)
    private String actuatorPassword;

    @Value(HoneycombConstants.PropertyValues.MGMT_BASE_PATH)
    private String mgmtBasePath;

    @Value(HoneycombConstants.PropertyValues.SHARED_USER)
    private String sharedUser;

    @Value(HoneycombConstants.PropertyValues.SHARED_PASSWORD)
    private String sharedPassword;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, HoneycombSecurityProperties securityProperties) {
        http.csrf(csrf -> csrf.disable());
        http.cors(cors -> cors.configurationSource(corsConfigurationSource(securityProperties)));

        http.headers(headers -> headers
            .contentTypeOptions(Customizer.withDefaults())
                .frameOptions(frame -> frame.mode(XFrameOptionsServerHttpHeadersWriter.Mode.DENY))
            .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.NO_REFERRER))
            .permissionsPolicy(permissions -> permissions.policy("geolocation=(), microphone=(), camera=()"))
            .hsts(hsts -> hsts.includeSubdomains(true).maxAge(java.time.Duration.ofDays(365))));

        http.authorizeExchange(exchanges -> {
            exchanges.pathMatchers(mgmtBasePath + "/**").hasRole(HoneycombConstants.Roles.ACTUATOR);
            exchanges.pathMatchers(HoneycombConstants.Paths.HONEYCOMB_SHARED + "/**").hasRole(HoneycombConstants.Roles.SHARED_INVOKER);
            exchanges.pathMatchers(HoneycombConstants.Paths.HONEYCOMB_SWAGGER_UI + "/**", HoneycombConstants.Paths.HONEYCOMB_API_DOCS + "/**").permitAll();
            if (securityProperties.isRequireAuth()) {
                exchanges.pathMatchers(HoneycombConstants.Paths.HONEYCOMB_BASE + "/**").authenticated();
            }
            exchanges.anyExchange().permitAll();
        });

        http.httpBasic(Customizer.withDefaults());

        HoneycombUtil.configureOAuth2(http, securityProperties);

        return http.build();
    }

    @Bean
        @ConditionalOnProperty(name = HoneycombConstants.ConfigKeys.JWT_ENABLED,
            havingValue = HoneycombConstants.Values.TRUE)
    public ReactiveJwtDecoder jwtDecoder(HoneycombSecurityProperties securityProperties) {
        return HoneycombUtil.jwtDecoder(securityProperties);
    }

    @Bean
    public MapReactiveUserDetailsService users(PasswordEncoder encoder) {
        var actuator = User.withUsername(actuatorUser).password(encoder.encode(actuatorPassword)).roles("ACTUATOR").build();
        var shared = User.withUsername(sharedUser).password(encoder.encode(sharedPassword)).roles("SHARED_INVOKER").build();
        return new MapReactiveUserDetailsService(actuator, shared);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS configuration for Honeycomb endpoints.
     * Defaults to restrictive settings; override via {@code honeycomb.security.cors.*} properties.
     */
    private CorsConfigurationSource corsConfigurationSource(HoneycombSecurityProperties securityProperties) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = securityProperties.getCorsAllowedOrigins();
        config.setAllowedOrigins(origins != null && !origins.isEmpty() ? origins : List.of());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-API-Key", "X-Request-Id", "X-Tenant-Id", "X-From-Cell", "traceparent", "tracestate"));
        config.setExposedHeaders(List.of("X-Request-Id", "X-Tenant-Id", "traceparent"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
