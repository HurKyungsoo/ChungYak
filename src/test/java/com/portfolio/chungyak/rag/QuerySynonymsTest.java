package com.portfolio.chungyak.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 일상어 → 공고문 용어 (#39) — LLM 용어 힌트와 "공고문 용어로 다시 묻기" */
class QuerySynonymsTest {

    @Test
    @DisplayName("1:1 용어만 힌트·다시 묻기로 — 조사가 붙어도, 갈래가 여럿인 말('돈')은 아니다")
    void aliases() {
        assertThat(QuerySynonyms.hintLine("모델하우스 어디 있어요?"))
                .isEqualTo("\n(용어: 질문의 '모델하우스' = 공고문의 '견본주택')");
        assertThat(QuerySynonyms.rephrase("모델하우스 어디 있어요?")).contains("견본주택 어디 있어요?");
        assertThat(QuerySynonyms.rephrase("월세는 얼마고 이사는 언제 해요?")).contains("월임대료는 얼마고 입주는 언제 해요?");

        assertThat(QuerySynonyms.hintLine("돈 얼마 내야 돼?")).isEmpty();
        assertThat(QuerySynonyms.rephrase("견본주택 주소가 어디예요?")).isEmpty();
    }
}
