package com.securevault;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("OpenApiExportTest: Export OpenAPI 3.0 JSON specification")
public class OpenApiExportTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exportOpenApiJsonSpecification() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        String rawJson = result.getResponse().getContentAsString();
        assertNotNull(rawJson);
        assertTrue(rawJson.contains("SecureVault"));

        // Format nicely
        Object jsonObj = objectMapper.readValue(rawJson, Object.class);
        String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObj);

        // Target paths
        Path rootPath = Paths.get("..", "openapi.json").toAbsolutePath().normalize();
        Path docsPath = Paths.get("..", "docs", "openapi.json").toAbsolutePath().normalize();

        // Ensure docs dir exists
        Files.createDirectories(docsPath.getParent());

        Files.writeString(rootPath, prettyJson);
        Files.writeString(docsPath, prettyJson);

        System.out.println("Exported OpenAPI spec to: " + rootPath);
        System.out.println("Exported OpenAPI spec to: " + docsPath);
    }
}
