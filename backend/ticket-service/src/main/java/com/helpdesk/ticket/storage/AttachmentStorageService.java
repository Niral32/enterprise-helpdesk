package com.helpdesk.ticket.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Local-disk storage for ticket attachments. Mirrors the avatar storage
 * pattern in user-service but allows many files per parent (the parent is a
 * ticket here, not a user) and supports PDFs alongside images.
 */
@Service
@Slf4j
public class AttachmentStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
        "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp",
        "application/pdf"
    );
    private static final long MAX_BYTES = 10L * 1024 * 1024; // 10 MB
    private static final int MAX_FILES_PER_TICKET = 10;

    @Value("${helpdesk.storage.attachments-path:/var/helpdesk/attachments}")
    private String storagePath;

    private Path rootDir;

    @PostConstruct
    void init() throws IOException {
        rootDir = Paths.get(storagePath);
        Files.createDirectories(rootDir);
        log.info("Attachment storage initialised at {}", rootDir.toAbsolutePath());
    }

    public StoredFile store(Long ticketId, MultipartFile file, long currentCount) throws IOException {
        validate(file, currentCount);
        String ext = pickExtension(file);
        String storedName = "tkt-" + ticketId + "-" + UUID.randomUUID() + "." + ext;
        Path target = rootDir.resolve(storedName);
        try (var in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return new StoredFile(storedName, target.toString());
    }

    public Resource load(String storedFilename) {
        Path p = rootDir.resolve(storedFilename).normalize();
        if (!p.startsWith(rootDir) || !Files.exists(p)) {
            return null;
        }
        return new FileSystemResource(p);
    }

    public void delete(String storedFilename) {
        if (storedFilename == null) return;
        Path p = rootDir.resolve(storedFilename).normalize();
        if (!p.startsWith(rootDir)) return; // path-traversal guard
        try {
            Files.deleteIfExists(p);
        } catch (IOException ex) {
            log.warn("Could not delete attachment {}: {}", p, ex.getMessage());
        }
    }

    private static void validate(MultipartFile file, long currentCount) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file uploaded.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("File too large (max 10 MB).");
        }
        if (currentCount >= MAX_FILES_PER_TICKET) {
            throw new IllegalArgumentException(
                "Maximum " + MAX_FILES_PER_TICKET + " attachments per ticket reached.");
        }
        String ct = file.getContentType();
        if (ct == null || !ALLOWED_TYPES.contains(ct.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                "Unsupported file type. Allowed: JPG, PNG, GIF, WEBP, PDF.");
        }
    }

    private static String pickExtension(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name != null && name.contains(".")) {
            String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            if (Set.of("jpg", "jpeg", "png", "gif", "webp", "pdf").contains(ext)) {
                return ext;
            }
        }
        String ct = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        return switch (ct) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "application/pdf" -> "pdf";
            default -> "bin";
        };
    }

    public record StoredFile(String storedFilename, String absolutePath) {}
}
