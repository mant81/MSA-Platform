package com.msa.hr.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
public class DataSourceConfig {
    @Bean
    public DataSource dataSource(
            @Value("${app.datasource-url:jdbc:h2:mem:hrdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE}") String datasourceUrl,
            @Value("${app.datasource-driver:org.h2.Driver}") String datasourceDriver,
            @Value("${app.datasource-username:sa}") String datasourceUsername,
            @Value("${app.datasource-password:}") String datasourcePassword
    ) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(datasourceDriver);
        dataSource.setUrl(datasourceUrl);
        dataSource.setUsername(datasourceUsername);
        dataSource.setPassword(datasourcePassword);
        return dataSource;
    }
}
