package cn.servicehub.catalog.web;

import cn.servicehub.catalog.application.ServiceCatalogService;
import cn.servicehub.catalog.domain.FormFieldDefinition;
import cn.servicehub.catalog.domain.FormFieldType;
import cn.servicehub.catalog.domain.ServiceCatalogItem;
import cn.servicehub.catalog.domain.StandardTag;
import cn.servicehub.catalog.config.RequesterCatalogForm;
import cn.servicehub.catalog.config.ConfiguredFormField;
import cn.servicehub.catalog.config.ConfigurableFormFieldType;
import cn.servicehub.ticket.domain.TicketType;
import java.util.List;
import java.util.Locale;

/** Requester-facing OpenAPI representation. It intentionally omits routing, SLA and internal rule configuration. */
public record ServiceCatalogPageResponse(List<Item> items, int page, int pageSize, int total) {
    public static Item item(ServiceCatalogItem value, ServiceCatalogService service, List<StandardTag> standardTags) {
        return new Item(value.id(), code(value.id()), value.name(), value.description(), ticketType(value), "GENERAL",
            service.currentFormVersion(value.id()), service.formSchemaHash(value), standardTags.stream().map(Tag::from).toList());
    }

    static Form form(ServiceCatalogItem value, ServiceCatalogService service, List<StandardTag> standardTags) {
        Item item = item(value, service, standardTags);
        return new Form(item, service.currentFormVersion(), service.formSchemaHash(value), value.fields().stream().map(Field::from).toList(),
            new TagPolicy(true, true, 20, standardTags.stream().map(tag -> tagCode(tag.name())).toList()));
    }

    static Form form(RequesterCatalogForm value, ServiceCatalogService service, List<StandardTag> standardTags) {
        Item item = new Item(value.item().id(), code(value.item().id()), value.item().name(), value.item().description(), ticketType(value.item()), "GENERAL",
            value.formVersion(), value.schemaHash(), standardTags.stream().map(Tag::from).toList());
        return new Form(item, value.formVersion(), value.schemaHash(), value.fields().stream().map(Field::from).toList(),
            new TagPolicy(value.tagPolicy().allowStandardTags(), value.tagPolicy().allowFreeTags(), value.tagPolicy().maxTags(), value.tagPolicy().allowedStandardTagCodes()));
    }

    private static String code(String id) {
        String source = id.startsWith("SC-") ? id.substring(3) : id;
        return source.replaceAll("[^A-Za-z0-9]+", "_").toUpperCase(Locale.ROOT);
    }

    private static TicketType ticketType(ServiceCatalogItem item) {
        return item.supportedTicketTypes().stream().sorted(java.util.Comparator.comparing(Enum::name)).findFirst()
            .orElseThrow(IllegalStateException::new);
    }

    static String tagCode(String name) {
        String source = name.replaceFirst("^#", "").replaceAll("[^A-Za-z0-9]+", "_").toUpperCase(Locale.ROOT);
        return "TAG-" + (source.length() >= 3 ? source : "TAG_" + Integer.toHexString(name.hashCode()).toUpperCase(Locale.ROOT));
    }

    public record Item(String id, String code, String name, String summary, TicketType ticketType, String categoryCode,
                       int publishedVersion, String formSchemaHash, List<Tag> tags) { }
    public record Tag(String code, String name, String lifecycleStatus) {
        static Tag from(StandardTag value) { return new Tag(tagCode(value.name()), value.name(), "PUBLISHED"); }
    }
    public record Form(Item serviceCatalogItem, int formVersion, String formSchemaHash, List<Field> fields, TagPolicy tagPolicy) { }
    public record Field(String code, String label, String type, boolean required, String defaultValue, String helpText,
                        int displayOrder, String dictionaryCode, Validation validation, List<Condition> visibleWhen,
                        List<Condition> requiredWhen, String sensitivity, String masking, boolean allowRuleMatching) {
        static Field from(FormFieldDefinition value) {
            return new Field(value.code(), value.label(), apiType(value.type()), value.required(), null, null, value.sortOrder() + 1,
                value.dictionaryCode(), value.maxLength() == null ? null : new Validation(value.maxLength()), List.of(), List.of(), "INTERNAL", "NONE",
                "error_code".equals(value.code()));
        }
        static Field from(ConfiguredFormField value) {
            return new Field(value.code(), value.label(), apiType(value.type()), value.required(), value.defaultValue(), value.helpText(), value.displayOrder(), value.dictionaryCode(),
                value.maxLength() == null ? null : new Validation(value.maxLength()), value.visibleWhen().stream().map(Condition::from).toList(),
                value.requiredWhen().stream().map(Condition::from).toList(), "INTERNAL", "NONE", "error_code".equals(value.code()));
        }
        private static String apiType(FormFieldType type) {
            return switch (type) { case TEXT, CI_ID -> "TEXT"; case SINGLE_SELECT -> "SINGLE_SELECT"; case MULTI_SELECT -> "MULTI_SELECT"; };
        }
        private static String apiType(ConfigurableFormFieldType type) { return type.name(); }
    }
    public record Condition(String fieldCode, String operator, List<String> values) {
        static Condition from(cn.servicehub.catalog.config.FormCondition value) {
            return new Condition(value.fieldCode(), value.operator().name(), value.values());
        }
    }
    public record Validation(Integer maxLength) { }
    public record TagPolicy(boolean allowStandardTags, boolean allowFreeTags, int maxTags, List<String> allowedStandardTagCodes) { }
}
