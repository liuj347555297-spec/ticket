package cn.servicehub.catalog.domain;

public record KnowledgeCase(String id, String title, String resolutionSummary, CatalogPublicationStatus publicationStatus) {
    public boolean isPublished() {
        return publicationStatus == CatalogPublicationStatus.PUBLISHED;
    }
}
