package cn.servicehub.catalog.domain;

import java.util.List;

public record DictionaryDefinition(String code, String name, CatalogPublicationStatus publicationStatus,
                                   List<DictionaryOption> options) {
    public DictionaryDefinition {
        options = options == null ? List.of() : List.copyOf(options);
    }

    public boolean permits(String optionCode) {
        return publicationStatus == CatalogPublicationStatus.PUBLISHED && options.stream()
            .anyMatch(option -> option.enabled() && option.code().equals(optionCode));
    }
}
