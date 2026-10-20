package com.ecommerce.management.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.ecommerce.management.repository.RefreshTokenRepository;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AppUserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void registrationIssuesTokenAndStoresOnlyPasswordHash() throws Exception {
        AuthTokens tokens = register("security-register@example.com");

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(refreshTokenRepository.findAll())
                .noneMatch(token -> token.getTokenHash().equals(tokens.refreshToken()));
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
        String token = register(email).accessToken();
        AppUser user = userRepository.findByEmail(email).orElseThrow();

        mvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Security User"))
                .andExpect(jsonPath("$.user_id").value(user.getId()))
                .andExpect(jsonPath("$.customer_id").value(user.getCustomer().getId()))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("customer"));
    }

    @Test
    void customerCanAccessOwnProfileButNotAnotherCustomerOrAdminList() throws Exception {
        String token = register("security-owner@example.com").accessToken();
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

    @Test
    void frontendOriginCanMakeCorsRequests() throws Exception {
        mvc.perform(options("/api/v1/products")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin",
                        "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("GET")));
    }

    @Test
    void refreshRotatesTokenAndRejectsPreviouslyUsedToken() throws Exception {
        AuthTokens original = register("security-refresh@example.com");

        String response = mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(original.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_expires_in").isNumber())
                .andReturn().getResponse().getContentAsString();
        String rotatedRefreshToken = objectMapper.readTree(response).get("refresh_token").asText();

        assertThat(rotatedRefreshToken).isNotEqualTo(original.refreshToken());
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(original.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void logoutRevokesRefreshTokenAndIsIdempotent() throws Exception {
        AuthTokens tokens = register("security-logout@example.com");

        mvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(tokens.refreshToken())))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(tokens.refreshToken())))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(tokens.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    private AuthTokens register(String email) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Security User","email":"%s",%n
                                 "password":"correct-horse-battery-staple"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.role").value("customer"))
                .andReturn().getResponse().getContentAsString();
        return new AuthTokens(
                objectMapper.readTree(response).get("access_token").asText(),
                objectMapper.readTree(response).get("refresh_token").asText());
    }

    private String refreshRequest(String refreshToken) {
        return """
                {"refresh_token":"%s"}
                """.formatted(refreshToken);
    }

    private record AuthTokens(String accessToken, String refreshToken) {
    }
}
