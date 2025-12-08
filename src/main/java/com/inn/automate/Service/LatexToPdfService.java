package com.inn.automate.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class LatexToPdfService {

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Convert LaTeX code to PDF using LaTeX.Online API
     * This is a free service that compiles LaTeX to PDF
     */
    public byte[] convertLatexToPdf(String latexCode) {
        try {
            log.info("Starting LaTeX to PDF conversion");

            // Method 1: Use LaTeX.Online API (Recommended - Free & Easy)
            return convertUsingLatexOnline(latexCode);

            // Method 2: Use local pdflatex (Uncomment if you have LaTeX installed)
            // return convertUsingLocalPdfLatex(latexCode);

        } catch (Exception e) {
            log.error("Error converting LaTeX to PDF", e);
            throw new RuntimeException("Failed to convert LaTeX to PDF: " + e.getMessage());
        }
    }

    /**
     * Method 1: Use LaTeX.Online API (Free service)
     * API Documentation: https://latexonline.cc/
     */
    private byte[] convertUsingLatexOnline(String latexCode) {
        try {
            // LaTeX.Online endpoint
            String url = "https://latexonline.cc/compile";

            // Prepare the request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);

            HttpEntity<String> entity = new HttpEntity<>(latexCode, headers);

            // Make the API call
            log.info("Calling LaTeX.Online API");
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    byte[].class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.info("PDF generated successfully, size: {} bytes", response.getBody().length);
                return response.getBody();
            } else {
                throw new RuntimeException("Failed to generate PDF from LaTeX.Online");
            }

        } catch (Exception e) {
            log.error("Error using LaTeX.Online: {}", e.getMessage());
            // Fallback to alternative method
            return convertUsingTexLive(latexCode);
        }
    }

    /**
     * Method 2: Use TeXLive.net API (Alternative free service)
     */
    private byte[] convertUsingTexLive(String latexCode) {
        try {
            log.info("Trying TeXLive.net API as fallback");

            // Create a multipart request with the LaTeX file
            String url = "https://texlive.net/cgi-bin/latexcgi";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("code", latexCode);
            requestBody.put("command", "pdflatex");

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    byte[].class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.info("PDF generated using TeXLive.net, size: {} bytes", response.getBody().length);
                return response.getBody();
            }

            throw new RuntimeException("Both LaTeX services failed");

        } catch (Exception e) {
            log.error("All PDF generation methods failed: {}", e.getMessage());
            // Return a placeholder PDF with error message
            return generateErrorPdf();
        }
    }

    /**
     * Method 3: Use local pdflatex installation
     * Requirements: LaTeX must be installed on the server
     * Install on Ubuntu: sudo apt-get install texlive-full
     * Install on Mac: brew install --cask mactex
     * Install on Windows: Download MiKTeX
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

            // Run pdflatex command
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "pdflatex",
                    "-interaction=nonstopmode",
                    "-output-directory=" + tempDir.toString(),
                    texFile.toString()
            );

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // Read output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("pdflatex: {}", line);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("pdflatex failed with exit code: " + exitCode);
            }

            // Read generated PDF
            if (Files.exists(pdfFile)) {
                byte[] pdfBytes = Files.readAllBytes(pdfFile);
                log.info("PDF generated locally, size: {} bytes", pdfBytes.length);
                return pdfBytes;
            } else {
                throw new RuntimeException("PDF file not generated");
            }

        } finally {
            // Cleanup temporary files
            cleanupTempFiles(tempDir);
        }
    }

    /**
     * Generate a simple error PDF when LaTeX compilation fails
     */
    private byte[] generateErrorPdf() {
        try {
            log.info("Generating error placeholder PDF");

            String errorLatex = "\\documentclass{article}\n" +
                    "\\usepackage[margin=1in]{geometry}\n" +
                    "\\begin{document}\n" +
                    "\\begin{center}\n" +
                    "\\Large\\textbf{Resume Generation Error}\n" +
                    "\\end{center}\n" +
                    "\\vspace{1cm}\n" +
                    "The resume could not be generated due to a LaTeX compilation error.\n\n" +
                    "Please try again or contact support.\n" +
                    "\\end{document}";

            // Try to compile the simple error message
            return convertUsingLatexOnline(errorLatex);

        } catch (Exception e) {
            log.error("Even error PDF generation failed", e);
            // Return minimal valid PDF bytes
            return getMinimalPdfBytes();
        }
    }

    /**
     * Cleanup temporary files
     */
    private void cleanupTempFiles(Path tempDir) {
        try {
            Files.walk(tempDir)
                    .sorted((a, b) -> -a.compareTo(b)) // Reverse order for deletion
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.warn("Failed to delete temp file: {}", path);
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to cleanup temp directory: {}", tempDir);
        }
    }

    /**
     * Get minimal valid PDF bytes (empty PDF)
     */
    private byte[] getMinimalPdfBytes() {
        // This is a minimal valid PDF structure
        String minimalPdf = "%PDF-1.4\n" +
                "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n" +
                "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n" +
                "3 0 obj\n<< /Type /Page /Parent 2 0 R /Resources 4 0 R /MediaBox [0 0 612 792] /Contents 5 0 R >>\nendobj\n" +
                "4 0 obj\n<< /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> >>\nendobj\n" +
                "5 0 obj\n<< /Length 44 >>\nstream\nBT /F1 12 Tf 100 700 Td (Resume Error) Tj ET\nendstream\nendobj\n" +
                "xref\n0 6\n0000000000 65535 f\n" +
                "0000000009 00000 n\n0000000058 00000 n\n0000000115 00000 n\n" +
                "0000000214 00000 n\n0000000304 00000 n\n" +
                "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n397\n%%EOF";

        return minimalPdf.getBytes();
    }

    /**
     * Validate LaTeX syntax (basic validation)
     */
    public boolean isValidLatex(String latexCode) {
        if (latexCode == null || latexCode.trim().isEmpty()) {
            return false;
        }

        // Check for basic LaTeX structure
        boolean hasDocumentClass = latexCode.contains("\\documentclass");
        boolean hasBeginDocument = latexCode.contains("\\begin{document}");
        boolean hasEndDocument = latexCode.contains("\\end{document}");

        return hasDocumentClass && hasBeginDocument && hasEndDocument;
    }
}