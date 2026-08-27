package cn.servicehub.access.application;

public class BackofficeAccessConflictException extends RuntimeException {
    public BackofficeAccessConflictException() { super("Backoffice access was changed by another administrator"); }
}
