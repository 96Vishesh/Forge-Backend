package com.inn.automate.Service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Service for extracting text content from resume files (PDF, DOCX, TXT)
 */
@Service
@Slf4j
public class ResumeExtractorService {

    /**
     * Extract text content from an uploaded resume file
     */
    public String extractTextFromFile(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IOException("File has no name");
        }

        String lowerFilename = filename.toLowerCase();
        log.info("Extracting text from file: {}", filename);

        String text;
        if (lowerFilename.endsWith(".pdf")) {
            text = extractFromPdf(file.getInputStream());
        } else if (lowerFilename.endsWith(".docx")) {
            text = extractFromDocx(file.getInputStream());
        } else if (lowerFilename.endsWith(".doc")) {
            throw new IOException("DOC format not supported. Please convert to PDF or DOCX.");
        } else if (lowerFilename.endsWith(".txt")) {
            text = new String(file.getBytes());
        } else {
            throw new IOException("Unsupported format. Please use PDF, DOCX, or TXT.");
        }

        // Clean the extracted text
        return cleanText(text);
    }

    private String extractFromPdf(InputStream inputStream) throws IOException {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            
            log.info("Extracted {} characters from PDF", text.length());
            
            if (text.trim().isEmpty()) {
                throw new IOException("PDF is empty or contains only images");
            }
            
            return text;
        }
    }

    private String extractFromDocx(InputStream inputStream) throws IOException {
        try {
            org.apache.poi.xwpf.usermodel.XWPFDocument document = 
                new org.apache.poi.xwpf.usermodel.XWPFDocument(inputStream);
            
            StringBuilder text = new StringBuilder();
            for (org.apache.poi.xwpf.usermodel.XWPFParagraph p : document.getParagraphs()) {
                text.append(p.getText()).append("\n");
            }
            for (org.apache.poi.xwpf.usermodel.XWPFTable table : document.getTables()) {
                for (org.apache.poi.xwpf.usermodel.XWPFTableRow row : table.getRows()) {
                    for (org.apache.poi.xwpf.usermodel.XWPFTableCell cell : row.getTableCells()) {
                        text.append(cell.getText()).append("\t");
                    }
                    text.append("\n");
                }
            }
            document.close();
            
            log.info("Extracted {} characters from DOCX", text.length());
            return text.toString();
        } catch (Exception e) {
            throw new IOException("Failed to extract from DOCX: " + e.getMessage(), e);
        }
    }

    /**
     * Clean extracted text - remove special characters that cause issues
     */
    private String cleanText(String text) {
        if (text == null) return "";
        
        // Replace Unicode bullet points with dashes
        text = text.replace("\u2022", "-");  // •
        text = text.replace("\u25E6", "-");  // ◦
        text = text.replace("\u25CB", "-");  // ○
        text = text.replace("\u25CF", "-");  // ●
        text = text.replace("\u25AA", "-");  // ▪
        text = text.replace("\u25AB", "-");  // ▫
        text = text.replace("\u25C6", "-");  // ◆
        text = text.replace("\u25C7", "-");  // ◇
        text = text.replace("\u25BA", "-");  // ►
        text = text.replace("\u25BB", "-");  // ▻
        text = text.replace("\u2023", "-");  // ‣
        text = text.replace("\u2043", "-");  // ⁃
        text = text.replace("\u2219", "-");  // ∙
        
        // Replace smart quotes
        text = text.replace("\u201C", "\"");  // "
        text = text.replace("\u201D", "\"");  // "
        text = text.replace("\u2018", "'");   // '
        text = text.replace("\u2019", "'");   // '
        text = text.replace("\u2013", "-");   // –
        text = text.replace("\u2014", "-");   // —
        
        // Remove replacement character (often shows as ?)
        text = text.replace("\uFFFD", "");
        
        // Remove control characters except newlines and tabs
        StringBuilder cleaned = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == '\n' || c == '\t' || c == '\r' || (c >= 32 && c < 127) || 
                (c >= 128 && c < 256) || Character.isLetterOrDigit(c) || Character.isWhitespace(c)) {
                cleaned.append(c);
            } else if (c > 256) {
                // Unknown Unicode - just skip it
            }
        }
        
        // Clean up multiple consecutive dashes or spaces
        String result = cleaned.toString();
        result = result.replaceAll("-{2,}", "-");
        result = result.replaceAll(" {2,}", " ");
        result = result.replaceAll("\\n{3,}", "\n\n");
        
        return result.trim();
    }
}
