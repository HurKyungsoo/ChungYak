package com.portfolio.chungyak.llm;

/**
 * 자연어 문장 한 개 -> {@link ExtractedProfile} 한 번 호출.
 *
 * LLM SDK 의존은 구현체({@code AnthropicProfileCaller})에만 둔다.
 * 서비스는 이 인터페이스만 알고, 단위테스트는 여기에 목을 끼운다.
 */
public interface LlmProfileCaller {

    /**
     * @return 추출된 프로필. 필드는 nullable(모르면 null). 응답 자체가 비면 null 가능.
     * @throws RuntimeException 호출/파싱 실패 (서비스가 잡아서 FAILED 로 바꾼다)
     */
    ExtractedProfile call(String naturalText);
}
