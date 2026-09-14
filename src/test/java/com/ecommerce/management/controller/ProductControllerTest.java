package com.ecommerce.management.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.ecommerce.management.dto.common.PageResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ecommerce.management.dto.product.ProductRequest;
import com.ecommerce.management.dto.product.ProductResponse;
import com.ecommerce.management.entity.enums.RecordStatus;
import com.ecommerce.management.service.ProductService;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ProductService productService;

    @Test
    void shouldListProducts() throws Exception {
        when(productService.findAll(1, 20, null, null, null, null)).thenReturn(new PageResponse<>(List.of(response(10, RecordStatus.ACTIVE)), 1, 20, 1, 1));
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sku").value("LAPTOP-1"));
    }

    @Test
    void shouldReturnProductDetail() throws Exception {
        when(productService.findById(1L)).thenReturn(response(10, RecordStatus.ACTIVE));
        mockMvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category_id").value(2));
    }

    @Test
    void shouldCreateProduct() throws Exception {
        when(productService.create(any(ProductRequest.class))).thenReturn(response(10, RecordStatus.ACTIVE));
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/products/1"));
    }

    @Test
    void shouldRejectNegativePriceAndStock() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category_id\":2,\"name\":\"Laptop\",\"sku\":\"LAPTOP-1\",\"price\":-1,\"stock\":-1}"))
                .andExpect(status().is(422));
    }

    @Test
    void shouldUpdateProduct() throws Exception {
        when(productService.update(any(Long.class), any(ProductRequest.class))).thenReturn(response(10, RecordStatus.ACTIVE));
        mockMvc.perform(put("/api/v1/products/1").contentType(MediaType.APPLICATION_JSON).content(validRequest()))
                .andExpect(status().isOk());
    }

    @Test
    void shouldUpdateProductStatus() throws Exception {
        when(productService.updateStatus(1L, RecordStatus.PASSIVE)).thenReturn(response(10, RecordStatus.PASSIVE));
        mockMvc.perform(patch("/api/v1/products/1/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"passive\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("passive"));
    }

    @Test
    void shouldUpdateProductStock() throws Exception {
        when(productService.updateStock(1L, 25)).thenReturn(response(25, RecordStatus.ACTIVE));
        mockMvc.perform(patch("/api/v1/products/1/stock")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stock\":25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(25));
    }

    @Test
    void shouldPassPageParametersAndReturnMetadata() throws Exception {
        when(productService.findAll(2, 5, null, null, null, null)).thenReturn(new PageResponse<>(List.of(), 2, 5, 5, 1));
        mockMvc.perform(get("/api/v1/products").param("page", "2").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.limit").value(5))
                .andExpect(jsonPath("$.total_elements").value(5))
                .andExpect(jsonPath("$.total_pages").value(1));
    }

    @Test
    void shouldRejectNonNumericPage() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void shouldPassOptionalCategoryFilter() throws Exception {
        when(productService.findAll(1, 5, 2L, null, null, null)).thenReturn(
                new PageResponse<>(List.of(response(10, RecordStatus.ACTIVE)), 1, 5, 1, 1));
        mockMvc.perform(get("/api/v1/products").param("category_id", "2").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].category_id").value(2))
                .andExpect(jsonPath("$.total_elements").value(1));
    }

    @Test
    void shouldRejectMalformedCategoryId() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("category_id", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void shouldPassStatusAloneAndWithCategory() throws Exception {
        when(productService.findAll(1, 20, null, "active", null, null)).thenReturn(
                new PageResponse<>(List.of(response(10, RecordStatus.ACTIVE)), 1, 20, 1, 1));
        when(productService.findAll(1, 20, 2L, "passive", null, null)).thenReturn(
                new PageResponse<>(List.of(response(10, RecordStatus.PASSIVE)), 1, 20, 1, 1));
        mockMvc.perform(get("/api/v1/products").param("status", "active"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].status").value("active"));
        mockMvc.perform(get("/api/v1/products").param("category_id", "2").param("status", "passive"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].status").value("passive"));
    }

    @Test
    void shouldPassSearchWithCategoryAndStatus() throws Exception {
        when(productService.findAll(1, 20, 2L, "active", "Laptop", null)).thenReturn(
                new PageResponse<>(List.of(response(10, RecordStatus.ACTIVE)), 1, 20, 1, 1));
        mockMvc.perform(get("/api/v1/products").param("category_id", "2")
                        .param("status", "active").param("search", "Laptop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Laptop"))
                .andExpect(jsonPath("$.total_elements").value(1));
    }

    @Test
    void shouldPassSortAlongsideAllFilters() throws Exception {
        when(productService.findAll(1, 20, 2L, "active", "Laptop", "price,desc")).thenReturn(
                new PageResponse<>(List.of(response(10, RecordStatus.ACTIVE)), 1, 20, 1, 1));
        mockMvc.perform(get("/api/v1/products").param("category_id", "2")
                        .param("status", "active").param("search", "Laptop").param("sort", "price,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Laptop"));
    }

    private String validRequest() {
        return "{\"category_id\":2,\"name\":\"Laptop\",\"sku\":\"LAPTOP-1\",\"price\":1250.00,\"stock\":10}";
    }

    private ProductResponse response(int stock, RecordStatus status) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 4, 12, 0);
        return new ProductResponse(1L, 2L, "Elektronik", "Laptop", "LAPTOP-1",
                new BigDecimal("1250.00"), stock, status, now, now);
    }
}
