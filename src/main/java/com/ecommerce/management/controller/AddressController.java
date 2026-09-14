package com.ecommerce.management.controller;

import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ecommerce.management.dto.address.AddressRequest;
import com.ecommerce.management.dto.address.AddressResponse;
import com.ecommerce.management.service.AddressService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/addresses")
public class AddressController {
    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public List<AddressResponse> findAll(@PathVariable Long customerId) {
        return addressService.findAll(customerId);
    }

    @GetMapping("/{id}")
    public AddressResponse findById(@PathVariable Long customerId, @PathVariable Long id) {
        return addressService.findById(customerId, id);
    }

    @PostMapping
    public ResponseEntity<AddressResponse> create(@PathVariable Long customerId,
            @Valid @RequestBody AddressRequest request) {
        AddressResponse address = addressService.create(customerId, request);
        return ResponseEntity.created(URI.create("/api/v1/customers/" + customerId + "/addresses/" + address.id()))
                .body(address);
    }

    @PutMapping("/{id}")
    public AddressResponse update(@PathVariable Long customerId, @PathVariable Long id,
            @Valid @RequestBody AddressRequest request) {
        return addressService.update(customerId, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long customerId, @PathVariable Long id) {
        addressService.delete(customerId, id);
        return ResponseEntity.noContent().build();
    }
}
