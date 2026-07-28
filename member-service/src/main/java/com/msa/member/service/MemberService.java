package com.msa.member.service;

import com.msa.core.BusinessProcessStatus;
import com.msa.core.BusinessStepStatus;
import com.msa.core.ErrorType;
import com.msa.core.TraceEventVo;
import com.msa.member.client.TraceClient;
import com.msa.member.client.internal.HrEmployeeFeignClient;
import com.msa.member.mapper.MemberMapper;
import com.msa.member.vo.MemberVo;
import com.msa.member.vo.MemberSignupVo;
import com.msa.member.vo.MemberProcessEventVo;
import com.msa.member.vo.MemberProcessStepVo;
import com.msa.member.vo.MemberProcessVo;
import java.util.List;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MemberService {
    private final MemberMapper memberMapper;
    private final TraceClient traceClient;
    private final HrEmployeeFeignClient hrEmployeeFeignClient;

    public MemberService(MemberMapper memberMapper, TraceClient traceClient, HrEmployeeFeignClient hrEmployeeFeignClient) {
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

    public MemberSignupVo insertSignup(MemberVo vo) {
        String processId = "SIGNUP-" + LocalDate.now().toString().replace("-", "") + "-" + UUID.randomUUID().toString().substring(0, 6);
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
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

        recordTrace(traceId, processId, vo.getMemberNo(), null, "REQUEST_STARTED", "API Gateway", "회원가입 요청 수신", BusinessStepStatus.RUNNING.name(), 0L, null, null);
        recordEvent(processId, null, "PROCESS_STARTED", "API Gateway", "회원가입 요청 수신", BusinessStepStatus.RUNNING.name(), traceId);
        recordEvent(processId, "REGISTER_MEMBER", "STEP_STARTED", "Member Service", "회원가입 시작", BusinessStepStatus.RUNNING.name(), traceId);

        try {
            selectStepStarted(processId, "CHECK_HR_EMPLOYEE", "HR Service", "인사정보 확인 시작", traceId);
            Map<String, Object> hrResult = hrEmployeeFeignClient.selectByEmployeeNo(vo.getMemberNo());
            if (hrResult == null || !Boolean.TRUE.equals(hrResult.get("exists"))) {
                selectStepTimeout(processId, "CHECK_HR_EMPLOYEE", "HR Service", "정상 직원이 아님", traceId);
                selectStepNotExecuted(processId, "CHECK_DORMANT_MEMBER", "Dormant Service", "HR 검증 실패로 미실행", traceId);
                selectStepNotExecuted(processId, "CHECK_EXTERNAL_ELIGIBILITY", "External Service", "HR 검증 실패로 미실행", traceId);
                selectStepNotExecuted(processId, "SAVE_MEMBER", "Member DB", "HR 검증 실패로 미실행", traceId);
                throw new IllegalStateException("HR_NOT_ACTIVE");
            }
            selectStepSucceeded(processId, "CHECK_HR_EMPLOYEE", "HR Service", "인사정보 확인 성공", traceId);

            selectStepStarted(processId, "CHECK_DORMANT_MEMBER", "Dormant Service", "휴면 여부 확인 시작", traceId);
            selectStepSucceeded(processId, "CHECK_DORMANT_MEMBER", "Dormant Service", "휴면 여부 확인 성공", traceId);

            selectStepStarted(processId, "CHECK_EXTERNAL_ELIGIBILITY", "External Service", "외부 가입 조건 확인 시작", traceId);

            if ("TIMEOUT".equalsIgnoreCase(vo.getStatus())) {
                selectStepTimeout(processId, "CHECK_EXTERNAL_ELIGIBILITY", "External Service", "응답 시간 초과", traceId);
                selectStepNotExecuted(processId, "SAVE_MEMBER", "Member DB", "이전 단계 실패로 미실행", traceId);
                throw new IllegalStateException("EXT_TIMEOUT");
            }

            selectStepSucceeded(processId, "CHECK_EXTERNAL_ELIGIBILITY", "External Service", "외부 가입 조건 확인 성공", traceId);

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
            recordEvent(processId, null, "PROCESS_SUCCEEDED", "Member Service", "회원가입 완료", BusinessStepStatus.SUCCESS.name(), traceId);
            recordTrace(traceId, processId, vo.getMemberNo(), null, "REQUEST_COMPLETED", "Member Service", "회원가입 완료", BusinessStepStatus.SUCCESS.name(), process.getElapsedMs(), null, null);
            MemberSignupVo response = new MemberSignupVo();
            response.setProcessId(processId);
            response.setProcessType(process.getProcessType());
            response.setTraceId(traceId);
            response.setMemberNo(saved.getMemberNo());
            response.setMemberName(saved.getMemberName());
            response.setStatus(saved.getStatus());
            return response;
        } catch (IllegalStateException ex) {
            process.setStatus(BusinessProcessStatus.FAILED.name());
            if ("HR_NOT_ACTIVE".equals(ex.getMessage())) {
                process.setErrorCode("HR_NOT_ACTIVE");
                process.setErrorType(ErrorType.BUSINESS.name());
                process.setErrorMessage("정상 직원이 아닙니다.");
            } else {
                process.setErrorCode("EXT_TIMEOUT");
                process.setErrorType(ErrorType.TIMEOUT.name());
                process.setErrorMessage("외부 시스템 응답 시간이 초과되었습니다.");
            }
            process.setEndTime(OffsetDateTime.now());
            process.setElapsedMs(Duration.between(startTime, process.getEndTime()).toMillis());
            memberMapper.updateProcess(process);
            recordEvent(processId, null, "PROCESS_FAILED", "Member Service", "회원가입 실패", BusinessStepStatus.FAILED.name(), traceId);
            String failedStepId = "HR_NOT_ACTIVE".equals(ex.getMessage()) ? "CHECK_HR_EMPLOYEE" : "CHECK_EXTERNAL_ELIGIBILITY";
            String failedServiceName = "HR_NOT_ACTIVE".equals(ex.getMessage()) ? "HR Service" : "External Service";
            String failedMessage = "HR_NOT_ACTIVE".equals(ex.getMessage()) ? "정상 직원이 아닙니다." : "외부 시스템 응답 시간이 초과되었습니다.";
            String failedStatus = "HR_NOT_ACTIVE".equals(ex.getMessage()) ? BusinessStepStatus.FAILED.name() : BusinessStepStatus.TIMEOUT.name();
            String failedErrorCode = "HR_NOT_ACTIVE".equals(ex.getMessage()) ? "HR_NOT_ACTIVE" : "EXT_TIMEOUT";
            recordTrace(traceId, processId, vo.getMemberNo(), failedStepId, "REQUEST_FAILED", failedServiceName, failedMessage, failedStatus, process.getElapsedMs(), failedErrorCode, failedMessage);
            MemberSignupVo response = new MemberSignupVo();
            response.setProcessId(processId);
            response.setProcessType(process.getProcessType());
            response.setTraceId(traceId);
            response.setMemberNo(vo.getMemberNo());
            response.setMemberName(vo.getMemberName());
            response.setStatus("FAILED");
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

    private void selectStepStarted(String processId, String stepId, String serviceName, String message, String traceId) {
        MemberProcessStepVo step = new MemberProcessStepVo();
        step.setProcessId(processId);
        step.setStepId(stepId);
        step.setStepName(message);
        step.setServiceName(serviceName);
        step.setStatus(BusinessStepStatus.RUNNING.name());
        step.setStartTime(OffsetDateTime.now());
        memberMapper.insertProcessStep(step);
        recordEvent(processId, stepId, "STEP_STARTED", serviceName, message, BusinessStepStatus.RUNNING.name(), traceId);
        recordTrace(traceId, processId, null, stepId, "STEP_STARTED", serviceName, message, BusinessStepStatus.RUNNING.name(), 0L, null, null);
    }

    private void selectStepSucceeded(String processId, String stepId, String serviceName, String message, String traceId) {
        MemberProcessStepVo step = new MemberProcessStepVo();
        step.setProcessId(processId);
        step.setStepId(stepId);
        step.setServiceName(serviceName);
        step.setStatus(BusinessStepStatus.SUCCESS.name());
        step.setEndTime(OffsetDateTime.now());
        memberMapper.updateProcessStep(step);
        recordEvent(processId, stepId, "STEP_SUCCEEDED", serviceName, message, BusinessStepStatus.SUCCESS.name(), traceId);
        recordTrace(traceId, processId, null, stepId, "STEP_SUCCEEDED", serviceName, message, BusinessStepStatus.SUCCESS.name(), step.getElapsedMs(), null, null);
    }

    private void selectStepTimeout(String processId, String stepId, String serviceName, String message, String traceId) {
        MemberProcessStepVo step = new MemberProcessStepVo();
        step.setProcessId(processId);
        step.setStepId(stepId);
        step.setServiceName(serviceName);
        step.setStatus(BusinessStepStatus.TIMEOUT.name());
        step.setErrorCode("EXT_TIMEOUT");
        step.setErrorType(ErrorType.TIMEOUT.name());
        step.setErrorMessage("외부 시스템 응답 시간이 초과되었습니다.");
        step.setEndTime(OffsetDateTime.now());
        memberMapper.updateProcessStep(step);
        recordEvent(processId, stepId, "STEP_TIMEOUT", serviceName, message, BusinessStepStatus.TIMEOUT.name(), traceId);
        recordTrace(traceId, processId, null, stepId, "STEP_TIMEOUT", serviceName, message, BusinessStepStatus.TIMEOUT.name(), step.getElapsedMs(), "EXT_TIMEOUT", step.getErrorMessage());
    }

    private void selectStepNotExecuted(String processId, String stepId, String serviceName, String message, String traceId) {
        MemberProcessStepVo step = new MemberProcessStepVo();
        step.setProcessId(processId);
        step.setStepId(stepId);
        step.setServiceName(serviceName);
        step.setStatus(BusinessStepStatus.NOT_EXECUTED.name());
        memberMapper.insertProcessStep(step);
        recordEvent(processId, stepId, "STEP_NOT_EXECUTED", serviceName, message, BusinessStepStatus.NOT_EXECUTED.name(), traceId);
        recordTrace(traceId, processId, null, stepId, "STEP_NOT_EXECUTED", serviceName, message, BusinessStepStatus.NOT_EXECUTED.name(), 0L, null, null);
    }

    private void recordEvent(String processId, String stepId, String eventType, String serviceName, String message, String status, String traceId) {
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

    private void recordTrace(String traceId, String processId, String businessKey, String stepId, String eventType, String serviceName, String message, String status, Long elapsedMs, String errorCode, String errorMessage) {
        if (traceId == null || traceId.isBlank()) {
            return;
        }
        TraceEventVo trace = new TraceEventVo();
        trace.setTraceId(traceId);
        trace.setProcessId(processId);
        trace.setBusinessKey(businessKey);
        trace.setProcessType("MEMBER_SIGNUP");
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
        traceClient.insert(trace);
    }
}
