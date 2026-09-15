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

import java.time.LocalDateTime;
import java.util.List;
import com.ecommerce.management.dto.common.PageResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ecommerce.management.dto.customer.CustomerRequest;
import com.ecommerce.management.dto.customer.CustomerResponse;
import com.ecommerce.management.entity.enums.RecordStatus;
import com.ecommerce.management.service.CustomerService;

@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean CustomerService customerService;

    @Test
    void shouldListCustomers() throws Exception {
        when(customerService.findAll(1, 20, null, null)).thenReturn(new PageResponse<>(List.of(response()), 1, 20, 1, 1));
        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].email").value("ada@example.com"));
    }

    @Test
    void shouldReturnCustomerDetail() throws Exception {
        when(customerService.findById(1L)).thenReturn(response());
        mockMvc.perform(get("/api/v1/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldCreateCustomer() throws Exception {
        when(customerService.create(any(CustomerRequest.class))).thenReturn(response());
        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada Lovelace\",\"email\":\"ada@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/customers/1"));
    }

    @Test
    void shouldRejectInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada Lovelace\",\"email\":\"invalid\"}"))
                .andExpect(status().is(422));
    }

    @Test
    void shouldUpdateCustomer() throws Exception {
        when(customerService.update(any(Long.class), any(CustomerRequest.class))).thenReturn(response());
        mockMvc.perform(put("/api/v1/customers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada Lovelace\",\"email\":\"ada@example.com\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldUpdateCustomerStatus() throws Exception {
        when(customerService.updateStatus(1L, RecordStatus.PASSIVE)).thenReturn(response(RecordStatus.PASSIVE));
        mockMvc.perform(patch("/api/v1/customers/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"passive\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("passive"));
    }

    @Test
    void shouldReturnRequestedPageAndSnakeCaseMetadata() throws Exception {
        when(customerService.findAll(2, 5, null, null)).thenReturn(new PageResponse<>(List.of(response()), 2, 5, 6, 2));
        mockMvc.perform(get("/api/v1/customers").param("page", "2").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.limit").value(5))
                .andExpect(jsonPath("$.total_elements").value(6))
                .andExpect(jsonPath("$.total_pages").value(2));
    }

    @Test
    void shouldRejectNonNumericPagination() throws Exception {
        for (String parameter : List.of("page", "limit")) {
            mockMvc.perform(get("/api/v1/customers").param(parameter, "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        }
        org.mockito.Mockito.verifyNoInteractions(customerService);
    }

    @Test
    void shouldPassFiltersAlongsidePagination() throws Exception {
        when(customerService.findAll(2, 5, "active", "ada")).thenReturn(
                new PageResponse<>(List.of(response()), 2, 5, 6, 2));
        mockMvc.perform(get("/api/v1/customers").param("page", "2").param("limit", "5")
                        .param("status", "active").param("search", "ada"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("active"))
                .andExpect(jsonPath("$.total_elements").value(6));
    }

    private CustomerResponse response() { return response(RecordStatus.ACTIVE); }

    private CustomerResponse response(RecordStatus status) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 4, 12, 0);
        return new CustomerResponse(1L, "Ada Lovelace", "ada@example.com", status, now, now);
    }
}
