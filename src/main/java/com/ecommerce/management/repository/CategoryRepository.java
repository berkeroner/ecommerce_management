package com.ecommerce.management.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecommerce.management.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);
}
