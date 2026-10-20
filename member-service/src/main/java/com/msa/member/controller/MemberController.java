package com.msa.member.controller;

import com.msa.member.service.MemberService;
import com.msa.member.vo.MemberProcessEventVo;
import com.msa.member.vo.MemberProcessStepVo;
import com.msa.member.vo.MemberProcessVo;
import com.msa.member.vo.MemberSignupVo;
import com.msa.member.vo.MemberVo;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/members")
public class MemberController {

  private final MemberService memberService;
  private final com.msa.member.service.SignupRequestService signupRequests;

  public MemberController(
      MemberService memberService, com.msa.member.service.SignupRequestService signupRequests) {
    this.signupRequests = signupRequests;
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
  public ResponseEntity<MemberSignupVo> insertSignup(
      @RequestBody MemberVo vo,
      @RequestAttribute("traceId") String traceId,
      java.security.Principal principal,
      @org.springframework.web.bind.annotation.RequestHeader(
              value = "Idempotency-Key",
              required = false)
          String key) {
    MemberSignupVo response = signupRequests.insert(vo, traceId, principal.getName(), key);
    int status =
        response.getErrorCode() == null
            ? 200
            : switch (response.getErrorCode()) {
              case "HR_NOT_ACTIVE" -> 422;
              case "HR_UNAVAILABLE" -> 503;
              case "HR_TIMEOUT", "EXT_TIMEOUT" -> 504;
              default -> 500;
            };
    var builder = ResponseEntity.status(status);
    if (response.getErrorId() != null) builder.header("X-Error-Id", response.getErrorId());
    return builder.body(response);
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
