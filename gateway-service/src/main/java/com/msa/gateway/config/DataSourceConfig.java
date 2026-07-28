package com.msa.gateway.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource(
            @Value("${app.datasource-url:jdbc:h2:mem:gatewaydb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE}") String url,
            @Value("${app.datasource-driver:org.h2.Driver}") String driver,
            @Value("${app.datasource-username:sa}") String username,
            @Value("${app.datasource-password:}") String password) {
        return DataSourceBuilder.create()
                .driverClassName(driver)
                .url(url)
                .username(username)
                .password(password)
                .build();
    }
}
