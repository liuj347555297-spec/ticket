package cn.servicehub.catalog.domain;

import java.util.List;
import java.util.Optional;

public interface ServiceCatalogRepository {
    List<ServiceCatalogItem> findPublishedItems();
    Optional<ServiceCatalogItem> findById(String id);
    Optional<DictionaryDefinition> findDictionary(String code);
    List<StandardTag> findEnabledStandardTags();
    List<KnowledgeCase> findPublishedCases();
    List<CaseMatchRule> findEnabledRules();
    void saveMatchRecord(CaseMatchRecord record);
}
