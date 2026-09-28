package com.tutorcraft.core.files.infrastructure;

import com.tutorcraft.core.shared.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Объекты хранилища как файлы в каталоге {@code tutorcraft.storage.local-root}: ключ S3 = относительный путь.
 * Ключи генерирует сервер ({@code StorageKeys}, media-worker), но всё равно проверяются: путь не может выйти
 * за пределы корня. Запись атомарная — сначала во временный файл рядом, затем rename.
 */
@Component
@LocalStorageEnabled
class LocalFileStore {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStore.class);
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*(/[A-Za-z0-9][A-Za-z0-9._-]*)*");
    private static final String PARENT_SEGMENT = "..";
    private static final String PARTIAL_SUFFIX = ".part-";
    private static final int COPY_BUFFER_BYTES = 64 * 1024;

    private final Path root;

    LocalFileStore(AppProperties properties) {
        this.root = createRoot(properties.storage().localRoot());
        log.info("Local file storage at {}", root);
    }

    /** Ключ допустим, если состоит из безопасных сегментов без «..» и начальной точки. */
    static boolean isValidKey(String key) {
        return key != null && SAFE_KEY.matcher(key).matches() && !key.contains(PARENT_SEGMENT);
    }

    Optional<Path> existing(String key) {
        Path path = resolve(key);
        return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    Optional<Long> sizeOf(String key) {
        return existing(key).map(LocalFileStore::size);
    }

    byte[] readPrefix(String key, int bytes) {
        try (InputStream input = open(key)) {
            return input.readNBytes(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read object prefix", e);
        }
    }

    InputStream open(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (NoSuchFileException e) {
            throw new UncheckedIOException("Object not found", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot open object", e);
        }
    }

    /** Удаление «по возможности», как у S3-реализации: ошибка только логируется. */
    void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("Cannot delete object from local storage: {}", e.getClass().getSimpleName());
        }
    }

    /**
     * Сохраняет тело, только если в нём ровно {@code expectedBytes} байт (читается не больше expectedBytes + 1);
     * иначе объект не создаётся. Существующий объект не перезаписывается.
     */
    boolean writeExactly(String key, InputStream body, long expectedBytes) {
        Path target = resolve(key);
        if (Files.exists(target)) {
            throw new UncheckedIOException(new FileAlreadyExistsException(key));
        }
        Path partial = target.resolveSibling(target.getFileName() + PARTIAL_SUFFIX + UUID.randomUUID());
        try {
            Files.createDirectories(target.getParent());
            if (copyLimited(body, partial, expectedBytes) != expectedBytes) {
                return false;
            }
            moveIntoPlace(partial, target);
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write object", e);
        } finally {
            deleteQuietly(partial);
        }
    }

    private static long copyLimited(InputStream body, Path partial, long maxBytes) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_BYTES];
        long total = 0;
        try (OutputStream output = Files.newOutputStream(partial)) {
            int read;
            while ((read = body.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    return maxBytes + 1;
                }
                output.write(buffer, 0, read);
            }
        }
        return total;
    }

    private static void moveIntoPlace(Path partial, Path target) throws IOException {
        try {
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(partial, target);
        }
    }

    private Path resolve(String key) {
        if (!isValidKey(key)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }

    private static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read object size", e);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Cannot delete partial upload: {}", e.getClass().getSimpleName());
        }
    }

    /** ARCH-05: недоступный каталог хранилища останавливает старт. */
    private static Path createRoot(String configured) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("STORAGE_DRIVER=local requires STORAGE_LOCAL_ROOT");
        }
        Path path = Path.of(configured).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create local storage directory " + path, e);
        }
        if (!Files.isWritable(path)) {
            throw new IllegalStateException("Local storage directory is not writable: " + path);
        }
        return path;
    }
}
