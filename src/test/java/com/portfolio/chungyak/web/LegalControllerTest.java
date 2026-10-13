package com.portfolio.chungyak.web;

import com.anthropic.client.AnthropicClient;
import com.portfolio.chungyak.alert.AlertMailer;
import com.portfolio.chungyak.llm.GroqChatClient;
import com.portfolio.chungyak.llm.LlmExplainer;
import com.portfolio.chungyak.rag.DocumentQaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 처리방침의 위탁 항목은 실제로 켜진 기능만 따라가야 한다 — 하지 않는 국외이전을 적어도, 하는 것을 빠뜨려도 안 된다.
 */
class LegalControllerTest {

    @SuppressWarnings("unchecked")
    private Model privacyModel(boolean qaEnabled, boolean semantic) {
        DocumentQaService qa = mock(DocumentQaService.class);
        when(qa.isEnabled()).thenReturn(qaEnabled);
        when(qa.isSemantic()).thenReturn(semantic);
        LegalController controller = new LegalController(mock(AlertMailer.class),
                mock(ObjectProvider.class), mock(ObjectProvider.class), mock(ObjectProvider.class), qa);
        Model model = new ConcurrentModel();
        controller.privacy(model);
        return model;
    }

    @Test
    @DisplayName("BM25 모드 Q&A(#22)는 Voyage 로 질문을 보내지 않는다 — Voyage 위탁을 적지 않는다")
    void bm25ModeDoesNotListVoyage() {
        Model model = privacyModel(true, false);

        assertThat(model.getAttribute("qaEnabled")).isEqualTo(true);
        assertThat(model.getAttribute("qaSemantic")).isEqualTo(false);
    }

    @Test
    @DisplayName("의미 검색(임베딩) 모드면 Voyage 위탁을 적는다")
    void semanticModeListsVoyage() {
        assertThat(privacyModel(true, true).getAttribute("qaSemantic")).isEqualTo(true);
        assertThat(privacyModel(false, true).getAttribute("qaSemantic")).as("Q&A 가 꺼져 있으면 위탁도 없다").isEqualTo(false);
    }
}
