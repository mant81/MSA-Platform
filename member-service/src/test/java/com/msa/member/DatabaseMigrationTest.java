package com.msa.member;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class DatabaseMigrationTest {
  @TempDir Path directory;

  String url(String name) {
    return "jdbc:h2:file:" + directory.resolve(name) + ";DB_CLOSE_ON_EXIT=FALSE";
  }

  @Test
  void freshInstallRepeatAndRestartPreserveRows() {
    String url = url("fresh");
    var flyway = Flyway.configure().dataSource(url, "sa", "").load();
    assertEquals(5, flyway.migrate().migrationsExecuted);
    var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
    jdbc.update(
        "insert into member(member_no,member_name,status) values ('persisted','Fixture','ACTIVE')");
    var restarted = Flyway.configure().dataSource(url, "sa", "").load();
    restarted.validate();
    assertEquals(0, restarted.migrate().migrationsExecuted);
    assertEquals(
        1,
        new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""))
            .queryForObject(
                "select count(*) from member where member_no='persisted'", Integer.class));
  }

  @Test
  void existingV1RequiresExplicitBaselineAndPreservesBusinessData() {
    String url = url("legacy");
    var ds = new DriverManagerDataSource(url, "sa", "");
    new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__initial.sql"))
        .execute(ds);
    assertThrows(
        org.flywaydb.core.api.FlywayException.class,
        () -> Flyway.configure().dataSource(ds).load().migrate());
    var flyway = Flyway.configure().dataSource(ds).baselineVersion("1").load();
    flyway.baseline();
    assertEquals(4, flyway.migrate().migrationsExecuted);
    assertEquals(
        2, new JdbcTemplate(ds).queryForObject("select count(*) from member", Integer.class));
  }
}
