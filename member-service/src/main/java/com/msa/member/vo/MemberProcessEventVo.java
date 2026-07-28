package com.msa.member.vo;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemberProcessEventVo {
    private Long id;
    private String processId;
    private String stepId;
    private String eventType;
    private OffsetDateTime eventTime;
    private String serviceName;
    private String message;
    private String status;
}
