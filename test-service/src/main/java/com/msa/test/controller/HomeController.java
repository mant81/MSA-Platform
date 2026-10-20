package com.msa.test.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

  @GetMapping("/operations")
  public String operations() {
    return "operations";
  }

  @GetMapping("/")
  public String index() {
    return "index";
  }
}
