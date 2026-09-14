package com.ecommerce.management.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.ecommerce.management.dto.address.AddressRequest;
import com.ecommerce.management.entity.Address;
import com.ecommerce.management.entity.enums.AddressType;
import com.ecommerce.management.entity.enums.AddressableType;
import com.ecommerce.management.repository.AddressRepository;
import com.ecommerce.management.repository.CustomerRepository;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {
    @Mock AddressRepository addressRepository;
    @Mock CustomerRepository customerRepository;
    @InjectMocks AddressService addressService;

    @Test
    void shouldCreateCustomerAddressAndTrimFields() {
        when(customerRepository.existsById(1L)).thenReturn(true);
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> {
            Address address = invocation.getArgument(0);
            assertEquals(AddressableType.CUSTOMER, address.getAddressableType());
            address.setId(10L);
            return address;
        });
        var response = addressService.create(1L, request(" 34000 "));
        assertEquals(10L, response.id());
        assertEquals(1L, response.customerId());
        assertEquals(AddressType.SHIPPING, response.addressType());
        assertEquals("Home", response.title());
        assertEquals("Istanbul", response.city());
        assertEquals("Kadikoy", response.district());
        assertEquals("Street 1", response.addressLine());
        assertEquals("34000", response.postalCode());
        assertNotNull(response.createdAt());
        assertEquals(response.createdAt(), response.updatedAt());
    }

    @Test
    void shouldListAndReadCustomerAddresses() {
        when(customerRepository.existsById(1L)).thenReturn(true);
        Address address = address();
        when(addressRepository.findAllByAddressableTypeAndAddressableId(AddressableType.CUSTOMER, 1L))
                .thenReturn(List.of(address));
        when(addressRepository.findByIdAndAddressableTypeAndAddressableId(10L, AddressableType.CUSTOMER, 1L))
                .thenReturn(Optional.of(address));
        assertEquals(10L, addressService.findAll(1L).getFirst().id());
        assertEquals(10L, addressService.findById(1L, 10L).id());
    }

    @Test
    void shouldReturnEmptyListForCustomerWithoutAddresses() {
        when(customerRepository.existsById(1L)).thenReturn(true);
        assertTrue(addressService.findAll(1L).isEmpty());
    }

    @Test
    void shouldRejectEveryOperationForMissingCustomer() {
        assertNotFound(() -> addressService.create(99L, request(null)));
        assertNotFound(() -> addressService.findAll(99L));
        assertNotFound(() -> addressService.findById(99L, 10L));
        assertNotFound(() -> addressService.update(99L, 10L, request(null)));
        assertNotFound(() -> addressService.delete(99L, 10L));
        verifyNoInteractions(addressRepository);
    }

    @Test
    void shouldRejectReadUpdateAndDeleteWhenAddressDoesNotBelongToCustomer() {
        when(customerRepository.existsById(1L)).thenReturn(true);
        when(addressRepository.findByIdAndAddressableTypeAndAddressableId(10L, AddressableType.CUSTOMER, 1L))
                .thenReturn(Optional.empty());
        assertNotFound(() -> addressService.findById(1L, 10L));
        assertNotFound(() -> addressService.update(1L, 10L, request(null)));
        assertNotFound(() -> addressService.delete(1L, 10L));
        verify(addressRepository, never()).save(any());
        verify(addressRepository, never()).delete(any(Address.class));
    }

    @Test
    void shouldUpdateFieldsPreservingOwnerAndCreationTimeAndAllowClearingPostalCode() {
        Address address = address();
        LocalDateTime createdAt = address.getCreatedAt();
        when(customerRepository.existsById(1L)).thenReturn(true);
        when(addressRepository.findByIdAndAddressableTypeAndAddressableId(10L, AddressableType.CUSTOMER, 1L))
                .thenReturn(Optional.of(address));
        when(addressRepository.save(address)).thenReturn(address);
        var response = addressService.update(1L, 10L, request(null));
        assertEquals("Home", response.title());
        assertNull(response.postalCode());
        assertEquals(createdAt, response.createdAt());
        assertTrue(response.updatedAt().isAfter(createdAt));
        assertEquals(1L, response.customerId());
        assertEquals(AddressableType.CUSTOMER, address.getAddressableType());
    }

    @Test
    void shouldDeleteOwnedAddress() {
        Address address = address();
        when(customerRepository.existsById(1L)).thenReturn(true);
        when(addressRepository.findByIdAndAddressableTypeAndAddressableId(10L, AddressableType.CUSTOMER, 1L))
                .thenReturn(Optional.of(address));
        addressService.delete(1L, 10L);
        verify(addressRepository).delete(address);
    }

    private void assertNotFound(org.junit.jupiter.api.function.Executable action) {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, action).getStatusCode());
    }

    private AddressRequest request(String postalCode) {
        return new AddressRequest(AddressType.SHIPPING, " Home ", " Istanbul ", " Kadikoy ", " Street 1 ", postalCode);
    }

    private Address address() {
        Address address = new Address();
        address.setId(10L);
        address.setAddressableType(AddressableType.CUSTOMER);
        address.setAddressableId(1L);
        address.setTitle("Old home");
        address.setPostalCode("06000");
        address.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        address.setUpdatedAt(address.getCreatedAt());
        return address;
    }
}
