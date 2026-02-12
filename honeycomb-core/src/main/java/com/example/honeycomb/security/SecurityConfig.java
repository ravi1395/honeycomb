package com.example.honeycomb.security;

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
import com.example.honeycomb.config.HoneycombSecurityProperties;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.example.honeycomb.util.HoneycombConstants;
import com.example.honeycomb.util.HoneycombUtil;

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
        var shared = User.withUsername(sharedUser).password(encoder.encode(sharedPassword)).roles("SHARED_INVOCER").build();
        return new MapReactiveUserDetailsService(actuator, shared);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
