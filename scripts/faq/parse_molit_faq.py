#!/usr/bin/env python3
"""국토부 「2024 주택청약 FAQ」 PDF 텍스트 → 문항별 JSON (src/main/resources/faq/molit-faq-2024-05.json).

원본: 공공데이터포털 15035942 「국토교통부_주택청약 및 공급규칙 FAQ_20240529」(이용허락범위 제한 없음),
      국토부 정책자료 https://www.molit.go.kr/USR/policyData/m_34681/dtl.jsp?id=4765 의 첨부 PDF.
PDF 는 저장소에 넣지 않는다(4.3MB). 텍스트는 PDFBox 3 의 PDFTextStripper 기본 설정으로 뽑는다:

    java -cp "pdfbox-3.x.jar;fontbox-3.x.jar;pdfbox-io-3.x.jar;commons-logging.jar" Pdf2Txt.java faq.pdf faq.txt
    python scripts/faq/parse_molit_faq.py faq.txt src/main/resources/faq/molit-faq-2024-05.json

구조(2024-05 판):
- 앞쪽 목차: 장 "1. 청약신청지역 ···· 3", 절 "가. 주요내용 ···· 3", 문항 "Q12. 제목 ···· 11"(제목이 여러 줄일 수 있다)
- 본문: "12 제목" 줄 + 답 문단. 각 절 첫머리엔 "주요내용"·관련 조문 상자가 있고 쪽마다 옆 탭 "청약제한사항5"(장 이름+번호)
답은 원문 그대로 두고, 쪽 번호·머리말·꼬리말·옆 탭 줄만 뺀다. 목차의 절 제목을 만나면 앞 문항 답이 끝난다 —
절 첫머리 개요·조문 상자가 앞 문항 답에 붙지 않게(처음 버전에선 Q120 답에 제2조 정의가 붙었다).
"""
import json
import re
import sys

FOOTERS = re.compile(r"^(2024 주택청약\s*FAQ|Apartment Application FAQ 2024|[ivxl]+|\d{1,3})$")
ARTICLE_BOX = re.compile(r"^제\d+조(의\d+)?\s*\([^)]{1,30}\)")
PART = re.compile(r"^[ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ]\.\s*\S")
TOC_Q = re.compile(r"^Q(\d+)\.\s*(.*)")
TOC_CHAPTER = re.compile(r"^(\d+)\.\s*(.+?)\s*·{2,}\s*(\d+)$")
TOC_SECTION = re.compile(r"^([가-하])\.\s*(.+?)\s*·{2,}\s*(\d+)$")


def squash(s):
    return re.sub(r"\s+", "", s)


def read_toc(lines):
    """목차 → (문항 제목·쪽, 장 탭 이름, 절 제목 집합)"""
    titles, pages, cur, buf = {}, {}, None, ""
    chapters, sections = {}, set()
    for line in lines:
        s = line.strip()
        m = TOC_CHAPTER.match(s)
        if m and cur is None:
            chapters[squash(m.group(2)) + m.group(1)] = int(m.group(1))
            chapters[squash(m.group(1) + "." + m.group(2))] = int(m.group(1))
            continue
        m = TOC_SECTION.match(s)
        if m and cur is None:
            sections.add(squash(m.group(1) + "." + m.group(2)))
            continue
        m = TOC_Q.match(s)
        if m:
            cur, buf = int(m.group(1)), m.group(2)
        elif cur is not None:
            buf += " " + s
        if cur is not None and "··" in buf:
            page = re.search(r"·{2,}\s*(\d+)\s*$", buf)
            titles.setdefault(cur, re.sub(r"\s*·{2,}.*$", "", buf).strip())
            if page:
                pages.setdefault(cur, int(page.group(1)))
            cur, buf = None, ""
    return titles, pages, chapters, sections


def main(src, dst):
    lines = open(src, encoding="utf-8").read().splitlines()
    titles, pages, chapters, sections = read_toc(lines)
    n = max(titles)

    # 본문 시작 = 목차가 끝난 뒤 "1 <Q1 제목 앞부분>" 줄
    head1 = squash(titles[1])[:12]
    body_start = None
    for i, line in enumerate(lines):
        s = line.strip()
        if re.match(r"^1\s", s) and squash(s[1:]).startswith(head1):
            body_start = i
    if body_start is None:
        sys.exit("본문 시작(Q1)을 찾지 못했다 — PDF 판이 바뀌었는지 확인")
    body = lines[body_start:]

    # 각 문항의 시작 줄: "<번호> <제목 앞부분>" (차례대로 찾는다)
    starts, pos = {}, 0
    for q in range(1, n + 1):
        head = squash(titles[q])[:8]
        for i in range(pos, len(body)):
            m = re.match(r"^(\d+)\s+(.*)", body[i].strip())
            if m and int(m.group(1)) == q and squash(m.group(2))[:8] == head[: len(squash(m.group(2))[:8])]:
                starts[i] = q
                pos = i + 1
                break
    missing = [q for q in range(1, n + 1) if q not in starts.values()]
    if missing:
        sys.exit(f"본문에서 못 찾은 문항: {missing[:20]}")

    # 첫 문항 앞의 장·절
    part, chapter_no, section = None, None, None
    for s in (l.strip() for l in lines[body_start - 8:body_start]):
        if PART.match(s):
            part = s
        if squash(s) in sections:
            section = s

    items, current, answer, collecting = [], None, [], False

    def close():
        if current is not None:
            items[-1]["answer"] = "\n".join(answer).strip()

    i = 0
    while i < len(body):
        s = body[i].strip()
        if i in starts:
            close()
            current, answer, collecting = starts[i], [], True
            items.append({"no": current, "part": part, "section": section, "page": pages.get(current),
                          "question": titles[current], "answer": ""})
            # 제목이 여러 줄이면 제목 글자 수만큼 줄을 먹는다
            title_len = len(squash(titles[current]))
            got = len(squash(re.sub(r"^\d+\s+", "", s)))
            i += 1
            while got < title_len and i < len(body) and i not in starts:
                got += len(squash(body[i]))
                i += 1
            continue
        sq = squash(s)
        if not s or FOOTERS.match(s):
            pass
        elif PART.match(s):
            part = s
        elif sq in chapters:                 # 옆 탭 — 새 장이면 앞 답이 끝난다, 같은 장이면 줄만 뺀다
            if chapters[sq] != chapter_no:
                chapter_no, section, collecting = chapters[sq], None, False
        elif sq in sections:                 # 절 제목 — 앞 답 끝, 다음 문항까지 개요·조문 상자는 버린다
            section, collecting = s, False
        elif ARTICLE_BOX.match(s):           # 문항 사이 조문 상자 "제2조(정의) …" — 답이 아니다
            collecting = False
        elif collecting:
            answer.append(body[i].rstrip())
        i += 1
    close()

    doc = {
        "source": "국토교통부 「2024 주택청약 FAQ」 (2024-05-29, 공공데이터포털 15035942, 이용허락범위 제한 없음)",
        "basisDate": "2024-05-29",
        "url": "https://www.molit.go.kr/USR/policyData/m_34681/dtl.jsp?id=4765",
        "items": sorted(items, key=lambda x: x["no"]),
    }
    with open(dst, "w", encoding="utf-8", newline="\n") as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)
    lens = sorted(len(it["answer"]) for it in items)
    short = [it["no"] for it in items if len(it["answer"]) < 20]
    print(f"{len(items)}문항, 답 중앙값 {lens[len(lens) // 2]}자, 최대 {lens[-1]}자, 짧은 답 {short[:20]}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
