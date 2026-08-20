package cn.servicehub.catalog.web;

import cn.servicehub.catalog.domain.DictionaryDefinition;
import cn.servicehub.catalog.domain.DictionaryOption;
import java.util.List;

public record DictionaryResponse(String code, String name, List<Option> options) {
    public static DictionaryResponse from(DictionaryDefinition dictionary) {
        return new DictionaryResponse(dictionary.code(), dictionary.name(), dictionary.options().stream().filter(DictionaryOption::enabled)
            .map(option -> new Option(option.code(), option.label(), option.sortOrder())).toList());
    }
    public record Option(String code, String label, int sortOrder) { }
}
