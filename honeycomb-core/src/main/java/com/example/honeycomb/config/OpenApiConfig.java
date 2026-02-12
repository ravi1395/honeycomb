package com.example.honeycomb.config;

import com.example.honeycomb.util.HoneycombConstants;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger documentation configuration.
 */
@Configuration
public class OpenApiConfig {

        @Value(HoneycombConstants.PropertyValues.SERVER_PORT)
    private int serverPort;

    @Bean
    public OpenAPI honeycombOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(HoneycombConstants.OpenApi.TITLE)
                        .description(HoneycombConstants.OpenApi.DESCRIPTION)
                        .version(HoneycombConstants.OpenApi.VERSION)
                        .contact(new Contact()
                                .name(HoneycombConstants.OpenApi.CONTACT_NAME)
                                .email(HoneycombConstants.OpenApi.CONTACT_EMAIL))
                        .license(new License()
                                .name(HoneycombConstants.OpenApi.LICENSE_NAME)
                                .url(HoneycombConstants.OpenApi.LICENSE_URL)))
                .servers(List.of(
                        new Server()
                                .url(HoneycombConstants.Schemes.HTTP
                                        + HoneycombConstants.Hosts.LOCALHOST
                                        + HoneycombConstants.Names.SEPARATOR_COLON
                                        + serverPort)
                                .description(HoneycombConstants.OpenApi.SERVER_DESC)
                ));
    }
}
