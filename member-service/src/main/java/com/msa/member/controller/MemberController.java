package com.msa.member.controller;

import com.msa.member.service.MemberService;
import com.msa.member.vo.MemberVo;
import com.msa.member.vo.MemberSignupVo;
import com.msa.member.vo.MemberProcessVo;
import com.msa.member.vo.MemberProcessStepVo;
import com.msa.member.vo.MemberProcessEventVo;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public List<MemberVo> selectAll() {
        return memberService.selectAll();
    }

    @GetMapping("/{memberNo}")
    public MemberVo selectByMemberNo(@PathVariable String memberNo) {
        return memberService.selectByMemberNo(memberNo);
    }

    @PostMapping
    public int insert(@RequestBody MemberVo vo) {
        return memberService.insert(vo);
    }

    @PostMapping("/signup")
    public ResponseEntity<MemberSignupVo> insertSignup(@RequestBody MemberVo vo,
            @RequestAttribute("traceId") String traceId) {
        MemberSignupVo response = memberService.insertSignup(vo, traceId);
        int status = response.getErrorCode() == null ? 200 : switch (response.getErrorCode()) {
            case "HR_NOT_ACTIVE" -> 422;
            case "HR_UNAVAILABLE" -> 503;
            case "HR_TIMEOUT", "EXT_TIMEOUT" -> 504;
            default -> 500;
        };
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/processes")
    public List<MemberProcessVo> selectProcesses() {
        return memberService.selectProcesses();
    }

    @GetMapping("/processes/{processId}")
    public MemberProcessVo selectProcess(@PathVariable String processId) {
        return memberService.selectProcess(processId);
    }

    @GetMapping("/processes/{processId}/steps")
    public List<MemberProcessStepVo> selectProcessSteps(@PathVariable String processId) {
        return memberService.selectProcessSteps(processId);
    }

    @GetMapping("/processes/{processId}/timeline")
    public List<MemberProcessEventVo> selectProcessTimeline(@PathVariable String processId) {
        return memberService.selectProcessTimeline(processId);
    }
}
