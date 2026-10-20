package com.msa.member.vo;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemberProcessStepVo {
  private String spanId;
  private String stepExecutionId;
  private Integer attempt;
  private Long id;
  private String processId;
  private String stepId;
  private String stepName;
  private String serviceName;
  private String status;
  private OffsetDateTime startTime;
  private OffsetDateTime endTime;
  private Long elapsedMs;
  private String errorCode;
  private String errorType;
  private String errorMessage;
}
