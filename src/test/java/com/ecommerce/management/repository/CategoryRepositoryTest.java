package com.ecommerce.management.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.ecommerce.management.entity.Category;

@DataJpaTest
@ActiveProfiles("test")
class CategoryRepositoryTest {

    @Autowired CategoryRepository categoryRepository;

    @Test
    void shouldCheckSlugExistence() {
        Category saved = categoryRepository.saveAndFlush(category("Elektronik", "elektronik"));

        assertTrue(categoryRepository.existsBySlug("elektronik"));
        assertFalse(categoryRepository.existsBySlug("kitap"));
        assertFalse(categoryRepository.existsBySlugAndIdNot("elektronik", saved.getId()));
        assertTrue(categoryRepository.existsBySlugAndIdNot("elektronik", saved.getId() + 1));
    }

    private Category category(String name, String slug) {
        LocalDateTime now = LocalDateTime.now();
        Category category = new Category();
        category.setName(name);
        category.setSlug(slug);
        category.setIsActive(true);
        category.setCreatedAt(now);
        category.setUpdatedAt(now);
        return category;
    }
}
