package com.portfolio.chungyak.web;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.portfolio.chungyak.web.form.EligibilityForm;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * POST 로 받은 판정 폼을 잠깐 들고 있다가, 뒤이은 GET 결과 화면이 다시 읽을 수 있게 하는
 * PRG(Post-Redirect-Get) 저장소.
 *
 * 결과를 POST 응답으로 직접 렌더링하면 새로고침할 때마다 브라우저가 "다시 제출하시겠습니까?"를
 * 띄우고, 결과 URL 을 북마크·공유할 수도 없었다. 이제 POST 는 폼을 이 저장소에 넣고 토큰이 붙은
 * GET URL 로 리다이렉트만 하며, 실제 판정·렌더링은 그 토큰으로 들어오는 GET 이 한다 —
 * 판정 로직 자체가 결정론적이라 몇 번을 다시 계산해도 결과는 같다.
 *
 * 소득·자산 같은 민감한 값이 URL 에 그대로 노출되지 않도록 폼 전체를 서버에 잠깐(TTL) 들고
 * URL 에는 토큰만 남긴다. 로그인이 없는 서비스라 세션이 아니라 캐시로 둔다 — 토큰을 아는
 * 사람은(다른 탭·기기 포함) 만료 전까지 같은 결과를 다시 볼 수 있다는 뜻이라, 링크를
 * 공유할 때는 그만큼 결과가 함께 공유된다는 점을 화면에서 안내한다.
 */
@Component
public class EligibilityResultStore {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final long MAX_ENTRIES = 5_000;

    public record Submission(Long announcementId, EligibilityForm form, boolean explain) {}

    private final Cache<String, Submission> cache = Caffeine.newBuilder()
            .maximumSize(MAX_ENTRIES)
            .expireAfterWrite(TTL)
            .build();

    /** 폼을 저장하고 조회용 토큰을 돌려준다. announcementId 는 GET 쪽에서 URL의 {id}와
     *  토큰이 서로 다른 공고를 가리키지 않는지 확인하는 데 쓴다. */
    public String put(Long announcementId, EligibilityForm form, boolean explain) {
        String token = UUID.randomUUID().toString();
        cache.put(token, new Submission(announcementId, form, explain));
        return token;
    }

    /** 토큰이 없거나 만료됐으면 비어 있다 — 호출부는 폼 화면으로 돌려보낸다. */
    public Optional<Submission> get(String token) {
        return Optional.ofNullable(cache.getIfPresent(token));
    }
}
