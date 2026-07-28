package com.msa.member.mapper;

import com.msa.core.TraceEventVo;
import com.msa.member.vo.MemberVo;
import com.msa.member.vo.MemberProcessEventVo;
import com.msa.member.vo.MemberProcessStepVo;
import com.msa.member.vo.MemberProcessVo;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MemberMapper {
    List<MemberVo> selectAll();
    MemberVo selectByMemberNo(String memberNo);
    int insert(MemberVo vo);

    int insertProcess(MemberProcessVo vo);
    int updateProcess(MemberProcessVo vo);
    List<MemberProcessVo> selectProcesses();
    MemberProcessVo selectProcess(String processId);

    int insertProcessStep(MemberProcessStepVo vo);
    int updateProcessStep(MemberProcessStepVo vo);
    List<MemberProcessStepVo> selectProcessSteps(String processId);

    int insertProcessEvent(MemberProcessEventVo vo);
    List<MemberProcessEventVo> selectProcessTimeline(String processId);

    int insertTraceEvent(TraceEventVo vo);
}
