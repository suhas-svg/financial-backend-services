package com.suhasan.finance.transaction_service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Keeps docs/api/transaction-service.openapi.json equal to what the service actually serves.
 * The committed spec drives the generated frontend types and the CI breaking-change check.
 *
 * <p>After an intended API change, regenerate it with
 * {@code ./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true}.
 */
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("openapi")
class OpenApiSpecTest {

    private static final Path SPEC = Path.of("..", "docs", "api", "transaction-service.openapi.json");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void committedSpecMatchesTheServedSpec() throws Exception {
        final String served = mockMvc.perform(get("/v3/api-docs"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        final JsonNode servedSpec = mapper.readTree(served);
        assertThat(servedSpec.path("openapi").asText()).startsWith("3.");

        if (Boolean.getBoolean("openapi.update") || !Files.exists(SPEC)) {
            Files.createDirectories(SPEC.getParent());
            final Object sorted = mapper.treeToValue(servedSpec, Object.class);
            Files.writeString(SPEC, mapper.writeValueAsString(sorted).replace("\r\n", "\n") + "\n");
            return;
        }
        assertThat(mapper.readTree(Files.readString(SPEC)))
                .as("docs/api/transaction-service.openapi.json is stale; rerun with -Dopenapi.update=true and commit it")
                .isEqualTo(servedSpec);
    }
}
