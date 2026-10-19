package com.securevault.service.ocr;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TesseractOcrService {

    private static final Logger logger = LoggerFactory.getLogger(TesseractOcrService.class);

    @Value("${securevault.ocr.tessdata-path:storage/tessdata}")
    private String tessdataPath;

    @Value("${securevault.ocr.language:eng}")
    private String language;

    @Value("${securevault.ocr.enabled:true}")
    private boolean enabled;

    private String resolvedTessdataPath;
    private String detectedCliPath;
    private boolean tessdataFound = false;
    private boolean tesseractCliAvailable = false;

    public TesseractOcrService() {}

    public TesseractOcrService(String tessdataPath, String language, boolean enabled) {
        this.tessdataPath = tessdataPath;
        this.language = language;
        this.enabled = enabled;
    }

    @PostConstruct
    public void init() {
        // Configure JNA search paths for macOS Homebrew / Linux standard locations
        String currentJna = System.getProperty("jna.library.path", "");
        String homebrewLib = "/opt/homebrew/lib:/usr/local/lib:/usr/lib";
        if (!currentJna.contains("/opt/homebrew/lib")) {
            System.setProperty("jna.library.path", currentJna.isBlank() ? homebrewLib : currentJna + ":" + homebrewLib);
        }

        resolveTessdataPath();
        detectTesseractCli();

        logger.info("============================================================");
        logger.info("TESSERACT OCR SERVICE INITIALIZATION:");
        logger.info("  Enabled:               {}", enabled);
        logger.info("  Language:              {}", language);
        logger.info("  Configured Tessdata:   {}", tessdataPath);
        logger.info("  Resolved Tessdata Dir: {}", resolvedTessdataPath != null ? resolvedTessdataPath : "NOT FOUND");
        logger.info("  Tessdata File Exists:  {} ({}.traineddata)", tessdataFound, language);
        logger.info("  Tesseract CLI Path:    {}", detectedCliPath != null ? detectedCliPath : "NOT FOUND");
        logger.info("  CLI Execution Ready:   {}", tesseractCliAvailable);
        logger.info("============================================================");
    }

    private void resolveTessdataPath() {
        String lang = (language != null && !language.isBlank()) ? language : "eng";
        String traineddataFile = lang + ".traineddata";

        List<File> candidatePaths = new ArrayList<>();
        if (tessdataPath != null && !tessdataPath.isBlank()) {
            candidatePaths.add(new File(tessdataPath));
            candidatePaths.add(new File("..", tessdataPath));
            candidatePaths.add(new File("springapp", tessdataPath));
        }
        // System fallback paths
        candidatePaths.add(new File("/opt/homebrew/share/tessdata"));
        candidatePaths.add(new File("/usr/local/share/tessdata"));
        candidatePaths.add(new File("/usr/share/tesseract-ocr/5/tessdata"));
        candidatePaths.add(new File("/usr/share/tesseract-ocr/4.00/tessdata"));
        candidatePaths.add(new File("/usr/share/tessdata"));

        for (File candidate : candidatePaths) {
            if (candidate.exists() && candidate.isDirectory()) {
                File targetData = new File(candidate, traineddataFile);
                if (targetData.exists()) {
                    this.resolvedTessdataPath = candidate.getAbsolutePath();
                    this.tessdataFound = true;
                    return;
                }
            }
        }

        // If specific language file wasn't found, check if directory at least exists
        for (File candidate : candidatePaths) {
            if (candidate.exists() && candidate.isDirectory()) {
                this.resolvedTessdataPath = candidate.getAbsolutePath();
                this.tessdataFound = false;
                return;
            }
        }

        this.resolvedTessdataPath = new File(tessdataPath != null ? tessdataPath : "storage/tessdata").getAbsolutePath();
        this.tessdataFound = false;
    }

    private void detectTesseractCli() {
        String[] possibleCommands = {"tesseract", "/opt/homebrew/bin/tesseract", "/usr/local/bin/tesseract", "/usr/bin/tesseract"};
        for (String cmd : possibleCommands) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
                Process process = pb.start();
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    this.detectedCliPath = cmd;
                    this.tesseractCliAvailable = true;
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        this.tesseractCliAvailable = false;
    }

    public String doOcr(File file) {
        if (!enabled) {
            logger.warn("OCR is disabled in configuration.");
            throw new OcrExtractionException("OCR processing is disabled in configuration.");
        }

        // 1. Try Tess4J native API first
        try {
            ITesseract tesseract = createTesseractInstance();
            String result = tesseract.doOCR(file);
            if (result != null && !result.isBlank()) {
                return result.trim();
            }
        } catch (Throwable t) {
            logger.warn("Tess4J native invocation failed ({}: {}). Attempting CLI fallback...",
                    t.getClass().getSimpleName(), t.getMessage());
        }

        // 2. Fall back to Tesseract CLI if available
        if (tesseractCliAvailable && detectedCliPath != null) {
            try {
                return runCliOcr(file).trim();
            } catch (Exception e) {
                logger.error("Tesseract CLI fallback also failed for file {}: {}", file.getName(), e.getMessage(), e);
                throw new OcrExtractionException("Both Tess4J and Tesseract CLI failed for file: " + file.getName() + " (" + e.getMessage() + ")", e);
            }
        }

        throw new OcrExtractionException("Tesseract OCR execution failed for file: " + file.getName() + " and no CLI fallback available.");
    }

    public String doOcr(BufferedImage image) {
        if (!enabled) {
            logger.warn("OCR is disabled in configuration.");
            throw new OcrExtractionException("OCR processing is disabled in configuration.");
        }

        // 1. Try Tess4J native API first
        try {
            ITesseract tesseract = createTesseractInstance();
            String result = tesseract.doOCR(image);
            if (result != null && !result.isBlank()) {
                return result.trim();
            }
        } catch (Throwable t) {
            logger.warn("Tess4J native invocation failed for BufferedImage ({}: {}). Attempting CLI fallback...",
                    t.getClass().getSimpleName(), t.getMessage());
        }

        // 2. Fall back to Tesseract CLI with temporary image file
        if (tesseractCliAvailable && detectedCliPath != null) {
            File tempImage = null;
            try {
                tempImage = File.createTempFile("ocr_render_", ".png");
                ImageIO.write(image, "png", tempImage);
                return runCliOcr(tempImage).trim();
            } catch (Exception e) {
                logger.error("Tesseract CLI fallback failed for rendered image: {}", e.getMessage(), e);
                throw new OcrExtractionException("Tesseract OCR failed for rendered image: " + e.getMessage(), e);
            } finally {
                if (tempImage != null && tempImage.exists()) {
                    tempImage.delete();
                }
            }
        }

        throw new OcrExtractionException("Tesseract OCR failed for rendered image and no CLI fallback available.");
    }

    private String runCliOcr(File imageFile) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(detectedCliPath != null ? detectedCliPath : "tesseract");
        cmd.add(imageFile.getAbsolutePath());
        cmd.add("stdout");
        if (language != null && !language.isBlank()) {
            cmd.add("-l");
            cmd.add(language);
        }
        if (resolvedTessdataPath != null && tessdataFound) {
            cmd.add("--tessdata-dir");
            cmd.add(resolvedTessdataPath);
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        Process process = pb.start();

        String output;
        try (InputStream is = process.getInputStream()) {
            output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String err;
            try (InputStream es = process.getErrorStream()) {
                err = new String(es.readAllBytes(), StandardCharsets.UTF_8);
            }
            throw new OcrExtractionException("Tesseract CLI exited with code " + exitCode + ": " + err);
        }
        return output;
    }

    protected ITesseract createTesseractInstance() {
        ITesseract tesseract = new Tesseract();
        if (resolvedTessdataPath != null && !resolvedTessdataPath.isBlank()) {
            tesseract.setDatapath(resolvedTessdataPath);
        }
        if (language != null && !language.isBlank()) {
            tesseract.setLanguage(language);
        }
        return tesseract;
    }

    public String getTessdataPath() {
        return tessdataPath;
    }

    public void setTessdataPath(String tessdataPath) {
        this.tessdataPath = tessdataPath;
        resolveTessdataPath();
    }

    public String getResolvedTessdataPath() {
        return resolvedTessdataPath;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isTessdataFound() {
        return tessdataFound;
    }

    public boolean isTesseractCliAvailable() {
        return tesseractCliAvailable;
    }
}
