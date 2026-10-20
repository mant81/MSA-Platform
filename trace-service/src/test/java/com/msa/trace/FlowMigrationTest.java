package com.msa.trace;

import static org.junit.jupiter.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class FlowMigrationTest {
  @Test
  void upgradeRebuildsTerminalStepsAndPreservesOriginalEvents() {
    var ds = new DriverManagerDataSource("jdbc:h2:mem:flowmigration;DB_CLOSE_DELAY=-1", "sa", "");
    Flyway.configure().dataSource(ds).target("7").load().migrate();
    var jdbc = new JdbcTemplate(ds);
    for (String type : java.util.List.of("STEP_FAILED", "STEP_STARTED")) {
      jdbc.update(
          "insert into trace_event(trace_id,process_id,source_service,event_type,step_id,"
              + "step_execution_id,attempt,aggregate_version,status) values ('trace','process',"
              + "'member-service',?,'HR','execution',1,?,'TIMEOUT')",
          type,
          type.equals("STEP_STARTED") ? 99 : 5);
    }
    var flyway = Flyway.configure().dataSource(ds).load();
    assertEquals(1, flyway.migrate().migrationsExecuted);
    assertEquals(
        "TIMED_OUT", jdbc.queryForObject("select status from flow_step_projection", String.class));
    assertEquals(2, jdbc.queryForObject("select count(*) from trace_event", Integer.class));
    assertEquals(0, flyway.migrate().migrationsExecuted);
  }
}
