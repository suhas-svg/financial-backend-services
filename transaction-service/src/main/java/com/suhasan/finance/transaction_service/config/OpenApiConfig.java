package com.suhasan.finance.transaction_service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI document served at /v3/api-docs when API_DOCS_ENABLED=true. The committed copy in
 * docs/api/ is the reference: frontend types are generated from it and CI rejects breaking
 * changes to it. Errors are RFC 9457 Problem Details (application/problem+json).
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER = "bearerAuth";
    static final String PROBLEM = "Problem";

    @Bean
    public OpenAPI transactionServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Transaction Service API")
                        .description("Transfers, deposits/withdrawals, scheduled transfers, ledger, disputes, risk and Outcome Protection. Errors use RFC 9457 Problem Details.")
                        .version("v1"))
                // Path prefix the gateway and the frontend use; the service itself listens on :8081.
                .servers(List.of(new Server().url("/transaction-api").description("Through the gateway")))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
    /** Documents the error body of every operation: RFC 9457 Problem Details. */
    @Bean
    public OpenApiCustomizer problemDetailResponses() {
        return openApi -> {
            // Registered here: springdoc replaces the schemas given on the OpenAPI bean.
            openApi.getComponents().addSchemas(PROBLEM, problemSchema());
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
                    operation.getResponses().addApiResponse("default", new ApiResponse()
                            .description("Error, as RFC 9457 Problem Details")
                            .content(new Content().addMediaType("application/problem+json",
                                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM)))))));
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Schema<?> problemSchema() {
        return new ObjectSchema()
                .description("RFC 9457 Problem Details. error, message, path and timestamp repeat title, "
                        + "detail and instance for clients written before the RFC format.")
                .addProperty("type", new StringSchema().format("uri")
                        .example("urn:financial:problem:insufficient-funds"))
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema().format("uri"))
                .addProperty("error", new StringSchema())
                .addProperty("message", new StringSchema())
                .addProperty("path", new StringSchema())
                .addProperty("timestamp", new DateTimeSchema())
                .addProperty("validationErrors", new MapSchema()
                        .additionalProperties(new StringSchema())
                        .description("Field name to message, for validation failures"))
                .addProperty("transactionId", new StringSchema())
                .required(List.of("type", "title", "status"));
    }
}
