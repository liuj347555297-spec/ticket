package cn.servicehub.attachment.application;

import java.io.InputStream;

/** Provider boundary: implementations must not expose a public URL or accept a client storage key. */
public interface StoragePort {
    void put(String storageKey, byte[] content);
    InputStream open(String storageKey);
    void delete(String storageKey);
}
