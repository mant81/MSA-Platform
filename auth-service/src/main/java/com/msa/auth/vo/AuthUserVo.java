package com.msa.auth.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthUserVo {
    private Long id;
    private String userId;
    private String userName;
    private String passwordHash;
    private String status;
}
