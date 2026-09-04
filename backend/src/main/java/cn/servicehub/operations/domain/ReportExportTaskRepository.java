package cn.servicehub.operations.domain;

import java.util.List;
import java.util.Optional;

public interface ReportExportTaskRepository {
    void create(ReportExportTask task);
    Optional<ReportExportTask> findById(String id);
    List<ReportExportTask> pending(int limit);
    boolean claim(String id, long expectedVersion);
    void complete(String id, long expectedVersion, byte[] content, String sha256, String fileName);
    void fail(String id, long expectedVersion, String errorCode);
    boolean recordDownload(String id, long expectedVersion);
}
