package com.ecommerce.management.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.ecommerce.management.entity.AppUser;
import com.ecommerce.management.entity.enums.UserRole;
import com.ecommerce.management.repository.AppUserRepository;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AppUserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void registrationIssuesTokenAndStoresOnlyPasswordHash() throws Exception {
        String token = register("security-register@example.com");

        assertThat(token).isNotBlank();
        AppUser user = userRepository.findByEmail("security-register@example.com").orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo("correct-horse-battery-staple");
        assertThat(passwordEncoder.matches("correct-horse-battery-staple", user.getPasswordHash()))
                .isTrue();
        assertThat(user.getRole()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    void protectedEndpointRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/customers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void currentUserEndpointRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void currentUserEndpointReturnsAuthenticatedUser() throws Exception {
        String email = "security-me@example.com";
        String token = register(email);
        AppUser user = userRepository.findByEmail(email).orElseThrow();

        mvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").value(user.getId()))
                .andExpect(jsonPath("$.customer_id").value(user.getCustomer().getId()))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("customer"));
    }

    @Test
    void customerCanAccessOwnProfileButNotAnotherCustomerOrAdminList() throws Exception {
        String token = register("security-owner@example.com");
        AppUser owner = userRepository.findByEmail("security-owner@example.com").orElseThrow();

        mvc.perform(get("/api/v1/customers/{id}", owner.getCustomer().getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("security-owner@example.com"));

        mvc.perform(get("/api/v1/customers/{id}", owner.getCustomer().getId() + 9999)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mvc.perform(get("/api/v1/customers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessCustomerList() throws Exception {
        String email = "security-admin@example.com";
        if (!userRepository.existsByEmail(email)) {
            LocalDateTime now = LocalDateTime.now();
            AppUser admin = new AppUser();
            admin.setEmail(email);
            admin.setPasswordHash(passwordEncoder.encode("admin-password-123"));
            admin.setRole(UserRole.ADMIN);
            admin.setCreatedAt(now);
            admin.setUpdatedAt(now);
            userRepository.save(admin);
        }

        String response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"security-admin@example.com","password":"admin-password-123"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(response).get("access_token").asText();

        mvc.perform(get("/api/v1/customers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void publicCatalogDoesNotRequireToken() throws Exception {
        mvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk());
    }

    private String register(String email) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Security User","email":"%s",%n
                                 "password":"correct-horse-battery-staple"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.role").value("customer"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("access_token").asText();
    }
}
