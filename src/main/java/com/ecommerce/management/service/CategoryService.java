package com.ecommerce.management.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.management.dto.category.CategoryRequest;
import com.ecommerce.management.dto.category.CategoryResponse;
import com.ecommerce.management.entity.Category;
import com.ecommerce.management.repository.CategoryRepository;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse findById(Long id) {
        return toResponse(getCategory(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String slug = normalizeSlug(request.slug());
        ensureSlugIsAvailable(slug, null);

        LocalDateTime now = LocalDateTime.now();
        Category category = new Category();
        category.setName(request.name().trim());
        category.setSlug(slug);
        category.setIsActive(request.isActive());
        category.setCreatedAt(now);
        category.setUpdatedAt(now);

        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = getCategory(id);
        String slug = normalizeSlug(request.slug());
        ensureSlugIsAvailable(slug, id);

        category.setName(request.name().trim());
        category.setSlug(slug);
        category.setIsActive(request.isActive());
        category.setUpdatedAt(LocalDateTime.now());

        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void delete(Long id) {
        Category category = getCategory(id);
        categoryRepository.delete(category);
    }

    private Category getCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Category not found: " + id));
    }

    private void ensureSlugIsAvailable(String slug, Long currentCategoryId) {
        boolean exists = currentCategoryId == null
                ? categoryRepository.existsBySlug(slug)
                : categoryRepository.existsBySlugAndIdNot(slug, currentCategoryId);

        if (exists) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Category slug already exists: " + slug);
        }
    }

    private String normalizeSlug(String slug) {
        return slug.trim().toLowerCase();
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getIsActive(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }
}
