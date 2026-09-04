package cn.servicehub.knowledge.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.servicehub.knowledge.domain.KnowledgeDocument;
import cn.servicehub.knowledge.domain.KnowledgeDocumentVersion;
import cn.servicehub.knowledge.domain.KnowledgePublicationStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeScopeModelTest {
    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void businessVisibleDocumentAndVersionRequireOrganizationAndCatalogScope() {
        assertThrows(IllegalArgumentException.class, () -> document(KnowledgePublicationStatus.PUBLISHED, null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> version(KnowledgePublicationStatus.PENDING_REVIEW, "org-it", List.of()));
        assertDoesNotThrow(() -> document(KnowledgePublicationStatus.PUBLISHED, "org-it", List.of("SC-browser-performance")));
    }

    @Test
    void migrationPendingLegacyRowsRemainLoadableButCannotMasqueradeAsPublished() {
        assertDoesNotThrow(() -> document(KnowledgePublicationStatus.MIGRATION_PENDING, null, List.of()));
        assertDoesNotThrow(() -> version(KnowledgePublicationStatus.MIGRATION_PENDING, null, List.of()));
    }

    private KnowledgeDocument document(KnowledgePublicationStatus status, String organizationId, List<String> catalogIds) {
        return new KnowledgeDocument("KDOC-00000000-0000-0000-0000-000000000001", "title", "BROWSER", List.of(), organizationId,
            catalogIds, status, "KVER-00000000-0000-0000-0000-000000000001", "iam-u-1001", NOW, NOW, null, null, null);
    }

    private KnowledgeDocumentVersion version(KnowledgePublicationStatus status, String organizationId, List<String> catalogIds) {
        return new KnowledgeDocumentVersion("KVER-00000000-0000-0000-0000-000000000001", "KDOC-00000000-0000-0000-0000-000000000001",
            1, "knowledge/test", "text/plain", 4, organizationId, catalogIds, status, null, NOW, null);
    }
}
