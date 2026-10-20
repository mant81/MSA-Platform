package com.msa.auth.mapper;

import com.msa.auth.vo.RefreshFamilyVo;
import com.msa.auth.vo.RefreshSessionVo;
import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthRefreshMapper {
    RefreshSessionVo selectSession(String tokenHash);
    RefreshFamilyVo selectFamilyForUpdate(String familyId);
    int insertFamily(RefreshFamilyVo family);
    int insertSession(RefreshSessionVo session);
    int markUsed(@Param("tokenHash") String tokenHash, @Param("now") OffsetDateTime now);
    int revokeFamily(@Param("familyId") String familyId, @Param("now") OffsetDateTime now);
    int revokeSessions(@Param("familyId") String familyId, @Param("now") OffsetDateTime now);
    int insertAudit(@Param("eventId") String eventId, @Param("userId") String userId,
            @Param("familyId") String familyId, @Param("eventType") String eventType, @Param("now") OffsetDateTime now);
}
