package com.msa.trace.controller;

import com.msa.trace.service.ServiceMonitor;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/operations")
public class ServiceStatusController {
  private final ServiceMonitor monitor;
  private final com.msa.trace.service.AlertService alerts;

  public ServiceStatusController(
      ServiceMonitor monitor, com.msa.trace.service.AlertService alerts) {
    this.monitor = monitor;
    this.alerts = alerts;
  }

  @GetMapping("/alerts")
  public Map<String, Object> selectDelivery() {
    return alerts.selectDelivery();
  }

  @PostMapping("/services/{id}/maintenance")
  public Map<String, Object> maintenance(
      @PathVariable String id, @RequestParam boolean enabled, java.security.Principal actor) {
    return Map.of("updated", monitor.maintenance(id, enabled, actor.getName()));
  }

  @GetMapping("/services")
  public Map<String, Object> selectStatus() {
    return monitor.selectStatus();
  }

  @GetMapping("/services/history")
  public List<Map<String, Object>> selectHistory() {
    return monitor.selectHistory();
  }

  @GetMapping("/incidents")
  public List<Map<String, Object>> selectIncidents() {
    return monitor.selectIncidents();
  }

  @PostMapping("/incidents/{id}/ack")
  public Map<String, Object> acknowledge(@PathVariable String id, java.security.Principal actor) {
    return Map.of("updated", monitor.acknowledge(id, actor.getName()));
  }
}
