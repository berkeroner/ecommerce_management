package com.ecommerce.management.service;

import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.management.entity.AppUser;
import com.ecommerce.management.entity.enums.UserRole;
import com.ecommerce.management.repository.AppUserRepository;

@Service
public class AdminBootstrapService implements ApplicationRunner {
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminBootstrapService(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
            @Value("${app.security.bootstrap-admin.email:}") String email,
            @Value("${app.security.bootstrap-admin.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) {
            return;
        }
        if (email.isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                    "Both ADMIN_EMAIL and ADMIN_PASSWORD must be configured together");
        }
        if (password.length() < 12 || password.length() > 72) {
            throw new IllegalStateException("ADMIN_PASSWORD must be between 12 and 72 characters");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        AppUser admin = new AppUser();
        admin.setEmail(normalizedEmail);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(UserRole.ADMIN);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        userRepository.save(admin);
    }
}
