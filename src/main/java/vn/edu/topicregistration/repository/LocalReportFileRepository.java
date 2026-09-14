package vn.edu.topicregistration.repository;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import vn.edu.topicregistration.config.ReportStorageProperties;

@Repository
@Profile("!preview & !test")
public class LocalReportFileRepository implements ReportFileRepository {
    private static final long MAX_SIZE_BYTES = 20L * 1024 * 1024;
    private static final Pattern STORAGE_KEY = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf");

    private final Path root;

    public LocalReportFileRepository(ReportStorageProperties properties) {
        Objects.requireNonNull(properties, "properties");
        try {
            Path configuredRoot = properties.root().toAbsolutePath().normalize();
            Files.createDirectories(configuredRoot);
            this.root = configuredRoot.toRealPath();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot initialize report storage", exception);
        }
    }

    @Override
    public StoredReportFile store(InputStream source, long expectedSize) throws IOException {
        Objects.requireNonNull(source, "source");
        if (expectedSize < 1 || expectedSize > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("Report size is outside the allowed range");
        }

        String storageKey = UUID.randomUUID() + ".pdf";
        Path target = safePath(storageKey);
        Path temporary = Files.createTempFile(root, ".report-upload-", ".tmp");
        long copied = 0;
        try {
            try (InputStream input = source;
                    OutputStream output = Files.newOutputStream(
                            temporary,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    copied += count;
                    if (copied > MAX_SIZE_BYTES) {
                        throw new IllegalArgumentException(
                                "Report exceeds the maximum allowed size");
                    }
                    output.write(buffer, 0, count);
                }
            }
            if (copied != expectedSize) {
                throw new IllegalArgumentException("Report size changed during upload");
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            return new StoredReportFile(storageKey, copied);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public InputStream open(String storageKey) throws IOException {
        return Files.newInputStream(safePath(storageKey), StandardOpenOption.READ);
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(safePath(storageKey));
        } catch (IOException ignored) {
            // Cleanup is best effort. No storage path or key is written to logs.
        }
    }

    private Path safePath(String storageKey) {
        if (storageKey == null || !STORAGE_KEY.matcher(storageKey).matches()) {
            throw new IllegalArgumentException("Invalid report storage key");
        }
        Path resolved = root.resolve(storageKey).normalize();
        if (!Objects.equals(resolved.getParent(), root)) {
            throw new IllegalArgumentException("Invalid report storage location");
        }
        return resolved;
    }
}
