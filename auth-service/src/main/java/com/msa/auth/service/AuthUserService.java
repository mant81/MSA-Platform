package com.msa.auth.service;

import com.msa.auth.mapper.AuthUserMapper;
import com.msa.auth.vo.AuthUserVo;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AuthUserService {
    private final AuthUserMapper authUserMapper;

    public AuthUserService(AuthUserMapper authUserMapper) {
        this.authUserMapper = authUserMapper;
    }

    public List<AuthUserVo> selectAll() {
        return authUserMapper.selectAll();
    }

    public int insert(AuthUserVo vo) {
        return authUserMapper.insert(vo);
    }

    public AuthUserVo selectByUserId(String userId) {
        return authUserMapper.selectByUserId(userId);
    }

    public AuthUserVo selectByUserIdAndPassword(String userId, String password) {
        AuthUserVo vo = authUserMapper.selectByUserId(userId);
        if (vo == null) {
            return null;
        }
        if (password == null || !password.equals(vo.getPasswordHash())) {
            return null;
        }
        return vo;
    }
}
