/** A non-executable design template. Coordinates are authored BPMN DI, not a UI-only flow. */
export function createEmptyBpmn(): string {
  return `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
  id="Definitions_ServiceHub" targetNamespace="https://servicehub.local/design">
  <bpmn:process id="Process_ServiceHub" name="新流程设计" isExecutable="false">
    <bpmn:startEvent id="StartEvent_Apply" name="发起申请">
      <bpmn:outgoing>Flow_ToReview</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:userTask id="UserTask_Review" name="受理审批">
      <bpmn:incoming>Flow_ToReview</bpmn:incoming>
      <bpmn:outgoing>Flow_ToEnd</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:endEvent id="EndEvent_Complete" name="完成">
      <bpmn:incoming>Flow_ToEnd</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_ToReview" sourceRef="StartEvent_Apply" targetRef="UserTask_Review" />
    <bpmn:sequenceFlow id="Flow_ToEnd" sourceRef="UserTask_Review" targetRef="EndEvent_Complete" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="Diagram_ServiceHub">
    <bpmndi:BPMNPlane id="Plane_ServiceHub" bpmnElement="Process_ServiceHub">
      <bpmndi:BPMNShape id="Shape_Apply" bpmnElement="StartEvent_Apply">
        <dc:Bounds x="160" y="182" width="36" height="36" />
        <bpmndi:BPMNLabel><dc:Bounds x="148" y="225" width="60" height="20" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_Review" bpmnElement="UserTask_Review">
        <dc:Bounds x="290" y="160" width="120" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_Complete" bpmnElement="EndEvent_Complete">
        <dc:Bounds x="504" y="182" width="36" height="36" />
        <bpmndi:BPMNLabel><dc:Bounds x="504" y="225" width="36" height="20" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Edge_ToReview" bpmnElement="Flow_ToReview">
        <di:waypoint x="196" y="200" /><di:waypoint x="290" y="200" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_ToEnd" bpmnElement="Flow_ToEnd">
        <di:waypoint x="410" y="200" /><di:waypoint x="504" y="200" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`
}
