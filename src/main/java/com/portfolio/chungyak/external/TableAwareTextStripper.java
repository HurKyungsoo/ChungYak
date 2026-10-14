package com.portfolio.chungyak.external;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 표를 행 단위 문장으로 펴서 쓰는 텍스트 추출기 (#42).
 *
 * 기본 {@link PDFTextStripper} 는 위치순으로 글자를 늘어놓아 표의 칸이 섞인다 — 여러 행에 걸친 칸("362,000천원 이하")이
 * 옆 칸 글자 사이에 끼고, 머리글은 두 줄로 쪼개져 값과 떨어진다. 그래서 검색이 정답 발췌를 넘겨도 LLM 이
 * "공고문에 명시되어 있지 않다"고 답했다(운영 사례: 공고 2997 자산 기준).
 *
 * 이 추출기는 {@link PdfTables} 로 찾은 표 안의 글자는 본문에서 빼고, 표가 있던 자리에 표를 행마다 한 줄로 쓴다.
 * 머리글이 있는 표는 각 값 앞에 열 이름을 붙여 행 하나만 봐도 뜻이 통하게 한다:
 * {@code 주택형(타입): 55A · 구분: 마이너스옵션 · 발코니확장비용(계): 5,554 · 계약금(계약시): 1,000}.
 * 표를 못 찾은 페이지는 기본 추출과 똑같다.
 *
 * 형식은 eval 로 골랐다(2026-10-14, 공고문 6건 같은 PDF, 69문항): 운영 검색(하이브리드 + rerank 상위 20)은
 * 위치순 61 → 열 이름 붙인 행 62 / " | " 만 62 로 같았고, BM25 단독은 43 → 34 / 40 으로 내려갔다(표 글이 늘어 청크가
 * 바뀐다 — BM25 모드는 Voyage 키가 없는 로컬뿐). 행 하나만 발췌돼도 값의 뜻이 붙어 있도록 열 이름 형식을 쓴다.
 * 표 글자를 빼고 남은 빈 줄은 쓰지 않는다 — 빈 줄은 {@code ChunkSplitter} 의 문단 경계라 청크를 잘게 쪼갰다.
 */
class TableAwareTextStripper extends PDFTextStripper {

    /** 여러 행에 걸친 칸을 아래 행마다 되풀이할 최대 길이 — 긴 설명 칸까지 되풀이하면 글이 불어난다 */
    private static final int REPEAT_MAX_CHARS = 40;
    /** 머리글로 보지 않을 표 — 머리글 칸 중 숫자가 든 칸이 이 비율을 넘으면 데이터 행이다 */
    private static final double HEADER_MAX_NUMERIC = 0.5;
    /** 머리글 칸은 짧다 — 이보다 긴 칸이 첫 행에 있으면 다음 페이지로 이어진 표의 본문 행이다 */
    private static final int HEADER_MAX_CHARS = 40;

    private List<PdfTables.Table> tables = List.of();
    private final List<TextPosition> pageChars = new ArrayList<>();
    private int nextTable;
    private Map<PdfTables.Table, List<String>> rendered;
    /** 이 줄에 쓴 글이 있나 — 표 글자를 빼고 남은 빈 줄이 문단 경계로 잘못 읽히지 않게(청크가 엉뚱하게 갈린다) */
    private boolean lineHasText;

    TableAwareTextStripper() throws IOException {
        super();
        setSortByPosition(true);
    }

    @Override
    protected void startPage(PDPage page) throws IOException {
        super.startPage(page);
        pageChars.clear();
        nextTable = 0;
        rendered = null;
        try {
            tables = PdfTables.find(page).stream()
                    .filter(t -> t.cells().size() >= 2)   // 칸 하나짜리 상자(유의사항 테두리)는 본문 그대로 둔다
                    .toList();
        } catch (RuntimeException e) {
            tables = List.of();   // 표를 못 읽어도 본문 추출은 계속한다
        }
    }

    @Override
    protected void processTextPosition(TextPosition text) {
        if (!tables.isEmpty()) pageChars.add(text);
        super.processTextPosition(text);
    }

    @Override
    protected void writeString(String text, List<TextPosition> positions) throws IOException {
        if (tables.isEmpty() || positions.isEmpty()) {
            super.writeString(text, positions);
            lineHasText = true;
            return;
        }
        TextPosition first = positions.get(0);
        double y = first.getYDirAdj();
        writeTablesAbove(y);
        if (inTable(first)) return;   // 표 글자는 표 자리에서 행 단위로 쓴다
        super.writeString(text, positions);
        lineHasText = true;
    }

    @Override
    protected void writeLineSeparator() throws IOException {
        if (!tables.isEmpty() && !lineHasText) return;   // 표 글자만 있던 줄은 줄바꿈도 쓰지 않는다
        super.writeLineSeparator();
        lineHasText = false;
    }

    @Override
    protected void endPage(PDPage page) throws IOException {
        writeTablesAbove(Double.MAX_VALUE);
        super.endPage(page);
    }

    private boolean inTable(TextPosition t) {
        double cx = t.getXDirAdj() + t.getWidthDirAdj() / 2;
        double cy = t.getYDirAdj() - t.getHeightDir() / 2;
        // 표 테두리 안이어도 어느 칸에도 안 든 글자(칸이 닫히지 않은 안쪽 표)는 본문으로 쓴다 —
        // 표 자리에서 빼기만 하고 칸으로 다시 쓰지 않으면 글이 사라진다(공고 3018 "입주 순위에 따라 차등").
        for (PdfTables.Table table : tables) {
            if (!table.contains(cx, cy)) continue;
            for (PdfTables.Cell cell : table.cells()) {
                if (cell.contains(cx, cy)) return true;
            }
        }
        return false;
    }

    private void writeTablesAbove(double y) throws IOException {
        while (nextTable < tables.size() && tables.get(nextTable).y1() < y) {
            PdfTables.Table table = tables.get(nextTable++);
            List<String> lines = renderedTables().get(table);
            if (lines == null || lines.isEmpty()) continue;
            lineHasText = true;
            writeLineSeparator();
            for (String line : lines) {
                writeString(line);
                lineHasText = true;
                writeLineSeparator();
            }
        }
    }

    private Map<PdfTables.Table, List<String>> renderedTables() {
        if (rendered == null) {
            rendered = new LinkedHashMap<>();
            for (PdfTables.Table table : tables) rendered.put(table, render(table, pageChars));
        }
        return rendered;
    }

    // === 표 → 행 ===

    /** 칸 하나의 글 — 칸 안에서 줄을 나눠 위→아래, 왼→오른쪽으로 잇는다. 줄바꿈은 공백 하나로. */
    static String cellText(PdfTables.Cell cell, List<TextPosition> chars) {
        List<TextPosition> in = new ArrayList<>();
        for (TextPosition t : chars) {
            double cx = t.getXDirAdj() + t.getWidthDirAdj() / 2;
            double cy = t.getYDirAdj() - t.getHeightDir() / 2;
            if (cell.contains(cx, cy)) in.add(t);
        }
        if (in.isEmpty()) return "";
        in.sort(Comparator.comparingDouble(TextPosition::getYDirAdj));
        List<List<TextPosition>> lines = new ArrayList<>();
        for (TextPosition t : in) {
            List<TextPosition> line = lines.isEmpty() ? null : lines.get(lines.size() - 1);
            if (line != null && Math.abs(line.get(0).getYDirAdj() - t.getYDirAdj()) <= Math.max(2, t.getHeightDir() * 0.5)) {
                line.add(t);
            } else {
                lines.add(new ArrayList<>(List.of(t)));
            }
        }
        StringBuilder sb = new StringBuilder();
        for (List<TextPosition> line : lines) {
            line.sort(Comparator.comparingDouble(TextPosition::getXDirAdj));
            if (sb.length() > 0) sb.append(' ');
            TextPosition prev = null;
            for (TextPosition t : line) {
                // 굵은 글씨를 두 번 찍은 겹친 글자는 하나만
                if (prev != null && prev.getUnicode().equals(t.getUnicode())
                        && Math.abs(prev.getXDirAdj() - t.getXDirAdj()) < 1) continue;
                if (prev != null && t.getXDirAdj() - (prev.getXDirAdj() + prev.getWidthDirAdj()) > t.getWidthOfSpace() * 0.6
                        && sb.charAt(sb.length() - 1) != ' ') {
                    sb.append(' ');
                }
                sb.append(t.getUnicode());
                prev = t;
            }
        }
        return sb.toString().replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }

    /**
     * 표 하나를 행 단위 줄로.
     * <ul>
     *   <li>열이 둘이면 "이름: 값" 표로 본다(공급위치 | 인천 … 같은 항목 표).</li>
     *   <li>첫 행이 글자 머리글이면 값마다 열 이름을 붙인다. 첫 행에 여러 열을 합친 칸이 있으면 둘째 행까지 머리글
     *       ("실별 구분 가격" 아래 "거실·침실1 …").</li>
     *   <li>그 밖에는 칸을 " | " 로 잇는다.</li>
     * </ul>
     * 여러 행에 걸친 칸은 짧으면 걸친 행마다 되풀이한다 — 행 하나만 발췌돼도 값이 붙어 있게.
     */
    static List<String> render(PdfTables.Table table, List<TextPosition> chars) {
        List<PdfTables.Cell> cells = table.cells();
        Map<PdfTables.Cell, String> text = new LinkedHashMap<>();
        for (PdfTables.Cell c : cells) text.put(c, cellText(c, chars));
        if (text.values().stream().allMatch(String::isEmpty)) return List.of();

        double[] ys = boundaries(cells.stream().flatMap(c -> java.util.stream.Stream.of(c.y1(), c.y2())).toList());
        double[] xs = boundaries(cells.stream().flatMap(c -> java.util.stream.Stream.of(c.x1(), c.x2())).toList());
        int rows = ys.length - 1;
        int cols = xs.length - 1;

        // 행 r 에 걸친 칸들(왼→오른쪽)
        List<List<PdfTables.Cell>> rowCells = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            double mid = (ys[r] + ys[r + 1]) / 2;
            rowCells.add(cells.stream().filter(c -> c.y1() <= mid && c.y2() >= mid)
                    .sorted(Comparator.comparingDouble(PdfTables.Cell::x1)).toList());
        }

        int headerRows = headerRows(rowCells, text, rows, cols, xs);
        List<String> out = new ArrayList<>();
        if (headerRows > 0) {
            for (int r = 0; r < headerRows; r++) out.add(joinPlain(rowCells.get(r), text, ys[r]));
        }
        for (int r = headerRows; r < rows; r++) {
            List<String> parts = new ArrayList<>();
            String lastLabel = null;
            boolean any = false;
            boolean labelled = headerRows > 0;
            for (PdfTables.Cell c : rowCells.get(r)) {
                String value = text.get(c);
                boolean startsHere = Math.abs(c.y1() - ys[r]) <= PdfTables.TOL;
                if (startsHere && !value.isEmpty()) any = true;
                if (!startsHere && value.length() > REPEAT_MAX_CHARS) continue;
                if (value.isEmpty()) continue;
                if (cols == 2 && headerRows == 0) {
                    parts.add(value);
                } else if (labelled) {
                    String label = headerLabel(c, rowCells, text, headerRows);
                    if (!label.isEmpty() && label.equals(lastLabel)) {
                        // 여러 열을 덮는 머리글 아래 값들은 이름을 한 번만 — "건축물 종류: 주택 공동주택"
                        parts.set(parts.size() - 1, parts.get(parts.size() - 1) + " " + value);
                        continue;
                    }
                    parts.add(label.isEmpty() || label.equals(value) ? value : label + ": " + value);
                    lastLabel = label;
                } else {
                    parts.add(value);
                }
            }
            if (!any || parts.isEmpty()) continue;   // 걸친 칸만 되풀이되는 줄은 쓰지 않는다
            String sep = labelled ? " · " : (cols == 2 && headerRows == 0 ? ": " : " | ");
            out.add(String.join(sep, parts));
        }
        return out;
    }

    /** 금액·세대수처럼 숫자만 있는 칸 — "47,793", "10%", "-24,000,000" */
    private static boolean amount(String s) {
        return s.matches("[-+]?[\\d,.]+%?");
    }

    /**
     * 머리글 행 수 — 0~3.
     * 첫 행은 짧은 글자 칸이어야 머리글이다(긴 칸이 있으면 앞 페이지에서 이어진 표의 본문 행).
     * 숫자 표는 숫자만 있는 칸이 처음 나오는 행 앞까지를 머리글로 본다 — 머리글이 여러 단인 표가 많다
     * ("임대조건 > 임대보증금 > 합계·계약금·잔금", "계약금 10% > 계약시").
     */
    private static int headerRows(List<List<PdfTables.Cell>> rowCells, Map<PdfTables.Cell, String> text,
                                  int rows, int cols, double[] xs) {
        if (rows < 3 || cols < 3) return 0;
        List<PdfTables.Cell> first = rowCells.get(0);
        long nonEmpty = first.stream().filter(c -> !text.get(c).isEmpty()).count();
        long numeric = first.stream().filter(c -> text.get(c).matches(".*\\d.*") && !text.get(c).matches(".*[가-힣].*")).count();
        if (nonEmpty < 2 || numeric > nonEmpty * HEADER_MAX_NUMERIC) return 0;
        if (first.stream().anyMatch(c -> text.get(c).length() > HEADER_MAX_CHARS)) return 0;

        boolean numericTable = text.values().stream().anyMatch(TableAwareTextStripper::amount);
        if (!numericTable) return 1;
        int header = 1;
        while (header < Math.min(3, rows - 1)) {
            final double top = rowTop(rowCells.get(header));
            List<PdfTables.Cell> starting = rowCells.get(header).stream()
                    .filter(c -> Math.abs(c.y1() - top) <= PdfTables.TOL).toList();
            boolean textOnly = !starting.isEmpty() && starting.stream().noneMatch(c -> amount(text.get(c)))
                    && starting.stream().allMatch(c -> text.get(c).length() <= HEADER_MAX_CHARS);
            if (!textOnly) break;
            header++;
        }
        return header;
    }

    private static double rowTop(List<PdfTables.Cell> cells) {
        return cells.stream().mapToDouble(PdfTables.Cell::y1).max().orElse(0);
    }

    private static int spans(PdfTables.Cell c, double[] xs) {
        int n = 0;
        for (int i = 0; i < xs.length - 1; i++) {
            double mid = (xs[i] + xs[i + 1]) / 2;
            if (c.x1() <= mid && c.x2() >= mid) n++;
        }
        return n;
    }

    /** 값 칸 위에 있는 머리글 칸들의 글(위 → 아래) */
    private static String headerLabel(PdfTables.Cell value, List<List<PdfTables.Cell>> rowCells,
                                      Map<PdfTables.Cell, String> text, int headerRows) {
        double mid = (value.x1() + value.x2()) / 2;
        TreeSet<String> seen = new TreeSet<>();
        List<String> parts = new ArrayList<>();
        for (int r = 0; r < headerRows; r++) {
            for (PdfTables.Cell h : rowCells.get(r)) {
                if (h.x1() <= mid && h.x2() >= mid && h != value) {
                    String t = text.get(h);
                    if (!t.isEmpty() && seen.add(t)) parts.add(t);
                }
            }
        }
        return String.join(" ", parts);
    }

    private static String joinPlain(List<PdfTables.Cell> cells, Map<PdfTables.Cell, String> text, double rowTop) {
        return cells.stream()
                .filter(c -> Math.abs(c.y1() - rowTop) <= PdfTables.TOL)
                .map(text::get).filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" | "));
    }

    /** 가까운 좌표를 하나로 묶은 정렬된 경계값 */
    private static double[] boundaries(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compare);
        List<Double> out = new ArrayList<>();
        for (double v : sorted) {
            if (out.isEmpty() || v - out.get(out.size() - 1) > PdfTables.TOL) out.add(v);
        }
        return out.stream().mapToDouble(Double::doubleValue).toArray();
    }
}
