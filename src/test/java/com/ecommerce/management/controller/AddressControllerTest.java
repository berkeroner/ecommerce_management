package com.ecommerce.management.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import com.ecommerce.management.dto.address.AddressRequest;
import com.ecommerce.management.dto.address.AddressResponse;
import com.ecommerce.management.entity.enums.AddressType;
import com.ecommerce.management.service.AddressService;

@WebMvcTest(AddressController.class)
class AddressControllerTest {
    private static final String BASE = "/api/v1/customers/1/addresses";
    private static final String BODY = """
            {"address_type":"shipping","title":"Home","city":"Istanbul",
             "district":"Kadikoy","address_line":"Street 1","postal_code":"34000"}
            """;
    @Autowired MockMvc mockMvc;
    @MockitoBean AddressService addressService;

    @Test
    void shouldCreateAddressWithLocation() throws Exception {
        when(addressService.create(eq(1L), any(AddressRequest.class))).thenReturn(response());
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", BASE + "/10"))
                .andExpect(jsonPath("$.customer_id").value(1))
                .andExpect(jsonPath("$.address_type").value("shipping"));
    }

    @Test
    void shouldListAndReadAddresses() throws Exception {
        when(addressService.findAll(1L)).thenReturn(List.of(response()));
        when(addressService.findById(1L, 10L)).thenReturn(response());
        mockMvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(10));
        mockMvc.perform(get(BASE + "/10")).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Home"));
    }

    @Test
    void shouldUpdateAddress() throws Exception {
        when(addressService.update(eq(1L), eq(10L), any(AddressRequest.class))).thenReturn(response());
        mockMvc.perform(put(BASE + "/10").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(10));
        verify(addressService).update(1L, 10L,
                new AddressRequest(AddressType.SHIPPING, "Home", "Istanbul", "Kadikoy", "Street 1", "34000"));
    }

    @Test
    void shouldDeleteAddressWithoutResponseBody() throws Exception {
        mockMvc.perform(delete(BASE + "/10")).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(addressService).delete(1L, 10L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}"})
    void shouldRejectMissingFieldsAndInvalidEnum(String body) throws Exception {
        assertInvalid(body);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Home", "Istanbul", "Kadikoy", "Street 1"})
    void shouldRejectBlankRequiredFields(String value) throws Exception {
        assertInvalid(BODY.replace(value, " "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Home", "Istanbul", "Kadikoy", "34000"})
    void shouldRejectFieldsExceedingColumnLengths(String value) throws Exception {
        assertInvalid(BODY.replace(value, "a".repeat(value.equals("34000") ? 21 : 101)));
    }

    @Test
    void shouldAllowOmittedPostalCode() throws Exception {
        when(addressService.create(eq(1L), any(AddressRequest.class))).thenReturn(response());
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace(",\"postal_code\":\"34000\"", "")))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldReturnNotFoundForUnavailableAddress() throws Exception {
        var missing = new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found");
        when(addressService.findById(1L, 10L)).thenThrow(missing);
        when(addressService.update(eq(1L), eq(10L), any(AddressRequest.class))).thenThrow(missing);
        doThrow(missing).when(addressService).delete(1L, 10L);
        mockMvc.perform(get(BASE + "/10")).andExpect(status().isNotFound());
        mockMvc.perform(put(BASE + "/10").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(BASE + "/10")).andExpect(status().isNotFound());
    }

    private void assertInvalid(String body) throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(422));
        mockMvc.perform(put(BASE + "/10").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(422));
        verifyNoInteractions(addressService);
    }

    private AddressResponse response() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 10, 12, 0);
        return new AddressResponse(10L, 1L, AddressType.SHIPPING, "Home", "Istanbul", "Kadikoy", "Street 1", "34000", now, now);
    }
}
