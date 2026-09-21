package com.ecommerce.management.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.ecommerce.management.entity.Category;
import com.ecommerce.management.entity.Product;
import com.ecommerce.management.entity.enums.RecordStatus;

@DataJpaTest
@ActiveProfiles("test")
class ProductRepositoryTest {

    @Autowired jakarta.persistence.EntityManager entityManager;

    @Test
    void shouldReserveStockAndUpdateTimestamp() {
        Product saved = productRepository.saveAndFlush(product(categoryRepository.saveAndFlush(category())));
        LocalDateTime now = LocalDateTime.of(2026, 9, 18, 12, 0);
        org.junit.jupiter.api.Assertions.assertEquals(1,
                productRepository.reserveStock(saved.getId(), 3, RecordStatus.ACTIVE, now));
        entityManager.clear();
        Product reloaded = productRepository.findById(saved.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(7, reloaded.getStock());
        org.junit.jupiter.api.Assertions.assertEquals(now, reloaded.getUpdatedAt());
    }

    @Test
    void shouldReserveExactRemainingStockOnlyOnce() {
        Product saved = productRepository.saveAndFlush(product(categoryRepository.saveAndFlush(category())));
        org.junit.jupiter.api.Assertions.assertEquals(1,
                productRepository.reserveStock(saved.getId(), 10, RecordStatus.ACTIVE, LocalDateTime.now()));
        org.junit.jupiter.api.Assertions.assertEquals(0,
                productRepository.reserveStock(saved.getId(), 1, RecordStatus.ACTIVE, LocalDateTime.now()));
        entityManager.clear();
        org.junit.jupiter.api.Assertions.assertEquals(0,
                productRepository.findById(saved.getId()).orElseThrow().getStock());
    }

    @Test
    void shouldNotChangeStockOrTimestampWhenReservationFails() {
        Product saved = productRepository.saveAndFlush(product(categoryRepository.saveAndFlush(category())));
        LocalDateTime original = saved.getUpdatedAt();
        org.junit.jupiter.api.Assertions.assertEquals(0,
                productRepository.reserveStock(saved.getId(), 11, RecordStatus.ACTIVE, original.plusDays(1)));
        entityManager.clear();
        Product reloaded = productRepository.findById(saved.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(10, reloaded.getStock());
        // Veritabanının timestamp hassasiyetine uygun karşılaştırma.
        org.junit.jupiter.api.Assertions.assertEquals(original.withNano(0), reloaded.getUpdatedAt().withNano(0));
    }

    @Test
    void shouldRejectPassiveAndMissingProductsForReservation() {
        Product saved = product(categoryRepository.saveAndFlush(category()));
        saved.setStatus(RecordStatus.PASSIVE);
        productRepository.saveAndFlush(saved);
        org.junit.jupiter.api.Assertions.assertEquals(0,
                productRepository.reserveStock(saved.getId(), 1, RecordStatus.ACTIVE, LocalDateTime.now()));
        org.junit.jupiter.api.Assertions.assertEquals(0,
                productRepository.reserveStock(Long.MAX_VALUE, 1, RecordStatus.ACTIVE, LocalDateTime.now()));
        entityManager.clear();
        org.junit.jupiter.api.Assertions.assertEquals(10,
                productRepository.findById(saved.getId()).orElseThrow().getStock());
    }

    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;

    @Test
    void shouldCheckSkuAndCategoryExistence() {
        Category category = categoryRepository.saveAndFlush(category());
        Product saved = productRepository.saveAndFlush(product(category));

        assertTrue(productRepository.existsByCategoryId(category.getId()));
        assertTrue(productRepository.existsBySku("LAPTOP-1"));
        assertFalse(productRepository.existsBySku("PHONE-1"));
        assertFalse(productRepository.existsBySkuAndIdNot("LAPTOP-1", saved.getId()));
        assertTrue(productRepository.existsBySkuAndIdNot("LAPTOP-1", saved.getId() + 1));
    }

    @Test
    void shouldReturnDistinctOrderedPagesWithTotalCount() {
        Category category = categoryRepository.saveAndFlush(category());
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Product product = product(category);
            product.setSku("PAGE-" + i);
            ids.add(productRepository.saveAndFlush(product).getId());
        }
        var sort = org.springframework.data.domain.Sort.by("id");
        var first = productRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 2, sort));
        var second = productRepository.findAll(org.springframework.data.domain.PageRequest.of(1, 2, sort));
        org.junit.jupiter.api.Assertions.assertEquals(ids.subList(0, 2),
                first.getContent().stream().map(Product::getId).toList());
        org.junit.jupiter.api.Assertions.assertEquals(ids.subList(2, 3),
                second.getContent().stream().map(Product::getId).toList());
        org.junit.jupiter.api.Assertions.assertEquals(3, first.getTotalElements());
        org.junit.jupiter.api.Assertions.assertEquals(2, first.getTotalPages());
    }

    @Test
    void shouldFilterBeforePaginationAndCountOnlyMatchingProducts() {
        Category selected = categoryRepository.saveAndFlush(category());
        Category other = category();
        other.setSlug("other");
        categoryRepository.saveAndFlush(other);
        java.util.List<Long> selectedIds = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Product product = product(i % 2 == 0 ? selected : other);
            product.setSku("FILTER-" + i);
            productRepository.saveAndFlush(product);
            if (i % 2 == 0) selectedIds.add(product.getId());
        }
        var sort = org.springframework.data.domain.Sort.by("id");
        var first = productRepository.findAllByCategoryId(selected.getId(),
                org.springframework.data.domain.PageRequest.of(0, 2, sort));
        var second = productRepository.findAllByCategoryId(selected.getId(),
                org.springframework.data.domain.PageRequest.of(1, 2, sort));
        org.junit.jupiter.api.Assertions.assertEquals(selectedIds.subList(0, 2),
                first.getContent().stream().map(Product::getId).toList());
        org.junit.jupiter.api.Assertions.assertEquals(selectedIds.subList(2, 3),
                second.getContent().stream().map(Product::getId).toList());
        org.junit.jupiter.api.Assertions.assertEquals(3, first.getTotalElements());
        org.junit.jupiter.api.Assertions.assertEquals(2, first.getTotalPages());
        var missing = productRepository.findAllByCategoryId(Long.MAX_VALUE,
                org.springframework.data.domain.PageRequest.of(0, 2, sort));
        assertTrue(missing.isEmpty());
        org.junit.jupiter.api.Assertions.assertEquals(0, missing.getTotalElements());
    }

    @Test
    void shouldCombineCategoryAndStatusBeforePagingAndCounting() {
        Category selected = categoryRepository.saveAndFlush(category());
        Category other = category();
        other.setSlug("other-status");
        categoryRepository.saveAndFlush(other);
        for (int i = 0; i < 5; i++) {
            Product product = product(i == 4 ? other : selected);
            product.setSku("STATUS-" + i);
            product.setStatus(i == 3 ? RecordStatus.PASSIVE : RecordStatus.ACTIVE);
            productRepository.saveAndFlush(product);
        }
        var pageable = org.springframework.data.domain.PageRequest.of(0, 2,
                org.springframework.data.domain.Sort.by("id"));
        var allActive = productRepository.findAllByStatus(RecordStatus.ACTIVE, pageable);
        org.junit.jupiter.api.Assertions.assertEquals(4, allActive.getTotalElements());
        var first = productRepository.findAllByCategoryIdAndStatus(selected.getId(), RecordStatus.ACTIVE, pageable);
        var second = productRepository.findAllByCategoryIdAndStatus(selected.getId(), RecordStatus.ACTIVE, pageable.next());
        org.junit.jupiter.api.Assertions.assertEquals(3, first.getTotalElements());
        org.junit.jupiter.api.Assertions.assertEquals(2, first.getTotalPages());
        org.junit.jupiter.api.Assertions.assertEquals(2, first.getContent().size());
        org.junit.jupiter.api.Assertions.assertEquals(1, second.getContent().size());
        first.forEach(product -> {
            org.junit.jupiter.api.Assertions.assertEquals(selected.getId(), product.getCategory().getId());
            org.junit.jupiter.api.Assertions.assertEquals(RecordStatus.ACTIVE, product.getStatus());
        });
        assertTrue(productRepository.findAllByCategoryIdAndStatus(other.getId(), RecordStatus.PASSIVE, pageable).isEmpty());
    }

    @Test
    void shouldSearchNameOrSkuAndCombineFiltersBeforePaging() {
        Category selected = categoryRepository.saveAndFlush(category());
        Category other = category();
        other.setSlug("search-other");
        categoryRepository.saveAndFlush(other);
        for (int i = 0; i < 5; i++) {
            Product product = product(i == 4 ? other : selected);
            product.setName(i == 0 ? "Gaming LaPTop" : "Keyboard");
            product.setSku(i == 0 ? "NAME-MATCH" : "LAPTOP-" + i);
            product.setStatus(i == 3 ? RecordStatus.PASSIVE : RecordStatus.ACTIVE);
            productRepository.saveAndFlush(product);
        }
        var pageable = org.springframework.data.domain.PageRequest.of(0, 2,
                org.springframework.data.domain.Sort.by("id"));
        org.junit.jupiter.api.Assertions.assertEquals(5,
                productRepository.search(null, null, "laptop", pageable).getTotalElements());
        var first = productRepository.search(selected.getId(), RecordStatus.ACTIVE, "laptop", pageable);
        var second = productRepository.search(selected.getId(), RecordStatus.ACTIVE, "laptop", pageable.next());
        org.junit.jupiter.api.Assertions.assertEquals(3, first.getTotalElements());
        org.junit.jupiter.api.Assertions.assertEquals(2, first.getTotalPages());
        org.junit.jupiter.api.Assertions.assertEquals(2, first.getContent().size());
        org.junit.jupiter.api.Assertions.assertEquals(1, second.getContent().size());
        assertTrue(productRepository.search(null, null, "missing", pageable).isEmpty());
    }

    @Test
    void shouldTreatPercentAndUnderscoreAsLiteralCharacters() {
        Category category = categoryRepository.saveAndFlush(category());
        Product literal = product(category);
        literal.setName("Discount 50%_off");
        productRepository.saveAndFlush(literal);
        Product other = product(category);
        other.setSku("OTHER");
        productRepository.saveAndFlush(other);
        var pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        org.junit.jupiter.api.Assertions.assertEquals(1,
                productRepository.search(null, null, "%_", pageable).getTotalElements());
    }

    @Test
    void shouldSortFilteredPagesByPriceAndBreakTiesById() {
        Category category = categoryRepository.saveAndFlush(category());
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Product product = product(category);
            product.setSku("SORT-" + i);
            product.setPrice(new BigDecimal(i == 0 ? "10" : "20"));
            ids.add(productRepository.saveAndFlush(product).getId());
        }
        var sorting = org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "price")
                .and(org.springframework.data.domain.Sort.by("id"));
        var pageable = org.springframework.data.domain.PageRequest.of(0, 2, sorting);
        var first = productRepository.search(category.getId(), RecordStatus.ACTIVE, "sort", pageable);
        var second = productRepository.search(category.getId(), RecordStatus.ACTIVE, "sort", pageable.next());
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(ids.get(1), ids.get(2)),
                first.getContent().stream().map(Product::getId).toList());
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(ids.get(3), ids.get(0)),
                second.getContent().stream().map(Product::getId).toList());
        org.junit.jupiter.api.Assertions.assertEquals(4, first.getTotalElements());
    }

    private Category category() {
        LocalDateTime now = LocalDateTime.now();
        Category category = new Category();
        category.setName("Elektronik");
        category.setSlug("elektronik");
        category.setIsActive(true);
        category.setCreatedAt(now);
        category.setUpdatedAt(now);
        return category;
    }

    private Product product(Category category) {
        LocalDateTime now = LocalDateTime.now();
        Product product = new Product();
        product.setCategory(category);
        product.setName("Laptop");
        product.setSku("LAPTOP-1");
        product.setPrice(new BigDecimal("1250.00"));
        product.setStock(10);
        product.setStatus(RecordStatus.ACTIVE);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        return product;
    }
}
