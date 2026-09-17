package com.portfolio.chungyak.web.view;

/** 공통요건(재당첨·소득·자산·통장·거주) 확인 결과 한 건 — rule.RequirementCheck 를 화면 전용 값으로 옮긴 것. */
public record CommonCheckView(String kindLabel, String status, String reason, String improvementHint) {

    public boolean pass() { return "PASS".equals(status); }
    public boolean fail() { return "FAIL".equals(status); }
    public boolean missing() { return "MISSING".equals(status); }
}
