package com.portfolio.chungyak.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 자연어 질문 -> {@link ExtractedProfile} 추출.
 *
 * ★ CLAUDE.md 절대 규칙의 "앞" 쪽이다.
 *   - LLM 은 여기서 값만 뽑는다. 자격은 판정하지 않는다.
 *   - 뽑은 값은 규칙 엔진으로 바로 가지 않는다 — 사용자가 폼에서 확인·수정한 뒤에야
 *     EligibilityController -> EligibilityEngine 으로 들어간다.
 *   - 애매한 필드는 추측하지 않고 null 로 남긴다.
 *
 * ANTHROPIC_API_KEY 가 없으면 {@link LlmProfileCaller} 빈이 없고, extract() 는
 * DISABLED 를 반환한다. 다른 화면은 이 서비스와 무관하게 동작한다.
 */
@Slf4j
@Service
public class ProfileExtractionService {

    private final Optional<LlmProfileCaller> caller;

    public ProfileExtractionService(Optional<LlmProfileCaller> caller) {
        this.caller = caller;
    }

    /** 이 기능을 쓸 수 있는지 — 화면에서 자연어 입력창 노출 여부를 정한다. */
    public boolean isAvailable() {
        return caller.isPresent();
    }

    public ProfileExtractionResult extract(String naturalText) {
        if (caller.isEmpty()) {
            return ProfileExtractionResult.disabled();
        }
        if (naturalText == null || naturalText.isBlank()) {
            return ProfileExtractionResult.failed("입력이 비어 있음");
        }

        ExtractedProfile extracted;
        try {
            extracted = caller.get().call(naturalText.strip());
        } catch (RuntimeException e) {
            // 어떤 실패든 되물음이 필요 없다 — 사용자는 아래 폼을 직접 채우면 된다.
            log.warn("자연어 추출 실패 — {}", e.toString());
            return ProfileExtractionResult.failed("LLM 호출 오류");
        }

        if (extracted == null || !extracted.hasAnyValue()) {
            log.info("자연어 추출 — 조건을 하나도 못 읽음. 입력='{}'", preview(naturalText));
            return ProfileExtractionResult.failed("조건을 읽지 못함");
        }

        log.info("자연어 추출 완료 — 입력='{}', 미확인 필드={}",
                preview(naturalText), extracted.unknownFieldLabels());
        return ProfileExtractionResult.extracted(extracted);
    }

    private static String preview(String s) {
        String t = s.strip().replaceAll("\\s+", " ");
        return t.length() <= 60 ? t : t.substring(0, 60) + "…";
    }
}
