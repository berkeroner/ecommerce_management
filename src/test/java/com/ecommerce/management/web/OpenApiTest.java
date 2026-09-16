package com.ecommerce.management.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiTest {
    @Autowired MockMvc mvc;

    @Test
    void documentationIncludesControllersAndSnakeCaseRequestFields() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Mini E-Ticaret API"))
                .andExpect(jsonPath("$.paths['/api/v1/products'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/categories'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/customers'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/customers/{customerId}/addresses'].post").exists())
                .andExpect(jsonPath("$.components.schemas.AddressRequest.properties.address_type").exists())
                .andExpect(jsonPath("$.components.schemas.AddressRequest.properties.address_line").exists());
    }

    @Test
    void swaggerUiAndItsConfigurationAreAvailable() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").value("/v3/api-docs"));
    }
}
