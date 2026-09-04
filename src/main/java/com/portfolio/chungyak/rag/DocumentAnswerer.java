package com.portfolio.chungyak.rag;

import java.util.List;

/**
 * 공고문 발췌문 + 질문 → 자연어 답변.
 *
 * ★ 이건 판정이 아니라 <b>정보 검색</b>이다(방향 3). 그래서 LLM 을 자유롭게 쓴다 —
 * 단, 답은 <b>주어진 발췌문에 근거</b>해야 하고 발췌에 없으면 "확인되지 않는다"고 말한다.
 * ANTHROPIC_API_KEY 가 없으면 구현 빈이 없어 Q&A 기능이 꺼진다.
 */
public interface DocumentAnswerer {

    /**
     * @param question 사용자 질문
     * @param numberedExcerpts "[1] ...", "[2] ..." 형태로 번호가 붙은 공고문 조각들
     * @return 발췌문에 근거한 한국어 답변 (근거는 [n] 으로 표기)
     */
    String answer(String question, List<String> numberedExcerpts);
}
