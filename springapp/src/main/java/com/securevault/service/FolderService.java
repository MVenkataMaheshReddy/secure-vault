package com.securevault.service;

import com.securevault.dto.FolderDTO;
import com.securevault.entity.Folder;
import com.securevault.entity.User;
import com.securevault.exception.ResourceNotFoundException;
import com.securevault.repository.FolderRepository;
import com.securevault.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FolderService {

    private final FolderRepository folderRepository;
    private final UserRepository userRepository;

    public FolderService(FolderRepository folderRepository, UserRepository userRepository) {
        this.folderRepository = folderRepository;
        this.userRepository = userRepository;
    }

    private FolderDTO mapToDTO(Folder folder) {
        return new FolderDTO(
                folder.getFol_id(),
                folder.getFol_name(),
                folder.getOwner() != null ? folder.getOwner().getId() : null,
                folder.getCreatedAt(),
                folder.getDescription()
        );
    }

    private Folder mapToEntity(FolderDTO dto) {
        Folder folder = new Folder();
        folder.setFol_id(dto.getFol_id());
        folder.setFol_name(dto.getFol_name());
        folder.setDescription(dto.getDescription());
        folder.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : LocalDate.now());

        if (dto.getOwnerId() != null) {
            User owner = userRepository.findById(dto.getOwnerId())
                    .orElse(null);
            folder.setOwner(owner);
        }
        return folder;
    }

    @Transactional(readOnly = true)
    public List<FolderDTO> getAllFolders(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<Folder> folders = folderRepository.findAll(pageable);
        return folders.getContent().stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Transactional
    public FolderDTO saveFolder(FolderDTO dto) {
        Folder folder = mapToEntity(dto);
        Folder saved = folderRepository.save(folder);
        return mapToDTO(saved);
    }

    @Transactional
    public FolderDTO updateFolder(Long fol_id, FolderDTO dto) {
        Folder folder = mapToEntity(dto);
        folder.setFol_id(fol_id);
        Folder updated = folderRepository.save(folder);
        return mapToDTO(updated);
    }

    @Transactional
    public void deleteFolder(Long fol_id) {
        folderRepository.deleteById(fol_id);
    }

    @Transactional(readOnly = true)
    public Optional<FolderDTO> getFolderById(Long id) {
        return folderRepository.findById(id).map(this::mapToDTO);
    }

    @Transactional(readOnly = true)
    public Folder getFolderEntityById(Long id) {
        return folderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found with id: " + id));
    }
}
