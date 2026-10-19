package com.securevault.controller;

import com.securevault.dto.FolderDTO;
import com.securevault.service.FolderService;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Folder Management", description = "Endpoints for organizing documents into folders")
@RestController
@RequestMapping("/folders")
@PreAuthorize("isAuthenticated()")
public class FolderController {

    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    @Operation(summary = "Get all folders", description = "Retrieve paginated list of folders")
    @GetMapping("/fetch")
    public ResponseEntity<List<FolderDTO>> getAllFolders(@RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        return ResponseEntity.ok(folderService.getAllFolders(safePage, safeSize));
    }

    @Operation(summary = "Create folder", description = "Create a new folder for categorizing documents")
    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<FolderDTO> createFolder(@Valid @RequestBody FolderDTO folderDTO) {
        FolderDTO saved = folderService.saveFolder(folderDTO);
        return ResponseEntity.ok(saved);
    }

    @Operation(summary = "Update folder", description = "Update folder details")
    @PutMapping("/update/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<FolderDTO> updateFolder(@PathVariable Long id, @Valid @RequestBody FolderDTO folderDTO) {
        return ResponseEntity.ok(folderService.updateFolder(id, folderDTO));
    }

    @Operation(summary = "Delete folder", description = "Delete a folder")
    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<String> deleteFolder(@PathVariable Long id) {
        folderService.deleteFolder(id);
        return ResponseEntity.ok("Folder deleted successfully!");
    }

    @Operation(summary = "Get folder by ID", description = "Retrieve folder details by ID")
    @GetMapping("/{id}")
    public ResponseEntity<Optional<FolderDTO>> getFolderById(@PathVariable Long id) {
        return ResponseEntity.ok(folderService.getFolderById(id));
    }
}
