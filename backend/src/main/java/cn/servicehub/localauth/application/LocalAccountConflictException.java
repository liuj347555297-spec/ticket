package cn.servicehub.localauth.application;

public final class LocalAccountConflictException extends RuntimeException {
    public LocalAccountConflictException() { super("Local account changed concurrently"); }
}
