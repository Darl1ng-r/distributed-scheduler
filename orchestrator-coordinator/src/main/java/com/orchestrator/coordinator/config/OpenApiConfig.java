package com.orchestrator.coordinator.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Distributed Task Orchestrator & Scheduler API")
                        .version("1.0.0")
                        .description("REST APIs for managing dynamic task schedules, execution history, and dead letter queue replay.")
                        .contact(new Contact().name("System Admin").email("admin@mycompany.com")));
    }
}
