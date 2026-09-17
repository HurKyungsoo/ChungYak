package com.portfolio.chungyak.web;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.service.AnnouncementQueryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/**
 * 검색엔진용 sitemap.xml · robots.txt (C5).
 *
 * sitemap 은 접수중·접수예정 공고만 담는다 — 닫힌 공고까지 넣으면 수천 건이라
 * 사이트맵만 비대해지고, 지금 신청 가능한 공고를 노출하는 게 유입 목적과 더 맞는다.
 */
@RestController
@RequiredArgsConstructor
public class SeoController {

    private final AnnouncementQueryService queryService;

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap(HttpServletRequest request) {
        String base = ServletUriComponentsBuilder.fromContextPath(request).build().toUriString();
        List<Announcement> announcements = queryService.findOpenOrUpcoming(null, null, null);

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        appendUrl(xml, base + "/announcements", "daily");
        appendUrl(xml, base + "/announcements/calendar", "daily");
        appendUrl(xml, base + "/general-supply", "monthly");
        for (Announcement a : announcements) {
            appendUrl(xml, base + "/announcements/" + a.getId(), "weekly");
        }
        xml.append("</urlset>\n");
        return xml.toString();
    }

    private void appendUrl(StringBuilder xml, String loc, String changefreq) {
        xml.append("  <url><loc>").append(loc).append("</loc><changefreq>")
                .append(changefreq).append("</changefreq></url>\n");
    }

    /** robots.txt 의 Sitemap 줄은 절대 URL 이어야 하는데, 배포 도메인을 코드에 박을 수 없어
     * 여기서도 요청 기준으로 만든다. */
    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots(HttpServletRequest request) {
        String base = ServletUriComponentsBuilder.fromContextPath(request).build().toUriString();
        return """
                User-agent: *
                Allow: /
                Disallow: /api/admin/
                Disallow: /h2-console/
                Disallow: /actuator/

                Sitemap: %s/sitemap.xml
                """.formatted(base);
    }
}
