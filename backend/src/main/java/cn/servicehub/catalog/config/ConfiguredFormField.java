package cn.servicehub.catalog.config;

import java.util.List;

public record ConfiguredFormField(String code, String label, ConfigurableFormFieldType type, boolean required,
                                  String defaultValue, String helpText, Integer maxLength, String dictionaryCode,
                                  int displayOrder, List<FormCondition> visibleWhen, List<FormCondition> requiredWhen) {
    public ConfiguredFormField {
        visibleWhen = visibleWhen == null ? List.of() : List.copyOf(visibleWhen);
        requiredWhen = requiredWhen == null ? List.of() : List.copyOf(requiredWhen);
    }
}
