package com.ecommerce.management.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ecommerce.management.dto.category.CategoryRequest;
import com.ecommerce.management.dto.category.CategoryResponse;
import com.ecommerce.management.service.CategoryService;

@WebMvcTest(CategoryController.class)
class CategoryControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean CategoryService categoryService;

    @Test
    void shouldListCategories() throws Exception {
        when(categoryService.findAll()).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].slug").value("elektronik"));
    }

    @Test
    void shouldReturnCategoryDetail() throws Exception {
        when(categoryService.findById(1L)).thenReturn(response());

        mockMvc.perform(get("/api/v1/categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Elektronik"));
    }

    @Test
    void shouldCreateCategory() throws Exception {
        when(categoryService.create(any(CategoryRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Elektronik","slug":"elektronik","is_active":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/categories/1"))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldRejectInvalidCategory() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"slug\":\"\",\"is_active\":null}"))
                .andExpect(status().is(422));
    }

    @Test
    void shouldUpdateCategory() throws Exception {
        when(categoryService.update(any(Long.class), any(CategoryRequest.class))).thenReturn(response());

        mockMvc.perform(put("/api/v1/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Elektronik\",\"slug\":\"elektronik\",\"is_active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("elektronik"));
    }

    @Test
    void shouldDeleteCategory() throws Exception {
        mockMvc.perform(delete("/api/v1/categories/1"))
                .andExpect(status().isNoContent());

        verify(categoryService).delete(1L);
    }

    private CategoryResponse response() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 4, 12, 0);
        return new CategoryResponse(1L, "Elektronik", "elektronik", true, now, now);
    }
}
