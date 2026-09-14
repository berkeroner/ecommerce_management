package com.ecommerce.management.service;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.ecommerce.management.dto.address.AddressRequest;
import com.ecommerce.management.dto.address.AddressResponse;
import com.ecommerce.management.entity.Address;
import com.ecommerce.management.entity.enums.AddressableType;
import com.ecommerce.management.repository.AddressRepository;
import com.ecommerce.management.repository.CustomerRepository;

@Service
public class AddressService {
    private final AddressRepository addressRepository;
    private final CustomerRepository customerRepository;

    public AddressService(AddressRepository addressRepository, CustomerRepository customerRepository) {
        this.addressRepository = addressRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> findAll(Long customerId) {
        ensureCustomerExists(customerId);
        return addressRepository.findAllByAddressableTypeAndAddressableId(AddressableType.CUSTOMER, customerId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AddressResponse findById(Long customerId, Long id) {
        return toResponse(getAddress(customerId, id));
    }

    @Transactional
    public AddressResponse create(Long customerId, AddressRequest request) {
        ensureCustomerExists(customerId);
        Address address = new Address();
        address.setAddressableType(AddressableType.CUSTOMER);
        address.setAddressableId(customerId);
        LocalDateTime now = LocalDateTime.now();
        address.setCreatedAt(now);
        address.setUpdatedAt(now);
        applyRequest(address, request);
        return toResponse(addressRepository.save(address));
    }

    @Transactional
    public AddressResponse update(Long customerId, Long id, AddressRequest request) {
        Address address = getAddress(customerId, id);
        applyRequest(address, request);
        address.setUpdatedAt(LocalDateTime.now());
        return toResponse(addressRepository.save(address));
    }

    @Transactional
    public void delete(Long customerId, Long id) {
        addressRepository.delete(getAddress(customerId, id));
    }

    private void ensureCustomerExists(Long customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found: " + customerId);
        }
    }

    private Address getAddress(Long customerId, Long id) {
        ensureCustomerExists(customerId);
        return addressRepository.findByIdAndAddressableTypeAndAddressableId(id, AddressableType.CUSTOMER, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found: " + id));
    }

    private void applyRequest(Address address, AddressRequest request) {
        address.setAddressType(request.addressType());
        address.setTitle(request.title().trim());
        address.setCity(request.city().trim());
        address.setDistrict(request.district().trim());
        address.setAddressLine(request.addressLine().trim());
        address.setPostalCode(request.postalCode() == null ? null : request.postalCode().trim());
    }

    private AddressResponse toResponse(Address address) {
        return new AddressResponse(address.getId(), address.getAddressableId(), address.getAddressType(),
                address.getTitle(), address.getCity(), address.getDistrict(), address.getAddressLine(),
                address.getPostalCode(), address.getCreatedAt(), address.getUpdatedAt());
    }
}
