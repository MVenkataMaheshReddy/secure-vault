package com.securevault.service;

import com.securevault.entity.Document;
import com.securevault.entity.DocumentVersion;
import com.securevault.entity.OcrStatus;
import com.securevault.entity.User;
import com.securevault.repository.DocumentVersionRepository;
import com.securevault.service.ocr.CompositeTextExtractionService;
import com.securevault.service.ocr.DocxTextExtractionService;
import com.securevault.service.ocr.ImageTextExtractionService;
import com.securevault.service.ocr.OcrProcessingService;
import com.securevault.service.ocr.PdfTextExtractionService;
import com.securevault.service.ocr.TesseractOcrService;
import com.securevault.service.ocr.TextExtractionService;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TextExtractionServiceTest {

    @Mock
    private TesseractOcrService tesseractOcrService;

    @Mock
    private DocumentVersionRepository documentVersionRepository;

    @TempDir
    Path tempDir;

    private PdfTextExtractionService pdfExtractor;
    private DocxTextExtractionService docxExtractor;
    private ImageTextExtractionService imageExtractor;
    private CompositeTextExtractionService compositeService;
    private OcrProcessingService ocrProcessingService;

    @BeforeEach
    void setUp() {
        pdfExtractor = new PdfTextExtractionService(tesseractOcrService);
        docxExtractor = new DocxTextExtractionService();
        imageExtractor = new ImageTextExtractionService(tesseractOcrService);

        List<TextExtractionService> extractors = List.of(pdfExtractor, docxExtractor, imageExtractor);
        compositeService = new CompositeTextExtractionService(extractors);
        ocrProcessingService = new OcrProcessingService(documentVersionRepository, compositeService);
    }

    @Test
    void testPdfTextStripperExtractsEmbeddedDigitalTextWithoutOcr() throws Exception {
        // Create digital PDF with embedded text containing "CGPA: 9.06"
        Path pdfPath = tempDir.resolve("grade_sheet.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(doc, page)) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 12);
                contentStream.newLineAtOffset(100, 700);
                contentStream.showText("Academic Transcript - CGPA: 9.06 Completed");
                contentStream.endText();
            }
            doc.save(pdfPath.toFile());
        }

        assertTrue(pdfExtractor.supports("application/pdf", "grade_sheet.pdf"));
        String extracted = pdfExtractor.extractText(pdfPath);

        assertNotNull(extracted);
        assertTrue(extracted.contains("CGPA: 9.06"));
        // Tesseract OCR should NOT have been invoked because embedded text was present
        verify(tesseractOcrService, never()).doOcr(any(BufferedImage.class));
    }

    @Test
    void testPdfWithoutEmbeddedTextFallsBackToPageRenderingAndOcr() throws Exception {
        // Create empty/scanned PDF without text stream
        Path blankPdf = tempDir.resolve("scanned.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            doc.save(blankPdf.toFile());
        }

        when(tesseractOcrService.doOcr(any(BufferedImage.class))).thenReturn("Scanned Result: CGPA: 9.06");

        String extracted = pdfExtractor.extractText(blankPdf);
        assertNotNull(extracted);
        assertTrue(extracted.contains("CGPA: 9.06"));
        verify(tesseractOcrService, atLeastOnce()).doOcr(any(BufferedImage.class));
    }

    @Test
    void testDocxTextExtractionViaApachePoi() throws Exception {
        Path docxPath = tempDir.resolve("test_document.docx");
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream fos = new FileOutputStream(docxPath.toFile())) {
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText("Confidential Project Report: SecureVault System");
            doc.write(fos);
        }

        assertTrue(docxExtractor.supports("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "test_document.docx"));
        String text = docxExtractor.extractText(docxPath);

        assertNotNull(text);
        assertTrue(text.contains("Confidential Project Report: SecureVault System"));
    }

    @Test
    void testImageTextExtractionViaTesseract() throws Exception {
        Path imagePath = tempDir.resolve("id_card.png");
        Files.write(imagePath, new byte[]{1, 2, 3, 4});

        when(tesseractOcrService.doOcr(any(File.class))).thenReturn("License Number: ABC-12345");

        assertTrue(imageExtractor.supports("image/png", "id_card.png"));
        String text = imageExtractor.extractText(imagePath);

        assertEquals("License Number: ABC-12345", text);
        verify(tesseractOcrService).doOcr(imagePath.toFile());
    }

    @Test
    void testOcrProcessingServiceMarksDoneOnSuccess() throws Exception {
        Path pdfPath = tempDir.resolve("doc.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Extracted OCR Content with CGPA: 9.06");
                cs.endText();
            }
            doc.save(pdfPath.toFile());
        }

        User owner = new User("Alice", "alice@example.com", "hash");
        Document doc = new Document("Grade Sheet", "doc.pdf", "ACADEMIC", "Transcript", owner);
        doc.setId(10L);

        DocumentVersion version = new DocumentVersion(doc, 1, pdfPath.toString(), Files.size(pdfPath),
                "application/pdf", "checksum123", owner, "v1");
        version.setId(100L);
        version.setOcrStatus(OcrStatus.PENDING);

        when(documentVersionRepository.findById(100L)).thenReturn(Optional.of(version));
        when(documentVersionRepository.save(any(DocumentVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        ocrProcessingService.processOcr(100L);

        assertEquals(OcrStatus.DONE, version.getOcrStatus());
        assertNotNull(version.getOcrText());
        assertTrue(version.getOcrText().contains("CGPA: 9.06"));
    }

    @Test
    void testOcrProcessingServiceMarksSkippedForUnsupportedFormat() {
        Path txtPath = tempDir.resolve("readme.txt");
        try {
            Files.writeString(txtPath, "Plain text");
        } catch (Exception ignored) {}

        User owner = new User("Alice", "alice@example.com", "hash");
        Document doc = new Document("Readme", "readme.txt", "DOCS", "Notes", owner);
        doc.setId(11L);

        DocumentVersion version = new DocumentVersion(doc, 1, txtPath.toString(), 10L,
                "text/plain", "checksumTxt", owner, "v1");
        version.setId(101L);

        when(documentVersionRepository.findById(101L)).thenReturn(Optional.of(version));
        when(documentVersionRepository.save(any(DocumentVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        ocrProcessingService.processOcr(101L);

        assertEquals(OcrStatus.SKIPPED, version.getOcrStatus());
    }

    @Test
    void testOcrProcessingServiceMarksFailedWhenFileMissingOrExtractorFails() {
        User owner = new User("Alice", "alice@example.com", "hash");
        Document doc = new Document("Missing", "missing.pdf", "DOCS", "Notes", owner);
        doc.setId(12L);

        DocumentVersion version = new DocumentVersion(doc, 1, tempDir.resolve("non_existent.pdf").toString(), 10L,
                "application/pdf", "chk", owner, "v1");
        version.setId(102L);

        when(documentVersionRepository.findById(102L)).thenReturn(Optional.of(version));
        when(documentVersionRepository.save(any(DocumentVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        ocrProcessingService.processOcr(102L);

        assertEquals(OcrStatus.FAILED, version.getOcrStatus());
    }
}
