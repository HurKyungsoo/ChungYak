package com.portfolio.chungyak.web;

import com.anthropic.client.AnthropicClient;
import com.portfolio.chungyak.alert.AlertMailer;
import com.portfolio.chungyak.llm.GroqChatClient;
import com.portfolio.chungyak.llm.LlmExplainer;
import com.portfolio.chungyak.rag.DocumentQaService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 개인정보 처리방침 (ROADMAP D5).
 *
 * 처리방침은 "실제로 하는 일"과 같아야 한다 — 서버에서 꺼져 있는 기능(메일 발송·AI)의 위탁 항목을
 * 늘 적어 두면 사실과 다르고, 켜졌는데 빠지면 고지 누락이다. 그래서 지금 켜진 기능을 보고 해당 절만 그린다.
 */
@Controller
@RequiredArgsConstructor
public class LegalController {

    private final AlertMailer alertMailer;
    private final ObjectProvider<AnthropicClient> anthropicClient;
    private final ObjectProvider<GroqChatClient> groqClient;
    private final ObjectProvider<LlmExplainer> explainer;
    private final DocumentQaService qaService;

    /** 개인정보 보호책임자 연락처 — 비어 있으면 화면이 "준비 중"으로 표시한다(실제 연락처는 운영자가 넣는다). */
    @Value("${app.privacy-contact:}")
    private String privacyContact;

    @GetMapping("/privacy")
    public String privacy(Model model) {
        model.addAttribute("mailEnabled", alertMailer.isEnabled());
        model.addAttribute("aiEnabled", anthropicClient.getIfAvailable() != null);
        // Groq 는 판정 요약을 켠 경우에만 소득·자산이 담긴 판정 근거를 보낸다 — 켜졌을 때만 그 사실을 적는다
        model.addAttribute("groqEnabled", groqClient.getIfAvailable() != null);
        model.addAttribute("groqExplain", groqClient.getIfAvailable() != null && explainer.getIfAvailable() != null);
        model.addAttribute("qaEnabled", qaService.isEnabled());
        // Voyage 는 의미 검색(임베딩)일 때만 질문을 받는다 — BM25 모드(#22)에서는 위탁하지 않으니 적지 않는다
        model.addAttribute("qaSemantic", qaService.isEnabled() && qaService.isSemantic());
        model.addAttribute("privacyContact", privacyContact);
        return "legal/privacy";
    }
}
