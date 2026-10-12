package com.portfolio.chungyak.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/announcements";
    }

    /**
     * "내 조건" — 이 브라우저에 저장된 판정 조건을 보고 지우는 화면. 조건은 서버가 모르므로(localStorage)
     * 화면이 직접 읽어 그린다. 서버는 빈 틀만 내려준다.
     */
    @GetMapping("/my-profile")
    public String myProfile() {
        return "my-profile";
    }
}
