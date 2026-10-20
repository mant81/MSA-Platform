package com.msa.core;

import java.time.OffsetDateTime;

public class TraceEventVo {
  private String environment;

  public String getEnvironment() {
    return environment;
  }

  public void setEnvironment(String value) {
    this.environment = value;
  }

  private String instanceId;

  public String getInstanceId() {
    return instanceId;
  }

  public void setInstanceId(String value) {
    this.instanceId = value;
  }

  private String deploymentVersion;

  public String getDeploymentVersion() {
    return deploymentVersion;
  }

  public void setDeploymentVersion(String value) {
    this.deploymentVersion = value;
  }

  private Long aggregateVersion;

  public Long getAggregateVersion() {
    return aggregateVersion;
  }

  public void setAggregateVersion(Long value) {
    this.aggregateVersion = value;
  }

  private String stepExecutionId;

  public String getStepExecutionId() {
    return stepExecutionId;
  }

  public void setStepExecutionId(String value) {
    this.stepExecutionId = value;
  }

  private Integer attempt;

  public Integer getAttempt() {
    return attempt;
  }

  public void setAttempt(Integer value) {
    this.attempt = value;
  }

  private String processDefinitionVersion;

  public String getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public void setProcessDefinitionVersion(String value) {
    this.processDefinitionVersion = value;
  }

  private String errorId;

  public String getErrorId() {
    return errorId;
  }

  public void setErrorId(String value) {
    this.errorId = value;
  }

  private String causedByErrorId;

  public String getCausedByErrorId() {
    return causedByErrorId;
  }

  public void setCausedByErrorId(String value) {
    this.causedByErrorId = value;
  }

  private String errorGroupId;

  public String getErrorGroupId() {
    return errorGroupId;
  }

  public void setErrorGroupId(String value) {
    this.errorGroupId = value;
  }

  private String eventId;

  public String getEventId() {
    return eventId;
  }

  public void setEventId(String value) {
    this.eventId = value;
  }

  private String spanId;

  public String getSpanId() {
    return spanId;
  }

  public void setSpanId(String value) {
    this.spanId = value;
  }

  private String parentSpanId;

  public String getParentSpanId() {
    return parentSpanId;
  }

  public void setParentSpanId(String value) {
    this.parentSpanId = value;
  }

  private String legacyTraceId;

  public String getLegacyTraceId() {
    return legacyTraceId;
  }

  public void setLegacyTraceId(String value) {
    this.legacyTraceId = value;
  }

  private Long id;
  private String traceId;
  private String processId;
  private String businessKey;
  private String processType;
  private String stepId;
  private String serviceName;
  private String sourceService;

  public String getSourceService() {
    return sourceService;
  }

  public void setSourceService(String sourceService) {
    this.sourceService = sourceService;
  }

  private String eventType;
  private String httpMethod;
  private String path;
  private String status;
  private Long elapsedMs;
  private String errorCode;
  private String errorMessage;
  private OffsetDateTime createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

  public String getProcessId() {
    return processId;
  }

  public void setProcessId(String processId) {
    this.processId = processId;
  }

  public String getBusinessKey() {
    return businessKey;
  }

  public void setBusinessKey(String businessKey) {
    this.businessKey = businessKey;
  }

  public String getProcessType() {
    return processType;
  }

  public void setProcessType(String processType) {
    this.processType = processType;
  }

  public String getStepId() {
    return stepId;
  }

  public void setStepId(String stepId) {
    this.stepId = stepId;
  }

  public String getServiceName() {
    return serviceName;
  }

  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getHttpMethod() {
    return httpMethod;
  }

  public void setHttpMethod(String httpMethod) {
    this.httpMethod = httpMethod;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Long getElapsedMs() {
    return elapsedMs;
  }

  public void setElapsedMs(Long elapsedMs) {
    this.elapsedMs = elapsedMs;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
