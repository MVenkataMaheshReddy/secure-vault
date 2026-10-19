package com.securevault.service.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securevault.entity.DocumentVersion;
import com.securevault.entity.OcrStatus;
import com.securevault.entity.Role;
import com.securevault.entity.RoleName;
import com.securevault.entity.User;
import com.securevault.repository.DocumentVersionRepository;
import com.securevault.repository.RoleRepository;
import com.securevault.repository.UserRepository;
import com.securevault.security.JwtUtil;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "securevault.ocr.enabled=true",
        "securevault.ocr.tessdata-path=storage/tessdata"
})
@DisplayName("OCR End-to-End Flow Test: Upload Image with 'CGPA 9.06', Verify PENDING -> DONE, and Content Search '9.06'")
public class OcrEndToEndFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    private String userToken;
    private User testUser;

    @BeforeEach
    public void setup() {
        Role employeeRole = roleRepository.findByName(RoleName.EMPLOYEE)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.EMPLOYEE)));

        testUser = userRepository.findByEmail("ocr_tester@securevault.com")
                .orElseGet(() -> {
                    User u = new User(
                            "OCR Tester",
                            "ocr_tester@securevault.com",
                            passwordEncoder.encode("Password@123"),
                            new HashSet<>(Collections.singletonList(employeeRole))
                    );
                    return userRepository.save(u);
                });

        userToken = jwtUtil.generateToken(testUser);
    }

    /**
     * Generates or loads a valid PNG image containing clear, crisp text: "Student Record: CGPA 9.06".
     */
    private byte[] getTestImageBytes() throws Exception {
        // Try reading existing test_cgpa.png if available
        Path path1 = Paths.get("test_cgpa.png");
        Path path2 = Paths.get("../test_cgpa.png");
        if (Files.exists(path1)) {
            return Files.readAllBytes(path1);
        } else if (Files.exists(path2)) {
            return Files.readAllBytes(path2);
        }

        // Otherwise generate high-resolution image programmatically
        BufferedImage image = new BufferedImage(600, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 600, 200);
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Serif", Font.BOLD, 36));
        g2d.drawString("Student Record: CGPA 9.06", 50, 110);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("Step 1 to 5: Upload image with 'CGPA 9.06', assert PENDING -> DONE, and search '9.06' finding it")
    public void testOcrUploadProcessingAndSearchFlow() throws Exception {
        byte[] imageBytes = getTestImageBytes();
        MockMultipartFile imageFile = new MockMultipartFile(
                "file",
                "grade_sheet.png",
                "image/png",
                imageBytes
        );

        // 1. Upload the image document
        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents")
                        .file(imageFile)
                        .param("title", "Official Grade Sheet")
                        .param("category", "ACADEMIC")
                        .param("description", "Semester transcript record")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.ocrStatus").value("PENDING"))
                .andReturn();

        JsonNode uploadJson = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        long documentId = uploadJson.get("id").asLong();

        // 2. Poll for OCR status change: PENDING -> DONE (wait up to 10 seconds for async thread pool)
        DocumentVersion version = null;
        boolean ocrDone = false;
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < 10000) {
            version = documentVersionRepository
                    .findByDocumentIdAndVersionNumber(documentId, 1)
                    .orElse(null);

            if (version != null && version.getOcrStatus() == OcrStatus.DONE) {
                ocrDone = true;
                break;
            }
            Thread.sleep(250);
        }

        assertNotNull(version, "DocumentVersion 1 must exist in the database");
        assertTrue(ocrDone, "OCR status should transition from PENDING to DONE, but was: " + version.getOcrStatus() + " (error: " + version.getOcrErrorMessage() + ")");
        assertNotNull(version.getOcrText(), "Extracted OCR text must not be null");
        assertTrue(version.getOcrText().contains("9.06"),
                "Extracted OCR text must contain '9.06'. Actual text: [" + version.getOcrText() + "]");

        // 3. Search for "9.06" and verify the document is found via content search
        MvcResult searchResult = mockMvc.perform(get("/api/documents/search")
                        .param("q", "9.06")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", not(empty())))
                .andExpect(jsonPath("$.content[0].id").value(documentId))
                .andExpect(jsonPath("$.content[0].ocrStatus").value("DONE"))
                .andExpect(jsonPath("$.content[0].matchSource").value("content"))
                .andExpect(jsonPath("$.content[0].snippet", containsString("9.06")))
                .andReturn();

        JsonNode searchJson = objectMapper.readTree(searchResult.getResponse().getContentAsString());
        assertEquals(1, searchJson.get("content").size(), "Search for '9.06' should return exactly the uploaded document");

        // 4. Test "Retry OCR" functionality:
        // Manually reset status to FAILED, then invoke POST /api/documents/{id}/ocr/retry
        version.setOcrStatus(OcrStatus.FAILED);
        version.setOcrErrorMessage("Simulated transient error");
        documentVersionRepository.save(version);

        mockMvc.perform(post("/api/documents/" + documentId + "/ocr/retry")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        // Wait for retry to finish and become DONE again
        boolean retriedDone = false;
        long retryStart = System.currentTimeMillis();
        while (System.currentTimeMillis() - retryStart < 10000) {
            DocumentVersion retriedVersion = documentVersionRepository
                    .findByDocumentIdAndVersionNumber(documentId, 1)
                    .orElse(null);
            if (retriedVersion != null && retriedVersion.getOcrStatus() == OcrStatus.DONE) {
                retriedDone = true;
                break;
            }
            Thread.sleep(250);
        }

        assertTrue(retriedDone, "Retry OCR should successfully reprocess and transition status back to DONE");
    }
}
