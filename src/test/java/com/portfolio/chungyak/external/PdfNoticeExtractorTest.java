package com.portfolio.chungyak.external;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PDF 본문 추출 — 순수 함수. PDFBox 로 만든 PDF 를 다시 읽어 검증한다(바이너리 픽스처 없이).
 */
class PdfNoticeExtractorTest {

    private final PdfNoticeExtractor extractor = new PdfNoticeExtractor();

    private static byte[] pdf(String... lines) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                cs.beginText();
                cs.newLineAtOffset(50, 750);
                cs.setLeading(14);
                for (String line : lines) {
                    cs.showText(line);
                    cs.newLine();
                }
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("본문이 충분한 PDF -> 텍스트 추출")
    void extractsText() throws Exception {
        byte[] bytes = pdf(
                "Notice of Housing Supply - Section 1 Eligibility",
                "Applicants must be members of a household without any home,",
                "resident in the metropolitan area, with a subscription account",
                "opened at least six months ago and six or more payments made.",
                "Section 2 Special Supply - newlyweds within 7 years of marriage,",
                "multi-child households, first-time buyers and elderly-support types.",
                "Section 3 Schedule - application from 2026-09-17, winners on 2026-10-10.");

        var text = extractor.extract(bytes);

        assertThat(text).isPresent();
        assertThat(text.get()).contains("Eligibility").contains("Special Supply").contains("Schedule");
    }

    @Test
    @DisplayName("PDF 매직바이트가 아니면 (HWP·HTML 오류페이지 등) empty")
    void rejectsNonPdf() {
        assertThat(extractor.extract("<html>error</html>".getBytes())).isEmpty();
        assertThat(extractor.extract(new byte[]{0x50, 0x4B, 0x03, 0x04})).isEmpty();   // ZIP(HWPX) 시그니처
        assertThat(extractor.extract(null)).isEmpty();
        assertThat(extractor.extract(new byte[0])).isEmpty();
    }

    @Test
    @DisplayName("본문이 너무 짧으면 empty (표지만 있는 파일 등)")
    void tooShortIsEmpty() throws Exception {
        assertThat(extractor.extract(pdf("Cover page only"))).isEmpty();
    }

    @Test
    @DisplayName("깨진 PDF 는 예외 없이 empty")
    void corruptPdfIsEmpty() {
        byte[] corrupt = "%PDF-1.6\n<<garbage not a real pdf>>".getBytes();
        assertThat(extractor.extract(corrupt)).isEmpty();
    }
}
