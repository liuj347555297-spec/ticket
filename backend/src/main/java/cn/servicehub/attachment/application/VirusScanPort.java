package cn.servicehub.attachment.application;

public interface VirusScanPort {
    ScanResult scan(String storageKey, byte[] content);
    record ScanResult(boolean clean, String detail) { }
}
