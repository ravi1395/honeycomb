package com.example.honeycomb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.example.honeycomb.config.HoneycombProperties;

@SpringBootApplication
@EnableConfigurationProperties(HoneycombProperties.class)
public class HoneycombApplication {
    public static void main(String[] args) {
        SpringApplication.run(HoneycombApplication.class, args);
    }
}
