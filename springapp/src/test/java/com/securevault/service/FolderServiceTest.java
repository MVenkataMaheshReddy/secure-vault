package com.securevault.service;

import com.securevault.dto.FolderDTO;
import com.securevault.entity.Folder;
import com.securevault.entity.User;
import com.securevault.exception.ResourceNotFoundException;
import com.securevault.repository.FolderRepository;
import com.securevault.repository.UserRepository;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FolderServiceTest {

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FolderService folderService;

    private User sampleUser;
    private Folder sampleFolder;

    @BeforeEach
    void setUp() {
        sampleUser = new User("Alice", "alice@example.com", "pass");
        sampleUser.setId(10L);

        sampleFolder = new Folder();
        sampleFolder.setFol_id(1L);
        sampleFolder.setFol_name("Finance");
        sampleFolder.setDescription("Financial documents");
        sampleFolder.setOwner(sampleUser);
        sampleFolder.setCreatedAt(LocalDate.now());
    }

    @Test
    void testGetAllFolders_ReturnsList() {
        Page<Folder> page = new PageImpl<>(Collections.singletonList(sampleFolder));
        when(folderRepository.findAll(any(PageRequest.class))).thenReturn(page);

        List<FolderDTO> list = folderService.getAllFolders(0, 10);

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("Finance", list.get(0).getFol_name());
        assertEquals(10L, list.get(0).getOwnerId());
    }

    @Test
    void testSaveFolder_Success() {
        FolderDTO inputDto = new FolderDTO(null, "Tax", 10L, LocalDate.now(), "Tax papers");

        when(userRepository.findById(10L)).thenReturn(Optional.of(sampleUser));
        when(folderRepository.save(any(Folder.class))).thenAnswer(inv -> {
            Folder f = inv.getArgument(0);
            f.setFol_id(5L);
            return f;
        });

        FolderDTO saved = folderService.saveFolder(inputDto);

        assertNotNull(saved);
        assertEquals(5L, saved.getFol_id());
        assertEquals("Tax", saved.getFol_name());
        assertEquals(10L, saved.getOwnerId());
    }

    @Test
    void testUpdateFolder_Success() {
        FolderDTO updateDto = new FolderDTO(1L, "Finance Updated", 10L, LocalDate.now(), "Updated notes");

        when(userRepository.findById(10L)).thenReturn(Optional.of(sampleUser));
        when(folderRepository.save(any(Folder.class))).thenAnswer(inv -> inv.getArgument(0));

        FolderDTO updated = folderService.updateFolder(1L, updateDto);

        assertNotNull(updated);
        assertEquals(1L, updated.getFol_id());
        assertEquals("Finance Updated", updated.getFol_name());
        assertEquals("Updated notes", updated.getDescription());
    }

    @Test
    void testDeleteFolder_Success() {
        doNothing().when(folderRepository).deleteById(1L);

        folderService.deleteFolder(1L);

        verify(folderRepository, times(1)).deleteById(1L);
    }

    @Test
    void testGetFolderById_Found() {
        when(folderRepository.findById(1L)).thenReturn(Optional.of(sampleFolder));

        Optional<FolderDTO> opt = folderService.getFolderById(1L);

        assertTrue(opt.isPresent());
        assertEquals("Finance", opt.get().getFol_name());
    }

    @Test
    void testGetFolderById_NotFound() {
        when(folderRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<FolderDTO> opt = folderService.getFolderById(99L);

        assertFalse(opt.isPresent());
    }

    @Test
    void testGetFolderEntityById_Success() {
        when(folderRepository.findById(1L)).thenReturn(Optional.of(sampleFolder));

        Folder entity = folderService.getFolderEntityById(1L);

        assertNotNull(entity);
        assertEquals(1L, entity.getFol_id());
    }

    @Test
    void testGetFolderEntityById_ThrowsNotFound() {
        when(folderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> folderService.getFolderEntityById(99L));
    }
}
