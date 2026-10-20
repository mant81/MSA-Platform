package com.msa.member.vo;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignupRecoveryVo {
  private String requestKey;
  private String ownerId;
  private String memberNo;
  private String initialTraceId;
  private String status;
  private int retryCount;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
  @com.fasterxml.jackson.annotation.JsonIgnore private boolean hasResponse;
}
