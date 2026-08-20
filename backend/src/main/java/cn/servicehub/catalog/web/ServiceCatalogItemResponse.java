package cn.servicehub.catalog.web;

import cn.servicehub.catalog.domain.FormFieldDefinition;
import cn.servicehub.catalog.domain.ServiceCatalogItem;
import java.util.List;

public record ServiceCatalogItemResponse(String id, String name, String description, List<String> supportedTicketTypes,
                                         List<Field> fields) {
    public static ServiceCatalogItemResponse from(ServiceCatalogItem item) {
        return new ServiceCatalogItemResponse(item.id(), item.name(), item.description(),
            item.supportedTicketTypes().stream().map(Enum::name).sorted().toList(),
            item.fields().stream().map(Field::from).toList());
    }

    public record Field(String code, String label, String type, boolean required, Integer maxLength,
                        String dictionaryCode, int sortOrder) {
        private static Field from(FormFieldDefinition value) {
            return new Field(value.code(), value.label(), value.type().name(), value.required(), value.maxLength(),
                value.dictionaryCode(), value.sortOrder());
        }
    }
}
