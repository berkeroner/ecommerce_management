package com.ecommerce.management.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.management.dto.product.ProductRequest;
import com.ecommerce.management.dto.product.ProductResponse;
import com.ecommerce.management.entity.Category;
import com.ecommerce.management.entity.Product;
import com.ecommerce.management.entity.enums.RecordStatus;
import com.ecommerce.management.repository.CategoryRepository;
import com.ecommerce.management.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void shouldCreateActiveProductAndNormalizeSku() {
        Category category = category(1L, "Bilgisayar");
        ProductRequest request = new ProductRequest(
                1L,
                " Mekanik Klavye ",
                " keyboard-001 ",
                new BigDecimal("2500.00"),
                20);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.existsBySku("KEYBOARD-001")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(10L);
            return product;
        });

        ProductResponse response = productService.create(request);

        assertEquals(10L, response.id());
        assertEquals(1L, response.categoryId());
        assertEquals("Bilgisayar", response.categoryName());
        assertEquals("Mekanik Klavye", response.name());
        assertEquals("KEYBOARD-001", response.sku());
        assertEquals(new BigDecimal("2500.00"), response.price());
        assertEquals(20, response.stock());
        assertEquals(RecordStatus.ACTIVE, response.status());
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void shouldReturnNotFoundWhenCategoryDoesNotExist() {
        ProductRequest request = request(99L, "MONITOR-001", "5000.00", 5);
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> productService.create(request));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void shouldReturnConflictWhenSkuAlreadyExists() {
        Category category = category(1L, "Bilgisayar");
        ProductRequest request = request(1L, "monitor-001", "5000.00", 5);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.existsBySku("MONITOR-001")).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> productService.create(request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void shouldReturnNotFoundWhenProductDoesNotExist() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> productService.findById(99L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void shouldUpdateProductAndPreserveStatus() {
        Category oldCategory = category(1L, "Bilgisayar");
        Category newCategory = category(2L, "Aksesuar");
        Product product = product(
                10L, oldCategory, "Klavye", "KEYBOARD-001",
                "2500.00", 20, RecordStatus.PASSIVE);
        ProductRequest request = new ProductRequest(
                2L,
                " Kablosuz Klavye ",
                " wireless-001 ",
                new BigDecimal("3000.00"),
                15);

        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(newCategory));
        when(productRepository.existsBySkuAndIdNot("WIRELESS-001", 10L)).thenReturn(false);
        when(productRepository.save(product)).thenReturn(product);

        ProductResponse response = productService.update(10L, request);

        assertEquals(2L, response.categoryId());
        assertEquals("Kablosuz Klavye", response.name());
        assertEquals("WIRELESS-001", response.sku());
        assertEquals(new BigDecimal("3000.00"), response.price());
        assertEquals(15, response.stock());
        assertEquals(RecordStatus.PASSIVE, response.status());
        verify(productRepository).save(product);
    }

    @Test
    void shouldUpdateProductStatus() {
        Product product = product(
                10L, category(1L, "Bilgisayar"), "Klavye", "KEYBOARD-001",
                "2500.00", 20, RecordStatus.ACTIVE);

        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        ProductResponse response = productService.updateStatus(10L, RecordStatus.PASSIVE);

        assertEquals(RecordStatus.PASSIVE, response.status());
        verify(productRepository).save(product);
    }

    @Test
    void shouldUpdateProductStock() {
        Product product = product(
                10L, category(1L, "Bilgisayar"), "Klavye", "KEYBOARD-001",
                "2500.00", 20, RecordStatus.ACTIVE);

        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        ProductResponse response = productService.updateStock(10L, 35);

        assertEquals(35, response.stock());
        verify(productRepository).save(product);
    }

    @Test
    void shouldRejectNegativeStock() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> productService.updateStock(10L, -1));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(productRepository, never()).findById(any(Long.class));
        verify(productRepository, never()).save(any(Product.class));
    }

    private ProductRequest request(
            Long categoryId,
            String sku,
            String price,
            Integer stock) {
        return new ProductRequest(
                categoryId,
                "Test Urunu",
                sku,
                new BigDecimal(price),
                stock);
    }

    private Category category(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setSlug(name.toLowerCase());
        category.setIsActive(true);
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());
        return category;
    }

    private Product product(
            Long id,
            Category category,
            String name,
            String sku,
            String price,
            Integer stock,
            RecordStatus status) {
        Product product = new Product();
        product.setId(id);
        product.setCategory(category);
        product.setName(name);
        product.setSku(sku);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        product.setStatus(status);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        return product;
    }
}
