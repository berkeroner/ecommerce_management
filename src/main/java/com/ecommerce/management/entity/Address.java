package com.ecommerce.management.entity;

import java.time.LocalDateTime;

import com.ecommerce.management.entity.enums.AddressType;
import com.ecommerce.management.entity.enums.AddressableType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "addresses", indexes = @Index(name = "idx_addresses_addressable", columnList = "addressable_type,addressable_id"))
@Getter @Setter @NoArgsConstructor
public class Address {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING) @Column(name = "addressable_type", nullable = false, length = 100)
    private AddressableType addressableType;

    @Column(name = "addressable_id", nullable = false)
    private Long addressableId;

    @Enumerated(EnumType.STRING) @Column(name = "address_type", nullable = false, length = 30)
    private AddressType addressType;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 100)
    private String district;

    @Lob @Column(name = "address_line", nullable = false, columnDefinition = "text")
    private String addressLine;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
