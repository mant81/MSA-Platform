package com.msa.member.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemberSignupVo {
    private String processId;
    private String processType;
    private String traceId;
    private String memberNo;
    private String memberName;
    private String status;
}
