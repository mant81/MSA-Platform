package com.msa.auth.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource(
            @Value("${app.database-type:H2}") String databaseType,
            @Value("${app.datasource-url:jdbc:h2:mem:authdb;MODE=MariaDB;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE}") String url,
            @Value("${app.datasource-driver:org.h2.Driver}") String driver,
            @Value("${app.datasource-username:sa}") String username,
            @Value("${app.datasource-password:}") String password) {
        DatabaseProfile profile = DatabaseProfile.from(databaseType);
        return DataSourceBuilder.create()
                .driverClassName(profile.driver(driver))
                .url(profile.url(url))
                .username(profile.username(username))
                .password(profile.password(password))
                .build();
    }

    private enum DatabaseProfile {
        H2, MARIADB, POSTGRESQL;

        static DatabaseProfile from(String value) {
            return DatabaseProfile.valueOf(value == null ? "H2" : value.trim().toUpperCase());
        }

        String driver(String fallback) {
            return switch (this) {
                case H2 -> "org.h2.Driver";
                case MARIADB -> "org.mariadb.jdbc.Driver";
                case POSTGRESQL -> "org.postgresql.Driver";
            };
        }

        String url(String fallback) {
            return switch (this) {
                case H2 -> fallback;
                case MARIADB -> "jdbc:mariadb://localhost:3306/authdb";
                case POSTGRESQL -> "jdbc:postgresql://localhost:5432/authdb";
            };
        }

        String username(String fallback) {
            return switch (this) {
                case H2 -> fallback;
                case MARIADB, POSTGRESQL -> "msa";
            };
        }

        String password(String fallback) {
            return switch (this) {
                case H2 -> fallback;
                case MARIADB, POSTGRESQL -> "msa";
            };
        }
    }
}
