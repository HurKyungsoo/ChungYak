package com.portfolio.chungyak.web;

import com.portfolio.chungyak.alert.AlertMailer;
import com.portfolio.chungyak.service.SyncStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 모든 화면이 공유하는 모델 값.
 *
 * 지금은 푸터의 "데이터 최종 갱신" 하나뿐이다 — 공고 데이터는 매일 배치로 들어오므로
 * 언제 갱신됐는지가 곧 신뢰의 근거다. 수집이 조용히 실패해 며칠 낡았다면 그 사실도
 * 사용자가 알아야 한다(숨기면 낡은 데이터를 최신인 것처럼 보여주게 된다).
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAttributes {

    private static final DateTimeFormatter KST =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));

    private final SyncStatus syncStatus;
    private final AlertMailer alertMailer;

    /**
     * 메일 발송이 켜져 있는지 — 꺼져 있으면 "새 공고 알림" 신청 입구를 숨긴다.
     * 발송이 안 되는데 받기만 하면 이메일·소득·자산을 쓸모없이 저장하게 되고, 확인 메일도 오지 않는다.
     */
    @ModelAttribute("alertMailEnabled")
    public boolean alertMailEnabled() {
        return alertMailer.isEnabled();
    }

    /** 마지막으로 <b>성공한</b> 수집 시각. 한 번도 안 돌았으면 빈 문자열 — 화면에서 줄 자체를 감춘다. */
    @ModelAttribute("lastSyncAt")
    public String lastSyncAt() {
        Instant at = syncStatus.getLastSuccessAt();
        return at == null ? "" : KST.format(at);
    }
}
