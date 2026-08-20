package cn.servicehub.catalog.domain;

public record FormFieldDefinition(String code, String label, FormFieldType type, boolean required,
                                  Integer maxLength, String dictionaryCode, int sortOrder) {
}
