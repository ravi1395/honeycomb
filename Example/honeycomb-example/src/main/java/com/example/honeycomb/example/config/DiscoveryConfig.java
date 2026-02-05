package com.example.honeycomb.example.config;

import com.example.honeycomb.example.ExampleConstants;
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
                return ExampleConstants.Discovery.NOOP_DESCRIPTION;
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
