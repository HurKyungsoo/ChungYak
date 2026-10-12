package com.portfolio.chungyak.web.view;

import org.springframework.stereotype.Component;

/**
 * 템플릿용 얇은 진입점 — Thymeleaf 3.1 은 {@code th:utext} 안에서 {@code T(...)} 정적 호출을 막는다
 * ("Instantiation of new objects and access to static classes ... is forbidden"). 빈 참조는 허용되므로
 * {@code th:utext="${@emphasis.html(reason)}"} 로 쓴다. 로직은 {@link EmphasizedText} 에 있다.
 */
@Component("emphasis")
public class EmphasisHelper {

    public String html(String text) {
        return EmphasizedText.html(text);
    }
}
