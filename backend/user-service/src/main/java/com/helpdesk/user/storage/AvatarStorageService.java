package com.helpdesk.user.storage;

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
 * Local-disk storage for profile avatars. One image per user; uploading
 * replaces the previous file. Backed by a Docker volume in production
 * (see docker-compose.yml -> helpdesk-uploads).
 *
 * Why local disk instead of S3 / DB BLOB:
 *   - Simple, no extra infra; works offline.
 *   - DB BLOB bloats backups and JPA caches.
 *   - For a small org-internal helpdesk this is the right shape; swap the
 *     implementation later if you go multi-node.
 */
@Service
@Slf4j
public class AvatarStorageService {

    private static final Set<String> ALLOWED_TYPES =
        Set.of("image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif");
    private static final long MAX_BYTES = 5L * 1024 * 1024; // 5 MB

    @Value("${helpdesk.storage.avatars-path:/var/helpdesk/avatars}")
    private String storagePath;

    private Path rootDir;

    @PostConstruct
    void init() throws IOException {
        rootDir = Paths.get(storagePath);
        Files.createDirectories(rootDir);
        log.info("Avatar storage initialised at {}", rootDir.toAbsolutePath());
    }

    /**
     * Persists a new avatar for the given user, replacing any existing one,
     * and returns the public URL (served by the gateway via user-service).
     */
    public String save(Long userId, MultipartFile file) throws IOException {
        validate(file);
        String ext = pickExtension(file);
        // user-{id}-{uuid}.{ext} — the UUID is a cache-buster so the browser
        // refreshes when the user replaces the photo.
        String filename = "user-" + userId + "-" + UUID.randomUUID() + "." + ext;
        Path target = rootDir.resolve(filename);
        // Delete any previous file(s) for this user.
        deleteExisting(userId);
        try (var in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        String url = "/api/users/avatars/" + filename;
        log.info("Saved avatar for userId={} at {}", userId, target);
        return url;
    }

    public void deleteByUrl(String url) {
        if (url == null || !url.startsWith("/api/users/avatars/")) return;
        String filename = url.substring("/api/users/avatars/".length());
        Path p = rootDir.resolve(filename).normalize();
        if (!p.startsWith(rootDir)) return; // path-traversal guard
        try {
            Files.deleteIfExists(p);
        } catch (IOException ex) {
            log.warn("Could not delete avatar {}: {}", p, ex.getMessage());
        }
    }

    public Resource load(String filename) {
        Path p = rootDir.resolve(filename).normalize();
        if (!p.startsWith(rootDir) || !Files.exists(p)) {
            return null;
        }
        return new FileSystemResource(p);
    }

    private void deleteExisting(Long userId) {
        String prefix = "user-" + userId + "-";
        try (var stream = Files.list(rootDir)) {
            stream
                .filter(p -> p.getFileName().toString().startsWith(prefix))
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // best effort
                    }
                });
        } catch (IOException ignored) {
            // best effort
        }
    }

    private static void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file uploaded.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("Image too large (max 5 MB).");
        }
        String ct = file.getContentType();
        if (ct == null || !ALLOWED_TYPES.contains(ct.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                "Unsupported file type. Use JPG, PNG, GIF, or WEBP.");
        }
    }

    private static String pickExtension(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name != null && name.contains(".")) {
            String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            if (Set.of("jpg", "jpeg", "png", "gif", "webp").contains(ext)) {
                return ext;
            }
        }
        // Fall back to content-type → extension.
        String ct = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        return switch (ct) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "img";
        };
    }
}
