package com.msa.trace.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DataSourceConfig {
  @Bean
  public DataSource dataSource(
      @Value("${app.datasource-url}") String url,
      @Value("${app.datasource-username:sa}") String username,
      @Value("${app.datasource-password:}") String password) {
    return DataSourceBuilder.create()
        .driverClassName(DatabaseDriver.fromJdbcUrl(url).getDriverClassName())
        .url(url)
        .username(username)
        .password(password)
        .build();
  }
}
