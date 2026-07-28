package com.msa.member.vo;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemberProcessVo {
    private Long id;
    private String processId;
    private String processType;
    private String processName;
    private String businessKey;
    private String traceId;
    private String status;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private Long elapsedMs;
    private String errorCode;
    private String errorType;
    private String errorMessage;
}
