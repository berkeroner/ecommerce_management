package com.ecommerce.management.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import com.ecommerce.management.controller.ProductController;
import com.ecommerce.management.service.ProductService;

@WebMvcTest(ProductController.class)
class ApiContractTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProductService service;

    @Test
    void validationUsesSnakeCaseFieldErrorsAndCorrelationId() throws Exception {
        mvc.perform(post("/api/v1/products").header("X-Correlation-ID", "test-123")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is(422))
                .andExpect(header().string("X-Correlation-ID", "test-123"))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.category_id").isArray())
                .andExpect(jsonPath("$.error.correlation_id").value("test-123"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"status\":\"invalid\"}"})
    void unreadableRequestsAre400(String body) throws Exception {
        mvc.perform(patch("/api/v1/products/1/status").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.correlation_id").isNotEmpty());
        verifyNoInteractions(service);
    }

    @Test
    void preservesNotFoundAndConflictStatus() throws Exception {
        when(service.findById(1L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: 1"));
        when(service.findById(2L)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Conflict"));
        mvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Product not found: 1"));
        mvc.perform(get("/api/v1/products/2"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void unexpectedFailureHidesInternalMessage() throws Exception {
        when(service.findById(1L)).thenThrow(new IllegalStateException("private database details"));
        mvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("An unexpected error occurred"));
        assertNull(MDC.get("correlation_id"));
    }

    @Test
    void invalidPathAndUnknownRouteUseErrorEnvelope() throws Exception {
        mvc.perform(get("/api/v1/products/abc"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/v1/missing"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void correlationIdIsAvailableDuringRequestAndCleanedUpAfterwards() throws Exception {
        when(service.findAll(1, 20, null, null, null, null)).thenAnswer(invocation -> {
            assertEquals("request-123", MDC.get("correlation_id"));
            return new com.ecommerce.management.dto.common.PageResponse<>(List.of(), 1, 20, 0, 0);
        });
        mvc.perform(get("/api/v1/products").header("X-Correlation-ID", "request-123"))
                .andExpect(status().isOk()).andExpect(header().string("X-Correlation-ID", "request-123"));
        assertNull(MDC.get("correlation_id"));
    }

    @Test
    void unsafeCorrelationIdIsReplacedWithUuid() throws Exception {
        var result = mvc.perform(get("/api/v1/products").header("X-Correlation-ID", "a".repeat(65)))
                .andExpect(status().isOk()).andReturn();
        assertDoesNotThrow(() -> java.util.UUID.fromString(result.getResponse().getHeader("X-Correlation-ID")));
    }
}
