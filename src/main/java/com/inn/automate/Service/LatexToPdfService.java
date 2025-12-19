package com.inn.automate.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
@Slf4j
public class LatexToPdfService {

    private final RestTemplate restTemplate = new RestTemplate();

    // Path to pdflatex - configurable via application.properties
    @Value("${latex.pdflatex.path:C:\\\\Users\\\\xghos\\\\AppData\\\\Local\\\\Programs\\\\MiKTeX\\\\miktex\\\\bin\\\\x64\\\\pdflatex.exe}")
    private String pdflatexPath;

    /**
     * Convert LaTeX code to PDF
     * Primary method: Local pdflatex (MiKTeX)
     * Fallback: External APIs
     */
    public byte[] convertLatexToPdf(String latexCode) {
        try {
            log.info("Starting LaTeX to PDF conversion");
            log.debug("LaTeX code length: {} characters", latexCode.length());

            // Primary Method: Use local pdflatex (most reliable)
            byte[] pdfBytes = convertUsingLocalPdfLatex(latexCode);

            // Verify the PDF is valid
            if (pdfBytes != null && isPdfValid(pdfBytes)) {
                log.info("✅ PDF generated successfully using local pdflatex: {} bytes", pdfBytes.length);
                return pdfBytes;
            } else {
                log.warn("❌ Local pdflatex produced invalid output, trying external APIs");
                throw new RuntimeException("Local PDF generation produced invalid output");
            }

        } catch (Exception e) {
            log.warn("Local pdflatex failed: {}. Trying external APIs...", e.getMessage());
            
            // Fallback to external APIs
            try {
                byte[] pdfBytes = convertUsingLatexOnline(latexCode);
                if (pdfBytes != null && isPdfValid(pdfBytes)) {
                    log.info("✅ PDF generated using external API: {} bytes", pdfBytes.length);
                    return pdfBytes;
                }
            } catch (Exception ex) {
                log.error("External API also failed: {}", ex.getMessage());
            }
            
            // Last resort: return error PDF
            log.error("All PDF generation methods failed");
            return getMinimalPdfBytes();
        }
    }

    /**
     * Use local pdflatex installation (MiKTeX)
     * This is the most reliable method
     */
    private byte[] convertUsingLocalPdfLatex(String latexCode) throws IOException, InterruptedException {
        log.info("Using local pdflatex installation");

        // Create temporary directory
        Path tempDir = Files.createTempDirectory("latex_");
        String fileName = "resume_" + UUID.randomUUID().toString().substring(0, 8);
        Path texFile = tempDir.resolve(fileName + ".tex");
        Path pdfFile = tempDir.resolve(fileName + ".pdf");

        try {
            // Write LaTeX code to file
            Files.write(texFile, latexCode.getBytes());

            // Check if pdflatex exists
            File pdflatexExe = new File(pdflatexPath);
            String pdflatexCmd = pdflatexExe.exists() ? pdflatexPath : "pdflatex";
            
            log.info("Using pdflatex command: {}", pdflatexCmd);

            // Run pdflatex command
            ProcessBuilder processBuilder = new ProcessBuilder(
                    pdflatexCmd,
                    "-interaction=nonstopmode",
                    "-output-directory=" + tempDir.toString(),
                    texFile.toString()
            );

            processBuilder.redirectErrorStream(true);
            processBuilder.directory(tempDir.toFile());
            Process process = processBuilder.start();

            // Read output for logging
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.debug("pdflatex: {}", line);
                }
            }

            // Wait for process to complete (max 60 seconds)
            boolean finished = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("pdflatex timed out after 60 seconds");
            }

            int exitCode = process.exitValue();
            log.info("pdflatex exit code: {}", exitCode);

            // Check if PDF was created
            if (Files.exists(pdfFile)) {
                byte[] pdfBytes = Files.readAllBytes(pdfFile);
                log.info("PDF created successfully: {} bytes", pdfBytes.length);
                return pdfBytes;
            } else {
                log.error("PDF file was not created. pdflatex output:\n{}", output.toString().substring(0, Math.min(1000, output.length())));
                throw new RuntimeException("PDF file was not generated by pdflatex");
            }

        } finally {
            // Cleanup temporary files
            try {
                Files.deleteIfExists(texFile);
                Files.deleteIfExists(pdfFile);
                Files.deleteIfExists(tempDir.resolve(fileName + ".aux"));
                Files.deleteIfExists(tempDir.resolve(fileName + ".log"));
                Files.deleteIfExists(tempDir);
            } catch (Exception e) {
                log.warn("Failed to cleanup temp files: {}", e.getMessage());
            }
        }
    }

    /**
     * Fallback: Use LaTeX.Online API (Free service)
     */
    private byte[] convertUsingLatexOnline(String latexCode) {
        try {
            String url = "https://latexonline.cc/compile?text=" +
                    java.net.URLEncoder.encode(latexCode, "UTF-8");

            log.info("Calling LaTeX.Online API");

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_PDF));

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    byte[].class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                byte[] responseBytes = response.getBody();

                if (isPdfValid(responseBytes)) {
                    log.info("PDF generated successfully via API, size: {} bytes", responseBytes.length);
                    return responseBytes;
                } else {
                    String preview = new String(responseBytes, 0, Math.min(100, responseBytes.length));
                    log.warn("Response is not a valid PDF. Received: {}", preview);
                    throw new RuntimeException("API returned invalid data instead of PDF");
                }
            } else {
                throw new RuntimeException("Failed to generate PDF - HTTP " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error using LaTeX.Online: {}", e.getMessage());
            throw new RuntimeException("External API failed: " + e.getMessage());
        }
    }

    /**
     * Verify if bytes are a valid PDF (public for use by other services)
     * Checks both PDF header (%PDF) and EOF marker (%%EOF)
     */
    public boolean isPdfValid(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length < 8) {
            return false;
        }

        // Check PDF magic number: %PDF
        boolean hasPdfHeader = pdfBytes[0] == '%' &&
                pdfBytes[1] == 'P' &&
                pdfBytes[2] == 'D' &&
                pdfBytes[3] == 'F';
        
        if (!hasPdfHeader) {
            return false;
        }
        
        // Additional check: Look for %%EOF near the end (valid PDF structure)
        String tail = new String(pdfBytes, Math.max(0, pdfBytes.length - 20), 
                Math.min(20, pdfBytes.length));
        boolean hasEofMarker = tail.contains("%%EOF");
        
        return hasEofMarker;
    }

    /**
     * Validate LaTeX structure
     */
    public boolean isValidLatex(String latexCode) {
        if (latexCode == null || latexCode.trim().isEmpty()) {
            return false;
        }
        
        // Basic LaTeX structure check
        return latexCode.contains("\\documentclass") && 
               latexCode.contains("\\begin{document}") && 
               latexCode.contains("\\end{document}");
    }

    /**
     * Generate a minimal valid PDF with error message
     * This is used as absolute last resort
     */
    private byte[] getMinimalPdfBytes() {
        // A minimal valid PDF with an error message
        String pdfContent = 
            "%PDF-1.4\n" +
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n" +
            "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n" +
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] " +
            "/Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n" +
            "4 0 obj\n<< /Length 180 >>\nstream\n" +
            "BT\n/F1 18 Tf\n50 700 Td\n(Resume Generation Error) Tj\n" +
            "/F1 12 Tf\n0 -30 Td\n(The resume could not be generated due to) Tj\n" +
            "0 -20 Td\n(technical difficulties. Please try again or) Tj\n" +
            "0 -20 Td\n(contact support for assistance.) Tj\nET\n" +
            "endstream\nendobj\n" +
            "5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n" +
            "xref\n0 6\n0000000000 65535 f \n0000000009 00000 n \n0000000058 00000 n \n" +
            "0000000115 00000 n \n0000000266 00000 n \n0000000498 00000 n \n" +
            "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n576\n%%EOF";
        
        return pdfContent.getBytes();
    }
}