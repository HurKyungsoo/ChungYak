package com.portfolio.chungyak.llm;

import com.portfolio.chungyak.llm.ExtractionEvalScorer.CaseScore;
import com.portfolio.chungyak.llm.ExtractionEvalScorer.FieldResult;
import com.portfolio.chungyak.llm.ExtractionEvalCase.Kind;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 추출 eval 결과 집계 + 사람이 읽을 스코어카드.
 *
 * 지표:
 * <ul>
 *   <li><b>필드 정확도</b> — 검사한 전체 필드 기대 중 맞은 비율</li>
 *   <li><b>null 원칙 준수</b> — "문장에 근거 없음 → null" 기대 중 지켜진 비율 (이 프로젝트의 핵심)</li>
 *   <li><b>케이스 통과율</b> — 한 케이스의 모든 기대가 맞은 비율</li>
 * </ul>
 */
record ExtractionEvalScorecard(String model, int caseTotal, int casePassed,
                               int fieldTotal, int fieldCorrect,
                               int nullTotal, int nullCorrect,
                               Map<String, int[]> perField,
                               List<CaseScore> scores) {

    static ExtractionEvalScorecard of(List<CaseScore> scores, String model) {
        int casePassed = 0, fieldTotal = 0, fieldCorrect = 0, nullTotal = 0, nullCorrect = 0;
        Map<String, int[]> perField = new LinkedHashMap<>();
        for (CaseScore cs : scores) {
            if (cs.passed()) casePassed++;
            for (FieldResult f : cs.fields()) {
                fieldTotal++;
                if (f.correct()) fieldCorrect++;
                if (f.kind() == Kind.NULL) {
                    nullTotal++;
                    if (f.correct()) nullCorrect++;
                }
                int[] pf = perField.computeIfAbsent(f.field(), k -> new int[2]);
                pf[1]++;
                if (f.correct()) pf[0]++;
            }
        }
        return new ExtractionEvalScorecard(model, scores.size(), casePassed,
                fieldTotal, fieldCorrect, nullTotal, nullCorrect, perField, scores);
    }

    double fieldAccuracy()  { return fieldTotal == 0 ? 1.0 : (double) fieldCorrect / fieldTotal; }
    double nullDiscipline() { return nullTotal == 0 ? 1.0 : (double) nullCorrect / nullTotal; }
    double casePassRate()   { return caseTotal == 0 ? 1.0 : (double) casePassed / caseTotal; }

    String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== 자연어 추출 eval (n=").append(caseTotal)
                .append(", model=").append(model).append(") ===\n");
        sb.append(String.format("  필드 정확도      : %3d/%-3d  (%.1f%%)%n",
                fieldCorrect, fieldTotal, fieldAccuracy() * 100));
        sb.append(String.format("  null 원칙 준수   : %3d/%-3d  (%.1f%%)   ← 언급 없는 필드를 추측하지 않음%n",
                nullCorrect, nullTotal, nullDiscipline() * 100));
        sb.append(String.format("  케이스 전체 통과 : %3d/%-3d  (%.1f%%)%n",
                casePassed, caseTotal, casePassRate() * 100));

        sb.append("\n  실패 케이스:\n");
        boolean anyFail = false;
        for (CaseScore cs : scores) {
            if (cs.passed()) continue;
            anyFail = true;
            if (!cs.statusOk()) {
                sb.append("    [").append(cs.id()).append("] 추출 상태 불일치\n");
            }
            for (FieldResult f : cs.fields()) {
                if (f.correct()) continue;
                sb.append(String.format("    [%s] %s: 기대 %s, 실제 %s%n",
                        cs.id(), f.field(), display(f.expected()), display(f.actual())));
            }
        }
        if (!anyFail) sb.append("    (없음)\n");

        sb.append("\n  필드별:\n");
        perField.forEach((field, pf) ->
                sb.append(String.format("    %-20s %d/%d%n", field, pf[0], pf[1])));
        return sb.toString();
    }

    private static String display(Object o) {
        return o == null ? "null" : o.toString();
    }
}
