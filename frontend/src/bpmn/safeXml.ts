export const MAX_BPMN_XML_BYTES = 512 * 1024

const BPMN_NAMESPACE = 'http://www.omg.org/spec/BPMN/20100524/MODEL'
const BPMN_DI_NAMESPACE = 'http://www.omg.org/spec/BPMN/20100524/DI'
// Keep this deliberately limited drawing grammar aligned with backend SafeBpmnXml.
const NODE_ELEMENTS = new Set([
  'startEvent', 'endEvent', 'userTask', 'task', 'manualTask', 'serviceTask', 'sendTask',
  'receiveTask', 'businessRuleTask', 'exclusiveGateway', 'parallelGateway', 'inclusiveGateway',
  'eventBasedGateway', 'subProcess', 'intermediateCatchEvent', 'intermediateThrowEvent', 'boundaryEvent',
])
const MODEL_ELEMENTS = new Set([
  ...NODE_ELEMENTS, 'definitions', 'process', 'sequenceFlow', 'incoming', 'outgoing',
  'collaboration', 'participant', 'laneSet', 'lane', 'flowNodeRef', 'association', 'textAnnotation', 'text',
])
const DRAWING_ELEMENTS = new Map<string, Set<string>>([
  [BPMN_NAMESPACE, MODEL_ELEMENTS],
  [BPMN_DI_NAMESPACE, new Set(['BPMNDiagram', 'BPMNPlane', 'BPMNShape', 'BPMNEdge', 'BPMNLabel'])],
  ['http://www.omg.org/spec/DD/20100524/DC', new Set(['Bounds'])],
  ['http://www.omg.org/spec/DD/20100524/DI', new Set(['waypoint'])],
])
const ALLOWED_ATTRIBUTES = new Set([
  'id', 'name', 'targetNamespace', 'exporter', 'exporterVersion', 'isExecutable', 'sourceRef',
  'targetRef', 'processRef', 'default', 'gatewayDirection', 'attachedToRef', 'cancelActivity',
  'triggeredByEvent', 'bpmnElement', 'isHorizontal', 'isExpanded', 'isMarkerVisible',
  'x', 'y', 'width', 'height', 'labelStyle', 'isInterrupting',
])
const REFERENCE_ATTRIBUTES = ['sourceRef', 'targetRef', 'processRef', 'attachedToRef', 'default', 'bpmnElement']

/**
 * Defense in depth for the drawing toolkit, not a replacement for server validation.
 * XML is parsed as data only. Executable metadata and external resource definitions
 * are rejected rather than silently removed and then saved as a different process.
 */
export function validateBpmnXml(xml: string): void {
  if (!xml.trim()) throw new Error('尚未提供 BPMN 流程图。')
  if (xml.length > MAX_BPMN_XML_BYTES || new TextEncoder().encode(xml).byteLength > MAX_BPMN_XML_BYTES) {
    throw new Error('BPMN 文件过大，请使用不超过 512 KiB 的流程图。')
  }
  if (/<!\s*(DOCTYPE|ENTITY)\b/i.test(xml) || /<\?(?!xml\s)/i.test(xml)) {
    throw new Error('流程图不允许包含 DTD、实体声明或外部处理指令。')
  }
  const document = new DOMParser().parseFromString(xml, 'application/xml')
  if (document.getElementsByTagNameNS('*', 'parsererror').length) {
    throw new Error('BPMN XML 格式不正确，请检查文件是否完整。')
  }
  const root = document.documentElement
  if (root.localName !== 'definitions' || root.namespaceURI !== BPMN_NAMESPACE) {
    throw new Error('这不是标准的 BPMN 2.0 definitions 文件。')
  }
  const elements = Array.from(document.getElementsByTagName('*'))
  if (elements.length > 4000) throw new Error('流程图元素过多，请拆分后再导入。')
  if (document.getElementsByTagNameNS(BPMN_NAMESPACE, 'process').length !== 1) {
    throw new Error('本期仅支持一个 process 的基础流程设计，不支持多流程或多泳池。')
  }
  const ids = new Set<string>()
  for (const element of elements) {
    if (!DRAWING_ELEMENTS.get(element.namespaceURI ?? '')?.has(element.localName)) {
      throw new Error(`本期基础设计不支持 ${element.localName}；仅接受受控节点与 DI 布局，不接受执行扩展。`)
    }
    let depth = 0
    for (let parent: Node | null = element; parent; parent = parent.parentNode) {
      if (++depth > 32) throw new Error('流程图嵌套层级过深。')
    }
    const id = element.getAttribute('id')
    if (id) {
      if (!/^[A-Za-z_][A-Za-z0-9_.-]{0,127}$/.test(id) || ids.has(id)) {
        throw new Error('流程图包含重复或不合法的元素 ID（最多 128 位字母、数字、下划线、点和连字符）。')
      }
      ids.add(id)
    }
    if (element.namespaceURI === BPMN_NAMESPACE && NODE_ELEMENTS.has(element.localName) && !id) {
      throw new Error('每个流程节点都必须具有唯一 ID。')
    }
    for (const attribute of Array.from(element.attributes)) {
      if (attribute.namespaceURI === 'http://www.w3.org/2000/xmlns/') continue
      if (attribute.namespaceURI || !ALLOWED_ATTRIBUTES.has(attribute.name)) {
        throw new Error(`本期基础设计不支持属性 ${attribute.name}，请移除扩展配置后再导入。`)
      }
      if (attribute.value.length > 500 || /\$\{|#\{|<|>/.test(attribute.value)) {
        throw new Error('流程图属性内容不符合受控设计规则（最多 500 字，不含表达式或尖括号）。')
      }
      if (attribute.name === 'isExecutable' && !['false', '0'].includes(attribute.value)) {
        throw new Error('本设计器仅接收不可执行设计稿，请将流程 isExecutable 设置为 false。')
      }
      if (['x', 'y', 'width', 'height'].includes(attribute.localName)) {
        const coordinate = Number(attribute.value)
        if (!/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(attribute.value.trim())
          || !Number.isFinite(coordinate) || Math.abs(coordinate) > 1000000
          || (['width', 'height'].includes(attribute.localName) && coordinate <= 0)) {
          throw new Error('流程图布局坐标无效或超出可显示范围。')
        }
      }
    }
  }
  for (const element of elements) {
    for (const attribute of REFERENCE_ATTRIBUTES) {
      if (element.hasAttribute(attribute) && !ids.has(element.getAttribute(attribute) ?? '')) {
        throw new Error('流程图含有不存在的节点或连线引用，请检查连线和 DI 布局。')
      }
    }
    if (element.namespaceURI === BPMN_NAMESPACE && ['incoming', 'outgoing', 'flowNodeRef'].includes(element.localName)
      && !ids.has(element.textContent?.trim() ?? '')) {
      throw new Error('流程图含有不存在的节点或连线引用。')
    }
  }
  if (!document.getElementsByTagNameNS(BPMN_DI_NAMESPACE, 'BPMNDiagram').length) {
    throw new Error('此 BPMN 缺少 DI 布局，无法预览；请使用包含图形布局的文件。')
  }
}
