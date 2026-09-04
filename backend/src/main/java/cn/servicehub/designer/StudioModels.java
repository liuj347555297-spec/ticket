package cn.servicehub.designer;

import java.time.Instant;
import java.util.List;

/** Design metadata only. No submitted form values or runtime authority are accepted here. */
public final class StudioModels {
    private StudioModels() { }
    public record Option(String value, String label) { }
    public record Field(String id, String code, String label, String control, int controlVersion, boolean required,
                        boolean sensitive, String helpText, List<Option> options, String dictionaryCode) {
        public Field { options = options == null ? List.of() : List.copyOf(options); }
    }
    public record Form(String formId, String code, String name, int revision, String status, List<Field> fields) {
        public Form { fields = fields == null ? List.of() : List.copyOf(fields); }
    }
    public record Binding(String nodeId, String formId, int formRevision, int displayOrder, String mode, boolean requiredOnComplete) { }
    public record Input(long version, String name, String organizationId, String bpmnXml, List<Form> forms,
                        List<Binding> nodeBindings, String reason, String systemCode, String serviceCatalogItemId) {
        public Input { forms = forms == null ? List.of() : List.copyOf(forms); nodeBindings = nodeBindings == null ? List.of() : List.copyOf(nodeBindings); }
        public Input(long version, String name, String organizationId, String bpmnXml, List<Form> forms,
                     List<Binding> nodeBindings, String reason) {
            this(version, name, organizationId, bpmnXml, forms, nodeBindings, reason, null, null);
        }
    }
    public record Draft(String id, long version, String name, String organizationId, String bpmnXml, List<Form> forms,
                        List<Binding> nodeBindings, String reason, String executionMode, Instant updatedAt,
                        String systemCode, String serviceCatalogItemId) {
        public Draft { forms = List.copyOf(forms); nodeBindings = List.copyOf(nodeBindings); }
        public Draft(String id, long version, String name, String organizationId, String bpmnXml, List<Form> forms,
                     List<Binding> nodeBindings, String reason, String executionMode, Instant updatedAt) {
            this(id, version, name, organizationId, bpmnXml, forms, nodeBindings, reason, executionMode, updatedAt, null, null);
        }
        public Input input() { return new Input(version, name, organizationId, bpmnXml, forms, nodeBindings, reason, systemCode, serviceCatalogItemId); }
    }
    public record Summary(String id, String name, String organizationId, long version, String executionMode, Instant updatedAt,
                          String systemCode, String serviceCatalogItemId) {
        public Summary(String id, String name, String organizationId, long version, String executionMode, Instant updatedAt) {
            this(id, name, organizationId, version, executionMode, updatedAt, null, null);
        }
        static Summary from(Draft d) { return new Summary(d.id(), d.name(), d.organizationId(), d.version(), d.executionMode(), d.updatedAt(), d.systemCode(), d.serviceCatalogItemId()); }
    }
}
