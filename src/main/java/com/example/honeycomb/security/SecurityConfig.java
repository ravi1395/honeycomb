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

@Configuration
public class SecurityConfig {
    @Value("${actuator.user:admin}")
    private String actuatorUser;

    @Value("${actuator.password:changeit}")
    private String actuatorPassword;

    @Value("${management.endpoints.web.base-path:/actuator}")
    private String mgmtBasePath;

    @Value("${shared.user:shared}")
    private String sharedUser;

    @Value("${shared.password:changeit}")
    private String sharedPassword;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http.csrf(csrf -> csrf.disable())
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers(mgmtBasePath + "/**").hasRole("ACTUATOR")
                .pathMatchers("/honeycomb/shared/**").hasRole("SHARED_INVOCER")
                .anyExchange().permitAll()
            )
            .httpBasic(Customizer.withDefaults());
        return http.build();
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
