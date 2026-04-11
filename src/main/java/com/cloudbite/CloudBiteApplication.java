package com.cloudbite;

import com.cloudbite.enums.Role;
import com.cloudbite.model.User;
import com.cloudbite.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@Slf4j
@RequiredArgsConstructor
@EnableScheduling
public class CloudBiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudBiteApplication.class, args);
        log.info("🍽️  CloudBite Server started successfully!");
    }

    @Bean
    CommandLineRunner initAdminUser(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.email}") String adminEmail,
            @Value("${app.admin.password}") String adminPassword,
            @Value("${app.admin.name}") String adminName
    ) {
        return args -> {
            if (!userRepository.existsByEmail(adminEmail)) {
                User admin = User.builder()
                        .name(adminName)
                        .email(adminEmail)
                        .password(passwordEncoder.encode(adminPassword))
                        .role(Role.ADMIN)
                        .phone("9999999999")
                        .isActive(true)
                        .isVerified(true)
                        .build();
                userRepository.save(admin);
                log.info("✅ Admin user created: {}", adminEmail);
            } else {
                log.info("ℹ️  Admin user already exists: {}", adminEmail);
            }
        };
    }
}
