package com.cloudbite.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSchemaFix {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void fixUserAddressesColumns() {
        try {
            jdbcTemplate.execute("ALTER TABLE user_addresses MODIFY COLUMN latitude DOUBLE NULL");
            jdbcTemplate.execute("ALTER TABLE user_addresses MODIFY COLUMN longitude DOUBLE NULL");
            log.info("Fixed user_addresses columns - latitude/longitude now nullable");
        } catch (Exception e) {
            log.warn("Could not modify user_addresses columns (may already be nullable): {}", e.getMessage());
        }
    }
}