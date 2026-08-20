package cn.servicehub.catalog.web;

import cn.servicehub.catalog.domain.DictionaryDefinition;
import cn.servicehub.catalog.domain.DictionaryOption;
import java.util.List;

public record DictionaryEntryPageResponse(List<Entry> items, int formVersion) {
    static DictionaryEntryPageResponse from(DictionaryDefinition dictionary, int formVersion) {
        return new DictionaryEntryPageResponse(dictionary.options().stream().filter(DictionaryOption::enabled)
            .map(option -> new Entry(option.code(), option.label(), option.sortOrder() + 1)).toList(), formVersion);
    }
    public record Entry(String code, String label, int displayOrder) { }
}
