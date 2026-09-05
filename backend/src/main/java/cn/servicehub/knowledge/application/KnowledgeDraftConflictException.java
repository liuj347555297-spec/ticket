package cn.servicehub.knowledge.application;

public final class KnowledgeDraftConflictException extends RuntimeException {
    public KnowledgeDraftConflictException() { super("Knowledge draft changed concurrently"); }
}
