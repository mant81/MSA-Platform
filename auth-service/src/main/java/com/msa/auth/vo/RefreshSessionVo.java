package com.msa.auth.vo;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefreshSessionVo {
    private String tokenHash;
    private String familyId;
    private OffsetDateTime expiresAt;
    private OffsetDateTime usedAt;
    private OffsetDateTime revokedAt;
}
