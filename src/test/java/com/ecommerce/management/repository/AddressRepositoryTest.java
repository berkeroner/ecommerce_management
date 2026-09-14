package com.ecommerce.management.repository;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import com.ecommerce.management.entity.Address;
import com.ecommerce.management.entity.enums.AddressType;
import com.ecommerce.management.entity.enums.AddressableType;
import jakarta.persistence.EntityManager;

@DataJpaTest
@ActiveProfiles("test")
class AddressRepositoryTest {
    @Autowired AddressRepository addressRepository;
    @Autowired EntityManager entityManager;

    @Test
    void shouldScopeListAndDetailByBothOwnerIdAndType() {
        Address owned = addressRepository.saveAndFlush(address(AddressableType.CUSTOMER, 1L));
        Address other = addressRepository.saveAndFlush(address(AddressableType.CUSTOMER, 2L));
        Address order = addressRepository.saveAndFlush(address(AddressableType.ORDER, 1L));
        entityManager.clear();
        var addresses = addressRepository.findAllByAddressableTypeAndAddressableId(AddressableType.CUSTOMER, 1L);
        assertEquals(1, addresses.size());
        assertEquals(owned.getId(), addresses.getFirst().getId());
        assertTrue(addressRepository.findByIdAndAddressableTypeAndAddressableId(owned.getId(), AddressableType.CUSTOMER, 1L).isPresent());
        assertTrue(addressRepository.findByIdAndAddressableTypeAndAddressableId(other.getId(), AddressableType.CUSTOMER, 1L).isEmpty());
        assertTrue(addressRepository.findByIdAndAddressableTypeAndAddressableId(order.getId(), AddressableType.CUSTOMER, 1L).isEmpty());
    }

    @Test
    void shouldPersistUpdatesAndDeleteOnlySelectedAddress() {
        Address owned = addressRepository.saveAndFlush(address(AddressableType.CUSTOMER, 1L));
        Address order = addressRepository.saveAndFlush(address(AddressableType.ORDER, 1L));
        owned.setAddressLine("New street");
        owned.setPostalCode(null);
        addressRepository.saveAndFlush(owned);
        entityManager.clear();
        Address updated = addressRepository.findById(owned.getId()).orElseThrow();
        assertEquals("New street", updated.getAddressLine());
        assertNull(updated.getPostalCode());
        addressRepository.delete(updated);
        addressRepository.flush();
        entityManager.clear();
        assertFalse(addressRepository.existsById(owned.getId()));
        assertTrue(addressRepository.existsById(order.getId()));
    }

    private Address address(AddressableType type, Long ownerId) {
        Address address = new Address();
        address.setAddressableType(type);
        address.setAddressableId(ownerId);
        address.setAddressType(AddressType.BILLING);
        address.setTitle("Home");
        address.setCity("Istanbul");
        address.setDistrict("Kadikoy");
        address.setAddressLine("Street 1");
        address.setPostalCode("34000");
        address.setCreatedAt(LocalDateTime.now());
        address.setUpdatedAt(address.getCreatedAt());
        return address;
    }
}
