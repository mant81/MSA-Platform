package com.msa.member.service;

import com.msa.core.BusinessException;
import com.msa.core.BusinessProcessStatus;
import com.msa.core.BusinessStepStatus;
import com.msa.core.ErrorType;
import com.msa.core.TraceEventVo;
import com.msa.member.client.TraceClient;
import com.msa.member.mapper.MemberMapper;
import com.msa.member.vo.MemberProcessEventVo;
import com.msa.member.vo.MemberProcessStepVo;
import com.msa.member.vo.MemberProcessVo;
import com.msa.member.vo.MemberSignupVo;
import com.msa.member.vo.MemberVo;
import feign.FeignException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class MemberService {
  private final MemberMapper memberMapper;
  private final TraceClient traceClient;
  private final HrDependencyService hrEmployeeFeignClient;

  public MemberService(
      MemberMapper memberMapper,
      TraceClient traceClient,
      HrDependencyService hrEmployeeFeignClient) {
    this.memberMapper = memberMapper;
    this.traceClient = traceClient;
    this.hrEmployeeFeignClient = hrEmployeeFeignClient;
  }

  public List<MemberVo> selectAll() {
    return memberMapper.selectAll();
  }

  public MemberVo selectByMemberNo(String memberNo) {
    return memberMapper.selectByMemberNo(memberNo);
  }

  public int insert(MemberVo vo) {
    return memberMapper.insert(vo);
  }

  public MemberSignupVo insertSignup(MemberVo vo, String traceId) {
    String processId =
        "SIGNUP-"
            + LocalDate.now().toString().replace("-", "")
            + "-"
            + UUID.randomUUID().toString().replace("-", "");
    OffsetDateTime startTime = OffsetDateTime.now();

    MemberProcessVo process = new MemberProcessVo();
    process.setProcessId(processId);
    process.setProcessType("MEMBER_SIGNUP");
    process.setProcessName("회원가입");
    process.setBusinessKey(vo.getMemberNo());
    process.setTraceId(traceId);
    process.setStatus(BusinessProcessStatus.RUNNING.name());
    process.setStartTime(startTime);
    memberMapper.insertProcess(process);

    recordTrace(
        traceId,
        processId,
        vo.getMemberNo(),
        null,
        "REQUEST_STARTED",
        "API Gateway",
        "회원가입 요청 수신",
        BusinessStepStatus.RUNNING.name(),
        0L,
        null,
        null);
    recordEvent(
        processId,
        null,
        "PROCESS_STARTED",
        "API Gateway",
        "회원가입 요청 수신",
        BusinessStepStatus.RUNNING.name(),
        traceId);

    String currentStepId = "CHECK_HR_EMPLOYEE";
    String currentService = "HR Service";
    try {
      selectStepStarted(processId, "CHECK_HR_EMPLOYEE", "HR Service", "인사정보 확인 시작", traceId);
      Map<String, Object> hrResult =
          hrEmployeeFeignClient.selectEmployee(vo.getMemberNo(), traceId);
      if (hrResult == null || !Boolean.TRUE.equals(hrResult.get("active"))) {
        throw new BusinessException("HR_NOT_ACTIVE", "정상 직원이 아닙니다.");
      }
      selectStepSucceeded(processId, "CHECK_HR_EMPLOYEE", "HR Service", "인사정보 확인 성공", traceId);

      currentStepId = "CHECK_DORMANT_MEMBER";
      currentService = "Dormant Service";
      selectStepStarted(
          processId, "CHECK_DORMANT_MEMBER", "Dormant Service", "휴면 여부 확인 시작", traceId);
      selectStepSucceeded(
          processId, "CHECK_DORMANT_MEMBER", "Dormant Service", "휴면 여부 확인 성공", traceId);

      currentStepId = "CHECK_EXTERNAL_ELIGIBILITY";
      currentService = "External Service";
      selectStepStarted(
          processId, "CHECK_EXTERNAL_ELIGIBILITY", "External Service", "외부 가입 조건 확인 시작", traceId);

      if ("TIMEOUT".equalsIgnoreCase(vo.getStatus())) {
        throw new BusinessException("EXT_TIMEOUT", "외부 시스템 응답 시간이 초과되었습니다.");
      }

      selectStepSucceeded(
          processId, "CHECK_EXTERNAL_ELIGIBILITY", "External Service", "외부 가입 조건 확인 성공", traceId);

      currentStepId = "SAVE_MEMBER";
      currentService = "Member DB";
      selectStepStarted(processId, "SAVE_MEMBER", "Member DB", "회원정보 저장 시작", traceId);
      MemberVo saved = new MemberVo();
      saved.setMemberNo(vo.getMemberNo());
      saved.setMemberName(vo.getMemberName());
      saved.setStatus(vo.getStatus() == null ? "ACTIVE" : vo.getStatus());
      memberMapper.insert(saved);
      selectStepSucceeded(processId, "SAVE_MEMBER", "Member DB", "회원정보 저장 성공", traceId);

      process.setStatus(BusinessProcessStatus.SUCCESS.name());
      process.setEndTime(OffsetDateTime.now());
      process.setElapsedMs(Duration.between(startTime, process.getEndTime()).toMillis());
      memberMapper.updateProcess(process);
      recordEvent(
          processId,
          null,
          "PROCESS_SUCCEEDED",
          "Member Service",
          "회원가입 완료",
          BusinessStepStatus.SUCCESS.name(),
          traceId);
      recordTrace(
          traceId,
          processId,
          vo.getMemberNo(),
          null,
          "REQUEST_COMPLETED",
          "Member Service",
          "회원가입 완료",
          BusinessStepStatus.SUCCESS.name(),
          process.getElapsedMs(),
          null,
          null);
      MemberSignupVo response = new MemberSignupVo();
      response.setProcessId(processId);
      response.setProcessType(process.getProcessType());
      response.setTraceId(traceId);
      response.setMemberNo(saved.getMemberNo());
      response.setMemberName(saved.getMemberName());
      response.setStatus(saved.getStatus());
      return response;
    } catch (RuntimeException ex) {
      if (ex instanceof DataAccessException) throw ex;
      String code;
      ErrorType errorType;
      String message;
      if (ex instanceof BusinessException business) {
        code = business.getCode();
        errorType = "EXT_TIMEOUT".equals(code) ? ErrorType.TIMEOUT : ErrorType.BUSINESS;
        message = business.getMessage();
      } else if (ex instanceof FeignException
          || ex instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException) {
        boolean timeout = false;
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
          if (cause instanceof SocketTimeoutException
              || cause instanceof java.net.http.HttpTimeoutException) {
            timeout = true;
            break;
          }
        }
        code = timeout ? "HR_TIMEOUT" : "HR_UNAVAILABLE";
        errorType = timeout ? ErrorType.TIMEOUT : ErrorType.CONNECTION;
        message = timeout ? "HR 응답 시간이 초과되었습니다." : "HR 서비스를 사용할 수 없습니다.";
      } else {
        code = ex instanceof DataAccessException ? "DATA_WRITE_FAILED" : "INTERNAL_ERROR";
        errorType = ex instanceof DataAccessException ? ErrorType.DATABASE : ErrorType.INTERNAL;
        message = "회원가입 처리에 실패했습니다.";
      }
      String errorId = UUID.randomUUID().toString();
      if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
          instanceof org.springframework.web.context.request.ServletRequestAttributes attributes)
        attributes.getRequest().setAttribute("errorId", errorId);
      String stepStatus =
          errorType == ErrorType.TIMEOUT
              ? BusinessStepStatus.TIMEOUT.name()
              : BusinessStepStatus.FAILED.name();
      selectStepFailed(
          processId, currentStepId, currentService, traceId, stepStatus, code, errorType, message);
      List<String> steps =
          List.of(
              "CHECK_HR_EMPLOYEE",
              "CHECK_DORMANT_MEMBER",
              "CHECK_EXTERNAL_ELIGIBILITY",
              "SAVE_MEMBER");
      for (String step : steps.subList(steps.indexOf(currentStepId) + 1, steps.size())) {
        String service =
            switch (step) {
              case "CHECK_DORMANT_MEMBER" -> "Dormant Service";
              case "CHECK_EXTERNAL_ELIGIBILITY" -> "External Service";
              default -> "Member DB";
            };
        selectStepNotExecuted(processId, step, service, "이전 단계 실패로 미실행", traceId);
      }
      process.setStatus(BusinessProcessStatus.FAILED.name());
      process.setErrorCode(code);
      process.setErrorType(errorType.name());
      process.setErrorMessage(message);
      process.setEndTime(OffsetDateTime.now());
      process.setElapsedMs(Duration.between(startTime, process.getEndTime()).toMillis());
      memberMapper.updateProcess(process);
      recordEvent(
          processId,
          null,
          "PROCESS_FAILED",
          currentService,
          message,
          BusinessProcessStatus.FAILED.name(),
          traceId);
      recordTrace(
          traceId,
          processId,
          vo.getMemberNo(),
          currentStepId,
          "REQUEST_FAILED",
          currentService,
          message,
          stepStatus,
          process.getElapsedMs(),
          code,
          message);
      MemberSignupVo response = new MemberSignupVo();
      response.setProcessId(processId);
      response.setProcessType(process.getProcessType());
      response.setTraceId(traceId);
      response.setMemberNo(vo.getMemberNo());
      response.setMemberName(vo.getMemberName());
      response.setStatus("FAILED");
      response.setErrorId(errorId);
      response.setErrorCode(code);
      response.setErrorType(errorType.name());
      response.setMessage(message);
      return response;
    }
  }

  public List<MemberProcessVo> selectProcesses() {
    return memberMapper.selectProcesses();
  }

  public MemberProcessVo selectProcess(String processId) {
    return memberMapper.selectProcess(processId);
  }

  public List<MemberProcessStepVo> selectProcessSteps(String processId) {
    return memberMapper.selectProcessSteps(processId);
  }

  public List<MemberProcessEventVo> selectProcessTimeline(String processId) {
    return memberMapper.selectProcessTimeline(processId);
  }

  private void selectStepStarted(
      String processId, String stepId, String serviceName, String message, String traceId) {
    MemberProcessStepVo step = new MemberProcessStepVo();
    step.setProcessId(processId);
    step.setStepId(stepId);
    step.setStepName(message);
    step.setServiceName(serviceName);
    step.setStatus(BusinessStepStatus.RUNNING.name());
    step.setStartTime(OffsetDateTime.now());
    step.setSpanId(com.msa.core.TraceContext.id().substring(0, 16));
    step.setStepExecutionId(UUID.randomUUID().toString());
    step.setAttempt(1);
    memberMapper.insertProcessStep(step);
    if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
        instanceof org.springframework.web.context.request.ServletRequestAttributes attributes) {
      attributes.getRequest().setAttribute("activeStepSpanId", step.getSpanId());
      attributes.getRequest().setAttribute("processId", processId);
    }
    recordEvent(
        processId,
        stepId,
        "STEP_STARTED",
        serviceName,
        message,
        BusinessStepStatus.RUNNING.name(),
        traceId);
    recordTrace(
        traceId,
        processId,
        null,
        stepId,
        "STEP_STARTED",
        serviceName,
        message,
        BusinessStepStatus.RUNNING.name(),
        0L,
        null,
        null);
  }

  private void selectStepSucceeded(
      String processId, String stepId, String serviceName, String message, String traceId) {
    MemberProcessStepVo step = new MemberProcessStepVo();
    step.setProcessId(processId);
    step.setStepId(stepId);
    step.setServiceName(serviceName);
    step.setStatus(BusinessStepStatus.SUCCESS.name());
    step.setEndTime(OffsetDateTime.now());
    step.setElapsedMs(selectElapsedMs(processId, stepId, step.getEndTime()));
    memberMapper.updateProcessStep(step);
    recordEvent(
        processId,
        stepId,
        "STEP_SUCCEEDED",
        serviceName,
        message,
        BusinessStepStatus.SUCCESS.name(),
        traceId);
    recordTrace(
        traceId,
        processId,
        null,
        stepId,
        "STEP_SUCCEEDED",
        serviceName,
        message,
        BusinessStepStatus.SUCCESS.name(),
        step.getElapsedMs(),
        null,
        null);
  }

  private void selectStepFailed(
      String processId,
      String stepId,
      String serviceName,
      String traceId,
      String status,
      String code,
      ErrorType type,
      String message) {
    MemberProcessStepVo step = new MemberProcessStepVo();
    step.setProcessId(processId);
    step.setStepId(stepId);
    step.setServiceName(serviceName);
    step.setStatus(status);
    step.setErrorCode(code);
    step.setErrorType(type.name());
    step.setErrorMessage(message);
    step.setEndTime(OffsetDateTime.now());
    step.setElapsedMs(selectElapsedMs(processId, stepId, step.getEndTime()));
    memberMapper.updateProcessStep(step);
    recordEvent(processId, stepId, "STEP_FAILED", serviceName, message, status, traceId);
    recordTrace(
        traceId,
        processId,
        null,
        stepId,
        "STEP_FAILED",
        serviceName,
        message,
        status,
        step.getElapsedMs(),
        code,
        message);
  }

  private long selectElapsedMs(String processId, String stepId, OffsetDateTime end) {
    return memberMapper.selectProcessSteps(processId).stream()
        .filter(step -> stepId.equals(step.getStepId()) && step.getStartTime() != null)
        .mapToLong(step -> Math.max(0L, Duration.between(step.getStartTime(), end).toMillis()))
        .max()
        .orElse(0L);
  }

  private void selectStepNotExecuted(
      String processId, String stepId, String serviceName, String message, String traceId) {
    MemberProcessStepVo step = new MemberProcessStepVo();
    step.setProcessId(processId);
    step.setStepId(stepId);
    step.setServiceName(serviceName);
    step.setStatus(BusinessStepStatus.NOT_EXECUTED.name());
    step.setSpanId(com.msa.core.TraceContext.id().substring(0, 16));
    step.setStepExecutionId(UUID.randomUUID().toString());
    step.setAttempt(1);
    memberMapper.insertProcessStep(step);
    if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
        instanceof org.springframework.web.context.request.ServletRequestAttributes attributes) {
      attributes.getRequest().setAttribute("activeStepSpanId", step.getSpanId());
      attributes.getRequest().setAttribute("processId", processId);
    }
    recordEvent(
        processId,
        stepId,
        "STEP_NOT_EXECUTED",
        serviceName,
        message,
        BusinessStepStatus.NOT_EXECUTED.name(),
        traceId);
    recordTrace(
        traceId,
        processId,
        null,
        stepId,
        "STEP_NOT_EXECUTED",
        serviceName,
        message,
        BusinessStepStatus.NOT_EXECUTED.name(),
        0L,
        null,
        null);
  }

  private void recordEvent(
      String processId,
      String stepId,
      String eventType,
      String serviceName,
      String message,
      String status,
      String traceId) {
    MemberProcessEventVo event = new MemberProcessEventVo();
    event.setProcessId(processId);
    event.setStepId(stepId);
    event.setEventType(eventType);
    event.setEventTime(OffsetDateTime.now());
    event.setServiceName(serviceName);
    event.setMessage(message);
    event.setStatus(status);
    memberMapper.insertProcessEvent(event);
  }

  private void recordTrace(
      String traceId,
      String processId,
      String businessKey,
      String stepId,
      String eventType,
      String serviceName,
      String message,
      String status,
      Long elapsedMs,
      String errorCode,
      String errorMessage) {
    if (traceId == null || traceId.isBlank()) {
      return;
    }
    TraceEventVo trace = new TraceEventVo();
    trace.setTraceId(traceId);
    trace.setProcessId(processId);
    trace.setBusinessKey(businessKey);
    trace.setProcessType("MEMBER_SIGNUP");
    trace.setProcessDefinitionVersion("1");
    trace.setAggregateVersion((long) memberMapper.selectProcessTimeline(processId).size() + 1);
    if (stepId != null)
      memberMapper.selectProcessSteps(processId).stream()
          .filter(step -> stepId.equals(step.getStepId()))
          .findFirst()
          .ifPresent(
              step -> {
                trace.setStepExecutionId(step.getStepExecutionId());
                trace.setAttempt(step.getAttempt());
              });
    trace.setStepId(stepId);
    trace.setServiceName(serviceName);
    trace.setEventType(eventType);
    trace.setHttpMethod("POST");
    trace.setPath("/members/signup");
    trace.setStatus(status);
    trace.setElapsedMs(elapsedMs);
    trace.setErrorCode(errorCode);
    trace.setErrorMessage(errorMessage);
    trace.setCreatedAt(OffsetDateTime.now());
    if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
            instanceof org.springframework.web.context.request.ServletRequestAttributes attributes
        && attributes.getRequest().getAttribute("traceContext")
            instanceof com.msa.core.TraceContext context) {
      context.apply(trace);
      if (stepId != null)
        memberMapper.selectProcessSteps(processId).stream()
            .filter(step -> stepId.equals(step.getStepId()))
            .findFirst()
            .ifPresent(
                step -> {
                  trace.setSpanId(step.getSpanId());
                  trace.setParentSpanId(context.spanId());
                });
    }
    if (errorCode != null
        && org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
            instanceof org.springframework.web.context.request.ServletRequestAttributes attributes)
      trace.setErrorId((String) attributes.getRequest().getAttribute("errorId"));
    traceClient.insertDurable(trace);
  }
}
