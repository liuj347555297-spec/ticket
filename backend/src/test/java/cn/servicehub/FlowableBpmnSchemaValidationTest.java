package cn.servicehub;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.InputStream;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.common.engine.api.io.InputStreamProvider;
import org.junit.jupiter.api.Test;

/** Keeps XSD validation in CI even though executable-jar runtime skips duplicate schema loading. */
class FlowableBpmnSchemaValidationTest {

    @Test
    void publishedBpmnResourcesPassFlowableSchemaValidation() {
        assertSchemaValid("processes/ticket-lifecycle.bpmn20.xml");
        assertSchemaValid("processes/controlled-jump-approval.bpmn20.xml");
        assertSchemaValid("processes/handover-confirmation.bpmn20.xml");
        assertSchemaValid("processes/cohandler-confirmation.bpmn20.xml");
        assertSchemaValid("processes/lifecycle-action-approval.bpmn20.xml");
    }

    private void assertSchemaValid(String resource) {
        InputStreamProvider source = () -> {
            InputStream stream = getClass().getClassLoader().getResourceAsStream(resource);
            if (stream == null) throw new IllegalStateException("Published BPMN resource is missing");
            return stream;
        };
        assertDoesNotThrow(() -> new BpmnXMLConverter().validateModel(source), resource);
    }
}
