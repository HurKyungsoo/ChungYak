package com.portfolio.chungyak.external;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 표를 행 단위 문장으로 펴는 추출 (#42) — PDFBox 로 괘선 표를 직접 그려 확인한다.
 * 좌표는 PDF 기준(왼쪽 아래 원점). 한글 글꼴을 싣지 않으려고 글은 영문으로 쓴다 — 칸·머리글 논리는 같다.
 */
class TableAwareTextStripperTest {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    /** x 경계·y 경계로 격자를 그린다. skip 은 그리지 않을 [가로선 y 인덱스, x 시작 인덱스] (합친 칸 만들기) */
    private static void grid(PDPageContentStream cs, float[] xs, float[] ys, boolean rightBorder,
                             int[][] skipH) throws IOException {
        for (int j = 0; j < ys.length; j++) {
            for (int i = 0; i < xs.length - 1; i++) {
                boolean skip = false;
                for (int[] s : skipH) if (s[0] == j && s[1] == i) skip = true;
                if (skip) continue;
                cs.moveTo(xs[i], ys[j]);
                cs.lineTo(xs[i + 1], ys[j]);
            }
        }
        for (int i = 0; i < xs.length; i++) {
            if (!rightBorder && i == xs.length - 1) continue;
            cs.moveTo(xs[i], ys[0]);
            cs.lineTo(xs[i], ys[ys.length - 1]);
        }
        cs.stroke();
    }

    private static void text(PDPageContentStream cs, float x, float y, String s) throws IOException {
        cs.beginText();
        cs.setFont(FONT, 10);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }

    private static String extract(PDDocument doc) throws IOException {
        return new TableAwareTextStripper().getText(doc);
    }

    @Test
    @DisplayName("머리글이 있는 표는 값마다 열 이름을 붙이고, 여러 행에 걸친 칸은 걸친 행마다 되풀이한다")
    void labelsValuesAndRepeatsMergedCell() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            float[] xs = {50, 150, 250, 350};
            float[] ys = {700, 680, 660, 640};   // 위에서 아래로: 머리글 / A 행 / B 행
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                text(cs, 50, 720, "Before table");
                // Deposit 열의 A·B 행은 합친 칸 — 둘 사이 가로선(y=660, 셋째 열)을 그리지 않는다
                grid(cs, xs, ys, true, new int[][]{{2, 2}});
                text(cs, 55, 686, "Type");
                text(cs, 155, 686, "Price");
                text(cs, 255, 686, "Deposit");
                text(cs, 55, 666, "A");
                text(cs, 155, 666, "100");
                text(cs, 255, 656, "10");   // 합친 칸 가운데
                text(cs, 55, 646, "B");
                text(cs, 155, 646, "200");
                text(cs, 50, 600, "After table");
            }

            String out = extract(doc);

            assertThat(out).contains("Type | Price | Deposit");
            assertThat(out).contains("Type: A · Price: 100 · Deposit: 10");
            assertThat(out).contains("Type: B · Price: 200 · Deposit: 10");   // 합친 칸을 B 행에도
            // 표 밖 본문은 그대로, 표는 제자리(앞 글과 뒤 글 사이)에
            assertThat(out.indexOf("Before table")).isLessThan(out.indexOf("Type: A"));
            assertThat(out.indexOf("Type: B")).isLessThan(out.indexOf("After table"));
            // 표 칸 글자를 본문에 한 번 더 쓰지 않는다
            assertThat(out.split("200", -1)).hasSize(2);
        }
    }

    @Test
    @DisplayName("오른쪽 테두리가 없는 표도 마지막 열을 칸으로 읽는다 (공고 2997 <표5> 8인 열)")
    void closesOpenRightBorder() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            float[] xs = {50, 150, 250, 350};
            float[] ys = {700, 680, 660, 640};
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                grid(cs, xs, ys, false, new int[0][]);
                text(cs, 55, 686, "Size");
                text(cs, 155, 686, "Small");
                text(cs, 255, 686, "Large");
                text(cs, 55, 666, "130%");
                text(cs, 155, 666, "9,793,892");
                text(cs, 255, 666, "14,384,265");
                text(cs, 55, 646, "140%");
                text(cs, 155, 646, "10,547,268");
                text(cs, 255, 646, "15,490,747");
            }

            String out = extract(doc);

            // 머리글이 숫자뿐이면(예: "3P") 머리글로 보지 않는다 — 실제 공고문 머리글은 "3인 이하"처럼 한글이 섞인다
            assertThat(out).contains("Size: 130% · Small: 9,793,892 · Large: 14,384,265");
            assertThat(out).contains("Large: 15,490,747");
        }
    }

    @Test
    @DisplayName("괘선이 없는 페이지는 위치순 추출과 같다")
    void noTablesIsPlainText() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                text(cs, 50, 700, "First line");
                text(cs, 50, 680, "Second line");
            }
            assertThat(extract(doc).strip()).isEqualTo("First line\nSecond line".replace("\n", System.lineSeparator()));
        }
    }
}
