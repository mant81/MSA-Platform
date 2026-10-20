package com.msa.member.controller;

import com.msa.member.service.SignupRecoveryService;
import com.msa.member.vo.*;
import java.security.Principal;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/members/requests")
public class SignupRecoveryController {
  private final SignupRecoveryService service;

  public SignupRecoveryController(SignupRecoveryService service) {
    this.service = service;
  }

  @GetMapping
  public List<SignupRecoveryVo> selectRequests(@RequestParam(defaultValue = "") String cursor) {
    return service.selectRequests(cursor);
  }

  @GetMapping("/{key}/audit")
  public List<Map<String, Object>> selectAudit(@PathVariable String key) {
    return service.selectAudit(key);
  }

  @PostMapping("/{key}/allow-retry")
  public SignupRecoveryVo allowRetry(
      @PathVariable String key,
      @RequestBody SignupRecoveryCommandVo command,
      Principal actor,
      @RequestAttribute("traceId") String trace) {
    return service.allowRetry(key, command.reasonCode(), actor.getName(), trace);
  }
}
