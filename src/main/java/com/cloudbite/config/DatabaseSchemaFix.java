package com.cloudbite.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSchemaFix {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaFix(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void fixUserAddressesColumns() {
        try {
            jdbcTemplate.execute("ALTER TABLE user_addresses MODIFY COLUMN latitude DOUBLE NULL");
            jdbcTemplate.execute("ALTER TABLE user_addresses MODIFY COLUMN longitude DOUBLE NULL");
            System.out.println("Fixed user_addresses columns - latitude/longitude now nullable");
        } catch (Exception e) {
            System.out.println("Could not modify user_addresses columns: " + e.getMessage());
        }
    }

    @PostConstruct
    public void addSubCategoryColumn() {
        try {
            jdbcTemplate.execute("ALTER TABLE menu_items ADD COLUMN sub_category VARCHAR(255) DEFAULT 'General'");
            System.out.println("Added sub_category column to menu_items");
        } catch (Exception e) {
            System.out.println("Could not add sub_category column: " + e.getMessage());
        }
    }
}