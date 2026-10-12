package com.portfolio.chungyak.web.view;

import org.springframework.web.util.HtmlUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 판정 사유 문장 속 "내 값"을 굵게 (목업 Verdict: <b>52개월</b> · 기준 84개월 이내).
 *
 * <b>표시만 바꾼다</b> — 규칙 엔진이 만든 문장은 한 글자도 바꾸지 않고, 숫자 토큰에 {@code <strong>} 을
 * 씌울 뿐이다. 괄호 안 숫자는 기준값("요건(6개월)", "(2024년 기준)")이라 굵게 하지 않는다 —
 * 사용자가 입력한 값과 제도 기준을 눈으로 구분하게 하려는 것이다.
 *
 * 출력은 {@code th:utext} 로 쓰므로 원문은 반드시 이스케이프한다. 숫자 토큰을 찾는 건 이스케이프 <b>전</b>
 * 원문에서 하고, 조각마다 따로 이스케이프한다(이스케이프된 {@code &#39;} 의 숫자를 다시 잡지 않도록).
 */
public final class EmphasizedText {

    /** 4억원 · 3억 7,900만원 · 2,100만원 · 100개월 · 172% · 3회 · 84.97㎡ … */
    private static final Pattern NUMBER = Pattern.compile(
            "\\d[\\d,]*(?:\\.\\d+)?"
                    + "(?:\\s?억(?:\\s?\\d[\\d,]*만)?원?"
                    + "|\\s?(?:개월|세대|명|회|년|만원|원|%|만|㎡))?");

    private EmphasizedText() {}

    public static String html(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder(text.length() + 32);
        Matcher m = NUMBER.matcher(text);
        int last = 0;
        while (m.find()) {
            if (insideParentheses(text, m.start())) continue;   // 기준값은 그대로
            out.append(HtmlUtils.htmlEscape(text.substring(last, m.start()), "UTF-8"));
            out.append("<strong>").append(HtmlUtils.htmlEscape(m.group(), "UTF-8")).append("</strong>");
            last = m.end();
        }
        out.append(HtmlUtils.htmlEscape(text.substring(last), "UTF-8"));
        return out.toString();
    }

    private static boolean insideParentheses(String text, int index) {
        int depth = 0;
        for (int i = 0; i < index; i++) {
            char c = text.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && depth > 0) depth--;
        }
        return depth > 0;
    }
}
