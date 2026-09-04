export interface BpmnNodeSummary {
  id: string
  name: string
  type: string
}

export interface BpmnCanvasHandle {
  getXml: () => Promise<string>
  importXml: (xml: string) => Promise<boolean>
  renameSelectedNode: (name: string) => void
  fitViewport: () => void
}
