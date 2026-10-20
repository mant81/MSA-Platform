package com.msa.auth.vo;

import lombok.Getter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Setter;

@Getter
@Setter
public class AuthUserVo {
    private Long id;
    private String userId;
    private String userName;
    @JsonIgnore
    private String passwordHash;
    private String status;
    private String role;
}
