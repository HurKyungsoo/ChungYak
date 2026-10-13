package com.portfolio.chungyak.llm;

import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.rule.EligibilityDecision;
import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;
import com.portfolio.chungyak.rule.EligibilityEngine.UnitMatch;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 요약이 확정된 판정과 모순되는지 사후 검사.
 *
 * 순수 함수. LLM 이 "새 판정을 하지 말라"는 지시를 어기고 없는 자격을 지어내거나, 있는 자격을 없다고 하거나,
 * 근거에 없는 숫자를 만들어내는 경우를 잡는다. 세 가지를 본다:
 * <ol>
 *   <li><b>공고 전체</b> — 유형을 특정하지 않은 단정("신청 가능한 특별공급이 없습니다")이 {@link MatchResult#hasAnyMatch()} 와 반대인가</li>
 *   <li><b>유형별</b> — 문장이 특정 유형을 가리키면 그 유형의 판정과 맞는가. 유형별로 "다자녀가구는 자격이 없습니다"라고
 *       쓰는 건 사실이면 정상이다 — 예전 검사는 이런 문장을 "공고 전체 불가 단정"으로 오탐했다</li>
 *   <li><b>숫자</b> — 요약의 모든 숫자가 근거(또는 근거에서 계산되는 값: 개월→년, 차이, 합계)에 있는가</li>
 * </ol>
 *
 * 문장을 절(clause) 단위로 쪼개 보고, 유형 이름이 없는 절("따라서 신청할 수 없습니다")은 바로 앞에 나온 유형을 가리키는 것으로 본다.
 * 한 절에 긍정·부정이 섞여 있거나 어느 유형인지 알 수 없으면 판단을 보류한다(오탐으로 멀쩡한 요약을 버리는 쪽이 더 나쁘지 않지만
 * 대체 문장보다 나은 요약을 잃게 되므로, 확실할 때만 모순으로 본다).
 */
final class ContradictionCheck {

    private ContradictionCheck() {}

    /** 공고 전체에 대해 "신청할 수 있는 특별공급이 없다"는 명시적 단정 */
    private static final List<String> OVERALL_CANNOT = List.of(
            "신청가능한특별공급이없", "신청가능한특별공급유형이없", "신청가능한유형이없",
            "신청하실수있는특별공급이없", "신청할수있는특별공급이없", "해당하는특별공급이없");

    /**
     * 위 목록의 일반형 — "신청 가능 유형: 없음", "신청 가능한 특별공급은 없습니다" 처럼 '신청 가능'이 들어 있어도
     * 뒤에 '없'이 오면 부정이다 (긍정 어간 "신청가능" 에 걸리면 안 된다).
     */
    private static final Pattern OVERALL_CANNOT_RE = Pattern.compile(
            "신청(?:하실수있는|할수있는|가능한?)(?:특별공급)?(?:유형)?(?:이|은|[:：])?없");

    /** "신청할 수 없다" — 유형이 붙으면 그 유형이 실제로 신청 가능(물량 있는 주택형)이면 모순 */
    private static final List<String> NEG_APPLY = List.of(
            "신청할수없", "신청하실수없", "신청이불가", "신청이어렵", "신청불가");

    /** "자격이 없다" — 유형이 붙으면 그 유형이 자격 있음(또는 판정 보류)이면 모순 */
    private static final List<String> NEG_QUAL = List.of(
            "자격이없", "자격을충족하지못", "자격이안", "자격미달", "자격이부족");

    /**
     * 조건을 <b>설명</b>하는 말 — "세대주만 신청할 수 있는 제한", "신청할 수 있다는 조건" 은 자격이 있다는 단정이 아니다.
     * 뒤에 "특별공급·유형·공급·주택형" 이 오는 "신청할 수 있는 특별공급" 은 단정이므로 남긴다.
     */
    private static final Pattern CONDITION_PHRASE = Pattern.compile(
            "신청(?:하실|할)수있(?:다는|는(?!특별공급|유형|공급|주택형))|신청가능하다는|신청가능한(?!특별공급|유형|공급|주택형)");

    /** 긍정 어간과 함께 나오면 어느 쪽 단정인지 알 수 없는 부정·부족 표현 */
    private static final List<String> HEDGE_WORDS = List.of("않", "못", "없", "부족", "모자");

    private static final List<String> POSITIVE = List.of(
            "자격이됩니다", "자격이있습니다", "자격이되", "자격은충족", "자격을충족", "신청자격을충족",
            "신청가능", "신청하실수있", "신청할수있", "지원가능");

    private static final Pattern CLAUSE_SPLIT = Pattern.compile("[,，;；]|지만|반면|다만|그러나");
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("[.!?。\\n]+");
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern LIST_MARKER = Pattern.compile("(?m)^\\s*\\d+[.)]\\s");

    /**
     * @return 모순이 있으면 그 설명, 없으면 empty
     */
    static Optional<String> check(MatchResult result, String explanation) {
        if (explanation == null || explanation.isBlank()) {
            return Optional.of("요약이 비어 있음");
        }
        Optional<String> invented = inventedNumber(result, explanation);
        if (invented.isPresent()) return invented;
        Optional<String> misattributed = misattributedNumber(result, explanation);
        if (misattributed.isPresent()) return misattributed;
        return claimConflict(result, explanation);
    }

    private static final Pattern MARKDOWN = Pattern.compile("\\*\\*|__|`|(?m)^\\s*(?:#+|[-*•]|\\d+[.)])\\s");
    static final int MAX_SENTENCES = 3;

    /**
     * 형식 검사 — "다음에 할 일" 한두 문장(#20)이어야 한다. 마크다운이 섞이거나 {@value #MAX_SENTENCES}문장을 넘으면
     * 판정과 모순은 아니어도 화면에 쓸 수 없으므로 재생성 대상이다. AI 출력에만 쓴다(규칙 문장은 형식이 고정).
     */
    static Optional<String> formatProblem(String explanation) {
        if (explanation == null || explanation.isBlank()) return Optional.of("요약이 비어 있음");
        if (MARKDOWN.matcher(explanation).find()) return Optional.of("마크다운이 섞임");
        if (explanation.contains(ExplanationFacts.MONEY_MASK)) return Optional.of("가린 금액 표시(" + ExplanationFacts.MONEY_MASK + ")를 그대로 옮김");
        long sentences = SENTENCE_SPLIT.splitAsStream(explanation.strip()).filter(x -> !x.isBlank()).count();
        if (sentences > MAX_SENTENCES) return Optional.of("문장이 " + sentences + "개 — 최대 " + MAX_SENTENCES + "개");
        return Optional.empty();
    }

    // ── 판정 주장 ─────────────────────────────────────────────────────────────────

    private static Optional<String> claimConflict(MatchResult result, String explanation) {
        Set<SpecialSupplyType> applicable = EnumSet.noneOf(SpecialSupplyType.class);
        for (UnitMatch m : result.matches()) applicable.addAll(m.applicableTypes());
        Map<SpecialSupplyType, EligibilityDecision> decisions = result.decisions();

        String cleaned = explanation.replaceAll("[*#`>_~|]", " ");
        Set<SpecialSupplyType> last = new LinkedHashSet<>();   // 직전에 말한 유형 — 유형 없는 절이 가리키는 대상

        for (String sentence : SENTENCE_SPLIT.split(cleaned)) {
            Set<SpecialSupplyType> pending = new LinkedHashSet<>();   // 서술어 없이 유형만 나열된 절 ("신혼부부, 생애최초는 …")
            for (String clause : CLAUSE_SPLIT.split(sentence)) {
                String t = clause.replaceAll("\\s+", "")
                        .replace("신청가능성", "").replace("당첨가능성", "");
                if (t.isEmpty()) continue;

                Set<SpecialSupplyType> labels = mentionedTypes(t);
                boolean overallNeg = containsAny(t, OVERALL_CANNOT) || OVERALL_CANNOT_RE.matcher(t).find();
                boolean negApply = containsAny(t, NEG_APPLY);
                boolean negQual = containsAny(t, NEG_QUAL);
                boolean pos = !overallNeg && containsAny(CONDITION_PHRASE.matcher(t).replaceAll(""), POSITIVE);

                if (!overallNeg && !negApply && !negQual && !pos) {
                    // 서술어가 없다. "신혼부부, 생애최초는 …" 처럼 유형 이름만 있는 절이면 뒤 절이 이 유형들에 대해 말한다.
                    // 다른 내용이 붙은 절("다자녀가구는 자녀가 부족하고")은 다음 절의 유형과 섞이지 않게 넘기지 않는다.
                    String rest = t;
                    for (SpecialSupplyType ty : labels) rest = rest.replace(ty.getLabel(), "");
                    if (!labels.isEmpty() && rest.length() <= 3) pending.addAll(labels);
                    if (!labels.isEmpty()) last = new LinkedHashSet<>(labels);
                    continue;
                }
                boolean negative = overallNeg || negApply || negQual;
                if (pos && (negative || containsAny(t, HEDGE_WORDS))) continue;   // 긍정·부정이 섞였다 — 어느 쪽인지 알 수 없어 보류

                Set<SpecialSupplyType> targets = new LinkedHashSet<>(pending);
                targets.addAll(labels);
                pending.clear();

                // "신청 가능한 특별공급이 없습니다" — 유형을 말하지 않은 공고 전체에 대한 명시적 단정
                if (overallNeg && targets.isEmpty()) {
                    if (result.hasAnyMatch()) {
                        return Optional.of("신청 가능한 주택형이 있는데 요약은 '없다/불가능'이라고 단정함");
                    }
                    continue;
                }
                if (targets.isEmpty()) targets = last;   // "따라서 신청할 수 없습니다" — 바로 앞에 말한 유형에 대한 말
                else last = new LinkedHashSet<>(targets);

                if (targets.isEmpty()) {
                    // 가리킬 유형이 전혀 없다 — 공고 전체에 대한 말로 본다
                    if (negApply && result.hasAnyMatch()) {
                        return Optional.of("신청 가능한 주택형이 있는데 요약은 '없다/불가능'이라고 단정함");
                    }
                    if (pos && !result.hasAnyMatch()) {
                        return Optional.of("신청 가능한 주택형이 없는데 요약은 '가능하다'고 단정함");
                    }
                    continue;
                }

                for (SpecialSupplyType type : targets) {
                    EligibilityDecision d = decisions.get(type);
                    if (d == null) continue;
                    String label = type.getLabel();
                    if (negQual && (d.isEligible() || d.isUndetermined())) {
                        return Optional.of("'" + label + "' 은(는) " + (d.isEligible() ? "자격이 있는데" : "판정 보류인데")
                                + " 요약은 '자격 없음'이라고 단정함");
                    }
                    if ((negApply || overallNeg) && applicable.contains(type)) {
                        return Optional.of("'" + label + "' 은(는) 신청 가능한데 요약은 '신청할 수 없다'고 단정함");
                    }
                    if (pos && !d.isEligible()) {
                        return Optional.of("'" + label + "' 은(는) "
                                + (d.isUndetermined() ? "판정 보류" : "자격 없음") + "인데 요약은 '가능하다'고 단정함");
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** 절에 나온 유형 이름 — "기타"는 일반 단어로도 쓰여서 제외한다. */
    private static Set<SpecialSupplyType> mentionedTypes(String compactClause) {
        Set<SpecialSupplyType> found = new LinkedHashSet<>();
        for (SpecialSupplyType type : SpecialSupplyType.values()) {
            if (type == SpecialSupplyType.ETC) continue;
            if (compactClause.contains(type.getLabel())) found.add(type);
        }
        return found;
    }

    private static boolean containsAny(String haystack, List<String> needles) {
        return needles.stream().anyMatch(haystack::contains);
    }

    // ── 숫자 ─────────────────────────────────────────────────────────────────────

    /**
     * 요약에 근거(ExplanationFacts)에 없는 숫자가 있으면 지어낸 값으로 본다. 근거에서 계산되는 값은 허용한다:
     * 같은 줄 숫자끼리의 차이("12개월 모자람"), 개월→년, 유형별·전체 세대 합계.
     */
    private static Optional<String> inventedNumber(MatchResult result, String explanation) {
        Set<Integer> allowed = allowedNumbers(result);
        allowed.add(0);   // "0세대"·"0명" — 없다는 뜻이라 지어낸 사실이 되지 않는다

        String text = LIST_MARKER.matcher(explanation).replaceAll(" ").replace(",", "");
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            if (m.group().length() > 9) return Optional.of("요약에 근거에 없는 숫자 " + m.group());
            int n = Integer.parseInt(m.group());
            if (!allowed.contains(n)) return Optional.of("요약에 근거에 없는 숫자 " + n);
        }
        return Optional.empty();
    }

    /**
     * 보완 힌트의 숫자를 다른 유형에 붙였는가 — "청약통장을 3개월 더"가 생애최초의 힌트인데 요약이
     * "신혼부부는 3개월 더"라고 쓰면, 숫자는 근거에 있어도 엉뚱한 유형에 대한 조언이 된다.
     * 근거의 "- [유형] …" 줄에서 나온 숫자만 본다. 같은 숫자가 유형 없는 줄(세대수 등)에도 있으면 판단을 보류한다.
     */
    private static Optional<String> misattributedNumber(MatchResult result, String explanation) {
        Map<SpecialSupplyType, Set<Integer>> byType = new java.util.EnumMap<>(SpecialSupplyType.class);
        Set<Integer> general = new LinkedHashSet<>();
        for (String line : ExplanationFacts.format(result).replace(",", "").split("\\n")) {
            Matcher tag = TYPE_TAG.matcher(line);
            SpecialSupplyType owner = tag.find() ? typeOfLabel(tag.group(1)) : null;
            Set<Integer> nums = lineNumbers(line);
            if (owner == null) general.addAll(nums);
            else byType.computeIfAbsent(owner, k -> new LinkedHashSet<>()).addAll(nums);
        }
        Set<Integer> attributed = new LinkedHashSet<>();
        byType.values().forEach(attributed::addAll);
        attributed.removeAll(general);
        if (attributed.isEmpty()) return Optional.empty();

        for (String sentence : SENTENCE_SPLIT.split(explanation.replace(",", ""))) {
            Set<SpecialSupplyType> types = mentionedTypes(sentence.replaceAll("\\s+", ""));
            if (types.isEmpty()) continue;
            Matcher m = NUMBER.matcher(sentence);
            while (m.find()) {
                if (m.group().length() > 9) continue;
                int n = Integer.parseInt(m.group());
                if (!attributed.contains(n)) continue;
                boolean owned = types.stream().anyMatch(t -> byType.getOrDefault(t, Set.of()).contains(n));
                if (!owned) {
                    return Optional.of("숫자 " + n + " 은(는) "
                            + types.stream().map(SpecialSupplyType::getLabel).toList() + " 의 근거가 아님 — 다른 유형의 보완 수치");
                }
            }
        }
        return Optional.empty();
    }

    private static final Pattern TYPE_TAG = Pattern.compile("^- \\[([^\\]]+)]");

    private static SpecialSupplyType typeOfLabel(String label) {
        for (SpecialSupplyType t : SpecialSupplyType.values()) if (t.getLabel().equals(label)) return t;
        return null;
    }

    /** 한 줄의 숫자 + 그 줄에서 계산되는 값(36개월 → 3년, 같은 줄 숫자끼리의 차이 — "12개월 모자람") */
    private static Set<Integer> lineNumbers(String line) {
        List<Integer> nums = new ArrayList<>();
        Matcher m = NUMBER.matcher(line);
        while (m.find()) {
            if (m.group().length() > 9) continue;
            nums.add(Integer.parseInt(m.group()));
        }
        Set<Integer> out = new LinkedHashSet<>(nums);
        for (int a : nums) {
            if (a >= 12 && a % 12 == 0) out.add(a / 12);
            for (int b : nums) if (a > b) out.add(a - b);
        }
        return out;
    }

    private static Set<Integer> allowedNumbers(MatchResult result) {
        Set<Integer> allowed = new LinkedHashSet<>();
        for (String line : ExplanationFacts.format(result).replace(",", "").split("\\n")) {
            allowed.addAll(lineNumbers(line));
        }
        // 세대 합계 — 유형별(주택형 여러 개 합산)과 전체
        Map<SpecialSupplyType, Integer> perType = new java.util.EnumMap<>(SpecialSupplyType.class);
        for (UnitMatch m : result.matches()) {
            for (SpecialSupplyType type : m.applicableTypes()) {
                perType.merge(type, m.unitType().getSupplyBreakdown().countOf(type), Integer::sum);
            }
        }
        allowed.addAll(perType.values());
        allowed.add(perType.values().stream().mapToInt(Integer::intValue).sum());
        return allowed;
    }
}
