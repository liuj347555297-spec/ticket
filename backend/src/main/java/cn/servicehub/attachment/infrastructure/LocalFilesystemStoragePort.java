package cn.servicehub.attachment.infrastructure;

import cn.servicehub.attachment.AttachmentProperties;
import cn.servicehub.attachment.application.StoragePort;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.stereotype.Component;

/** Local development adapter. The root is deliberately outside web/static resources. */
@Component
public class LocalFilesystemStoragePort implements StoragePort {
    private final Path root;
    public LocalFilesystemStoragePort(AttachmentProperties properties) {
        this.root = Path.of(properties.storageRoot()).toAbsolutePath().normalize();
        try { Files.createDirectories(root); } catch (Exception e) { throw new IllegalStateException("Attachment storage is unavailable", e); }
    }
    @Override public void put(String storageKey, byte[] content) {
        Path target = resolve(storageKey);
        try { Files.createDirectories(target.getParent()); Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE); }
        catch (Exception e) { throw new IllegalStateException("Attachment bytes cannot be stored", e); }
    }
    @Override public InputStream open(String storageKey) {
        try { return new ByteArrayInputStream(Files.readAllBytes(resolve(storageKey))); }
        catch (Exception e) { throw new IllegalStateException("Attachment bytes are unavailable", e); }
    }
    @Override public void delete(String storageKey) { try { Files.deleteIfExists(resolve(storageKey)); } catch (Exception ignored) { } }
    private Path resolve(String key) {
        if (key == null || !key.matches("^[a-z0-9][a-z0-9/_-]{0,180}$")) throw new IllegalArgumentException("Invalid storage key");
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("Invalid storage key");
        return resolved;
    }
}
