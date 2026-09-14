package com.ecommerce.management.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ecommerce.management.entity.Address;
import com.ecommerce.management.entity.enums.AddressableType;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findAllByAddressableTypeAndAddressableId(AddressableType type, Long addressableId);

    Optional<Address> findByIdAndAddressableTypeAndAddressableId(
            Long id, AddressableType type, Long addressableId);
}
