package com.msa.auth.vo;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefreshFamilyVo {
    private String familyId;
    private String userId;
    private OffsetDateTime expiresAt;
    private OffsetDateTime revokedAt;
}
