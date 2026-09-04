package com.ecommerce.management.service;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.management.dto.category.CategoryRequest;
import com.ecommerce.management.dto.category.CategoryResponse;
import com.ecommerce.management.entity.Category;
import com.ecommerce.management.repository.CategoryRepository;
import com.ecommerce.management.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void shouldCreateCategory() {
        CategoryRequest request = new CategoryRequest(
                " Elektronik ", " Elektronik ", true);

        when(categoryRepository.existsBySlug("elektronik")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            category.setId(1L);
            return category;
        });

        CategoryResponse response = categoryService.create(request);

        assertEquals(1L, response.id());
        assertEquals("Elektronik", response.name());
        assertEquals("elektronik", response.slug());
        assertEquals(true, response.isActive());
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void shouldReturnConflictWhenSlugAlreadyExists() {
        CategoryRequest request = new CategoryRequest(
                "Elektronik", "elektronik", true);

        when(categoryRepository.existsBySlug("elektronik")).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> categoryService.create(request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void shouldReturnNotFoundWhenCategoryDoesNotExist() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> categoryService.findById(99L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void shouldUpdateCategory() {
        Category category = category(1L, "Elektronik", "elektronik", true);
        CategoryRequest request = new CategoryRequest(
                " Bilgisayar ", " Bilgisayar ", false);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsBySlugAndIdNot("bilgisayar", 1L)).thenReturn(false);
        when(categoryRepository.save(category)).thenReturn(category);

        CategoryResponse response = categoryService.update(1L, request);

        assertEquals("Bilgisayar", response.name());
        assertEquals("bilgisayar", response.slug());
        assertFalse(response.isActive());
        verify(categoryRepository).save(category);
    }

    @Test
    void shouldDeleteCategoryWhenItHasNoProducts() {
        Category category = category(1L, "Elektronik", "elektronik", true);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryId(1L)).thenReturn(false);

        categoryService.delete(1L);

        verify(categoryRepository).delete(category);
    }

    @Test
    void shouldNotDeleteCategoryWhenItHasProducts() {
        Category category = category(1L, "Elektronik", "elektronik", true);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryId(1L)).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> categoryService.delete(1L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    private Category category(Long id, String name, String slug, Boolean isActive) {
        LocalDateTime now = LocalDateTime.now();
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setSlug(slug);
        category.setIsActive(isActive);
        category.setCreatedAt(now);
        category.setUpdatedAt(now);
        return category;
    }
}