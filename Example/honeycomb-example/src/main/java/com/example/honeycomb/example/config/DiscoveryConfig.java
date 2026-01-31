package com.example.honeycomb.example.config;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import reactor.core.publisher.Flux;

@Configuration
public class DiscoveryConfig {

    @Bean
    @ConditionalOnMissingBean(ReactiveDiscoveryClient.class)
    public ReactiveDiscoveryClient noOpReactiveDiscoveryClient() {
        return new ReactiveDiscoveryClient() {
            @Override
            public String description() {
                return "noop-reactive-discovery-client";
            }

            @Override
            public Flux<ServiceInstance> getInstances(String serviceId) {
                return Flux.empty();
            }

            @Override
            public Flux<String> getServices() {
                return Flux.empty();
            }
        };
    }
}
