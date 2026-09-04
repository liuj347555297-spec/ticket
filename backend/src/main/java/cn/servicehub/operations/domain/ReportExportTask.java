package cn.servicehub.operations.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/** Query scope is captured at creation and is never replaced by later IAM changes. */
public record ReportExportTask(String id, String requesterIamUserId, String reportType, LocalDate from, LocalDate to,
                               Set<String> organizationScope, boolean unrestrictedScope, Status status,
                               byte[] resultContent, String sha256, String fileName, String errorCode,
                               Instant createdAt, Instant startedAt, Instant completedAt, int downloadCount, long version) {
    public enum Status { PENDING, RUNNING, COMPLETED, FAILED }
    public ReportExportTask { organizationScope=organizationScope==null?Set.of():Set.copyOf(organizationScope); resultContent=resultContent==null?null:resultContent.clone(); }
}
