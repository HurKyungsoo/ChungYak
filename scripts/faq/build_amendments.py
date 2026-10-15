#!/usr/bin/env python3
"""FAQ 기준일(2024-05-29) 뒤 「주택공급에 관한 규칙」 개정 → src/main/resources/faq/amendments.json

입력: 국가법령정보센터 제·개정이유 목록 페이지에서 뽑은 개정별 원문(effective·decree·reason) JSON.
    https://law.go.kr/LSW/lsRvsRsnListP.do?lsId=008243&chrClsCd=010102
    python scripts/faq/build_amendments.py law-reasons.json src/main/resources/faq/molit-faq-2024-05.json src/main/resources/faq/amendments.json

pattern 은 이 스크립트에서 정한다 — FAQ 문항(질문·답)에 걸리면 그 답 옆에 "이후 개정과 관련" 경고를 붙인다.
놓치는 것보다 더 붙는 쪽이 낫다(경고일 뿐 답을 숨기지 않는다). reason 은 법령정보센터 문장 그대로.
"""
import json
import re
import sys
from collections import Counter

PATTERNS = {
    "국토교통부령 제1592호": ("민영 신생아 특별공급·지역균형발전 특별공급 신설, 기관추천 특별공급 위원회",
                        r"신생아|기관추천|지역균형"),
    "국토교통부령 제1504호": ("사전당첨이 취소된 사람 우선공급", r"사전당첨|사전청약"),
    "국토교통부령 제1501호": ("무순위 청약은 무주택세대구성원만(지자체가 거주 요건 추가 가능), 가점제·노부모 부양 당첨자 부양가족 건강보험 요양급여 내역 제출",
                        r"무순위|부양가족"),
    "국토교통부령 제1486호": ("영업정지·벌점 사업주체의 입주자모집 제한 기간 단축(신청자와 무관)", r"영업정지|벌점"),
    "국토교통부령 제1471호": ("신혼부부 특별공급 확대·무주택 요건은 공고일 기준·혼인 전 당첨 이력자 1회 허용, 2024-06-19 이후 출산 가구 특별공급 1회 추가",
                        r"신혼부부|특별공급\s*횟수|횟수\s*제한|한\s*차례"),
    "국토교통부령 제1423호": ("아파트 외 소형 주택(85㎡·공시가 3억, 수도권 5억 이하) 1채는 무주택으로 인정",
                        r"소형|저가주택|주택공시가격|아파트\s*외"),
    "국토교통부령 제1391호": ("청약예금·부금·저축 기간과 납입을 주택청약종합저축에 합산, 월 납입 인정 한도 상향, 사전당첨자 통장 사용 범위 확대",
                        r"청약예금|청약부금|청약저축|납입\s*인정|인정\s*금액|저축총액|월\s*납입"),
}


def main(reasons_path, faq_path, dst):
    src = json.load(open(reasons_path, encoding="utf-8"))
    out = []
    for a in src:
        title, pattern = PATTERNS[a["decree"]]
        reason = re.sub(r"^【제정·개정이유】\s*\[일부개정\]\s*◇\s*개정이유 및 주요내용\s*", "", a["reason"])
        reason = re.sub(r"\s*<\s*국토교통부 제공\s*>\s*주택공급에 관한 규칙$|\s*주택공급에 관한 규칙$", "", reason).strip()
        out.append({"effective": a["effective"], "decree": a["decree"], "title": title,
                    "pattern": pattern, "reason": reason})
    doc = {
        "source": "국가법령정보센터 「주택공급에 관한 규칙」 제·개정이유 (https://law.go.kr/LSW/lsRvsRsnListP.do?lsId=008243&chrClsCd=010102)",
        "checkedOn": "2026-10-15",
        "after": "2024-05-29",
        "amendments": out,
    }
    with open(dst, "w", encoding="utf-8", newline="\n") as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)

    faq = json.load(open(faq_path, encoding="utf-8"))
    flagged = {}
    for it in faq["items"]:
        text = it["question"] + "\n" + it["answer"]
        hits = [a["effective"] for a in out if re.search(a["pattern"], text)]
        if hits:
            flagged[it["no"]] = hits
    print(f"경고 붙는 문항 {len(flagged)}/{len(faq['items'])}", Counter(h for v in flagged.values() for h in v))


if __name__ == "__main__":
    main(*sys.argv[1:4])
